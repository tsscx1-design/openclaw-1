package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.convertUriToBase64DataUrl
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class MediaItem(
  val jobId: String,
  val kind: String,
  val status: String,
  val request: String,
  val stage: String,
  val elapsedSec: Int,
  val durationSec: Int,
  val error: String,
  val mime: String,
  val lyrics: String,
)

private const val MEDIA_POLL_MS = 4_000L
private const val MEDIA_PREVIEW_MAX_PX = 900
private const val MEDIA_UPLOAD_MAX_PX = 1344

private fun parseMediaItems(arr: JSONArray?): List<MediaItem> {
  val items = arr ?: return emptyList()
  return (0 until items.length()).map { i ->
    val o = items.getJSONObject(i)
    MediaItem(
      jobId = o.optString("jobId"),
      kind = o.optString("kind"),
      status = o.optString("status"),
      request = o.optString("request"),
      stage = o.optString("stage"),
      elapsedSec = o.optInt("elapsedSec", 0),
      durationSec = o.optInt("durationSec", 0),
      error = o.optString("error"),
      mime = o.optString("mime"),
      lyrics = o.optString("lyrics"),
    )
  }
}

private fun kindLabel(kind: String) =
  when (kind) {
    "image" -> "🎨 画像"
    "video" -> "🎬 動画"
    else -> "🎵 音楽"
  }

private fun extensionFor(mime: String) =
  when (mime) {
    "image/png" -> "png"
    "video/webm" -> "webm"
    "video/mp4" -> "mp4"
    "video/x-matroska" -> "mkv"
    "audio/mpeg" -> "mp3"
    else -> "bin"
  }

private fun decodePreview(bytes: ByteArray): Bitmap? =
  try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MEDIA_PREVIEW_MAX_PX) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
  } catch (_: Exception) {
    null
  }

/** Copies a finished file into Pictures / Movies / Music under an "OpenClaw" folder. */
private fun saveToDevice(context: Context, file: File, mime: String): Boolean {
  val (collection, folder) =
    when {
      mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
      mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
      else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
    }
  val values =
    ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
      put(MediaStore.MediaColumns.MIME_TYPE, mime)
      put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/OpenClaw")
    }
  val uri = context.contentResolver.insert(collection, values) ?: return false
  context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
  return true
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .heightIn(min = 44.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(
          if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f),
        )
        .border(
          width = if (selected) 1.5.dp else 1.dp,
          color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f),
          shape = RoundedCornerShape(10.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
      color = if (selected) ClawTheme.colors.text else ClawTheme.colors.textMuted,
    )
  }
}

