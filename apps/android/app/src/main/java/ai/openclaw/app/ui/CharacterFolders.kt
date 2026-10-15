package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.io.ByteArrayOutputStream
import org.json.JSONObject

/** キャラ図鑑のフォルダ（名前・アイコン・並び順）。 */
internal data class ChFolder(val id: String, val name: String, val order: Int, val icon: Bitmap?)

internal suspend fun loadFolders(viewModel: MainViewModel): List<ChFolder> =
  runCatching {
    val rows = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ComicFolderList, "{}", timeoutMs = 30_000)).optJSONArray("folders")
    (0 until (rows?.length() ?: 0)).map { i ->
      val o = rows!!.getJSONObject(i)
      val b64 = o.optString("iconBase64")
      ChFolder(
        id = o.optString("id"),
        name = o.optString("name"),
        order = o.optInt("order"),
        icon = if (b64.isBlank()) null else runCatching { Base64.decode(b64, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull(),
      )
    }
  }.getOrDefault(emptyList())

/** 絵を 256px 以内に縮めた PNG の base64（フォルダのアイコン用）。 */
internal fun bitmapToIconBase64(source: Bitmap): String {
  val scale = minOf(1f, 256f / maxOf(source.width, source.height))
  val bmp = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true) else source
  val out = ByteArrayOutputStream()
  bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
  return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

/** フォルダの札：アイコン（なければ 📁）と名前と人数。並べ替え中は ◀ ▶ が出る。 */
@Composable
internal fun FolderCard(folder: ChFolder, count: Int, reorder: Boolean, onOpen: () -> Unit, onLeft: (() -> Unit)?, onRight: (() -> Unit)?, modifier: Modifier = Modifier) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ClawTheme.colors.surface).clickable(onClick = onOpen).padding(10.dp),
  ) {
    Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(ClawTheme.colors.surfaceRaised), contentAlignment = Alignment.Center) {
      val icon = folder.icon
      if (icon != null) Image(bitmap = icon.asImageBitmap(), contentDescription = folder.name, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp))
      else Text(text = "📁", style = ClawTheme.type.title)
    }
    Text(text = folder.name, style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.text, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    Text(text = "${count}人", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    if (reorder) ReorderButtons(onLeft, onRight)
  }
}

/** 並べ替え用の ◀ ▶（端では押せない）。 */
@Composable
internal fun ReorderButtons(onLeft: (() -> Unit)?, onRight: (() -> Unit)?) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
    Box(
      modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.canvas.copy(alpha = if (onLeft != null) 0.9f else 0.3f)).let { if (onLeft != null) it.clickable(onClick = onLeft) else it },
      contentAlignment = Alignment.Center,
    ) { Text(text = "◀", color = if (onLeft != null) ClawTheme.colors.text else ClawTheme.colors.textMuted) }
    Box(
      modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.canvas.copy(alpha = if (onRight != null) 0.9f else 0.3f)).let { if (onRight != null) it.clickable(onClick = onRight) else it },
      contentAlignment = Alignment.Center,
    ) { Text(text = "▶", color = if (onRight != null) ClawTheme.colors.text else ClawTheme.colors.textMuted) }
  }
}

/** フォルダの名前を入力する。 */
@Composable
internal fun FolderNameDialog(title: String, initial: String, onOk: (String) -> Unit, onDismiss: () -> Unit) {
  var name by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { ClawTextField(value = name, onValueChange = { name = it.take(40) }, placeholder = "フォルダの名前", label = "名前", modifier = Modifier.fillMaxWidth()) },
    confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onOk(name.trim()) }) { Text("決定") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )
}

/** キャラを入れるフォルダを選ぶ（未分類にも戻せる）。 */
@Composable
internal fun FolderMoveDialog(characterName: String, folders: List<ChFolder>, currentId: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("「$characterName」を入れるフォルダ") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ClawSecondaryButton(text = (if (currentId.isBlank()) "✓ " else "") + "📂 未分類（フォルダから出す）", onClick = { onPick("") }, modifier = Modifier.fillMaxWidth())
        folders.forEach { f ->
          ClawSecondaryButton(text = (if (currentId == f.id) "✓ " else "") + "📁 ${f.name}", onClick = { onPick(f.id) }, modifier = Modifier.fillMaxWidth())
        }
        if (folders.isEmpty()) Text(text = "フォルダがまだありません。一覧の「＋ フォルダを作る」で作れます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted, textAlign = TextAlign.Start)
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
  )
}
