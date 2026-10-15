package ai.openclaw.app.ui

import ai.openclaw.app.i18n.resolveNativeText
import ai.openclaw.app.ui.design.ClawTheme
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import org.json.JSONArray
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────
// タブ定義
// ─────────────────────────────────────────────────────────────

/** アプリの主要ナビゲーション先 */
@Immutable
internal enum class ShellDestination(
  val icon: ImageVector,
  val label: String,       // 通常表示ラベル
  val shortLabel: String,  // Rail/Bar 狭いとき用（3文字以内）
  val tab: Tab,
) {
  Home(
    icon = Icons.Outlined.BarChart,
    label = "ホーム",
    shortLabel = "ホーム",
    tab = Tab.Overview,
  ),
  Chat(
    icon = Icons.Outlined.ChatBubbleOutline,
    label = "チャット",
    shortLabel = "Chat",
    tab = Tab.Chat,
  ),
  SampleFactory(
    icon = Icons.Outlined.Palette,
    label = "サンプル画像製造",
    shortLabel = "見本",
    tab = Tab.SampleFactory,
  ),
  Sessions(
    icon = Icons.Outlined.AutoStories,
    label = "キャラ図鑑",
    shortLabel = "図鑑",
    tab = Tab.Sessions,
  ),
  Settings(
    icon = Icons.Outlined.Settings,
    label = "設定",
    shortLabel = "設定",
    tab = Tab.Settings,
  ),
}

// ─────────────────────────────────────────────────────────────
// タブカスタマイズ（並び替え・表示/非表示）
// ─────────────────────────────────────────────────────────────

internal data class ShellTabConfig(
  val id: ShellDestination,
  val order: Int,
  val visible: Boolean = true,
  /** 設定のどれかの画面へのショートカット（好きなものを足せる）。null = 通常のタブ。 */
  val route: SettingsRoute? = null,
  /** 画面の“中”の機能へのショートカット（例：生成の動画、キャラ図鑑）。ShellDeepLink の id。 */
  val deep: String? = null,
) {
  private val deepDef get() = ShellDeepLink.find(deep)
  val label: String get() = deepDef?.label ?: route?.title?.let { nativeLabel(it) } ?: id.label
  val shortLabel: String get() = deepDef?.label?.take(5) ?: route?.title?.let { nativeLabel(it).take(5) } ?: id.shortLabel
  val icon: ImageVector get() = deepDef?.icon ?: route?.icon ?: id.icon
}

private fun nativeLabel(text: ai.openclaw.app.i18n.NativeText): String = text.resolveNativeText()

/** 設定の「タブの表示・並べ替え」から、タブのカスタマイズ画面を開くための合図。 */
internal object TabCustomizeBus {
  var requested by mutableStateOf(false)
}

private const val TAB_PREFS_NAME = "shell_tab_customization"
private const val TAB_KEY = "tabs_v2"

