package ai.openclaw.app.ui

import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 列の数（0 = 画面の広さに合わせる）から、グリッドの列の決め方を作る。 */
internal fun fanBookCells(columns: Int, minSize: Dp): GridCells = if (columns > 0) GridCells.Fixed(columns) else GridCells.Adaptive(minSize)

/** 1列／2列／3列／自動 を選ぶ。選んだ列の数は、端末に覚えておく。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FanBookColumnsBar(columns: Int, onChange: (Int) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("並べ方：", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, modifier = Modifier.padding(top = 10.dp))
    listOf(0 to "自動", 1 to "1列", 2 to "2列", 3 to "3列", 4 to "4列").forEach { (n, label) ->
      ClawSecondaryButton(text = if (columns == n) "● $label" else label, onClick = { onChange(n) })
    }
  }
}

/** 分類ごとの言い方：アニメは「作品・キャラ」、俳優などは「事務所・人」、歌手などは「グループ・メンバー」。 */
internal data class FanBookTerms(val group: String, val member: String)

internal fun fanBookTerms(root: String): FanBookTerms =
  when (root) {
    "アニメ", "アダルトアニメ" -> FanBookTerms("作品", "キャラ")
    "俳優", "グラビアアイドル", "モデル", "YouTuber", "Vチューバー" -> FanBookTerms("事務所・所属", "人")
    else -> FanBookTerms("グループ", "メンバー")
  }

/** 押す＝開く、長押し＝メニュー、のカード。並び替えモードでは、◀ ▶ が出る。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FanBookTile(
  title: String,
  subtitle: String,
  hint: String,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier,
  onBefore: (() -> Unit)? = null,
  onAfter: (() -> Unit)? = null,
  /** アイコンの鍵。なければ、題名から決める（FanBookIcons.keyOfTitle）。 */
  iconKey: String? = null,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val iconVersion = FanBookIcons.version
  val icon = remember(iconKey, title, iconVersion) { FanBookIcons.load(context, iconKey ?: FanBookIcons.keyOfTitle(title)) }
  // 「表示の名前」に変えてあれば、それを出す（元の名前は、そのまま）
  val namesVersion = FanBookNames.version
  val shownTitle =
    remember(iconKey, title, namesVersion) {
      val label = FanBookNames.label(context, iconKey ?: FanBookIcons.keyOfTitle(title))
      when {
        label == null -> title
        title.startsWith("📖 ") -> "📖 $label"
        title.startsWith("📁 ") -> "📁 $label"
        else -> label
      }
    }
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(16.dp))
        .background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      if (icon != null) {
        androidx.compose.foundation.Image(
          bitmap = icon.asImageBitmap(),
          contentDescription = null,
          contentScale = androidx.compose.ui.layout.ContentScale.Crop,
          modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(shownTitle, style = ClawTheme.type.title, color = ClawTheme.colors.text)
        Text(subtitle, style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
      }
    }
    if (onBefore != null || onAfter != null) {
      androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClawSecondaryButton(text = "◀", onClick = { onBefore?.invoke() })
        ClawSecondaryButton(text = "▶", onClick = { onAfter?.invoke() })
      }
    } else {
      Text(hint, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
    }
  }
}

/** 「消しますか？」の確認。 */
@Composable
internal fun FanBookConfirmDialog(title: String, text: String, confirmText: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text, style = ClawTheme.type.caption, color = ClawTheme.colors.text) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )
}

