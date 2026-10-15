package ai.openclaw.app.ui

import kotlinx.coroutines.launch
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawTheme
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 作ってあるサンプル絵だけを、左右にスライドして見る拡大表示。画面のどこかをタップすると閉じる。 */
@Composable
internal fun SampleGalleryDialog(
  viewModel: MainViewModel,
  items: List<Pair<String, String>>,
  startKey: String,
  onDismiss: () -> Unit,
) {
  if (items.isEmpty()) return
  val start = items.indexOfFirst { it.first == startKey }.coerceAtLeast(0)
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    val pager = rememberPagerState(initialPage = start, pageCount = { items.size })
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)).clickable(onClick = onDismiss)) {
      HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
        val (key, _) = items[page]
        var bitmap by remember(key) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(key) { bitmap = SampleStore.bitmap(viewModel, key) }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          bitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = items[page].second, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
          } ?: Text("読み込み中…", color = Color.White)
        }
      }
      val saveContext = androidx.compose.ui.platform.LocalContext.current
      val saveScope = androidx.compose.runtime.rememberCoroutineScope()
      var saveNote by remember { mutableStateOf("") }
      Text(
        text = if (saveNote.isBlank()) "💾 保存" else saveNote,
        color = Color.White,
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 12.dp).clickable {
          val (key, label) = items[pager.currentPage]
          saveScope.launch {
            val bmp = SampleStore.bitmap(viewModel, key)
            saveNote = if (bmp != null && saveBitmapToPictures(saveContext, bmp, label)) "保存しました（写真）" else "保存できませんでした"
          }
        }.padding(horizontal = 12.dp, vertical = 8.dp),
      )
      Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp)) {
        Text(items[pager.currentPage].second, style = ClawTheme.type.title, color = Color.White)
        Text("${pager.currentPage + 1} / ${items.size}　左右にスライドで、ほかのサンプル", style = ClawTheme.type.caption, color = Color.White.copy(alpha = 0.7f))
      }
    }
  }
}
