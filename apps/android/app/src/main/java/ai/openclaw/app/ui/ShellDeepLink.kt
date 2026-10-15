package ai.openclaw.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/** 設定の画面の“中”にある機能（生成の各モード、キャラ図鑑など）を、ナビゲーションに直接足すための定義。 */
internal data class DeepLinkDef(val id: String, val label: String, val route: SettingsRoute, val icon: ImageVector)

internal object ShellDeepLink {
  /** 開いた先の画面が受け取る合図（受け取ったら null に戻る）。 */
  var pending by mutableStateOf<String?>(null)

  /** いちばん最近に開いた機能（ナビの選択表示用）。 */
  var last by mutableStateOf<String?>(null)

  val ALL: List<DeepLinkDef> =
    listOf(
      DeepLinkDef("media:image", "生成：画像", SettingsRoute.MediaStudio, Icons.Default.Palette),
      DeepLinkDef("media:video", "生成：動画", SettingsRoute.MediaStudio, Icons.Default.VideoLibrary),
      DeepLinkDef("media:manga", "生成：漫画", SettingsRoute.MediaStudio, Icons.AutoMirrored.Filled.MenuBook),
      DeepLinkDef("media:motion", "モーションコミック", SettingsRoute.MediaStudio, Icons.Default.Movie),
      DeepLinkDef("media:music", "生成：音楽", SettingsRoute.MediaStudio, Icons.Default.MusicNote),
      DeepLinkDef("media:voice", "生成：声", SettingsRoute.MediaStudio, Icons.Default.Mic),
      DeepLinkDef("media:modelSamples", "モデル別サンプル比較", SettingsRoute.MediaStudio, Icons.Default.AutoAwesome),
      DeepLinkDef("media:poseSamples", "体位サンプル", SettingsRoute.MediaStudio, Icons.Default.PhotoLibrary),
      DeepLinkDef("comic:works", "連載コミック（作品）", SettingsRoute.ComicStudio, Icons.AutoMirrored.Filled.MenuBook),
      DeepLinkDef("comic:library", "キャラ図鑑", SettingsRoute.ComicStudio, Icons.Default.Face),
    )

  fun find(id: String?): DeepLinkDef? = ALL.firstOrNull { it.id == id }
}
