package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 1つの区切り：名前と、その中の項目（id・名前）。 */
private data class ItemGroup(val name: String, val items: List<Pair<String, String>>)

/**
 * キャラ図鑑・モーション・体位の項目を、画像・動画・漫画の生成にも足すための選択画面。
 * 選んだものは「opt:…」「motion:…」「pose:…」「custom:…」の形で返り、ゲートウェイが絵の指示に足します。
 */
@Composable
internal fun ExtraItemsDialog(viewModel: MainViewModel, selected: Set<String>, onChange: (Set<String>) -> Unit, onClose: () -> Unit) {
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    ItemDisplay.load(androidx.compose.ui.platform.LocalContext.current)
    LaunchedEffect(Unit) {
      MotionCatalog.load(viewModel)
      SampleStore.refresh(viewModel)
    }
    var tab by remember { mutableStateOf("opt") }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(setOf<String>()) }

    val groups: List<ItemGroup> =
      when (tab) {
        "opt" -> CHARACTER_OPTION_CATALOG.map { genre -> ItemGroup(genre.name, genre.parts.flatMap { it.items }) }
        "motion" -> MotionCatalog.options.map { (group, items) -> ItemGroup(group, items) }
        "pose" -> MotionCatalog.poses.groupBy { it.third }.map { (group, rows) -> ItemGroup(group, rows.map { it.first to it.second }) }
        else -> listOf(ItemGroup("追加した項目", SampleStore.customItems.map { it.first to it.second }))
      }
    val prefix = tab + ":"
    val filtered =
      if (query.isBlank()) groups
      else groups.map { g -> ItemGroup(g.name, g.items.filter { it.second.contains(query.trim(), ignoreCase = true) }) }.filter { it.items.isNotEmpty() }

    Box(modifier = Modifier.fillMaxSize().background(ClawTheme.colors.canvas)) {
      LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically) {
            ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onClose)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "🧩 項目を足す", style = ClawTheme.type.title, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
          }
        }
        item {
          Text(
            text = "キャラ図鑑・モーション・体位の項目を、この作品づくりに足します。選んだ項目の指示が、内容に付け加わります（${selected.size}件を選択中）。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textMuted,
          )
        }
        item {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("opt" to "🧍 キャラ図鑑", "motion" to "🎞 モーション", "pose" to "🛏 体位", "custom" to "➕ 追加分").forEach { (id, label) ->
              Box(
                modifier =
                  Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (tab == id) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
                    .clickable { tab = id }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
              ) { Text(text = label, style = ClawTheme.type.caption, color = ClawTheme.colors.text) }
            }
          }
        }
        item { ItemSizePicker(Modifier.fillMaxWidth()) }
        item { ClawTextField(value = query, onValueChange = { query = it }, placeholder = "🔍 名前で探す", label = "さがす", modifier = Modifier.fillMaxWidth()) }
        if (selected.isNotEmpty()) {
          item { ClawSecondaryButton(text = "選んだ ${selected.size} 件をすべて外す", onClick = { onChange(emptySet()) }, modifier = Modifier.fillMaxWidth()) }
        }
        if (filtered.isEmpty()) item { Text(text = "ありません。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted) }
        filtered.forEach { group ->
          val key = "$tab/${group.name}"
          val isOpen = query.isNotBlank() || key in open
          val chosen = group.items.count { (prefix + it.first) in selected }
          item(key = "head-$key") {
            Row(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.4f))
                .clickable { open = if (key in open) open - key else open + key }.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
              Text(text = "${if (isOpen) "▼" else "▶"} ${group.name}（${group.items.size}）", style = ClawTheme.type.caption, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
              if (chosen > 0) Text(text = "${chosen}件", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
            }
          }
          if (isOpen) {
            item(key = "grid-$key") {
              ItemChoiceGrid(
                viewModel = viewModel,
                items = group.items.take(300).map { (id, label) -> ChoiceItem(id, label, prefix + id) },
                isSelected = { (prefix + it) in selected },
                onClick = { id -> onChange(if ((prefix + id) in selected) selected - (prefix + id) else selected + (prefix + id)) },
              )
            }
          }
        }
        item { ClawPrimaryButton(text = "決定（${selected.size}件）", onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
        item { Spacer(modifier = Modifier.heightIn(min = 24.dp)) }
      }
    }
  }
}
