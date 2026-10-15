package ai.openclaw.app.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import org.json.JSONArray
import org.json.JSONObject

// ────────────────────────────────────────────────
// ホームパネル（固定セクション）管理
// ────────────────────────────────────────────────

/** ホーム画面の固定パネル項目 */
enum class HomePanelId(val label: String, val icon: String, val collapsible: Boolean) {
  PC_HEALTH      ("PCの状態",           "💻", false),
  PARTNER        ("パートナーチャット",  "💬", false),
  MODEL_ENGINE   ("モデルエンジン",      "🤖", true),
  AGENT_STATUS   ("AI状態",             "⚡", false),
  SHORTCUTS      ("ショートカット",      "🏠", false),
  CHARA_SHORTCUT ("キャラ図鑑ショートカット", "👥", false),
  ATTENTION      ("注意事項",           "⚠️", false),
}

data class HomePanelConfig(
  val id: HomePanelId,
  val order: Int,
  val visible: Boolean = true,
  val collapsed: Boolean = false,   // collapsible なパネルのみ有効
)

private val DEFAULT_PANELS = listOf(
  HomePanelConfig(HomePanelId.PC_HEALTH,      0, true,  false),
  HomePanelConfig(HomePanelId.PARTNER,        1, true,  false),
  HomePanelConfig(HomePanelId.MODEL_ENGINE,   2, true,  false),
  HomePanelConfig(HomePanelId.AGENT_STATUS,   3, true,  false),
  HomePanelConfig(HomePanelId.SHORTCUTS,      4, true,  false),
  HomePanelConfig(HomePanelId.CHARA_SHORTCUT, 5, true,  false),
  HomePanelConfig(HomePanelId.ATTENTION,      6, true,  false),
)

private const val KEY_PANELS = "panels_v1"

fun loadHomePanels(context: Context): List<HomePanelConfig> {
  return try {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_PANELS, null) ?: return DEFAULT_PANELS
    val arr = JSONArray(json)
    val saved = (0 until arr.length()).map { i ->
      val o = arr.getJSONObject(i)
      HomePanelConfig(
        id        = HomePanelId.valueOf(o.getString("id")),
        order     = o.getInt("order"),
        visible   = o.getBoolean("visible"),
        collapsed = o.optBoolean("collapsed", false),
      )
    }.sortedBy { it.order }
    val savedIds = saved.map { it.id }.toSet()
    val missing = DEFAULT_PANELS.filter { it.id !in savedIds }.mapIndexed { i, p -> p.copy(order = saved.size + i) }
    saved + missing
  } catch (_: Exception) { DEFAULT_PANELS }
}

fun saveHomePanels(context: Context, panels: List<HomePanelConfig>) {
  val arr = JSONArray()
  panels.forEachIndexed { i, p ->
    arr.put(JSONObject().also { o ->
      o.put("id", p.id.name); o.put("order", i); o.put("visible", p.visible); o.put("collapsed", p.collapsed)
    })
  }
  context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_PANELS, arr.toString()).apply()
}

// ────────────────────────────────────────────────
// ショートカット定義
// ────────────────────────────────────────────────
enum class HomeShortcutId(val label: String, val icon: String, val description: String) {
  STUDIO_IMAGE   ("画像生成",        "🎨", "WAI/NoobAI/Z-Image等で画像を生成"),
  STUDIO_VIDEO   ("動画生成",        "🎬", "アニメ・実写動画を生成"),
  STUDIO_MUSIC   ("音楽生成",        "🎵", "歌あり・インスト音楽を生成"),
  STUDIO_MANGA   ("漫画生成",        "📖", "4コマ・1ページ漫画を生成"),
  STUDIO_VOICE   ("声生成",          "🎤", "Irodori-TTSで声を生成"),
  STUDIO_MOTION  ("モーションコミック","🎞", "体位・シチュエーションで動画化"),
  MODEL_SAMPLE   ("モデル比較",      "🖼️", "全モデルで同プロンプトを比較"),
  COMIC_CHARA    ("キャラ図鑑",      "👤", "キャラクターの作成・管理・学習"),
  COMIC_SERIES   ("連載コミック",    "📚", "コミックシリーズの作成・管理"),
  PARTNER_CHAT   ("パートナー",      "💬", "AIパートナーとチャット"),
  PARTNER_ALBUM  ("アルバム",        "📷", "パートナーの画像アルバム"),
  PARTNER_DREAM  ("夢日記",          "💤", "パートナーの夢・日記"),
  PC_HEALTH      ("PCヘルス",        "💻", "CPU・GPU・温度を確認"),
  DAILY_BRIEF    ("デイリーブリーフ","🗞", "今日のニュース・天気"),
  QUICK_CHAT     ("クイックチャット","✍", "チャット画面に直行"),
  MINI_GAME      ("ミニゲーム",      "🎮", "内蔵ゲームで遊ぶ"),
}

