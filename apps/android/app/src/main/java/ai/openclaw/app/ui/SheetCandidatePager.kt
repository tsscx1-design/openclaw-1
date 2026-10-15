package ai.openclaw.app.ui

import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 設定画の候補1枚。 */
internal data class CandidateItem(
  val jobId: String,
  val view: String,
  val adult: Boolean,
  val current: Boolean,
  val createdAt: Long,
)

/** 候補の種類の名前（立ち絵・三面図・360度。成人向けは先頭に 🔞）。 */
internal fun candidateKindLabel(item: CandidateItem): String =
  (if (item.adult) "🔞" else "") +
    when (item.view) {
      "three_view", "turnaround" -> "三面図"
      "360" -> "360度"
      else -> "立ち絵"
    }

/** 候補の見出し（「3/7 立ち絵・10/07 13:20」）。 */
internal fun candidateLabel(item: CandidateItem, index: Int, total: Int): String {
  val kind = candidateKindLabel(item)
  val time = if (item.createdAt > 0) SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN).format(Date(item.createdAt)) else ""
  return "${index + 1}/$total　$kind${if (time.isNotBlank()) "・$time" else ""}${if (item.current) "　✓現在の設定画" else ""}"
}

/** 端末の 画像/OpenClaw/<キャラ名> に保存する。保存できたら true。 */
internal fun saveBitmapToGallery(context: Context, bitmap: Bitmap, folder: String, fileName: String): Boolean {
  val safeFolder = folder.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "キャラ" }
  val values =
    ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
      put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
      put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/OpenClaw/$safeFolder")
    }
  val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
  return context.contentResolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } == true
}

/**
 * 立ち絵などの候補を、横にスライドして見比べる。「この設定画にする」まで候補は残り、
 * 端末への保存と、いらない候補の削除もここからできる。
 */
@Composable
internal fun SheetCandidatePager(
  characterName: String,
  items: List<CandidateItem>,
  loadImage: suspend (String) -> Bitmap?,
  onChoose: (String) -> Unit,
  onDelete: (String) -> Unit,
  onStatus: (String) -> Unit,
) {
  val context = LocalContext.current
  val state = rememberPagerState { items.size }
  var deleting by remember { mutableStateOf<String?>(null) }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
    Text("🖼 候補（${items.size}枚・左右にスライド）", style = ClawTheme.type.section, color = ClawTheme.colors.text)
    HorizontalPager(state = state, pageSpacing = 12.dp, modifier = Modifier.fillMaxWidth().height(580.dp)) { page ->
      val item = items[page]
      var bitmap by remember(item.jobId) { mutableStateOf<Bitmap?>(null) }
      var zoomed by remember(item.jobId) { mutableStateOf(false) }
      LaunchedEffect(item.jobId) { bitmap = loadImage(item.jobId) }
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val shown = bitmap
        if (shown != null) {
          Image(
            bitmap = shown.asImageBitmap(),
            contentDescription = "$characterName の候補",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(10.dp)).clickable { zoomed = true },
          )
          // タップで拡大
          if (zoomed) ZoomableImageDialog(bitmap = shown.asImageBitmap(), title = "$characterName ${candidateKindLabel(item)}", onDismiss = { zoomed = false })
        } else {
          Text("読み込み中…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, modifier = Modifier.height(420.dp))
        }
        Text(candidateLabel(item, page, items.size), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          ClawPrimaryButton(text = if (item.current) "✓ 設定中" else "この設定画にする", enabled = !item.current, onClick = { onChoose(item.jobId) }, modifier = Modifier.weight(1f))
          ClawSecondaryButton(
            text = "💾 保存",
            enabled = shown != null,
            onClick = {
              val ok = shown != null && saveBitmapToGallery(context, shown, characterName, "${characterName}_${candidateKindLabel(item)}_${item.jobId}.png")
              onStatus(if (ok) "「$characterName」の画像を、端末の 画像/OpenClaw/$characterName に保存しました" else "保存できませんでした")
            },
            modifier = Modifier.weight(1f),
          )
          if (!item.current) ClawSecondaryButton(text = "🗑", onClick = { deleting = item.jobId }, modifier = Modifier.padding(start = 0.dp))
        }
      }
    }
  }

  deleting?.let { jobId ->
    AlertDialog(
      onDismissRequest = { deleting = null },
      title = { Text("この候補を消しますか？") },
      text = { Text("消すと元に戻せません。") },
      confirmButton = { TextButton(onClick = { deleting = null; onDelete(jobId) }) { Text("消す") } },
      dismissButton = { TextButton(onClick = { deleting = null }) { Text("やめる") } },
    )
  }
}
