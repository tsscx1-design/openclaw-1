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
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

internal data class VoiceProjectRow(
  val id: String,
  val name: String,
  val status: String,
  val step: String,
  val error: String,
  val fileCount: Int,
  val totalMb: Double,
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
      )
    }
  val settings = obj.optJSONObject("settings")
  return rows to ((settings?.optInt("windowStartHour", 1) ?: 1) to (settings?.optInt("windowEndHour", 6) ?: 6))
}

private fun voiceStatusLabel(status: String): String =
  when (status) {
    "uploading" -> "📤 アップロード済み（予約前）"
    "queued" -> "🌙 夜の学習待ち"
    "training" -> "🔥 学習中"
    "ready" -> "✅ 完成"
    "failed" -> "❌ 失敗"
    else -> status
  }

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
          status = "アップロード完了。下の一覧で「今夜作る」を押してください。"
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

  ClawScaffold(contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 6.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nativeString("Back"), onClick = onBack)
          Text("🎙 声を作る", style = ClawTheme.type.display.copy(fontSize = 20.sp), color = ClawTheme.colors.text)
        }
        Text(
          "録音から、パートナー用のオリジナルの声を作ります。録音を入れるとすぐ「声まね」としてパートナーの声に選べます（3秒以上・雑音の少ない声）。より自然な声の学習は夜の空き時間にPCが自動で行い、その間メインのチャットは止まります。",
          style = ClawTheme.type.caption.copy(fontSize = 12.5.sp),
          color = ClawTheme.colors.textMuted,
        )
      }
      item {
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("新しい声", style = ClawTheme.type.section.copy(fontSize = 15.sp), color = ClawTheme.colors.text)
            ClawTextField(value = name, onValueChange = { name = it }, placeholder = "声の名前（例: ルナの声）", modifier = Modifier.fillMaxWidth())
            Text(
              "録音のコツ: 合計10〜30分、1人の声だけ、BGMなし。静かな部屋で、いろいろな文を自然に。MP3/M4A/WAVなど。",
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
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(v.name, style = ClawTheme.type.body, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
              Text(voiceStatusLabel(v.status), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            }
            Text("${v.fileCount}ファイル・${"%.1f".format(v.totalMb)}MB", style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted)
            if (v.status == "training" || v.status == "queued") Text(v.step, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
            if (v.error.isNotBlank()) Text(v.error, style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.danger)
            if (v.status == "ready") {
              Text("パートナー編集の「声を選ぶ」に「${v.name}（自作）」として出ます。", style = ClawTheme.type.caption, color = ClawTheme.colors.success)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (v.status == "uploading" || v.status == "failed") {
                ClawSecondaryButton(text = "🌙 今夜作る", onClick = {
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
}