data class HomeShortcut(
  val id: HomeShortcutId,
  val order: Int,
  val visible: Boolean = true,
  val expanded: Boolean = false,
)

private val DEFAULT_SHORTCUTS = listOf(
  HomeShortcut(HomeShortcutId.PARTNER_CHAT,   0, true,  false),
  HomeShortcut(HomeShortcutId.STUDIO_IMAGE,   1, true,  false),
  HomeShortcut(HomeShortcutId.STUDIO_MOTION,  2, true,  false),
  HomeShortcut(HomeShortcutId.MODEL_SAMPLE,   3, true,  false),
  HomeShortcut(HomeShortcutId.COMIC_CHARA,    4, true,  false),
  HomeShortcut(HomeShortcutId.COMIC_SERIES,   5, false, false),
  HomeShortcut(HomeShortcutId.STUDIO_VIDEO,   6, false, false),
  HomeShortcut(HomeShortcutId.STUDIO_MUSIC,   7, false, false),
  HomeShortcut(HomeShortcutId.STUDIO_MANGA,   8, false, false),
  HomeShortcut(HomeShortcutId.STUDIO_VOICE,   9, false, false),
  HomeShortcut(HomeShortcutId.PARTNER_ALBUM, 10, false, false),
  HomeShortcut(HomeShortcutId.PARTNER_DREAM, 11, false, false),
  HomeShortcut(HomeShortcutId.PC_HEALTH,     12, false, false),
  HomeShortcut(HomeShortcutId.DAILY_BRIEF,   13, false, false),
  HomeShortcut(HomeShortcutId.QUICK_CHAT,    14, false, false),
  HomeShortcut(HomeShortcutId.MINI_GAME,     15, false, false),
)

private const val PREFS_NAME = "home_customization"
private const val KEY_SHORTCUTS = "shortcuts_v1"

fun loadHomeShortcuts(context: Context): List<HomeShortcut> {
  return try {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_SHORTCUTS, null) ?: return DEFAULT_SHORTCUTS
    val arr = JSONArray(json)
    val saved = (0 until arr.length()).map { i ->
      val o = arr.getJSONObject(i)
      HomeShortcut(
        id       = HomeShortcutId.valueOf(o.getString("id")),
        order    = o.getInt("order"),
        visible  = o.getBoolean("visible"),
        expanded = o.optBoolean("expanded", false),
      )
    }.sortedBy { it.order }
    val savedIds = saved.map { it.id }.toSet()
    val missing = DEFAULT_SHORTCUTS.filter { it.id !in savedIds }.mapIndexed { i, s -> s.copy(order = saved.size + i) }
    saved + missing
  } catch (_: Exception) { DEFAULT_SHORTCUTS }
}

fun saveHomeShortcuts(context: Context, shortcuts: List<HomeShortcut>) {
  val arr = JSONArray()
  shortcuts.forEachIndexed { i, s ->
    arr.put(JSONObject().also { o ->
      o.put("id", s.id.name); o.put("order", i); o.put("visible", s.visible); o.put("expanded", s.expanded)
    })
  }
  context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_SHORTCUTS, arr.toString()).apply()
}

