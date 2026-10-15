package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawSectionHeader
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 入れ替える1人分。顔の持ち主（図鑑のキャラか、送った写真）と、動画の中の何番目の顔と入れ替えるか。 */
internal data class SwapRow(
  val characterId: String? = null,
  val inputId: String? = null,
  val label: String = "",
  /** 動画で左から何番目の顔か（0始まり）。null なら全員。 */
  val position: Int? = null,
)

/** 外へ送る「入れ替える顔」の一覧（顔を選んでいない行は除く）。 */
internal fun swapSourceMaps(rows: List<SwapRow>): List<Map<String, Any>> =
  rows.filter { it.characterId != null || it.inputId != null }.map { row ->
    buildMap {
      row.characterId?.let { put("characterId", it) }
      row.inputId?.let { put("inputId", it) }
      row.position?.let { put("position", it) }
    }
  }

private fun swapSourcesJson(rows: List<SwapRow>): JSONArray =
  JSONArray().also { array -> swapSourceMaps(rows).forEach { map -> array.put(JSONObject(map)) } }

/** 秒数を「約1時間5分」「約12分30秒」「約45秒」の形にする。 */
internal fun formatSwapDuration(seconds: Int): String {
  val sec = seconds.coerceAtLeast(0)
  return when {
    sec >= 3600 -> "約${sec / 3600}時間${(sec % 3600) / 60}分"
    sec >= 60 -> "約${sec / 60}分${if (sec % 60 >= 5) "${sec % 60}秒" else ""}"
    else -> "約${sec}秒"
  }
}

/** 入力欄の文字（秒）を数値にする。空や不正なら null。 */
internal fun parseSeconds(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it >= 0 }

private const val UPLOAD_CHUNK_BYTES = 2 * 1024 * 1024
private val QUALITIES = listOf("fast" to "はやい", "standard" to "ふつう", "high" to "きれい")

private class SwapEstimateView(
  val jobSec: Int,
  val waitSec: Int,
  val totalSec: Int,
  val frames: Int,
  val passes: Int,
  val videoSec: Int,
  val measured: Boolean,
)

private fun displayName(context: Context, uri: Uri): String {
  context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) cursor.getString(0)?.let { return it }
  }
  return uri.lastPathSegment ?: "file"
}

/** 受け取った動画を、端末の Movies/OpenClaw に保存する。成功したら、その場所を返す。 */
private fun saveVideoToDevice(context: Context, file: File, mime: String): Uri? {
  val image = mime.startsWith("image/")
  val values =
    ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
      put(MediaStore.MediaColumns.MIME_TYPE, mime)
      put(MediaStore.MediaColumns.RELATIVE_PATH, (if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES) + "/OpenClaw")
    }
  val collection = if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
  val uri = context.contentResolver.insert(collection, values) ?: return null
  context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return null
  return uri
}

@Composable
private fun SwapChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .heightIn(min = 44.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
        .border(
          width = if (selected) 1.5.dp else 1.dp,
          color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f),
          shape = RoundedCornerShape(10.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, style = ClawTheme.type.body, color = ClawTheme.colors.text)
  }
}

