package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawSectionHeader
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.convertUriToBase64DataUrl
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Bytes per voice.upload call; keeps each Gateway frame small on slow links. */
private const val UPLOAD_CHUNK_BYTES = 384 * 1024

/** Key the shared player uses while a voice-design draft is made or played. */
private const val DESIGN_KEY = "voice-design"

internal data class VoiceProjectRow(
  val id: String,
  val name: String,
  val status: String,
  val step: String,
  val error: String,
  val fileCount: Int,
  val totalMb: Double,
  /** AivisSpeech style of the night-trained version, once ready. */
  val speakerId: Int?,
  val iconBase64: String,
  /** "design" = made from a description; otherwise recordings. */
  val source: String,
  val caption: String,
)

internal fun parseVoiceProjects(json: String): Pair<List<VoiceProjectRow>, Pair<Int, Int>> {
  val obj = JSONObject(json)
  val arr = obj.optJSONArray("voices") ?: JSONArray()
  val rows =
    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { v ->
      val files = v.optJSONArray("files") ?: JSONArray()
      VoiceProjectRow(
        id = v.optString("id"),
        name = v.optString("name"),
        status = v.optString("status"),
        step = v.optString("step"),
        error = v.optString("error"),
        fileCount = files.length(),
        totalMb = (0 until files.length()).sumOf { files.optJSONObject(it)?.optLong("bytes") ?: 0L } / 1_048_576.0,
        speakerId = if (v.has("speakerId")) v.optInt("speakerId") else null,
        iconBase64 = v.optString("iconBase64"),
        source = v.optString("source"),
        caption = v.optString("caption"),
      )
    }
  val settings = obj.optJSONObject("settings")
  return rows to ((settings?.optInt("windowStartHour", 1) ?: 1) to (settings?.optInt("windowEndHour", 6) ?: 6))
}

private fun voiceStatusLabel(status: String): String =
  when (status) {
    "uploading" -> "📤 声まねで使えます"
    "queued" -> "🌙 夜の学習待ち"
    "training" -> "🔥 学習中"
    "ready" -> "✅ 学習済み"
    "failed" -> "❌ 学習失敗"
    else -> status
  }