@Composable
internal fun MediaStudioScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  var kind by remember { mutableStateOf("image") }
  var prompt by remember { mutableStateOf("") }
  var aspect by remember { mutableStateOf("portrait") }
  var lyrics by remember { mutableStateOf("") }
  var instrumental by remember { mutableStateOf(false) }
  var musicSeconds by remember { mutableStateOf(90) }
  var videoEngine by remember { mutableStateOf("standard") }
  var imageModel by remember { mutableStateOf("wai") }
  var videoSeconds by remember { mutableStateOf(5) }
  var sourceImageJobId by remember { mutableStateOf<String?>(null) }
  var sourceImageBase64 by remember { mutableStateOf<String?>(null) }
  var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
  var statusMessage by remember { mutableStateOf("") }
  var isSending by remember { mutableStateOf(false) }
  var deleteTarget by remember { mutableStateOf<MediaItem?>(null) }
  var playingVideo by remember { mutableStateOf<String?>(null) }
  var playingAudio by remember { mutableStateOf<String?>(null) }
  val previews = remember { mutableStateMapOf<String, Bitmap>() }
  val files = remember { mutableStateMapOf<String, File>() }
  val player = remember { mutableStateOf<MediaPlayer?>(null) }

  DisposableEffect(Unit) {
    onDispose { player.value?.release() }
  }

  suspend fun loadList() {
    try {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobList, JSONObject().put("limit", 40).toString()))
      items = parseMediaItems(res.optJSONArray("jobs"))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      statusMessage = "読み込みエラー: ${e.message}"
    }
  }

  /** Downloads a finished result into the app cache once; previews images on the way. */
  suspend fun fetchFile(item: MediaItem): File? {
    files[item.jobId]?.let { return it }
    return try {
      val res =
        JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobResult, JSONObject().put("jobId", item.jobId).toString()))
      val mime = res.optString("mime", item.mime)
      val bytes = withContext(Dispatchers.Default) { Base64.decode(res.optString("base64"), Base64.DEFAULT) }
      val file = File(context.cacheDir, "media-${item.jobId}.${extensionFor(mime)}")
      withContext(Dispatchers.IO) { file.writeBytes(bytes) }
      if (mime.startsWith("image/")) decodePreview(bytes)?.let { previews[item.jobId] = it }
      files[item.jobId] = file
      file
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      statusMessage = "取得エラー: ${e.message}"
      null
    }
  }

  fun generate() {
    if (isSending) return
    coroutineScope.launch {
      isSending = true
      try {
        val params = JSONObject().put("kind", kind).put("prompt", prompt.trim())
        if (kind != "music") params.put("aspect", aspect).put("imageModel", imageModel)
        if (kind == "video") {
          params.put("engine", videoEngine)
          if (videoEngine != "standard") params.put("durationSec", videoSeconds)
          sourceImageJobId?.let { params.put("sourceImageJobId", it) }
          sourceImageBase64?.let { params.put("sourceImageBase64", it) }
        }
        if (kind == "music") {
          params.put("instrumental", instrumental).put("durationSec", musicSeconds)
          if (!instrumental && lyrics.isNotBlank()) params.put("lyrics", lyrics.trim())
        }
        viewModel.runGatewayRpc(GatewayMethod.MediaGenerate, params.toString())
        statusMessage =
          when (kind) {
            "image" -> "画像を作り始めました（1枚 1分ほど）。作っている間、チャットは一時的に止まります。"
            "video" -> "動画を作り始めました（数分〜10分ほど）。作っている間、チャットは一時的に止まります。"
            else -> "曲を作り始めました（数分ほど）。作っている間、チャットは一時的に止まります。"
          }
        loadList()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        statusMessage = "開始できませんでした: ${e.message}"
      } finally {
        isSending = false
      }
    }
  }

  fun toggleAudio(item: MediaItem) {
    coroutineScope.launch {
      if (playingAudio == item.jobId) {
        player.value?.release()
        player.value = null
        playingAudio = null
        return@launch
      }
      val file = fetchFile(item) ?: return@launch
      player.value?.release()
      player.value =
        MediaPlayer().apply {
          setDataSource(file.absolutePath)
          setOnCompletionListener { playingAudio = null }
          prepare()
          start()
        }
      playingAudio = item.jobId
    }
  }

  fun save(item: MediaItem) {
    coroutineScope.launch {
      val file = fetchFile(item) ?: return@launch
      val ok = withContext(Dispatchers.IO) { runCatching { saveToDevice(context, file, item.mime) }.getOrDefault(false) }
      statusMessage = if (ok) "端末の「OpenClaw」フォルダに保存しました。" else "保存できませんでした。"
    }
  }

  fun delete(item: MediaItem) {
    coroutineScope.launch {
      try {
        viewModel.runGatewayRpc(GatewayMethod.MediaJobDelete, JSONObject().put("jobId", item.jobId).toString())
        previews.remove(item.jobId)
        files.remove(item.jobId)?.delete()
        loadList()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        statusMessage = "削除エラー: ${e.message}"
      }
    }
  }

  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      if (uri == null) return@rememberLauncherForActivityResult
      coroutineScope.launch {
        val dataUri = withContext(Dispatchers.IO) { convertUriToBase64DataUrl(context, uri, MEDIA_UPLOAD_MAX_PX) }
        if (dataUri == null) {
          statusMessage = "この画像は読み込めませんでした。"
        } else {
          sourceImageBase64 = dataUri.substringAfter(",")
          sourceImageJobId = null
          statusMessage = "選んだ画像を動かします。動きの指示を書いて「作る」を押してください。"
        }
      }
    }

  LaunchedEffect(Unit) { loadList() }
  val hasActive = items.any { it.status == "queued" || it.status == "running" }
  LaunchedEffect(hasActive) {
    while (hasActive) {
      delay(MEDIA_POLL_MS)
      loadList()
    }
  }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(text = "🎨 生成スタジオ", style = ClawTheme.type.title, color = ClawTheme.colors.text)
              Text(
                text = "画像・動画・音楽をこのPCで作ります（制限解除モデル）",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
            }
          }
          ClawIconButton(icon = Icons.Default.Refresh, contentDescription = "更新", onClick = { coroutineScope.launch { loadList() } })
        }
      }

      item {
        ClawPanel(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              listOf("image" to "🎨 画像", "video" to "🎬 動画", "music" to "🎵 音楽").forEach { (id, label) ->
                ChoiceChip(label = label, selected = kind == id, onClick = { kind = id }, modifier = Modifier.weight(1f))
              }
            }

            ClawTextField(
              value = prompt,
              onValueChange = { prompt = it },
              placeholder =
                when (kind) {
                  "image" -> "例: 銀髪で猫耳のメイドがカフェでコーヒーを運んでいる、笑顔"
                  "video" -> "例: 髪が風になびいて、こちらに微笑みかける（空欄なら自然に動かします）"
                  else -> "例: 明るいJ-POP、女性ボーカル、夏の海をドライブする気分"
                },
              minLines = 3,
              label =
                when (kind) {
                  "image" -> "どんな絵？（日本語でOK）"
                  "video" -> "どんな動き？（日本語でOK）"
                  else -> "どんな曲？（日本語でOK）"
                },
              modifier = Modifier.fillMaxWidth(),
            )

            if (kind == "image" || (kind == "video" && sourceImageJobId == null && sourceImageBase64 == null)) {
              Text(
                text = if (kind == "image") "絵のモデル" else "最初の1枚のモデル",
                style = ClawTheme.type.caption.copy(fontSize = 13.sp),
                color = ClawTheme.colors.text,
              )
              val models =
                listOf(
                  Triple("wai", "WAI（標準）", "人気のアニメ絵。続けて作ると1枚約30秒"),
                  Triple("noobai", "NoobAI", "アニメの成人向けで評価が最も高い。体の描写が正確。約30秒〜1分"),
                  Triple("zimage", "Z-Image", "文章の指示に強く、構図や小物も正確。1枚約1分半"),
                )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                models.forEach { (id, label, _) ->
                  ChoiceChip(label = label, selected = imageModel == id, onClick = { imageModel = id }, modifier = Modifier.weight(1f))
                }
              }
              Text(
                text = models.first { it.first == imageModel }.third +
                  if (imageModel == "zimage") "（初回はモデルのダウンロードがあります）" else "",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
            }

            if (kind != "music") {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("portrait" to "縦長", "square" to "正方形", "landscape" to "横長").forEach { (id, label) ->
                  ChoiceChip(label = label, selected = aspect == id, onClick = { aspect = id }, modifier = Modifier.weight(1f))
                }
              }
            }

            if (kind == "video") {
              Text(text = "作り方", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
              val engines =
                listOf(
                  Triple("standard", "標準", "制限解除版。2秒・約5分"),
                  Triple("long", "長い動画", "Wan2.2 SVI。5〜30秒、崩れにくい"),
                  Triple("audio", "音付き", "LTX-2。映像と音を同時に、〜10秒"),
                  Triple("fast", "高速", "FastWan 5B。〜5秒を速く"),
                )
              engines.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  row.forEach { (id, label, _) ->
                    ChoiceChip(label = label, selected = videoEngine == id, onClick = { videoEngine = id }, modifier = Modifier.weight(1f))
                  }
                }
              }
              Text(
                text = engines.first { it.first == videoEngine }.third +
                  if (videoEngine != "standard") "（初回はモデルのダウンロードで時間がかかります）" else "",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
              if (videoEngine != "standard") {
                val choices =
                  when (videoEngine) {
                    "long" -> listOf(5, 10, 20, 30)
                    "audio" -> listOf(4, 6, 8, 10)
                    else -> listOf(3, 5)
                  }
                if (videoSeconds !in choices) videoSeconds = choices.first()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  choices.forEach { sec ->
                    ChoiceChip(label = "${sec}秒", selected = videoSeconds == sec, onClick = { videoSeconds = sec }, modifier = Modifier.weight(1f))
                  }
                }
              }
              val source =
                when {
                  sourceImageJobId != null -> "作った画像を動かします"
                  sourceImageBase64 != null -> "選んだ画像を動かします"
                  else -> "文章から最初の1枚を描いて、それを動かします"
                }
              Text(text = "元にする絵: $source", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawSecondaryButton(text = "端末の画像を選ぶ", onClick = { photoPicker.launch("image/*") })
                if (sourceImageJobId != null || sourceImageBase64 != null) {
                  ClawSecondaryButton(
                    text = "文章から作るに戻す",
                    onClick = {
                      sourceImageJobId = null
                      sourceImageBase64 = null
                    },
                  )
                }
              }
              Text(
                text = "作った画像の下にある「この絵を動かす」からも選べます。",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
            }

            if (kind == "music") {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceChip(label = "🎤 歌あり", selected = !instrumental, onClick = { instrumental = false }, modifier = Modifier.weight(1f))
                ChoiceChip(label = "🎹 曲だけ", selected = instrumental, onClick = { instrumental = true }, modifier = Modifier.weight(1f))
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(30, 60, 90, 120).forEach { sec ->
                  ChoiceChip(label = "${sec}秒", selected = musicSeconds == sec, onClick = { musicSeconds = sec }, modifier = Modifier.weight(1f))
                }
              }
              if (!instrumental) {
                ClawTextField(
                  value = lyrics,
                  onValueChange = { lyrics = it },
                  placeholder = "空欄なら AI が日本語で作詞します。\n自分で書くなら [Verse] [Chorus] などで区切ってください。",
                  minLines = 4,
                  label = "歌詞（任意）",
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }

            ClawPrimaryButton(
              text = if (isSending) "送信中..." else "✨ 作る",
              onClick = { generate() },
              enabled = !isSending && (prompt.isNotBlank() || kind == "video"),
              modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            )
            if (statusMessage.isNotBlank()) {
              Text(text = statusMessage, style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.textMuted)
            }
          }
        }
      }

      if (items.isEmpty()) {
        item {
          Text(
            text = "まだ作品がありません。上で内容を書いて「作る」を押してください。\n作品はPCに保存され、ここから見たり端末に保存したりできます。",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
          )
        }
      }

      items(items, key = { it.jobId }) { item ->
        if (item.status == "done" && item.kind == "image") {
          LaunchedEffect(item.jobId) { if (!previews.containsKey(item.jobId)) fetchFile(item) }
        }
        ClawPanel(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "${kindLabel(item.kind)}　${item.request.ifBlank { "（指示なし）" }}",
              style = ClawTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
              color = ClawTheme.colors.text,
              maxLines = 3,
            )
            when (item.status) {
              "queued", "running" -> {
                val elapsed = if (item.elapsedSec > 0) "（${item.elapsedSec / 60}分${item.elapsedSec % 60}秒）" else ""
                Text(
                  text = "⏳ ${item.stage.ifBlank { "準備中" }} $elapsed",
                  style = ClawTheme.type.caption.copy(fontSize = 13.sp),
                  color = ClawTheme.colors.primary,
                )
              }
              "error" -> {
                Text(
                  text = "作れませんでした: ${item.error.ifBlank { "原因不明" }}",
                  style = ClawTheme.type.caption.copy(fontSize = 13.sp),
                  color = ClawTheme.colors.textMuted,
                )
              }
              else -> {
                when (item.kind) {
                  "image" -> {
                    val bitmap = previews[item.jobId]
                    if (bitmap != null) {
                      Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = item.request,
                        contentScale = ContentScale.Fit,
                        modifier =
                          Modifier.fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                            .clip(RoundedCornerShape(10.dp)),
                      )
                    } else {
                      Text(text = "読み込み中...", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                    }
                  }
                  "video" -> {
                    val file = files[item.jobId]
                    if (playingVideo == item.jobId && file != null) {
                      AndroidView(
                        factory = { ctx ->
                          VideoView(ctx).apply {
                            setVideoPath(file.absolutePath)
                            setOnPreparedListener { mp ->
                              mp.isLooping = true
                              start()
                            }
                          }
                        },
                        modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(10.dp)),
                      )
                    }
                    ClawSecondaryButton(
                      text = if (playingVideo == item.jobId) "■ 閉じる" else "▶ 再生",
                      onClick = {
                        if (playingVideo == item.jobId) {
                          playingVideo = null
                        } else {
                          coroutineScope.launch { if (fetchFile(item) != null) playingVideo = item.jobId }
                        }
                      },
                    )
                  }
                  else -> {
                    ClawSecondaryButton(
                      text = if (playingAudio == item.jobId) "■ 止める" else "▶ 再生",
                      onClick = { toggleAudio(item) },
                    )
                    if (item.lyrics.isNotBlank() && item.lyrics != "[Instrumental]") {
                      Text(
                        text = item.lyrics,
                        style = ClawTheme.type.caption.copy(fontSize = 12.sp, lineHeight = 18.sp),
                        color = ClawTheme.colors.textMuted,
                        maxLines = 12,
                      )
                    }
                  }
                }
                if (item.durationSec > 0) {
                  Text(
                    text = "作成時間 ${item.durationSec / 60}分${item.durationSec % 60}秒",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted,
                  )
                }
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (item.status == "done") {
                ClawSecondaryButton(text = "端末に保存", onClick = { save(item) })
                if (item.kind == "image") {
                  ClawSecondaryButton(
                    text = "この絵を動かす",
                    onClick = {
                      kind = "video"
                      sourceImageJobId = item.jobId
                      sourceImageBase64 = null
                      statusMessage = "この絵を動かします。上で動きの指示を書いて「作る」を押してください。"
                    },
                  )
                }
              }
              if (item.status == "done" || item.status == "error") {
                ClawSecondaryButton(text = "削除", onClick = { deleteTarget = item })
              }
            }
          }
        }
      }
    }

    deleteTarget?.let { target ->
      AlertDialog(
        onDismissRequest = { deleteTarget = null },
        title = { Text(text = "この作品を削除しますか？", color = ClawTheme.colors.text) },
        text = { Text(text = "PC内のファイルが消えます。端末に保存した分は残ります。", color = ClawTheme.colors.textMuted) },
        confirmButton = {
          ClawPrimaryButton(
            text = "削除する",
            onClick = {
              delete(target)
              deleteTarget = null
            },
          )
        },
        dismissButton = { ClawSecondaryButton(text = "キャンセル", onClick = { deleteTarget = null }) },
      )
    }
  }
}