/**
 * 顔入れ替え（FaceFusion）。素材（動画や画像）を選び、動画に映る顔を確認して、
 * 図鑑のキャラや写真の顔を割り当て、目安の時間を見てから始める。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FaceSwapScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var status by remember { mutableStateOf("") }
  var inputs by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // inputId to name
  var targetId by remember { mutableStateOf<String?>(null) }
  var probeText by remember { mutableStateOf("") }
  var uploadPercent by remember { mutableStateOf<Int?>(null) }
  var uploadJob by remember { mutableStateOf<Job?>(null) }

  var characters by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // id to name
  var pickingRow by remember { mutableStateOf<Int?>(null) }
  val rows = remember { mutableStateListOf(SwapRow()) }
  var faceThumbs by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
  var referenceSecText by remember { mutableStateOf("0") }
  var finding by remember { mutableStateOf(false) }

  var quality by remember { mutableStateOf("standard") }
  var enhancer by remember { mutableStateOf(false) }
  var trimStartText by remember { mutableStateOf("") }
  var trimEndText by remember { mutableStateOf("") }
  var estimate by remember { mutableStateOf<SwapEstimateView?>(null) }
  var estimateError by remember { mutableStateOf("") }

  var jobId by remember { mutableStateOf<String?>(null) }
  var jobStage by remember { mutableStateOf("") }
  var jobElapsed by remember { mutableStateOf(0) }
  var running by remember { mutableStateOf(false) }
  var savedUri by remember { mutableStateOf<Uri?>(null) }
  var savedMime by remember { mutableStateOf("video/mp4") }
  var downloadPercent by remember { mutableStateOf<Int?>(null) }

  suspend fun rpc(method: GatewayMethod, params: JSONObject, timeoutMs: Long = 30_000): JSONObject =
    JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = timeoutMs))

  suspend fun loadInputs() {
    runCatching {
      val array = rpc(GatewayMethod.FaceSwapInputs, JSONObject()).optJSONArray("inputs") ?: JSONArray()
      inputs = (0 until array.length()).map { array.getJSONObject(it).let { item -> item.optString("inputId") to item.optString("name") } }
    }
  }

  fun optionsJson(): JSONObject =
    JSONObject().apply {
      put("swapTarget", targetId ?: "")
      put("swapSources", swapSourcesJson(rows))
      put("swapEnhancer", enhancer)
      put("swapQuality", quality)
      parseSeconds(trimStartText)?.let { put("swapTrimStartSec", it) }
      parseSeconds(trimEndText)?.let { put("swapTrimEndSec", it) }
      parseSeconds(referenceSecText)?.let { put("swapReferenceSec", it) }
    }

  /** 端末のファイルを、2MBずつ PC へ送る。送れたら、その inputId を返す。 */
  suspend fun upload(uri: Uri): String? {
    val name = displayName(context, uri)
    val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    val total = if (size > 0) ((size + UPLOAD_CHUNK_BYTES - 1) / UPLOAD_CHUNK_BYTES).toInt() else 1
    var inputId: String? = null
    withContext(Dispatchers.IO) {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val buffer = ByteArray(UPLOAD_CHUNK_BYTES)
        var index = 0
        while (isActive) {
          var read = 0
          while (read < buffer.size) {
            val n = stream.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
          }
          if (read <= 0 && index > 0) break
          val data = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
          val result =
            rpc(
              GatewayMethod.FaceSwapUpload,
              JSONObject().put("name", name).put("index", index).put("total", total).put("dataBase64", data),
              timeoutMs = 120_000,
            )
          inputId = result.optString("inputId").ifBlank { null }
          index++
          uploadPercent = (index * 100 / total).coerceAtMost(100)
          if (index >= total) break
        }
      }
    }
    return inputId
  }

  suspend fun probeTarget(id: String) {
    runCatching {
      val probe = rpc(GatewayMethod.FaceSwapProbe, JSONObject().put("inputId", id))
      val video = probe.optString("kind") == "video"
      probeText =
        if (video) "動画 ${formatSwapDuration(probe.optDouble("durationSec").toInt()).removePrefix("約")}・${probe.optInt("width")}×${probe.optInt("height")}・${probe.optDouble("fps").toInt()}コマ/秒"
        else "画像 ${probe.optInt("width")}×${probe.optInt("height")}"
    }.onFailure { probeText = "" }
  }

  val pickMedia =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      if (uri == null) return@rememberLauncherForActivityResult
      uploadJob =
        scope.launch {
          status = "素材をPCへ送っています"
          uploadPercent = 0
          val id = runCatching { upload(uri) }.onFailure { status = "送信に失敗しました: ${it.message}" }.getOrNull()
          uploadPercent = null
          if (id != null) {
            targetId = id
            faceThumbs = emptyList()
            status = "素材を送りました"
            loadInputs()
            probeTarget(id)
          }
        }
    }

  var pendingFaceRow by remember { mutableStateOf<Int?>(null) }
  val pickFacePhoto =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      val row = pendingFaceRow
      if (uri == null || row == null) return@rememberLauncherForActivityResult
      uploadJob =
        scope.launch {
          status = "顔の写真をPCへ送っています"
          uploadPercent = 0
          val id = runCatching { upload(uri) }.onFailure { status = "送信に失敗しました: ${it.message}" }.getOrNull()
          uploadPercent = null
          if (id != null && row < rows.size) {
            rows[row] = rows[row].copy(characterId = null, inputId = id, label = "写真：${id.removePrefix("in:")}")
            status = "顔の写真を送りました"
            loadInputs()
          }
        }
    }

  LaunchedEffect(Unit) {
    loadInputs()
    runCatching {
      val array = rpc(GatewayMethod.ComicCharacterList, JSONObject()).optJSONArray("characters") ?: JSONArray()
      characters = (0 until array.length()).map { array.getJSONObject(it).let { item -> item.optString("id") to item.optString("name") } }
    }
  }

  // 目安の時間：設定を変えるたびに、PC に聞き直す。
  LaunchedEffect(targetId, quality, enhancer, trimStartText, trimEndText, rows.toList()) {
    estimate = null
    estimateError = ""
    val target = targetId ?: return@LaunchedEffect
    if (swapSourcesJson(rows).length() == 0) return@LaunchedEffect
    delay(500)
    runCatching {
      val result = rpc(GatewayMethod.MediaEstimate, optionsJson().put("kind", "faceswap"))
      estimate =
        SwapEstimateView(
          jobSec = result.optInt("jobSec"),
          waitSec = result.optInt("waitSec"),
          totalSec = result.optInt("totalSec"),
          frames = result.optInt("frames"),
          passes = result.optInt("passes", 1),
          videoSec = result.optInt("videoSec"),
          measured = result.optBoolean("measured"),
        )
    }.onFailure { estimateError = it.message ?: "目安を出せませんでした" }
  }

  /** できあがった動画を、PC から 3MB ずつ受け取って、端末に保存する。 */
  suspend fun download(id: String) {
    downloadPercent = 0
    val file = File(context.cacheDir, "faceswap-$id.mp4")
    var mime = "video/mp4"
    withContext(Dispatchers.IO) {
      file.outputStream().use { out ->
        var offset = 0L
        while (true) {
          val chunk = rpc(GatewayMethod.FaceSwapResultChunk, JSONObject().put("jobId", id).put("offset", offset), timeoutMs = 120_000)
          val size = chunk.optLong("size")
          mime = chunk.optString("mime").ifBlank { mime }
          val bytes = Base64.decode(chunk.optString("dataBase64"), Base64.DEFAULT)
          if (bytes.isEmpty()) break
          out.write(bytes)
          offset += bytes.size
          downloadPercent = if (size > 0) (offset * 100 / size).toInt().coerceAtMost(100) else null
          if (offset >= size) break
        }
      }
    }
    val named = File(context.cacheDir, "顔入れ替え-$id" + if (mime.startsWith("image/")) ".png" else ".mp4")
    file.renameTo(named)
    savedMime = mime
    savedUri = withContext(Dispatchers.IO) { saveVideoToDevice(context, named, mime) }
    downloadPercent = null
    status = if (savedUri != null) "完成しました。端末のギャラリー（Movies/OpenClaw）に保存しました" else "完成しましたが、端末への保存に失敗しました"
  }

  var confirmStart by remember { mutableStateOf(false) }

  fun start() {
    val target = targetId
    if (target == null || swapSourcesJson(rows).length() == 0) {
      status = "素材と、入れ替える顔を選んでください"
      return
    }
    confirmStart = true
  }

  fun reallyStart() {
    scope.launch {
      running = true
      savedUri = null
      jobStage = "送信中"
      jobElapsed = 0
      runCatching {
        val job = rpc(GatewayMethod.MediaGenerate, optionsJson().put("kind", "faceswap").put("prompt", "顔入れ替え"))
        val id = job.optString("jobId")
        jobId = id
        while (true) {
          delay(3_000)
          val now = rpc(GatewayMethod.MediaJobGet, JSONObject().put("jobId", id))
          jobStage = now.optString("stage")
          jobElapsed = now.optInt("elapsedSec")
          when (now.optString("status")) {
            "done" -> {
              download(id)
              break
            }
            "error" -> {
              status = "失敗しました: ${now.optString("error")}"
              break
            }
          }
        }
      }.onFailure { status = "エラー: ${it.message}" }
      running = false
    }
  }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
          Column(modifier = Modifier.padding(start = 8.dp)) {
            Text("🎭 顔入れ替え", style = ClawTheme.type.title, color = ClawTheme.colors.text)
            Text("PCの中だけで処理します（外へ送りません）", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
        }
      }

      // ① 素材
      item {
        ClawPanel(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ClawSectionHeader("① 素材（動画か画像）")
            ClawPrimaryButton(text = "📱 端末から選んで、PCへ送る", onClick = { pickMedia.launch("*/*") }, modifier = Modifier.fillMaxWidth(), enabled = uploadPercent == null && !running)
            uploadPercent?.let { percent ->
              LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
              Text("送信中 $percent%（大きい動画は時間がかかります）", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
              TextButton(onClick = { uploadJob?.cancel(); uploadPercent = null; status = "送信を中止しました" }) { Text("中止") }
            }
            if (inputs.isNotEmpty()) {
              Text("PCにある素材（タップで選ぶ）", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((id, name) in inputs) {
                  SwapChip(name.take(24), selected = id == targetId, onClick = {
                    targetId = id
                    faceThumbs = emptyList()
                    scope.launch { probeTarget(id) }
                  })
                }
              }
            }
            if (probeText.isNotBlank()) Text(probeText, style = ClawTheme.type.body, color = ClawTheme.colors.text)
          }
        }
      }

      // ② 顔
      item {
        ClawPanel(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ClawSectionHeader("② 顔を入れ替える人")
            Text("動画に映る顔を探すと、左から番号がつきます。番号ごとに、入れる顔を選びます。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              ClawTextField(value = referenceSecText, onValueChange = { referenceSecText = it }, placeholder = "0", label = "何秒の場面で探す", modifier = Modifier.weight(1f))
              ClawSecondaryButton(
                text = if (finding) "探しています…" else "顔を探す",
                onClick = {
                  val target = targetId ?: return@ClawSecondaryButton
                  scope.launch {
                    finding = true
                    runCatching {
                      val faces = rpc(GatewayMethod.FaceSwapFaces, JSONObject().put("target", target).put("atSec", parseSeconds(referenceSecText) ?: 0.0), timeoutMs = 300_000).optJSONArray("faces") ?: JSONArray()
                      faceThumbs =
                        (0 until faces.length()).mapNotNull {
                          val bytes = Base64.decode(faces.getJSONObject(it).optString("thumbBase64"), Base64.DEFAULT)
                          BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                      status = if (faceThumbs.isEmpty()) "顔が見つかりませんでした（別の秒数を試してください）" else "${faceThumbs.size}人の顔が見つかりました"
                    }.onFailure { status = "顔を探せませんでした: ${it.message}" }
                    finding = false
                  }
                },
                enabled = targetId != null && !finding && !running,
              )
            }
            if (faceThumbs.isNotEmpty()) {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                faceThumbs.forEachIndexed { index, bitmap ->
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "${index + 1}番の顔", contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)))
                    Text("${index + 1}番", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
                  }
                }
              }
            }
            rows.forEachIndexed { index, row ->
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("入れ替える人 ${index + 1}", style = ClawTheme.type.body, color = ClawTheme.colors.text)
                Text(if (row.label.isBlank()) "（顔を選んでください）" else row.label, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  ClawSecondaryButton(text = "図鑑のキャラ", onClick = { pickingRow = index }, modifier = Modifier.weight(1f))
                  ClawSecondaryButton(text = "写真から", onClick = { pendingFaceRow = index; pickFacePhoto.launch("image/*") }, modifier = Modifier.weight(1f))
                }
                val slots = maxOf(faceThumbs.size, 1)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  SwapChip("全員", selected = row.position == null, onClick = { rows[index] = row.copy(position = null) })
                  for (slot in 0 until slots) {
                    SwapChip("${slot + 1}番の顔", selected = row.position == slot, onClick = { rows[index] = row.copy(position = slot) })
                  }
                }
                if (rows.size > 1) TextButton(onClick = { rows.removeAt(index) }) { Text("この人を外す") }
              }
            }
            if (rows.size < 4) ClawSecondaryButton(text = "＋ 人を追加（最大4人）", onClick = { rows.add(SwapRow(position = rows.size)) }, modifier = Modifier.fillMaxWidth())
          }
        }
      }

      // ③ 仕上がり
      item {
        ClawPanel(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ClawSectionHeader("③ 仕上がり")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              for ((key, label) in QUALITIES) SwapChip(label, selected = quality == key, onClick = { quality = key })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(checked = enhancer, onCheckedChange = { enhancer = it })
              Text("顔をくっきりさせる（遅くなります）", style = ClawTheme.type.body, color = ClawTheme.colors.text)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ClawTextField(value = trimStartText, onValueChange = { trimStartText = it }, placeholder = "最初から", label = "開始（秒）", modifier = Modifier.weight(1f))
              ClawTextField(value = trimEndText, onValueChange = { trimEndText = it }, placeholder = "最後まで", label = "終了（秒）", modifier = Modifier.weight(1f))
            }
            Text("一部だけ試したいときは、開始と終了を入れます（例：0〜10秒）。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
        }
      }

      // ④ 目安と開始
      item {
        ClawPanel(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ClawSectionHeader("④ 目安の時間")
            val view = estimate
            when {
              view != null -> {
                Text(
                  "かかる時間：${formatSwapDuration(view.jobSec)}（${view.frames}コマ × ${view.passes}人分）" +
                    if (view.waitSec > 0) "\n順番待ち：${formatSwapDuration(view.waitSec)}　合計：${formatSwapDuration(view.totalSec)}" else "",
                  style = ClawTheme.type.body,
                  color = ClawTheme.colors.text,
                )
                Text(
                  if (view.measured) "実際にかかった時間から出した目安です。" else "このPCで測った速さから出した目安です。実際は、動画の内容で前後します。",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )
              }
              estimateError.isNotBlank() -> Text(estimateError, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
              else -> Text("素材と顔を選ぶと、ここに目安が出ます。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            }
            ClawPrimaryButton(text = if (running) "処理中…" else "▶ 顔を入れ替える", onClick = { start() }, modifier = Modifier.fillMaxWidth(), enabled = !running && targetId != null && uploadPercent == null)
            GpuBusyBanner(viewModel)
            if (running) {
              LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
              Text("$jobStage（${formatSwapDuration(jobElapsed).removePrefix("約")}経過）", style = ClawTheme.type.body, color = ClawTheme.colors.text)
            }
            downloadPercent?.let { Text("できた動画を受け取っています $it%", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted) }
            savedUri?.let { uri ->
              ClawSecondaryButton(
                text = "▶ 開いて見る",
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, savedMime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) },
                modifier = Modifier.fillMaxWidth(),
              )
            }
            if (status.isNotBlank()) Text(status, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
        }
      }
    }
  }

  if (confirmStart) {
    val view = estimate
    AlertDialog(
      onDismissRequest = { confirmStart = false },
      title = { Text("チャットAIが止まります") },
      text = {
        Text(
          "顔入れ替えの間、PCのGPUを使うため、チャットAIを一時停止します（その間、会話の返事は遅れるか、返りません）。" +
            (if (view != null) "\n\nかかる時間の目安：${formatSwapDuration(view.jobSec)}" else "") +
            "\n\n終わると、自動で戻ります。始めますか？",
        )
      },
      confirmButton = { TextButton(onClick = { confirmStart = false; reallyStart() }) { Text("始める") } },
      dismissButton = { TextButton(onClick = { confirmStart = false }) { Text("やめる") } },
    )
  }

  pickingRow?.let { index ->
    AlertDialog(
      onDismissRequest = { pickingRow = null },
      title = { Text("図鑑のキャラを選ぶ") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          if (characters.isEmpty()) Text("図鑑にキャラがいません")
          for ((id, name) in characters.take(40)) {
            Text(
              name,
              style = ClawTheme.type.body,
              color = ClawTheme.colors.text,
              modifier = Modifier.fillMaxWidth().clickable {
                if (index < rows.size) rows[index] = rows[index].copy(characterId = id, inputId = null, label = "図鑑：$name")
                pickingRow = null
              }.padding(vertical = 10.dp),
            )
          }
        }
      },
      confirmButton = { TextButton(onClick = { pickingRow = null }) { Text("閉じる") } },
    )
  }
}