/** グループの長押しメニュー：並び替え・分類を変える・削除。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FanBookGroupMenu(
  group: String,
  onDismiss: () -> Unit,
  onMove: (Int) -> Unit,
  onChangeCategory: () -> Unit,
  onDelete: () -> Unit,
  hasIcon: Boolean = false,
  onPickIcon: () -> Unit = {},
  onClearIcon: () -> Unit = {},
  label: String = "",
  onLabel: (String) -> Unit = {},
) {
  var confirm by remember { mutableStateOf(false) }
  var newLabel by remember { mutableStateOf(label) }
  var custom by remember { mutableStateOf(false) }
  if (custom) {
    FanBookCustomDialog(group = group, onDismiss = { custom = false })
    return
  }
  if (confirm) {
    FanBookConfirmDialog(
      title = "「$group」を消しますか？",
      text = "グループと、中のメンバー全員が、ファンブックから見えなくなります（元のデータや写真のファイルは、消えません）。同じグループをまた足すと、元に戻ります。",
      confirmText = "消す",
      onDismiss = { confirm = false },
      onConfirm = onDelete,
    )
    return
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("「$group」") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ClawSecondaryButton(text = "← 前へ", onClick = { onMove(-1) })
          ClawSecondaryButton(text = "後ろへ →", onClick = { onMove(1) })
        }
        ClawSecondaryButton(text = "📁 分類を変える", onClick = onChangeCategory, modifier = Modifier.fillMaxWidth())
        ClawSecondaryButton(text = "🖼 アイコンを選ぶ（スマホの写真から）", onClick = onPickIcon, modifier = Modifier.fillMaxWidth())
        if (hasIcon) ClawSecondaryButton(text = "アイコンをやめる", onClick = onClearIcon, modifier = Modifier.fillMaxWidth())
        ClawTextField(value = newLabel, onValueChange = { newLabel = it }, placeholder = "表示する名前", label = "名前を変える（この端末の表示だけ）", modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ClawSecondaryButton(text = "✏️ 表示の名前を変える", onClick = { if (newLabel.isNotBlank()) onLabel(newLabel.trim()) })
          if (label.isNotBlank()) ClawSecondaryButton(text = "元の名前に戻す", onClick = { onLabel("") })
        }
        ClawSecondaryButton(text = "⭐ 追加機能", onClick = { custom = true }, modifier = Modifier.fillMaxWidth())
        ClawSecondaryButton(text = "🗑 このグループを消す", onClick = { confirm = true }, modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
  )
}

/** 分類の長押しメニュー：並び替え・名前を変える・大項目に入れる／出す・削除。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FanBookCategoryMenu(
  name: String,
  fixed: Boolean,
  parent: String,
  topNames: List<String>,
  onDismiss: () -> Unit,
  onMove: (Int) -> Unit,
  onParent: (String) -> Unit,
  onRename: (String) -> Unit,
  onDelete: () -> Unit,
  hasIcon: Boolean = false,
  onPickIcon: () -> Unit = {},
  onClearIcon: () -> Unit = {},
  label: String = "",
  onLabel: (String) -> Unit = {},
) {
  var confirm by remember { mutableStateOf(false) }
  var newName by remember { mutableStateOf("") }
  var newLabel by remember { mutableStateOf(label) }
  if (confirm) {
    FanBookConfirmDialog(
      title = "分類「$name」を消しますか？",
      text = if (fixed) "初めから用意されている分類は、隠れます（見えなくなるだけです）。中のグループは「その他」に移り、メンバーは消えません。同じ名前の分類を「＋ 分類を追加」で入れると、戻せます。" else "中のグループは「その他」に移ります。メンバーは消えません。",
      confirmText = "消す",
      onDismiss = { confirm = false },
      onConfirm = onDelete,
    )
    return
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("「$name」") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ClawSecondaryButton(text = "← 前へ", onClick = { onMove(-1) })
          ClawSecondaryButton(text = "後ろへ →", onClick = { onMove(1) })
        }
        Text(if (parent.isBlank()) "いまは、一番上に並んでいます。大項目の中に入れられます。" else "いまは「$parent」の中にあります。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          topNames.filter { it != name && it != parent }.forEach { top -> ClawSecondaryButton(text = "「$top」の中へ", onClick = { onParent(top) }) }
          if (parent.isNotBlank()) ClawSecondaryButton(text = "一番上に出す", onClick = { onParent("") })
        }
        ClawSecondaryButton(text = "🖼 アイコンを選ぶ（スマホの写真から）", onClick = onPickIcon, modifier = Modifier.fillMaxWidth())
        if (hasIcon) ClawSecondaryButton(text = "アイコンをやめる", onClick = onClearIcon, modifier = Modifier.fillMaxWidth())
        if (!fixed) {
          ClawTextField(value = newName, onValueChange = { newName = it }, placeholder = "新しい名前", label = "名前を変える", modifier = Modifier.fillMaxWidth())
          ClawSecondaryButton(text = "名前を変える", onClick = { if (newName.isNotBlank()) onRename(newName.trim()) }, modifier = Modifier.fillMaxWidth())
        } else {
          Text("初めから用意されている分類なので、名前そのものは変えられません。この端末での、表示の名前は、変えられます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          ClawTextField(value = newLabel, onValueChange = { newLabel = it }, placeholder = "表示する名前", label = "表示の名前（この端末だけ）", modifier = Modifier.fillMaxWidth())
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ClawSecondaryButton(text = "✏️ 表示の名前を変える", onClick = { if (newLabel.isNotBlank()) onLabel(newLabel.trim()) })
            if (label.isNotBlank()) ClawSecondaryButton(text = "元の名前に戻す", onClick = { onLabel("") })
          }
        }
        ClawSecondaryButton(text = if (fixed) "🗑 この分類を消す（隠す）" else "🗑 この分類を消す", onClick = { confirm = true }, modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
  )
}

private const val FANBOOK_PREFS = "openclaw-fanbook"

/** 覚えておいた列の数（0 = 自動）。 */
internal fun readFanBookColumns(context: android.content.Context): Int =
  runCatching { context.getSharedPreferences(FANBOOK_PREFS, android.content.Context.MODE_PRIVATE).getInt("columns", 0) }.getOrDefault(0)

