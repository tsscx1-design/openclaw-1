package ai.openclaw.app.ui

import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.PartnerAvatar
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** アイコンで選ぶ一覧の1つ（キャラ・モデルなど）。アイコンは、画像のアドレス・絵・絵文字のどれか。 */
internal data class IconChoice(
  val id: String,
  val name: String,
  val avatarUrl: String? = null,
  val bitmap: Bitmap? = null,
  val emoji: String = "",
  val note: String = "",
)

@Composable
private fun IconChoiceFace(choice: IconChoice, size: Int) {
  when {
    choice.bitmap != null ->
      Image(bitmap = choice.bitmap.asImageBitmap(), contentDescription = choice.name, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp).clip(CircleShape))
    !choice.avatarUrl.isNullOrBlank() -> PartnerAvatar(avatarUrl = choice.avatarUrl, name = choice.name, size = size.dp)
    else ->
      Box(modifier = Modifier.size(size.dp).clip(CircleShape).background(ClawTheme.colors.surfaceRaised), contentAlignment = Alignment.Center) {
        Text(text = choice.emoji.ifBlank { choice.name.take(1) }, fontSize = (size / 2.2f).sp)
      }
  }
}

/**
 * アイコンで選ぶ別ウィンドウ。[multi] なら何人でも（[minSelected] 人は残す）、そうでなければ1つ選ぶと閉じる。
 */
@Composable
internal fun IconPickerDialog(
  title: String,
  items: List<IconChoice>,
  selected: Set<String>,
  multi: Boolean,
  minSelected: Int = 0,
  onPick: (String) -> Unit,
  onClose: () -> Unit,
) {
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Box(modifier = Modifier.fillMaxSize().background(ClawTheme.colors.canvas)) {
      LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(text = title, style = ClawTheme.type.title, color = ClawTheme.colors.text) }
        if (multi) item { Text(text = "アイコンをタップで、入れる・外す（${selected.size}人を選択中${if (minSelected > 0) "。最低${minSelected}人は必要です" else ""}）", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted) }
        val rows = items.chunked(3)
        items(rows.size) { rowIndex ->
          val row = rows[rowIndex]
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { choice ->
              val isSelected = choice.id in selected
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                  Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surface)
                    .border(if (isSelected) 2.dp else 0.dp, if (isSelected) ClawTheme.colors.primary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable {
                      if (!multi || !(isSelected && selected.size <= minSelected)) onPick(choice.id)
                      if (!multi) onClose()
                    }
                    .padding(10.dp),
              ) {
                IconChoiceFace(choice, 64)
                Text(text = choice.name, style = ClawTheme.type.body.copy(fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium), color = ClawTheme.colors.text, maxLines = 2, textAlign = TextAlign.Center)
                if (choice.note.isNotBlank()) Text(text = choice.note, style = ClawTheme.type.captionSmall.copy(fontSize = 10.sp), color = ClawTheme.colors.textMuted, maxLines = 2, textAlign = TextAlign.Center)
              }
            }
            repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
          }
        }
        item { ClawPrimaryButton(text = "閉じる", onClick = onClose, modifier = Modifier.fillMaxWidth()) }
      }
    }
  }
}

/** 1人を選ぶ欄：選んでいるキャラのアイコンと名前を出し、押すとアイコンの選択ウィンドウが開く。 */
@Composable
internal fun CharacterSlotPicker(
  label: String,
  noneLabel: String,
  characters: List<Triple<String, String, List<String>>>,
  icons: Map<String, Bitmap>,
  selectedId: String,
  onPick: (String) -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  val current = characters.firstOrNull { it.first == selectedId }
  Text(text = label, style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
  Row(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).clickable { open = true }.padding(10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    IconChoiceFace(IconChoice(selectedId, current?.second ?: noneLabel, bitmap = icons[selectedId], emoji = if (current == null) "🎲" else ""), 40)
    Text(text = current?.second ?: noneLabel, style = ClawTheme.type.body, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
    Text(text = "選ぶ ▶", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
  }
  if (open) {
    IconPickerDialog(
      title = label,
      items = listOf(IconChoice("", noneLabel, emoji = "🎲")) + characters.map { IconChoice(it.first, it.second, bitmap = icons[it.first]) },
      selected = setOf(selectedId),
      multi = false,
      onPick = onPick,
      onClose = { open = false },
    )
  }
}