// ────────────────────────────────────────────────
// カスタマイズダイアログ（パネル + ショートカット の2タブ）
// ────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeCustomizeDialog(
  shortcuts: List<HomeShortcut>,
  panels: List<HomePanelConfig>,
  onSave: (List<HomeShortcut>, List<HomePanelConfig>) -> Unit,
  onDismiss: () -> Unit,
) {
  var tab by remember { mutableIntStateOf(0) }
  var shortcutItems by remember { mutableStateOf(shortcuts.toMutableList()) }
  var panelItems by remember { mutableStateOf(panels.toMutableList()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("🏠 ホームをカスタマイズ", color = ClawTheme.colors.text, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SecondaryTabRow(selectedTabIndex = tab, containerColor = ClawTheme.colors.canvas, contentColor = ClawTheme.colors.primary) {
          Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("📋 パネル", style = ClawTheme.type.captionSmall) })
          Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("⚡ ショートカット", style = ClawTheme.type.captionSmall) })
        }
        Spacer(Modifier.height(4.dp))
        Text("✅=表示 ⬜=非表示　☰を長押ししてドラッグ、または▲▼で並べ替え", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        Spacer(Modifier.height(2.dp))

        if (tab == 0) {
          // ── パネル並び替え ──
          ReorderableRows(panelItems, { it.id.name }, { from, to -> panelItems = panelItems.toMutableList().also { l -> l.add(to, l.removeAt(from)) } }) { p, idx, handle ->
            Row(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(if (p.visible) ClawTheme.colors.surface else ClawTheme.colors.surfacePressed)
                .padding(horizontal = 8.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("☰", fontSize = 16.sp, color = ClawTheme.colors.textMuted, modifier = handle.padding(end = 6.dp))
              Text(text = if (p.visible) "✅" else "⬜", fontSize = 16.sp,
                modifier = Modifier.combinedClickable(onClick = {
                  panelItems = panelItems.toMutableList().also { it[idx] = p.copy(visible = !p.visible) }
                }))
              Spacer(Modifier.width(6.dp))
              Text("${p.id.icon} ${p.id.label}", style = ClawTheme.type.body.copy(fontSize = 12.sp),
                color = if (p.visible) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
                modifier = Modifier.weight(1f))
              if (p.id.collapsible) {
                Text(text = if (p.collapsed) "📁" else "📂", fontSize = 14.sp,
                  modifier = Modifier.combinedClickable(onClick = {
                    panelItems = panelItems.toMutableList().also { it[idx] = p.copy(collapsed = !p.collapsed) }
                  }))
              } else {
                Spacer(Modifier.width(24.dp))
              }
              IconButton(onClick = {
                if (idx > 0) panelItems = panelItems.toMutableList().also { lst ->
                  val t = lst[idx-1]; lst[idx-1] = lst[idx]; lst[idx] = t
                }
              }, enabled = idx > 0, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "上へ", tint = if (idx > 0) ClawTheme.colors.text else ClawTheme.colors.textSubtle, modifier = Modifier.size(16.dp))
              }
              IconButton(onClick = {
                if (idx < panelItems.size-1) panelItems = panelItems.toMutableList().also { lst ->
                  val t = lst[idx+1]; lst[idx+1] = lst[idx]; lst[idx] = t
                }
              }, enabled = idx < panelItems.size-1, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "下へ", tint = if (idx < panelItems.size-1) ClawTheme.colors.text else ClawTheme.colors.textSubtle, modifier = Modifier.size(16.dp))
              }
            }
          }
        } else {
          // ── ショートカット並び替え ──
          ReorderableRows(shortcutItems, { it.id.name }, { from, to -> shortcutItems = shortcutItems.toMutableList().also { l -> l.add(to, l.removeAt(from)) } }) { s, idx, handle ->
            Row(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(if (s.visible) ClawTheme.colors.surface else ClawTheme.colors.surfacePressed)
                .padding(horizontal = 8.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("☰", fontSize = 16.sp, color = ClawTheme.colors.textMuted, modifier = handle.padding(end = 6.dp))
              Text(text = if (s.visible) "✅" else "⬜", fontSize = 16.sp,
                modifier = Modifier.combinedClickable(onClick = {
                  shortcutItems = shortcutItems.toMutableList().also { it[idx] = s.copy(visible = !s.visible) }
                }))
              Spacer(Modifier.width(6.dp))
              Text("${s.id.icon} ${s.id.label}", style = ClawTheme.type.body.copy(fontSize = 12.sp),
                color = if (s.visible) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
                modifier = Modifier.weight(1f))
              Text(text = if (s.expanded) "📂" else "📁", fontSize = 14.sp,
                modifier = Modifier.combinedClickable(onClick = {
                  shortcutItems = shortcutItems.toMutableList().also { it[idx] = s.copy(expanded = !s.expanded) }
                }))
              IconButton(onClick = {
                if (idx > 0) shortcutItems = shortcutItems.toMutableList().also { lst ->
                  val t = lst[idx-1]; lst[idx-1] = lst[idx]; lst[idx] = t
                }
              }, enabled = idx > 0, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "上へ", tint = if (idx > 0) ClawTheme.colors.text else ClawTheme.colors.textSubtle, modifier = Modifier.size(16.dp))
              }
              IconButton(onClick = {
                if (idx < shortcutItems.size-1) shortcutItems = shortcutItems.toMutableList().also { lst ->
                  val t = lst[idx+1]; lst[idx+1] = lst[idx]; lst[idx] = t
                }
              }, enabled = idx < shortcutItems.size-1, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "下へ", tint = if (idx < shortcutItems.size-1) ClawTheme.colors.text else ClawTheme.colors.textSubtle, modifier = Modifier.size(16.dp))
              }
            }
          }
        }
      }
    },
    confirmButton = { ClawPrimaryButton(text = "保存", onClick = { onSave(shortcutItems.toList(), panelItems.toList()) }) },
    dismissButton = { ClawSecondaryButton(text = "やめる", onClick = onDismiss) },
    containerColor = ClawTheme.colors.canvas,
  )
}

