package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.i18n.resolveNativeText
import ai.openclaw.app.ui.design.ClawPlainIconButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 「つくる」のタイルの開き先。生成の種類などは、画面の“中”まで直接開く（[ShellDeepLink] の id）。 */
private sealed interface CreateTarget {
  data class Deep(val id: String) : CreateTarget

  data class Route(val route: SettingsRoute) : CreateTarget
}

private data class CreateTile(
  val emoji: String,
  val label: String,
  val note: String,
  val target: CreateTarget,
)

private fun createSections(): List<Pair<String, List<CreateTile>>> =
  listOf(
    "作る" to
      listOf(
        CreateTile("🎨", "画像", "1枚の絵を描く", CreateTarget.Deep("media:image")),
        CreateTile("🎬", "動画", "絵や文章から動かす", CreateTarget.Deep("media:video")),
        CreateTile("📖", "漫画", "4コマから本格的な本まで", CreateTarget.Deep("media:manga")),
        CreateTile("🎞", "モーションコミック", "絵と声で物語にする", CreateTarget.Deep("media:motion")),
        CreateTile("🎤", "声", "セリフ・音声ドラマ", CreateTarget.Deep("media:voice")),
        CreateTile("🎵", "音楽", "歌あり・曲だけ", CreateTarget.Deep("media:music")),
      ),
    "キャラ・続きもの" to
      listOf(
        CreateTile("📚", "連載コミック", "同じキャラで続きを作る", CreateTarget.Deep("comic:works")),
        CreateTile("😶", nativeTitle(SettingsRoute.FaceSwap), SettingsRoute.FaceSwap.hint, CreateTarget.Route(SettingsRoute.FaceSwap)),
        CreateTile("🗂", nativeTitle(SettingsRoute.IdolMaster), SettingsRoute.IdolMaster.hint, CreateTarget.Route(SettingsRoute.IdolMaster)),
      ),
    "見本・素材" to
      listOf(
        CreateTile("🖼", "モデル比較", "同じ指示を全モデルで描く", CreateTarget.Deep("media:modelSamples")),
        CreateTile("🧪", "見本づくり", SettingsRoute.SampleFactory.hint, CreateTarget.Route(SettingsRoute.SampleFactory)),
        CreateTile("🕹", ShellDeepLink.find("media:poseSamples")?.label.orEmpty(), "見本の絵を見比べる", CreateTarget.Deep("media:poseSamples")),
        CreateTile("📦", nativeTitle(SettingsRoute.Shelf), SettingsRoute.Shelf.hint, CreateTarget.Route(SettingsRoute.Shelf)),
        CreateTile("🧩", nativeTitle(SettingsRoute.ItemCatalog), SettingsRoute.ItemCatalog.hint, CreateTarget.Route(SettingsRoute.ItemCatalog)),
        CreateTile("🎯", nativeTitle(SettingsRoute.MotionPoseCatalog), SettingsRoute.MotionPoseCatalog.hint, CreateTarget.Route(SettingsRoute.MotionPoseCatalog)),
        CreateTile("🖼", "Civitaiコンテンツ", SettingsRoute.CivitaiContent.hint, CreateTarget.Route(SettingsRoute.CivitaiContent)),
        CreateTile("📁", nativeTitle(SettingsRoute.LoraFileManager), SettingsRoute.LoraFileManager.hint, CreateTarget.Route(SettingsRoute.LoraFileManager)),
      ),
  )

private fun nativeTitle(route: SettingsRoute): String = route.title.resolveNativeText()

/** 下のタブ「つくる」：作るものを大きなタイルで選ぶ入口。押すと、その画面のその種類まで、すぐ開く。 */
@Composable
internal fun CreateHubScreen(
  viewModel: MainViewModel,
  showSidebarButton: Boolean,
  onOpenSidebar: () -> Unit,
  onOpenSettingsRoute: (SettingsRoute) -> Unit,
) {
  val open: (CreateTarget) -> Unit = { target ->
    when (target) {
      is CreateTarget.Deep -> openShellDeepLink(target.id, onOpenSettingsRoute)
      is CreateTarget.Route -> onOpenSettingsRoute(target.route)
    }
  }
  ClawScaffold(
    contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 4.dp),
    contentWindowInsets = WindowInsets.safeDrawing,
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      // スマホは2列、広げた画面は3〜4列
      val columns =
        when {
          maxWidth >= 720.dp -> 4
          maxWidth >= 480.dp -> 3
          else -> 2
        }
      LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (showSidebarButton) {
              ClawPlainIconButton(icon = Icons.Default.Menu, contentDescription = "メニューを開く", onClick = onOpenSidebar)
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(text = "🎨 つくる", style = ClawTheme.type.display.copy(fontSize = 24.sp, lineHeight = 28.sp), color = ClawTheme.colors.text)
              Text(text = "作りたいものを選ぶと、その画面がすぐ開きます", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            }
          }
        }
        item { GpuBusyBanner(viewModel) }
        createSections().forEach { (title, tiles) ->
          item(key = "create-$title") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(text = title, style = ClawTheme.type.section.copy(fontSize = 15.sp), color = ClawTheme.colors.text)
              tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  row.forEach { tile -> CreateTileCard(tile = tile, onClick = { open(tile.target) }, modifier = Modifier.weight(1f)) }
                  repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CreateTileCard(
  tile: CreateTile,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .heightIn(min = 72.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(ClawTheme.colors.surface)
        .border(1.dp, ClawTheme.colors.border, RoundedCornerShape(12.dp))
        .clickable(onClickLabel = tile.label, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(text = tile.emoji, fontSize = 26.sp)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = tile.label, style = ClawTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = ClawTheme.colors.text, maxLines = 2)
      if (tile.note.isNotBlank()) Text(text = tile.note, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted, maxLines = 3)
    }
  }
}
