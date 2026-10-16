package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawTheme
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The picture is shown at most this wide, so a large still does not fill the memory. */
private const val PREVIEW_MAX_PX = 1080

private fun decodeScaled(file: File): ImageBitmap? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(file.path, bounds)
  var sample = 1
  while (bounds.outWidth / (sample * 2) >= PREVIEW_MAX_PX) sample *= 2
  return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

/**
 * A picture or film from the media studio inside a chat bubble (selfies the partner sends). The
 * file is fetched once with media.job.result and kept in the app's cache.
 */
@Composable
internal fun PartnerChatJobMedia(viewModel: MainViewModel, jobId: String, kind: String) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var file by remember(jobId) { mutableStateOf<File?>(null) }
  var bitmap by remember(jobId) { mutableStateOf<ImageBitmap?>(null) }
  var error by remember(jobId) { mutableStateOf("") }
  var loading by remember(jobId) { mutableStateOf(false) }
  var fullscreen by remember(jobId) { mutableStateOf(false) }

  suspend fun load(): File? {
    file?.let { return it }
    loading = true
    return try {
      val ext = if (kind == "video") "mp4" else "png"
      val cached = File(context.cacheDir, "chat-media-$jobId.$ext")
      if (!withContext(Dispatchers.IO) { cached.isFile && cached.length() > 0L }) {
        val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobResult, JSONObject().put("jobId", jobId).toString(), timeoutMs = 120_000))
        val bytes = withContext(Dispatchers.Default) { Base64.decode(res.optString("base64"), Base64.DEFAULT) }
        if (bytes.isEmpty()) error("見つかりませんでした（保存庫から消されたかもしれません）")
        withContext(Dispatchers.IO) { cached.writeBytes(bytes) }
      }
      if (kind != "video") bitmap = withContext(Dispatchers.IO) { decodeScaled(cached) }
      file = cached
      cached
    } catch (e: Exception) {
      error = "読み込めませんでした：${e.message}"
      null
    } finally {
      loading = false
    }
  }

  LaunchedEffect(jobId) { if (kind != "video") load() }

  val shape = RoundedCornerShape(10.dp)
  if (kind == "video") {
    Box(
      modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(shape).background(ClawTheme.colors.surface).clickable { scope.launch { if (load() != null) fullscreen = true } }.padding(14.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(text = if (loading) "動画を読み込んでいます…" else "▶ 動画を見る", style = ClawTheme.type.body, color = ClawTheme.colors.primary)
    }
  } else {
    val shown = bitmap
    if (shown != null) {
      Image(
        bitmap = shown,
        contentDescription = "写真",
        contentScale = ContentScale.Fit,
        modifier = Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(max = 360.dp).clip(shape).clickable { fullscreen = true },
      )
    } else {
      Text(text = if (loading) "📸 写真を読み込んでいます…" else "📸 写真", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, modifier = Modifier.padding(top = 6.dp))
    }
  }
  if (error.isNotBlank()) Text(text = error, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)

  val opened = file
  if (fullscreen && opened != null) {
    Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { fullscreen = false }, contentAlignment = Alignment.Center) {
        if (kind == "video") {
          AndroidView(
            factory = { ctx ->
              VideoView(ctx).apply {
                setVideoPath(opened.path)
                setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                setOnPreparedListener { player ->
                  player.isLooping = true
                  start()
                }
              }
            },
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          bitmap?.let { Image(bitmap = it, contentDescription = "写真", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
      }
    }
  }
}