internal fun writeFanBookColumns(context: android.content.Context, columns: Int) {
  runCatching { context.getSharedPreferences(FANBOOK_PREFS, android.content.Context.MODE_PRIVATE).edit().putInt("columns", columns).apply() }
}

/** バックアップを作る／一覧から戻す。写真は入りません（写真のフォルダに残ります）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FanBookBackupDialog(
  viewModel: ai.openclaw.app.MainViewModel,
  onDismiss: () -> Unit,
  onRestored: (String) -> Unit,
) {
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  var rows by remember { mutableStateOf<List<Triple<String, String, Int>>>(emptyList()) }
  var dir by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var confirm by remember { mutableStateOf<String?>(null) }

  suspend fun call(method: ai.openclaw.app.gateway.GatewayMethod, params: org.json.JSONObject = org.json.JSONObject()): org.json.JSONObject =
    org.json.JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = 60_000))

  suspend fun reload() {
    runCatching {
      val res = call(ai.openclaw.app.gateway.GatewayMethod.IdolMasterBackupList)
      dir = res.optString("dir")
      val arr = res.optJSONArray("backups")
      rows = (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.map { Triple(it.optString("file"), it.optString("createdAt"), it.optInt("members")) }
    }.onFailure { message = "一覧を読めませんでした：${it.message}" }
  }
  androidx.compose.runtime.LaunchedEffect(Unit) { reload() }

  val target = confirm
  if (target != null) {
    FanBookConfirmDialog(
      title = "このバックアップに戻しますか？",
      text = "いまの名簿・分類・削除の状態は、戻す前に自動でバックアップします（あとで、それに戻せます）。",
      confirmText = "戻す",
      onDismiss = { confirm = null },
      onConfirm = {
        confirm = null
        busy = true
        scope.launch {
          runCatching { call(ai.openclaw.app.gateway.GatewayMethod.IdolMasterBackupRestore, org.json.JSONObject().put("file", target)) }
            .onSuccess { onRestored("バックアップに戻しました（${it.optInt("members")}人）") }
            .onFailure { message = "戻せませんでした：${it.message}" }
          busy = false
        }
      },
    )
  }
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("バックアップと復元") },
    text = {
      Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("名簿・分類・削除の記録を、1つのファイルにまとめます。写真は入りません（PC の写真のフォルダに残ります）。新しい20個まで残ります。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        ClawSecondaryButton(
          text = if (busy) "作っています…" else "💾 いまのバックアップを作る",
          onClick = {
            if (busy) return@ClawSecondaryButton
            busy = true
            scope.launch {
              runCatching { call(ai.openclaw.app.gateway.GatewayMethod.IdolMasterBackupCreate) }
                .onSuccess { message = "作りました（${it.optInt("members")}人）"; reload() }
                .onFailure { message = "作れませんでした：${it.message}" }
              busy = false
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        if (rows.isEmpty()) Text("まだバックアップがありません。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        rows.forEach { (file, at, members) ->
          ClawSecondaryButton(text = "↩ ${at.replace('T', ' ').take(16)}（${members}人）に戻す", onClick = { if (!busy) confirm = file }, modifier = Modifier.fillMaxWidth())
        }
        if (dir.isNotBlank()) Text("保存先（PC）：$dir", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    },
    confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("閉じる") } },
  )
}

/** 一覧の上に並べる、小さなボタン（高さを詰めた版）。 */
@Composable
internal fun FanBookSmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
  androidx.compose.material3.Surface(
    onClick = onClick,
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    color = if (primary) ClawTheme.colors.primary else ClawTheme.colors.surfaceRaised,
    contentColor = ClawTheme.colors.text,
    border = androidx.compose.foundation.BorderStroke(1.dp, if (primary) ClawTheme.colors.primary else ClawTheme.colors.borderStrong),
  ) {
    Text(text = text, style = ClawTheme.type.caption, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
  }
}