internal fun loadShellTabs(context: Context): List<ShellTabConfig> {
  return try {
    val prefs = context.getSharedPreferences(TAB_PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(TAB_KEY, null) ?: return defaultTabs()
    val arr = JSONArray(json)
    val saved = (0 until arr.length()).map { i ->
      val o = arr.getJSONObject(i)
      ShellTabConfig(
        id      = ShellDestination.valueOf(o.getString("id")),
        order   = o.getInt("order"),
        visible = o.getBoolean("visible"),
        route   = o.optString("route").takeIf { it.isNotBlank() }?.let { name -> SettingsRoute.entries.firstOrNull { it.name == name } },
        deep    = o.optString("deep").takeIf { it.isNotBlank() },
      )
    }.sortedBy { it.order }
    // 新しいタブが追加された場合は末尾に追加
    val savedIds = saved.filter { it.route == null }.map { it.id }.toSet()
    val missing = ShellDestination.entries.filter { it !in savedIds }
      .mapIndexed { i, d -> ShellTabConfig(d, saved.size + i, true) }
    saved + missing
  } catch (_: Exception) { defaultTabs() }
}

internal fun saveShellTabs(context: Context, tabs: List<ShellTabConfig>) {
  val arr = JSONArray()
  tabs.forEachIndexed { i, t ->
    arr.put(JSONObject().also { o ->
      o.put("id", t.id.name); o.put("order", i); o.put("visible", t.visible); t.route?.let { r -> o.put("route", r.name) }; t.deep?.let { d -> o.put("deep", d) }
    })
  }
  context.getSharedPreferences(TAB_PREFS_NAME, Context.MODE_PRIVATE)
    .edit().putString(TAB_KEY, arr.toString()).apply()
}

/** 初めの並び。チャットはホームからも開けるので、下のバーには出さない（タブのカスタマイズで戻せる）。 */
private fun defaultTabs(): List<ShellTabConfig> {
  val order =
    listOf(ShellDestination.Home, ShellDestination.SampleFactory, ShellDestination.Sessions, ShellDestination.Settings, ShellDestination.Chat)
  return order.mapIndexed { i, d -> ShellTabConfig(d, i, d != ShellDestination.Chat) }
}

// ─────────────────────────────────────────────────────────────
// タブカスタマイズダイアログ
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShellTabCustomizeDialog(
  tabs: List<ShellTabConfig>,
  onSave: (List<ShellTabConfig>) -> Unit,
  onDismiss: () -> Unit,
) {
  var items by remember { mutableStateOf(tabs.toMutableList()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("📱 タブをカスタマイズ", color = ClawTheme.colors.text, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("✅=表示 ⬜=非表示 ↑↓=並び替え（ホームは固定）。下の「追加」で、好きな画面を足せます。",
          style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        Spacer(Modifier.height(6.dp))
        items.forEachIndexed { idx, cfg ->
          val isFixed = cfg.id == ShellDestination.Home && cfg.route == null // ホームは常に表示・固定
          Row(
            modifier = Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(if (cfg.visible) ClawTheme.colors.surface else ClawTheme.colors.surfacePressed)
              .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (cfg.visible || isFixed) "✅" else "⬜",
              fontSize = 16.sp,
              modifier = if (!isFixed) Modifier.combinedClickable(onClick = {
                items = items.toMutableList().also { it[idx] = cfg.copy(visible = !cfg.visible) }
              }) else Modifier,
            )
            Spacer(Modifier.width(6.dp))
            Icon(cfg.icon, null, tint = if (cfg.visible || isFixed) ClawTheme.colors.primary else ClawTheme.colors.textSubtle,
              modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              cfg.label,
              style = ClawTheme.type.body.copy(fontSize = 13.sp),
              color = if (cfg.visible || isFixed) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
              modifier = Modifier.weight(1f),
            )
            if (isFixed) {
              Text("固定", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            } else {
              if (cfg.route != null) {
                IconButton(onClick = { items = items.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.size(28.dp)) {
                  Icon(Icons.Default.Close, "外す", tint = ClawTheme.colors.textMuted, modifier = Modifier.size(16.dp))
                }
              }
              IconButton(onClick = {
                if (idx > 0) items = items.toMutableList().also { lst ->
                  val t = lst[idx-1]; lst[idx-1] = lst[idx]; lst[idx] = t
                }
              }, enabled = idx > 0, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "上へ",
                  tint = if (idx > 0) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
                  modifier = Modifier.size(16.dp))
              }
              IconButton(onClick = {
                if (idx < items.size-1) items = items.toMutableList().also { lst ->
                  val t = lst[idx+1]; lst[idx+1] = lst[idx]; lst[idx] = t
                }
              }, enabled = idx < items.size-1, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "下へ",
                  tint = if (idx < items.size-1) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
                  modifier = Modifier.size(16.dp))
              }
            }
          }
        }
        Spacer(Modifier.height(8.dp))
        Text("➕ 追加できる画面", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        val have = items.filter { it.deep == null }.mapNotNull { it.route }.toSet()
        SettingsRoute.entries.filter { it.category != null && it !in have }.forEach { r ->
          Row(
            modifier = Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .clickable { items = (items + ShellTabConfig(ShellDestination.Settings, items.size, true, r)).toMutableList() }
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(r.icon, null, tint = ClawTheme.colors.textMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(nativeLabel(r.title), style = ClawTheme.type.body.copy(fontSize = 13.sp), color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
            Text("＋", color = ClawTheme.colors.primary)
          }
        }
        // 画面の“中”にある機能も、ナビに直接足せる
        Spacer(Modifier.height(8.dp))
        Text("➕ 画面の中の機能（ここから直接開く）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        val haveDeep = items.mapNotNull { it.deep }.toSet()
        ShellDeepLink.ALL.filter { it.id !in haveDeep }.forEach { d ->
          Row(
            modifier = Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .clickable { items = (items + ShellTabConfig(ShellDestination.Settings, items.size, true, d.route, d.id)).toMutableList() }
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(d.icon, null, tint = ClawTheme.colors.textMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(d.label, style = ClawTheme.type.body.copy(fontSize = 13.sp), color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
            Text("＋", color = ClawTheme.colors.primary)
          }
        }
      }
    },
    confirmButton = { ClawPrimaryButton(text = "保存", onClick = { onSave(items.toList()) }) },
    dismissButton = { ClawSecondaryButton(text = "やめる", onClick = onDismiss) },
    containerColor = ClawTheme.colors.canvas,
  )
}

// ─────────────────────────────────────────────────────────────
// ナビゲーションスキャフォールド
// ─────────────────────────────────────────────────────────────

/** Compact / Medium のブレークポイント (Material3 ガイドライン準拠) */
private val MEDIUM_WIDTH_BREAKPOINT = 600.dp

/**
 * 画面幅に基づいて最適なナビゲーション形式を自動選択するシェル。
 * タブの順序・表示は [loadShellTabs] / [saveShellTabs] で永続化。
 *
 * - Compact (< 600dp): BottomNavigationBar
 * - Medium/Expanded (≥ 600dp): NavigationRail
 * - BookPane検出時: NavigationSuiteType.None (SidebarNavigationShell に委譲)
 */
@Composable
internal fun AdaptiveShellScaffold(
  nav: ShellNavigation,
  hasBookPanes: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable (isCompact: Boolean) -> Unit,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var tabConfigs by remember { mutableStateOf(loadShellTabs(context)) }
  var showTabCustomize by remember { mutableStateOf(false) }

  val visibleTabs = tabConfigs.filter { it.visible }
  androidx.compose.runtime.LaunchedEffect(TabCustomizeBus.requested) {
    if (TabCustomizeBus.requested) {
      showTabCustomize = true
      TabCustomizeBus.requested = false
    }
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isCompact = maxWidth < MEDIUM_WIDTH_BREAKPOINT

    val layoutType = when {
      hasBookPanes -> NavigationSuiteType.None
      !isCompact   -> NavigationSuiteType.NavigationRail
      else         -> NavigationSuiteType.NavigationBar
    }

    NavigationSuiteScaffold(
      navigationSuiteItems = {
        val shortcutRoutes = visibleTabs.filter { it.deep == null }.mapNotNull { it.route }.toSet()
        visibleTabs.forEach { cfg ->
          val destination = cfg.id
          val open: () -> Unit = {
            val route = cfg.route
            if (cfg.deep != null) {
              ShellDeepLink.last = cfg.deep
              ShellDeepLink.pending = cfg.deep
            }
            if (route != null) nav.openSettingsRoute(route) else nav.selectTab(destination.tab)
          }
          item(
            icon = {
              Icon(
                imageVector = cfg.icon,
                contentDescription = cfg.label,
              )
            },
            label = {
              Text(
                text = if (!isCompact) cfg.shortLabel else cfg.label,
                style = ClawTheme.type.caption,
                maxLines = 1,
              )
            },
            selected = if (cfg.route != null) {
              nav.activeTab == Tab.Settings && nav.settingsRoute == cfg.route && (cfg.deep == null || ShellDeepLink.last == cfg.deep)
            } else {
              (nav.activeTab == destination.tab && !(destination == ShellDestination.Settings && nav.settingsRoute in shortcutRoutes)) ||
                (destination == ShellDestination.Settings && nav.activeTab == Tab.ProvidersModels) ||
                (destination == ShellDestination.Settings && nav.activeTab == Tab.Files)
            },
            onClick = open,
            // 長押しでタブカスタマイズダイアログを表示
            modifier = Modifier.combinedClickable(
              onClick = open,
              onLongClick = { showTabCustomize = true },
            ),
          )
        }
        // いつでも押せる「＋」：左のナビに、好きな画面を足す・隠す・並べ替える
        if (!isCompact) {
          item(
            icon = { Icon(imageVector = androidx.compose.material.icons.Icons.Default.Add, contentDescription = "ナビを編集") },
            label = { Text(text = "追加", style = ClawTheme.type.caption, maxLines = 1) },
            selected = false,
            onClick = { showTabCustomize = true },
          )
        }
      },
      layoutType = layoutType,
      navigationSuiteColors = NavigationSuiteDefaults.colors(
        navigationBarContainerColor = ClawTheme.colors.surface,
        navigationBarContentColor = ClawTheme.colors.text,
        navigationRailContainerColor = ClawTheme.colors.surface,
        navigationRailContentColor = ClawTheme.colors.text,
      ),
      // Clip so a closed modal drawer, parked left of the content, cannot paint beside the rail.
      content = { Box(Modifier.fillMaxSize().clipToBounds()) { content(isCompact) } },
    )
  }

  if (showTabCustomize) {
    ShellTabCustomizeDialog(
      tabs = tabConfigs,
      onSave = { newTabs ->
        saveShellTabs(context, newTabs)
        tabConfigs = newTabs
        showTabCustomize = false
      },
      onDismiss = { showTabCustomize = false },
    )
  }
}
