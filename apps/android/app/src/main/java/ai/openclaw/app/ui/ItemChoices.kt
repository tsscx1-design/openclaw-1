package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 項目の1つ。sampleKey があれば、その見本の絵を名前の横に小さく出す（キャラ図鑑 "opt:id"、モーション "motion:id"、体位 "pose:id"）。 */
internal data class ChoiceItem(val id: String, val label: String, val sampleKey: String? = null)

/** 見本の絵の小さな表示（なければ何も出さない）。タップで onZoom。 */
@Composable
private fun SampleThumb(viewModel: MainViewModel, key: String, onZoom: () -> Unit) {
  val version = SampleStore.available[key] ?: return
  var bmp by remember(key, version) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(key, version) { bmp = SampleStore.thumb(viewModel, key) }
  val size = (40 * ItemDisplay.scale).dp
  Box(
    modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(ClawTheme.colors.surface).clickable(onClick = onZoom),
    contentAlignment = Alignment.Center,
  ) {
    val shown = bmp
    if (shown != null) Image(bitmap = shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    else Text("🖼", fontSize = 12.sp)
  }
}

/** 項目の1マス：見本が小さく名前の横に出る。マスをタップで選ぶ、見本をタップで全画面（左右にスライド）。 */
@Composable
internal fun ItemChoiceCell(viewModel: MainViewModel, item: ChoiceItem, selected: Boolean, onClick: () -> Unit, onZoom: (String) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier =
      modifier
        .heightIn(min = (44 * ItemDisplay.scale).dp)
        .clip(RoundedCornerShape(10.dp))
        .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
        .border(if (selected) 1.5.dp else 1.dp, if (selected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item.sampleKey?.let { key -> SampleThumb(viewModel, key) { onZoom(key) } }
    Text(
      text = item.label,
      style = ClawTheme.type.body.copy(fontSize = (13 * ItemDisplay.scale).sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
      color = if (selected) ClawTheme.colors.text else ClawTheme.colors.textMuted,
      modifier = Modifier.weight(1f),
    )
  }
}

/** 見本のある項目だけを、画面の並びのまま左右にスライドして全画面で見る。 */
@Composable
private fun ZoomGallery(viewModel: MainViewModel, items: List<ChoiceItem>, startKey: String, onDismiss: () -> Unit) {
  val made = items.filter { it.sampleKey != null && SampleStore.hasSample(it.sampleKey) }.map { it.sampleKey!! to it.label }
  if (made.isNotEmpty()) SampleGalleryDialog(viewModel = viewModel, items = made, startKey = startKey, onDismiss = onDismiss)
}

/**
 * 項目を、いまの「列の数」と「大きさ」でならべる（選んだものは色が付く）。
 * 見本の絵は名前の横に小さく出て、タップで全画面。
 */
@Composable
internal fun ItemChoiceGrid(viewModel: MainViewModel, items: List<ChoiceItem>, isSelected: (String) -> Boolean, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
  var zoomKey by remember { mutableStateOf<String?>(null) }
  val columns = ItemDisplay.columns
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    items.chunked(columns).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { item ->
          ItemChoiceCell(viewModel, item, isSelected(item.id), onClick = { onClick(item.id) }, onZoom = { zoomKey = it }, modifier = Modifier.weight(1f))
        }
        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
      }
    }
  }
  zoomKey?.let { ZoomGallery(viewModel, items, it) { zoomKey = null } }
}

/**
 * たくさんある項目から1つ選ぶ全画面。検索、列の数と大きさの切り替え、見本の絵つき。
 * [clearLabel] があれば、いちばん上に「選ばない／外す」を出す。
 */
@Composable
internal fun ItemPickerDialog(
  viewModel: MainViewModel,
  title: String,
  items: List<ChoiceItem>,
  current: String?,
  clearLabel: String?,
  onPick: (String?) -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    var query by remember { mutableStateOf("") }
    var zoomKey by remember { mutableStateOf<String?>(null) }
    val columns = ItemDisplay.columns
    val shown = if (query.isBlank()) items else items.filter { it.label.contains(query.trim(), ignoreCase = true) }
    val rows = remember(shown, columns) { shown.chunked(columns) }
    Box(modifier = Modifier.fillMaxSize().background(ClawTheme.colors.canvas)) {
      LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically) {
            ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onDismiss)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, style = ClawTheme.type.title, color = ClawTheme.colors.text, modifier = Modifier.weight(1f), maxLines = 1)
          }
        }
        item { ItemSizePicker(Modifier.fillMaxWidth()) }
        item { ClawTextField(value = query, onValueChange = { query = it }, placeholder = "🔍 名前で絞り込む", label = "絞り込み", modifier = Modifier.fillMaxWidth()) }
        if (clearLabel != null && current != null) {
          item {
            Box(
              modifier = Modifier.fillMaxWidth().heightIn(min = (44 * ItemDisplay.scale).dp).clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised).clickable { onPick(null) }.padding(10.dp),
              contentAlignment = Alignment.Center,
            ) { Text(text = "✕ $clearLabel", style = ClawTheme.type.body, color = ClawTheme.colors.text) }
          }
        }
        item { Text(text = "${shown.size}件（見本の絵をタップで全画面、横にスライドで次へ）", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted) }
        items(rows.size) { rowIndex ->
          val row = rows[rowIndex]
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { item ->
              ItemChoiceCell(viewModel, item, item.id == current, onClick = { onPick(item.id) }, onZoom = { zoomKey = it }, modifier = Modifier.weight(1f))
            }
            repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
          }
        }
        item { Spacer(modifier = Modifier.heightIn(min = 24.dp)) }
      }
    }
    zoomKey?.let { ZoomGallery(viewModel, shown, it) { zoomKey = null } }
  }
}
