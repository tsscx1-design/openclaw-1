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
import androidx.compose.foundation.layout.safeDrawing
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
import kotlinx.coroutines.CancellationException
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
  val extractionMode: String,
)

internal data class VoiceSpeakerRow(val name: String, val seconds: Double)

private data class VoiceCaptionAnalysis(val caption: String, val warnings: List<String>)

@Composable
private fun VoiceCaptionAnalysisResult(result: VoiceCaptionAnalysis, onUse: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(result.caption, style = ClawTheme.type.caption, color = ClawTheme.colors.text)
    Text(
      "録音から確認できる範囲の特徴です。短い音声や雑音で精度が変わり、同じ声の再現は保証できません。発音・アクセントの不明点や性格は推測しません。",
      style = ClawTheme.type.caption,
      color = ClawTheme.colors.textMuted,
    )
    result.warnings.forEach { warning ->
      Text(warning, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
    }
    ClawSecondaryButton(text = "文字設定に使う", onClick = onUse)
  }
}

private fun parseVoiceSpeakers(result: JSONObject): List<VoiceSpeakerRow> {
  val values = result.optJSONArray("candidates") ?: JSONArray()
  return (0 until values.length()).mapNotNull { values.optJSONObject(it) }.map {
    VoiceSpeakerRow(it.optString("name"), it.optDouble("seconds"))
  }
}

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
        extractionMode = v.optString("extractionMode"),
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
internal fun VoiceStudioScreen(viewModel: MainViewModel, onBack: () -> Unit, embedded: Boolean = false) {
  // 音声スタジオの中に置くとき（embedded）は、見出し・戻る・画面の余白を、外の画面に任せる
  BackHandler(enabled = !embedded, onBack = onBack)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var voices by remember { mutableStateOf<List<VoiceProjectRow>>(emptyList()) }
  var window by remember { mutableStateOf(1 to 6) }
  var name by remember { mutableStateOf("") }
  var consent by remember { mutableStateOf(false) }
  // 声の許可の確認（出どころの種類と、許可の根拠）。確認を終えると、記録として、声のプロジェクトに残る
  var consentNote by remember { mutableStateOf("") }
  var consentAsk by remember { mutableStateOf<String?>(null) }
  var status by remember { mutableStateOf("") }
  var progress by remember { mutableStateOf<Float?>(null) }
  var importing by remember { mutableStateOf(false) }
  val speakerRows = remember { androidx.compose.runtime.mutableStateMapOf<String, List<VoiceSpeakerRow>>() }
  val captionAnalyses = remember { androidx.compose.runtime.mutableStateMapOf<Pair<String, String>, VoiceCaptionAnalysis>() }
  val captionAnalyzing = remember { androidx.compose.runtime.mutableStateMapOf<Pair<String, String>, Boolean>() }
  val captionErrors = remember { androidx.compose.runtime.mutableStateMapOf<Pair<String, String>, String>() }
  var savingArtifact by remember { mutableStateOf<Triple<String, String, String>?>(null) }
  val playingKey by PartnerVoicePlayer.playingKey.collectAsState()

  // Voice design (a voice from a description).
  var designCaption by remember { mutableStateOf("") }
  // Irodori model for voice design: "small" answers in seconds, "large" is more natural (CPU, ~1-2 min).
  var designModel by remember { mutableStateOf("small") }
  var designText by remember { mutableStateOf("") }
  var designName by remember { mutableStateOf("") }
  var designDraftId by remember { mutableStateOf<String?>(null) }
  var designStatus by remember { mutableStateOf("") }

  // Per-voice actions.
  var iconTargetId by remember { mutableStateOf<String?>(null) }
  var renameTarget by remember { mutableStateOf<VoiceProjectRow?>(null) }
  // "今から学習": confirm first, since training takes the GPU and pauses the chat.
  var trainNowTarget by remember { mutableStateOf<VoiceProjectRow?>(null) }
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

  fun analyzeCaption(voiceId: String, candidateName: String? = null) {
    val key = voiceId to (candidateName ?: "")
    if (captionAnalyzing[key] == true) return
    captionAnalyzing[key] = true
    captionErrors.remove(key)
    scope.launch {
      try {
        val params = JSONObject().put("voiceId", voiceId).put("save", candidateName == null)
        if (candidateName != null) params.put("name", candidateName)
        val result = JSONObject(viewModel.runGatewayRpc(
          GatewayMethod.VoiceCaptionAnalyze, params.toString(), timeoutMs = 300_000,
        ))
        val caption = result.getString("caption")
        check(caption.isNotBlank()) { "声の特徴を取得できませんでした。別の録音で試してください。" }
        val warnings = result.optJSONArray("warnings") ?: JSONArray()
          val savedCaption = result.optJSONObject("voice")?.optString("caption").orEmpty()
        captionAnalyses[key] = VoiceCaptionAnalysis(
            if (candidateName == null && savedCaption.isNotBlank()) savedCaption else caption,
          (0 until warnings.length()).mapNotNull { warnings.optString(it).takeIf(String::isNotBlank) },
        )
        if (candidateName == null) {
          status = "声の特徴を保存しました。既存の文字設定を保持し、音声計測の部分を更新しました。"
          refresh()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        captionErrors[key] = "声の特徴の解析エラー: ${e.message}。音声を確認して再試行してください。"
      } finally {
        captionAnalyzing.remove(key)
      }
    }
  }

  fun useCaption(caption: String) {
    designCaption = caption
    designStatus = "声の特徴を文字設定に取り込みました。上の「文章から声を作る」で編集できます。"
    status = designStatus
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

  suspend fun saveArtifact(uri: Uri, voiceId: String, kind: String, name: String) {
    val stream = context.contentResolver.openOutputStream(uri, "w") ?: error("保存先を開けませんでした")
    stream.use { output ->
      var offset = 0L
      do {
        val res = JSONObject(viewModel.runGatewayRpc(
          GatewayMethod.VoiceArtifactRead,
          JSONObject().put("voiceId", voiceId).put("kind", kind).put("name", name).put("offset", offset).toString(),
          timeoutMs = 120_000,
        ))
        val data = Base64.decode(res.optString("base64"), Base64.DEFAULT)
        withContext(Dispatchers.IO) { output.write(data) }
        val next = res.optLong("next", offset)
        if (next <= offset && next < res.optLong("total")) error("ダウンロードが途中で止まりました")
        offset = next
        status = "保存中: ${"%.0f".format(100.0 * offset / res.optLong("total").coerceAtLeast(1L))}%"
      } while (offset < res.optLong("total"))
    }
  }

  val audioSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri: Uri? ->
    val selected = savingArtifact
    savingArtifact = null
    if (uri != null && selected != null) scope.launch {
      runCatching { saveArtifact(uri, selected.first, selected.second, selected.third) }
        .onSuccess { status = "抽出音声を保存しました" }
        .onFailure { status = "保存エラー: ${it.message}" }
    }
  }
  val modelSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
    val selected = savingArtifact
    savingArtifact = null
    if (uri != null && selected != null) scope.launch {
      runCatching { saveArtifact(uri, selected.first, selected.second, selected.third) }
        .onSuccess { status = "学習済みモデルを保存しました" }
        .onFailure { status = "保存エラー: ${it.message}" }
    }
  }

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
            JSONObject(viewModel.runGatewayRpc(GatewayMethod.VoiceCreate, JSONObject().put("name", name.trim()).put("consent", true).put("consentNote", consentNote).toString()))
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

  val extractionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      importing = true
      try {
        val originalName = displayName(uri)
        if (!originalName.endsWith(".mp4", true) && !originalName.endsWith(".mp3", true)) {
          error("MP4またはMP3を選んでください")
        }
        val fileName = if (originalName.endsWith(".mp4", true)) "source.mp4" else "source.mp3"
        val created = JSONObject(viewModel.runGatewayRpc(
          GatewayMethod.VoiceCreate,
          JSONObject().put("name", name.trim()).put("consent", true).put("extraction", true).put("consentNote", consentNote).toString(),
        )).getJSONObject("voice")
        val voiceId = created.getString("id")
        withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(UPLOAD_CHUNK_BYTES)
            var index = 0
            while (true) {
              val count = input.read(buffer)
              if (count < 0) break
              if (count == 0) continue
              val chunk = Base64.encodeToString(buffer, 0, count, Base64.NO_WRAP)
              withContext(Dispatchers.Main) {
                status = "素材を送信中（${index + 1}チャンク）"
                viewModel.runGatewayRpc(GatewayMethod.VoiceUpload,
                  JSONObject().put("voiceId", voiceId).put("fileName", fileName)
                    .put("chunkIndex", index).put("chunkCount", 1).put("dataBase64", chunk).toString(),
                  timeoutMs = 120_000)
              }
              index++
            }
            if (index == 0) error("ファイルが空です")
          } ?: error("ファイルを開けませんでした")
        }
        status = "ボーカルと話者を解析しています。初回はモデルの準備に時間がかかります…"
        val result = JSONObject(viewModel.runGatewayRpc(GatewayMethod.VoiceSpeakerAnalyze,
          JSONObject().put("voiceId", voiceId).toString(), timeoutMs = 3_600_000))
        speakerRows[voiceId] = parseVoiceSpeakers(result)
        status = "話者を分けました。下から試聴して、使う声を選んでください。"
        name = ""
        consent = false
      } catch (e: Exception) {
        status = "話者の抽出エラー: ${e.message}"
      } finally {
        importing = false
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

  consentAsk?.let { which ->
    VoiceConsentDialog(
      onConfirm = { note ->
        consentNote = note
        consentAsk = null
        if (which == "upload") picker.launch("audio/*") else extractionPicker.launch(arrayOf("audio/mpeg", "video/mp4", "audio/*", "video/*"))
      },
      onDismiss = { consentAsk = null },
    )
  }
  ClawScaffold(
    contentPadding = if (embedded) PaddingValues(0.dp) else PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 6.dp),
    contentWindowInsets = if (embedded) androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) else androidx.compose.foundation.layout.WindowInsets.Companion.safeDrawing,
  ) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        if (!embedded) Row(verticalAlignment = Alignment.CenterVertically) {
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
              minLines = 4,
              modifier = Modifier.fillMaxWidth(),
            )
            Text("200文字での切り詰めはありません（保存は最大16,000文字）。高さ・響き・息っぽさ・滑舌・速度・語尾などを具体的に書いてください。生成モデルの上限を超えた場合は案内を表示します。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            ClawTextField(
              value = designText,
              onValueChange = { designText = it },
              placeholder = "試しに話させる文（空欄なら自動）。🥵 喘ぎ・😮‍💨 吐息・👂 囁きなどの絵文字も使えます",
              modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              ClawSecondaryButton(
                text = (if (designModel == "small") "● " else "") + "速い（数秒）",
                onClick = { designModel = "small" },
                modifier = Modifier.weight(1f),
              )
              ClawSecondaryButton(
                text = (if (designModel == "large") "● " else "") + "高品質（1〜2分）",
                onClick = { designModel = "large" },
                modifier = Modifier.weight(1f),
              )
            }
            val designing = playingKey == DESIGN_KEY && designStatus.startsWith("作")
            ClawPrimaryButton(
              text = if (designing) "作っています…（30秒ほど）" else if (designDraftId == null) "🎨 この説明で声を作ってみる" else "🔄 もう一度作る（別の声）",
              onClick = {
                designStatus = "作っています…"
                playFromPc(
                  DESIGN_KEY,
                  GatewayMethod.VoiceDesignTry,
                  JSONObject().put("caption", designCaption.trim()).put("text", designText.trim()).put("model", designModel),
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
              onClick = { consentAsk = "upload" },
              enabled = consent && name.isNotBlank() && progress == null && !importing,
              modifier = Modifier.fillMaxWidth(),
            )
            ClawSecondaryButton(
              text = "🎬 MP4 / MP3から話者ごとに声を抽出",
              onClick = { consentAsk = "extract" },
              enabled = consent && name.isNotBlank() && !importing && progress == null,
              modifier = Modifier.fillMaxWidth(),
            )
            Text("BGMや雑音を低減し、話者ごとに分けます。重なりを検出できた区間は除外します。本人または許可を得た声だけを選んでください。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
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
      if (status.isNotBlank()) item { Text(status, style = ClawTheme.type.caption, color = ClawTheme.colors.primary) }
      if (voices.isEmpty()) {
        item { Text("まだありません", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted) }
      }
      items(voices, key = { it.id }) { v ->
        val previewKey = "voice-${v.id}"
        val captionKey = v.id to ""
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
                  if (v.source == "design") "🎨 文章から作った声"
                  else if (v.extractionMode == "pending") "🎬 話者の選択待ち"
                  else "🎤 録音 ${v.fileCount}ファイル・${"%.1f".format(v.totalMb)}MB",
                  style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                  color = ClawTheme.colors.textMuted,
                )
                Text(voiceStatusLabel(v.status), style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted)
              }
            }
            if (v.caption.isNotBlank()) Text("「${v.caption}」", style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted)
            val analysis = captionAnalyses[captionKey]
            if (analysis != null) {
              VoiceCaptionAnalysisResult(analysis, onUse = { useCaption(analysis.caption) })
            } else if (v.caption.isNotBlank()) {
              ClawSecondaryButton(text = "文字設定に使う", onClick = { useCaption(v.caption) })
            }
            captionErrors[captionKey]?.let {
              Text(it, style = ClawTheme.type.caption, color = ClawTheme.colors.danger)
            }
            if (v.status == "training" || v.status == "queued") Text(v.step, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
            if (v.error.isNotBlank()) Text(v.error, style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.danger)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              if (v.extractionMode.isNotBlank()) {
                ClawSecondaryButton(text = "🗣 話者一覧", onClick = {
                  scope.launch {
                    runCatching {
                      val result = JSONObject(viewModel.runGatewayRpc(GatewayMethod.VoiceSpeakerList,
                        JSONObject().put("voiceId", v.id).toString()))
                      speakerRows[v.id] = parseVoiceSpeakers(result)
                    }.onFailure { status = "話者一覧エラー: ${it.message}" }
                  }
                })
              }
              if (v.extractionMode == "pending") {
                ClawSecondaryButton(text = "🔄 話者を解析", onClick = {
                  scope.launch {
                    status = "ボーカルと話者を解析しています…"
                    runCatching {
                      val result = JSONObject(viewModel.runGatewayRpc(GatewayMethod.VoiceSpeakerAnalyze,
                        JSONObject().put("voiceId", v.id).toString(), timeoutMs = 3_600_000))
                      speakerRows[v.id] = parseVoiceSpeakers(result)
                    }.onSuccess { status = "話者を分けました" }
                      .onFailure { status = "解析エラー: ${it.message}" }
                  }
                })
              }
              if (v.status == "ready") {
                ClawSecondaryButton(text = "⬇ 学習済みモデル", onClick = {
                  savingArtifact = Triple(v.id, "model", "")
                  modelSaver.launch("${v.name}.aivmx")
                })
              }
              if (v.fileCount > 0 && v.extractionMode != "pending") {
                ClawSecondaryButton(
                  text = if (captionAnalyzing[captionKey] == true) "声の特徴を解析して保存中…" else "声の特徴を解析して保存",
                  onClick = { analyzeCaption(v.id) },
                  enabled = captionAnalyzing[captionKey] != true,
                )
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
              if (v.source != "design" && v.extractionMode != "pending" && (v.status == "uploading" || v.status == "failed")) {
                ClawSecondaryButton(text = "🌙 今夜学習", onClick = {
                  scope.launch {
                    runCatching { viewModel.runGatewayRpc(GatewayMethod.VoiceQueue, JSONObject().put("voiceId", v.id).toString()) }
                      .onFailure { status = "予約エラー: ${it.message}" }
                    refresh()
                  }
                })
              }
              if (v.source != "design" && v.extractionMode != "pending" && (v.status == "uploading" || v.status == "failed" || v.status == "queued")) {
                ClawSecondaryButton(text = "⚡ 今から学習", onClick = { trainNowTarget = v })
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
            speakerRows[v.id]?.forEachIndexed { index, candidate ->
              val candidateCaptionKey = v.id to candidate.name
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("話者${index + 1}・${"%.0f".format(candidate.seconds)}秒", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
                ClawSecondaryButton(text = if (playingKey == "speaker-${v.id}-${candidate.name}") "⏹" else "▶", onClick = {
                  playFromPc("speaker-${v.id}-${candidate.name}", GatewayMethod.VoiceSpeakerPreview,
                    JSONObject().put("voiceId", v.id).put("name", candidate.name))
                })
                if ((v.extractionMode == "pending" || v.extractionMode == "selected") && v.status == "uploading") ClawSecondaryButton(text = "この声を使う", onClick = {
                  scope.launch {
                    runCatching { viewModel.runGatewayRpc(GatewayMethod.VoiceSpeakerSelect,
                      JSONObject().put("voiceId", v.id).put("name", candidate.name).toString()) }
                      .onSuccess { status = "話者${index + 1}を選びました。試し聞きや学習に使えます。" }
                      .onFailure { status = "選択エラー: ${it.message}" }
                    refresh()
                  }
                })
                ClawSecondaryButton(text = "⬇", onClick = {
                  savingArtifact = Triple(v.id, "candidate", candidate.name)
                  audioSaver.launch("${v.name}-${candidate.name}")
                })
                ClawSecondaryButton(
                  text = if (captionAnalyzing[candidateCaptionKey] == true) "声の特徴を解析中…" else "声の特徴を解析",
                  onClick = { analyzeCaption(v.id, candidate.name) },
                  enabled = candidate.name.isNotBlank() && captionAnalyzing[candidateCaptionKey] != true,
                )
              }
              captionAnalyses[candidateCaptionKey]?.let { result ->
                Text("話者${index + 1}の解析（選択中の声は変更しません）", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                VoiceCaptionAnalysisResult(result, onUse = { useCaption(result.caption) })
              }
              captionErrors[candidateCaptionKey]?.let {
                Text(it, style = ClawTheme.type.caption, color = ClawTheme.colors.danger)
              }
            }
          }
        }
      }
    }
  }

  trainNowTarget?.let { target ->
    AlertDialog(
      onDismissRequest = { trainNowTarget = null },
      title = { Text("「${target.name}」を今から学習しますか？") },
      text = {
        Text(
          "学習中は GPU を使うので、チャットと画像・動画の生成が止まります（録音の長さによって30分〜1時間ほど）。終わるとスマホに通知が届きます。",
          color = ClawTheme.colors.textMuted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          scope.launch {
            runCatching {
              viewModel.runGatewayRpc(GatewayMethod.VoiceQueue, JSONObject().put("voiceId", target.id).put("now", true).toString())
            }.onSuccess { status = "学習を始めました。終わるとスマホに通知が届きます。" }
              .onFailure { status = "学習を始められませんでした: ${it.message}" }
            trainNowTarget = null
            refresh()
          }
        }) { Text("今から学習") }
      },
      dismissButton = { TextButton(onClick = { trainNowTarget = null }) { Text("やめる") } },
    )
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
