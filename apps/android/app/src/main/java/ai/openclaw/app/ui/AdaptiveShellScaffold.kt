package ai.openclaw.app.ui

import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * アプリの主要ナビゲーション先。ボトムバーと NavigationRail の両方で使用。
 * Fold 端末のヒンジ対応（BookPaneBounds）は SidebarNavigationShell が処理するため、
 * このスキャフォールドは画面幅のみに基づく。
 */
@Immutable
internal enum class ShellDestination(
  val icon: ImageVector,
  val label: String,
  val tab: Tab,
) {
  Home(
    icon = Icons.Outlined.BarChart,
    label = "ホーム",
    tab = Tab.Overview,
  ),
  Chat(
    icon = Icons.Outlined.ChatBubbleOutline,
    label = "チャット",
    tab = Tab.Chat,
  ),
  Sessions(
    icon = Icons.Outlined.Description,
    label = "セッション",
    tab = Tab.Sessions,
  ),
  Settings(
    icon = Icons.Outlined.Settings,
    label = "設定",
    tab = Tab.Settings,
  ),
}

/** Compact / Medium のブレークポイント (Material3 ガイドライン準拠) */
private val MEDIUM_WIDTH_BREAKPOINT = 600.dp

/**
 * 画面幅に基づいて最適なナビゲーション形式を自動選択するシェル。
 *
 * - Compact (< 600dp): BottomNavigationBar → [content] に isCompact=true を渡す
 * - Medium/Expanded (≥ 600dp、BookPaneなし): NavigationRail
 * - BookPane検出時: NavigationSuiteType.None（SidebarNavigationShell に委譲）
 *
 * ClawTheme のカラーパレットに合わせたカスタムスタイルを適用。
 *
 * @param content レイアウト本体。[isCompact] が true のとき、スマホ用のボトムバーが
 *   表示されているため、各画面のハンバーガーボタン・サイドバードロワーは非表示にすること。
 */
@Composable
internal fun AdaptiveShellScaffold(
  nav: ShellNavigation,
  hasBookPanes: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable (isCompact: Boolean) -> Unit,
) {
  // BoxWithConstraints で画面幅を取得し、非推奨の currentWindowAdaptiveInfo() を回避
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isCompact = maxWidth < MEDIUM_WIDTH_BREAKPOINT

    val layoutType = when {
      // Fold のヒンジが検出された場合は既存の SidebarNavigationShell に委譲
      hasBookPanes -> NavigationSuiteType.None
      // 600dp 以上: NavigationRail（Drawer は SidebarNavigationShell が担当するため使わない）
      !isCompact -> NavigationSuiteType.NavigationRail
      // Compact: BottomNavigationBar
      else -> NavigationSuiteType.NavigationBar
    }

    NavigationSuiteScaffold(
      navigationSuiteItems = {
        ShellDestination.entries.forEach { destination ->
          item(
            icon = {
              Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
              )
            },
            label = {
              Text(
                text = destination.label,
                style = ClawTheme.type.caption,
              )
            },
            selected = nav.activeTab == destination.tab ||
              // Settings タブ内のサブルートも Settings として選択表示
              (destination == ShellDestination.Settings && nav.activeTab == Tab.ProvidersModels) ||
              (destination == ShellDestination.Settings && nav.activeTab == Tab.Files),
            onClick = { nav.selectTab(destination.tab) },
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
}
