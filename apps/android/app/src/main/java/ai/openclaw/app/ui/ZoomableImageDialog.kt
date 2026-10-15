package ai.openclaw.app.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 8f
private const val DOUBLE_TAP_ZOOM = 3f

/** 画像の中を動かせる範囲。拡大した分だけ、はみ出した半分まで。 */
internal fun zoomPanLimit(size: Float, scale: Float): Float = size * (scale - 1f) / 2f

/** 倍率を範囲に収める。 */
internal fun clampZoom(scale: Float): Float = scale.coerceIn(MIN_ZOOM, MAX_ZOOM)

/**
 * 画像を全画面で見る。2本の指でつまんで拡大・縮小、1本指でドラッグして動かす、
 * ダブルタップで拡大（もう一度で元に戻す）。
 */
@Composable
internal fun ZoomableImageDialog(
  bitmap: ImageBitmap,
  title: String,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    var scale by remember { mutableFloatStateOf(MIN_ZOOM) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    fun moveTo(next: Offset, nextScale: Float) {
      val maxX = zoomPanLimit(box.width.toFloat(), nextScale)
      val maxY = zoomPanLimit(box.height.toFloat(), nextScale)
      offset = Offset(next.x.coerceIn(-maxX, maxX), next.y.coerceIn(-maxY, maxY))
    }

    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.94f))
          .onSizeChanged { box = it },
    ) {
      Image(
        bitmap = bitmap,
        contentDescription = title,
        contentScale = ContentScale.Fit,
        modifier =
          Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
              detectTapGestures(
                onDoubleTap = {
                  if (scale > MIN_ZOOM) {
                    scale = MIN_ZOOM
                    offset = Offset.Zero
                  } else {
                    scale = DOUBLE_TAP_ZOOM
                  }
                },
              )
            }.pointerInput(Unit) {
              detectTransformGestures { _, pan, zoom, _ ->
                val next = clampZoom(scale * zoom)
                scale = next
                moveTo(offset + pan, next)
              }
            }.graphicsLayer {
              scaleX = scale
              scaleY = scale
              translationX = offset.x
              translationY = offset.y
            },
      )
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(text = title, color = Color.White, modifier = Modifier.weight(1f))
        val saveContext = androidx.compose.ui.platform.LocalContext.current
        var saveNote by remember { mutableStateOf("") }
        Text(
          text = if (saveNote.isBlank()) "💾 保存" else saveNote,
          color = Color.White,
          modifier = Modifier.clickable {
            saveNote = if (saveBitmapToPictures(saveContext, bitmap.asAndroidBitmap(), title)) "保存しました（写真）" else "保存できませんでした"
          }.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.White)
        }
      }
    }
  }
}