/** Round picture of a voice; a microphone when it has none. */
@Composable
internal fun VoiceIcon(iconBase64: String, size: Dp) {
  val bitmap =
    remember(iconBase64) {
      if (iconBase64.isBlank()) {
        null
      } else {
        runCatching {
          val bytes = Base64.decode(iconBase64, Base64.DEFAULT)
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
      }
    }
  Box(
    modifier = Modifier.size(size).clip(CircleShape).background(ClawTheme.colors.surfaceRaised),
    contentAlignment = Alignment.Center,
  ) {
    if (bitmap != null) {
      Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
      Text("🎙", fontSize = (size.value * 0.45f).sp)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VoiceStudioScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var voices by remember { mutableStateOf<List<VoiceProjectRow>>(emptyList()) }
  var window by remember { mutableStateOf(1 to 6) }
  var name by remember { mutableStateOf("") }
  var consent by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf("") }
  var progress by remember { mutableStateOf<Float?>(null) }
  val playingKey by PartnerVoicePlayer.playingKey.collectAsState()

  // Voice design (a voice from a description).
  var designCaption by remember { mutableStateOf("") }
  var designText by remember { mutableStateOf("") }
  var designName by remember { mutableStateOf("") }
  var designDraftId by remember { mutableStateOf<String?>(null) }
  var designStatus by remember { mutableStateOf("") }

  // Per-voice actions.
  var iconTargetId by remember { mutableStateOf<String?>(null) }
  var renameTarget by remember { mutableStateOf<VoiceProjectRow?>(null) }
  var renameText by remember { mutableStateOf("") }
  var applyTarget by remember { mutableStateOf<VoiceProjectRow?>(null) }
  var partners by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

  suspend fun refresh() {
    runCatching {
      val (rows, w) = parseVoiceProjects(viewModel.runGatewayRpc(GatewayMethod.VoiceList, "{}"))
      voices = rows
      window = w
    }.onFailure { status = "読み込みエラー: ${it.message}" }
  }

  LaunchedEffect(Unit) {
    while (true) {
      refresh()
      delay(10_000)
    }
  }

  fun displayName(uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    } ?: "recording.m4a"

  /** Asks the PC for audio under [key] and plays it; the same key stops it again. */
  fun playFromPc(key: String, method: GatewayMethod, params: JSONObject, onResult: (JSONObject) -> Unit = {}) {
    if (playingKey == key) {
      PartnerVoicePlayer.stop()
      return
    }
    PartnerVoicePlayer.markPending(key)
    scope.launch {
      try {
        val res = JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = 300_000))
        onResult(res)
        PartnerVoicePlayer.playBase64(context, res.optString("audioBase64"), key)
      } catch (e: Exception) {
        PartnerVoicePlayer.stop()
        status = "再生エラー: ${e.message}"
        designStatus = ""
      }
    }
  }

  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
      if (uris.isEmpty()) return@rememberLauncherForActivityResult
      scope.launch {
        try {
          val created =
            JSONObject(viewModel.runGatewayRpc(GatewayMethod.VoiceCreate, JSONObject().put("name", name.trim()).put("consent", true).toString()))
              .getJSONObject("voice")
          val voiceId = created.getString("id")
          uris.forEachIndexed { fileIndex, uri ->
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: return@forEachIndexed
            val fileName = displayName(uri)
            val chunks = (bytes.size + UPLOAD_CHUNK_BYTES - 1) / UPLOAD_CHUNK_BYTES
            for (i in 0 until chunks) {
              val part = bytes.copyOfRange(i * UPLOAD_CHUNK_BYTES, minOf(bytes.size, (i + 1) * UPLOAD_CHUNK_BYTES))
              val params =
                JSONObject()
                  .put("voiceId", voiceId)
                  .put("fileName", fileName)
                  .put("chunkIndex", i)
                  .put("chunkCount", chunks)
                  .put("dataBase64", Base64.encodeToString(part, Base64.NO_WRAP))
              viewModel.runGatewayRpc(GatewayMethod.VoiceUpload, params.toString(), timeoutMs = 60_000)
              progress = (fileIndex + (i + 1f) / chunks) / uris.size
              status = "アップロード中: $fileName (${i + 1}/$chunks)"
            }
          }
          status = "アップロード完了。下の一覧ですぐ試し聞きできます。より自然にするなら「今夜学習」を押してください。"
          name = ""
          consent = false
        } catch (e: Exception) {
          status = "アップロードエラー: ${e.message}"
        } finally {
          progress = null
          refresh()
        }
      }
    }

  val iconPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      val voiceId = iconTargetId
      iconTargetId = null
      if (uri == null || voiceId == null) return@rememberLauncherForActivityResult
      scope.launch {
        val dataUrl = withContext(Dispatchers.IO) { convertUriToBase64DataUrl(context, uri, 256) }
        if (dataUrl == null) {
          status = "この画像は読み込めませんでした。"
          return@launch
        }
        runCatching {
          viewModel.runGatewayRpc(
            GatewayMethod.VoiceIconSet,
            JSONObject().put("voiceId", voiceId).put("imageBase64", dataUrl.substringAfter(",")).toString(),
          )
        }.onFailure { status = "写真の保存エラー: ${it.message}" }
        refresh()
      }
    }

  ClawScaffold(contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 6.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nativeString("Back"), onClick = onBack)
          Text("🎙 声を作る", style = ClawTheme.type.display.copy(fontSize = 20.sp), color = ClawTheme.colors.text)
        }
        Text(
          "パートナー用の声を作ります。録音を入れるか、文章で声を説明するとすぐ「声まね」で話せます（約5秒で読み上げ）。より自然にしたい録音の声は、夜の空き時間にPCが学習します（その間メインのチャットは止まります）。",
          style = ClawTheme.type.caption.copy(fontSize = 12.5.sp),
          color = ClawTheme.colors.textMuted,
        )
      }

      // --- 声デザイン: 文章から声を作る（録音なし・実在の人の声ではない） ---
      item {
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎨 文章から声を作る（声デザイン）", style = ClawTheme.type.section.copy(fontSize = 15.sp), color = ClawTheme.colors.text)
            ClawTextField(
              value = designCaption,
              onValueChange = { designCaption = it },
              placeholder = "例: 明るくて少し甘えた感じの20代の女性。やわらかく、ゆっくり話す。",
              minLines = 2,
              modifier = Modifier.fillMaxWidth(),
            )
            ClawTextField(
              value = designText,
              onValueChange = { designText = it },
              placeholder = "試しに話させる文（空欄なら自動）",
              modifier = Modifier.fillMaxWidth(),
            )
            val designing = playingKey == DESIGN_KEY && designStatus.startsWith("作")
            ClawPrimaryButton(
              text = if (designing) "作っています…（30秒ほど）" else if (designDraftId == null) "🎨 この説明で声を作ってみる" else "🔄 もう一度作る（別の声）",
              onClick = {
                designStatus = "作っています…"
                playFromPc(
                  DESIGN_KEY,
                  GatewayMethod.VoiceDesignTry,
                  JSONObject().put("caption", designCaption.trim()).put("text", designText.trim()),
                ) { res ->
                  designDraftId = res.optString("draftId").ifBlank { null }
                  designStatus = "できました。気に入ったら名前を付けて保存してください。"
                }
              },
              enabled = designCaption.isNotBlank() && !designing,
              modifier = Modifier.fillMaxWidth(),
            )
            if (designStatus.isNotBlank()) Text(designStatus, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
            if (designDraftId != null) {
              ClawTextField(value = designName, onValueChange = { designName = it }, placeholder = "声の名前（例: ルナの声）", modifier = Modifier.fillMaxWidth())
              ClawSecondaryButton(
                text = "💾 この声を保存する",
                onClick = {
                  val draft = designDraftId ?: return@ClawSecondaryButton
                  scope.launch {
                    runCatching {
                      viewModel.runGatewayRpc(
                        GatewayMethod.VoiceDesignSave,
                        JSONObject().put("draftId", draft).put("name", designName.trim()).put("caption", designCaption.trim()).toString(),
                      )
                    }.onSuccess {
                      designStatus = "保存しました。下の「作った声」から写真を付けたりパートナーに適用できます。"
                      designDraftId = null
                      designName = ""
                    }.onFailure { designStatus = "保存エラー: ${it.message}" }
                    refresh()
                  }
                },
                enabled = designName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }

      // --- 録音から作る（本人の声 or 許可を得た声のみ） ---
      item {
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎤 録音から作る", style = ClawTheme.type.section.copy(fontSize = 15.sp), color = ClawTheme.colors.text)
            ClawTextField(value = name, onValueChange = { name = it }, placeholder = "声の名前（例: ルナの声）", modifier = Modifier.fillMaxWidth())
            Text(
              "声まねは10秒ほどの録音で使えます。夜の学習には合計10〜30分。1人の声だけ、BGMなし。MP3/M4A/WAVなど。",
              style = ClawTheme.type.caption.copy(fontSize = 12.sp),
              color = ClawTheme.colors.textMuted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(checked = consent, onCheckedChange = { consent = it })
              Text(
                "自分の声、または本人の許可を得た声です（芸能人・声優などの声は使えません）",
                style = ClawTheme.type.caption.copy(fontSize = 12.sp),
                color = ClawTheme.colors.text,
              )
            }
            ClawPrimaryButton(
              text = "録音ファイルを選んでアップロード",
              onClick = { picker.launch("audio/*") },
              enabled = consent && name.isNotBlank() && progress == null,
              modifier = Modifier.fillMaxWidth(),
            )
            progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            if (status.isNotBlank()) Text(status, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
          }
        }
      }
      item {
        ClawPanel {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              "学習する時間帯: 毎晩 ${window.first}時〜${window.second}時",
              style = ClawTheme.type.body,
              color = ClawTheme.colors.text,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
              val next = (window.first + 23) % 24 to window.second
              scope.launch {
                runCatching { viewModel.runGatewayRpc(GatewayMethod.VoiceSettingsSet, JSONObject().put("windowStartHour", next.first).toString()) }
                refresh()
              }
            }) { Text("開始−1h") }
            TextButton(onClick = {
              val next = window.first to (window.second + 1) % 24
              scope.launch {
                runCatching { viewModel.runGatewayRpc(GatewayMethod.VoiceSettingsSet, JSONObject().put("windowEndHour", next.second).toString()) }
                refresh()
              }
            }) { Text("終了+1h") }
          }
        }
      }
      item { ClawSectionHeader(title = "作った声") }
      if (voices.isEmpty()) {
        item { Text("まだありません", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted) }
      }
      items(voices, key = { it.id }) { v ->
        val previewKey = "voice-${v.id}"
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(modifier = Modifier.clip(CircleShape).clickable { iconTargetId = v.id; iconPicker.launch("image/*") }) {
                VoiceIcon(v.iconBase64, 52.dp)
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(v.name, style = ClawTheme.type.body.copy(fontSize = 15.sp), color = ClawTheme.colors.text)
                Text(
                  if (v.source == "design") "🎨 文章から作った声" else "🎤 録音 ${v.fileCount}ファイル・${"%.1f".format(v.totalMb)}MB",
                  style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                  color = ClawTheme.colors.textMuted,
                )
                Text(voiceStatusLabel(v.status), style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted)
              }
            }
            if (v.caption.isNotBlank()) Text("「${v.caption}」", style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted)
            if (v.status == "training" || v.status == "queued") Text(v.step, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
            if (v.error.isNotBlank()) Text(v.error, style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.danger)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              if (v.fileCount > 0) {
                ClawSecondaryButton(
                  text = if (playingKey == previewKey) "⏹ 止める" else "▶ 試し聞き",
                  onClick = { playFromPc(previewKey, GatewayMethod.VoicePreview, JSONObject().put("voiceId", v.id)) },
                )
                ClawSecondaryButton(text = "👤 パートナーに適用", onClick = {
                  applyTarget = v
                  scope.launch {
                    runCatching {
                      val arr = JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerList, "{}")).optJSONArray("partners") ?: JSONArray()
                      partners = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { it.optString("id") to it.optString("name") }
                    }.onFailure { status = "パートナー一覧の読み込みエラー: ${it.message}" }
                  }
                })
              }
              ClawSecondaryButton(text = "✏️ 名前", onClick = { renameTarget = v; renameText = v.name })
              ClawSecondaryButton(text = "🖼 写真", onClick = { iconTargetId = v.id; iconPicker.launch("image/*") })
              if (v.source != "design" && (v.status == "uploading" || v.status == "failed")) {
                ClawSecondaryButton(text = "🌙 今夜学習", onClick = {
                  scope.launch {
                    runCatching { viewModel.runGatewayRpc(GatewayMethod.VoiceQueue, JSONObject().put("voiceId", v.id).toString()) }
                      .onFailure { status = "予約エラー: ${it.message}" }
                    refresh()
                  }
                })
              }
              if (v.status != "training") {
                ClawSecondaryButton(text = "削除", onClick = {
                  scope.launch {
                    runCatching { viewModel.runGatewayRpc(GatewayMethod.VoiceDelete, JSONObject().put("voiceId", v.id).toString()) }
                    refresh()
                  }
                })
              }
            }
          }
        }
      }
    }
  }

  renameTarget?.let { target ->
    AlertDialog(
      onDismissRequest = { renameTarget = null },
      title = { Text("声の名前") },
      text = { ClawTextField(value = renameText, onValueChange = { renameText = it }, placeholder = "声の名前", modifier = Modifier.fillMaxWidth()) },
      confirmButton = {
        TextButton(onClick = {
          scope.launch {
            runCatching {
              viewModel.runGatewayRpc(GatewayMethod.VoiceRename, JSONObject().put("voiceId", target.id).put("name", renameText.trim()).toString())
            }.onFailure { status = "名前の変更エラー: ${it.message}" }
            renameTarget = null
            refresh()
          }
        }, enabled = renameText.isNotBlank()) { Text("保存") }
      },
      dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("やめる") } },
    )
  }

  applyTarget?.let { target ->
    AlertDialog(
      onDismissRequest = { applyTarget = null },
      title = { Text("「${target.name}」をどのパートナーに？") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          if (partners.isEmpty()) Text("読み込み中…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          partners.forEach { (partnerId, partnerName) ->
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(ClawTheme.colors.surfaceRaised)
                  .clickable {
                    // The night-trained version is faster and steadier once it exists.
                    val voice =
                      if (target.status == "ready" && target.speakerId != null) {
                        JSONObject().put("engine", "aivis").put("speakerId", target.speakerId).put("label", "${target.name}（自作）")
                      } else {
                        JSONObject().put("engine", "clone").put("speakerId", 0).put("voiceId", target.id).put("label", "${target.name}（声まね）")
                      }
                    scope.launch {
                      runCatching {
                        viewModel.runGatewayRpc(
                          GatewayMethod.PartnerSettingsSet,
                          JSONObject().put("partnerId", partnerId).put("settings", JSONObject().put("voice", voice)).toString(),
                        )
                      }.onSuccess { status = "「$partnerName」の声を「${target.name}」にしました。" }
                        .onFailure { status = "適用エラー: ${it.message}" }
                      applyTarget = null
                    }
                  }.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
              Text(partnerName, style = ClawTheme.type.body, color = ClawTheme.colors.text)
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { applyTarget = null }) { Text("閉じる") } },
    )
  }
}
