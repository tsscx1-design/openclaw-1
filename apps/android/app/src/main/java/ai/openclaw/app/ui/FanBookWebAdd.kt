package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

// グループの画面・分類の画面から直接開く、検索（Google Cloud の Vertex AI Search）。
// 結果のページの名前を選んで、グループの「メンバー」か、分類の「グループ」として足す。
// 取れるのは、ページの名前（タイトル）とサイト名だけ。誕生日・出身地などのプロフィールは、取れない
// （足したあとに、グループの「📥 補う」や、1人のページの「🌐 ネットで調べる」で補う）。

/** 足し先。group があれば、そのグループの「メンバー」として。なければ、category の中の「グループ」として。 */
internal data class WebAddTarget(val category: String, val group: String?)

/**
 * 「検索して足す」の入口。どのグループ・どの分類からでも、同じ入口。
 *  ・まとめて検索：データベース（アニメの作品名・キャラの名前、人物、グループ）と、Google Cloud（登録したサイト）を、
 *    同じ名前で、同時に探して、1つの画面に並べる。どちらからでも足せる。
 *  ・Google でサイト検索（アプリの中で開く）：Google の画面で、データベースやホームページを開いて、そのページから足す。
 */
@Composable
internal fun FanBookSearchHubDialog(
  target: WebAddTarget,
  onDatabase: () -> Unit,
  onSite: () -> Unit,
  onDismiss: () -> Unit,
) {
  val where = if (target.group != null) "「${target.group}」" else "「${target.category}」"
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("$where に、検索して足す") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ClawPrimaryButton(text = "🔎 まとめて検索（データベース＋Google）", onClick = onDatabase, modifier = Modifier.fillMaxWidth())
        Text("アニメは、作品名・キャラの名前から。人物やグループは、Wikidata・TMDB などから。同じ名前で、Google Cloud の登録サイトも、同時に探して、1つの画面に並べます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        ClawSecondaryButton(text = "🌐 Googleでサイト検索（アプリの中で開く）", onClick = onSite, modifier = Modifier.fillMaxWidth())
        Text("Google の画面で、データベースやホームページを開いて、そのページから足します。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
  )
}