// ────────────────────────────────────────────────
// 折り畳みカード（ホームの各アイテム）
// ────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeShortcutCard(
  shortcut: HomeShortcut,
  onToggleExpand: () -> Unit,
  onLongPress: () -> Unit,
  content: @Composable () -> Unit,
) {
  val rotation by animateFloatAsState(if (shortcut.expanded) 180f else 0f, label = "chevron")
  Column(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ClawTheme.colors.surface),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth()
        .combinedClickable(onClick = onToggleExpand, onLongClick = onLongPress)
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Text(shortcut.id.icon, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column {
          Text("${shortcut.id.icon} ${shortcut.id.label}", style = ClawTheme.type.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = ClawTheme.colors.text)
          if (!shortcut.expanded) Text(shortcut.id.description, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        }
      }
      Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ClawTheme.colors.textMuted, modifier = Modifier.size(22.dp).rotate(rotation))
    }
    AnimatedVisibility(visible = shortcut.expanded, enter = expandVertically(), exit = shrinkVertically()) {
      Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { content() }
    }
  }
}

// ────────────────────────────────────────────────
// 長押し→追加/削除ダイアログ
// ────────────────────────────────────────────────
@Composable
fun AddToHomeDialog(
  shortcutId: HomeShortcutId,
  currentShortcuts: List<HomeShortcut>,
  onAdd: () -> Unit,
  onRemove: () -> Unit,
  onDismiss: () -> Unit,
) {
  val isAdded = currentShortcuts.firstOrNull { it.id == shortcutId }?.visible == true
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("${shortcutId.icon} ${shortcutId.label}", color = ClawTheme.colors.text, fontWeight = FontWeight.Bold) },
    text = { Text(if (isAdded) "ホームから削除しますか？" else "ホームに追加しますか？\nタップで折り畳み開閉できます。", color = ClawTheme.colors.textMuted) },
    confirmButton = { ClawPrimaryButton(text = if (isAdded) "ホームから削除" else "ホームに追加", onClick = { if (isAdded) onRemove() else onAdd() }) },
    dismissButton = { ClawSecondaryButton(text = "やめる", onClick = onDismiss) },
    containerColor = ClawTheme.colors.canvas,
  )
}


/**
 * 並べ替えできる行の並び。「☰」を長押ししてドラッグすると、行が指についてきて、
 * 越えた分だけ入れ替わる（[onMove] に、元の位置と新しい位置が渡る）。
 */
@Composable
private fun <T> ReorderableRows(
  items: List<T>,
  keyOf: (T) -> String,
  onMove: (from: Int, to: Int) -> Unit,
  rowContent: @Composable (item: T, index: Int, handle: Modifier) -> Unit,
) {
  var draggingKey by remember { mutableStateOf<String?>(null) }
  var dragDy by remember { mutableFloatStateOf(0f) }
  var rowPx by remember { mutableFloatStateOf(100f) }
  val currentItems by rememberUpdatedState(items)
  val currentMove by rememberUpdatedState(onMove)

  Column {
    items.forEachIndexed { index, item ->
      val key = keyOf(item)
      val dragging = draggingKey == key
      val handle =
        Modifier.pointerInput(key) {
          detectDragGesturesAfterLongPress(
            onDragStart = { draggingKey = key; dragDy = 0f },
            onDragEnd = { draggingKey = null; dragDy = 0f },
            onDragCancel = { draggingKey = null; dragDy = 0f },
            onDrag = { change, amount ->
              change.consume()
              dragDy += amount.y
              val from = currentItems.indexOfFirst { keyOf(it) == key }
              if (from >= 0 && rowPx > 0f) {
                val to = (from + (dragDy / rowPx).roundToInt()).coerceIn(0, currentItems.size - 1)
                if (to != from) {
                  currentMove(from, to)
                  dragDy -= (to - from) * rowPx
                }
              }
            },
          )
        }
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .onSizeChanged { rowPx = it.height.toFloat() }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = if (dragging) dragDy else 0f; alpha = if (dragging) 0.85f else 1f },
      ) {
        rowContent(item, index, handle)
      }
    }
  }
}
