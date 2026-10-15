package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.json.JSONObject

// Google のサイト検索を、アプリの中で開く（別のアプリに移らない）。データベースやホームページを開いて、
// そのページから、ファンブックに足す：
//   ・「このページの名前を、足す」＝ページの名前から、メンバー（グループを選んだとき）か、グループ（選ばないとき）として。
//   ・「このページから、メンバー一覧を取り込む」＝今ある「🔗 URL」取込の待ち行列に入れる（グループを選んだとき）。
// Google の画面を、そのまま表示しているだけです（ページの中身を、自動で読み取ることは、しません）。
// 取り込みは、いつもの取込と同じで、robots.txt を守り、ボット対策は避けません。

/** 開いたときの足し先。null は、画面の上で選ぶ。 */
internal data class SiteSearchStart(val category: String?, val group: String?)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FanBookSiteSearchDialog(
  viewModel: MainViewModel,
  categories: List<IdolCategoryRow>,
  start: SiteSearchStart,
  onDismiss: () -> Unit,
  onChanged: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  var category by remember { mutableStateOf(start.category ?: categories.firstOrNull()?.name.orEmpty()) }
  // null＝メンバーではなく、グループとして足す
  var group by remember { mutableStateOf(start.group) }
  var query by remember { mutableStateOf("") }
  var web by remember { mutableStateOf<WebView?>(null) }
  var pageUrl by remember { mutableStateOf("") }
  var pageTitle by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }
  var changed by remember { mutableStateOf(0) }
  var nameDraft by remember { mutableStateOf<String?>(null) }

  val groupsOfCategory = categories.firstOrNull { it.name == category }?.groups?.map { it.group }.orEmpty()
  val onGoogleItself = pageUrl.isBlank() || Uri.parse(pageUrl).host.orEmpty().contains("google")
  val where = if (group != null) "「$group」のメンバー" else "「$category」のグループ"

  fun search() {
    if (query.isBlank()) return
    web?.loadUrl("https://www.google.com/search?hl=ja&q=" + Uri.encode(query.trim()))
  }

  fun addName(name: String) {
    val one = name.trim()
    if (one.isBlank() || busy) return
    busy = true
    scope.launch {
      runCatching {
        val g = group
        if (g != null) viewModel.runGatewayRpc(GatewayMethod.IdolMasterMemberAdd, JSONObject().put("group", g).put("name", one).toString(), timeoutMs = 30_000)
        else viewModel.runGatewayRpc(GatewayMethod.IdolMasterGroupAdd, JSONObject().put("category", category).put("group", one).toString(), timeoutMs = 30_000)
      }
        .onSuccess { changed += 1; message = "$where に「$one」を足しました（${changed}件）。続けて探せます" }
        .onFailure { message = "足せませんでした：${it.message}（同じ名前がすでにあるときも、足せません）" }
      nameDraft = null
      busy = false
    }
  }

  fun importPage() {
    val g = group ?: return
    if (busy || onGoogleItself) return
    busy = true
    scope.launch {
      runCatching { viewModel.runGatewayRpc(GatewayMethod.IdolMasterSourceQueueAdd, JSONObject().put("group", g).put("url", pageUrl).put("savePhotos", true).put("addMissing", true).toString(), timeoutMs = 60_000) }
        .onSuccess { changed += 1; message = "「$g」の取り込みを、待機に入れました。進み具合は、ホームの「📥 取り込みの状況」で見られます" }
        .onFailure { message = "待機に入れられませんでした：${it.message}" }
      busy = false
    }
  }

  val draft = nameDraft
  if (draft != null) {
    AlertDialog(
      onDismissRequest = { if (!busy) nameDraft = null },
      title = { Text("$where に足す名前") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("ページの名前から取りました。直してから、足してください。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          ClawTextField(value = draft, onValueChange = { nameDraft = it }, placeholder = "名前", modifier = Modifier.fillMaxWidth())
        }
      },
      confirmButton = { TextButton(enabled = !busy, onClick = { addName(draft) }) { Text(if (busy) "足しています…" else "足す") } },
      dismissButton = { TextButton(enabled = !busy, onClick = { nameDraft = null }) { Text("やめる") } },
    )
  }

  Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
    Column(modifier = Modifier.fillMaxSize().background(ClawTheme.colors.surface)) {
      Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("🌐 Google でサイト検索 → ファンブックに足す", style = ClawTheme.type.caption, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
        TextButton(enabled = !busy, onClick = { if (changed > 0) onChanged("ファンブックに${changed}件、足しました") else onDismiss() }) { Text("閉じる") }
      }
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("足し先の分類：", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          categories.forEach { c -> FanBookSmallButton(text = if (c.name == category) "● ${c.name}" else c.name, onClick = { category = c.name; group = null }) }
        }
        Text("足し先のグループ（選ばないと、「$category」の中の、グループとして足します）：", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          FanBookSmallButton(text = if (group == null) "● （グループとして足す）" else "（グループとして足す）", onClick = { group = null })
          groupsOfCategory.forEach { g -> FanBookSmallButton(text = if (g == group) "● $g" else g, onClick = { group = g }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          ClawTextField(value = query, onValueChange = { query = it }, placeholder = "名前・作品名など", modifier = Modifier.weight(1f))
          ClawPrimaryButton(text = "検索", onClick = { search() })
        }
      }
      AndroidView(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp),
        factory = { ctx ->
          WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient =
              object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                  pageUrl = url.orEmpty()
                  pageTitle = view?.title.orEmpty()
                }
              }
            loadUrl("https://www.google.com/?hl=ja")
            web = this
          }
        },
      )
      Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (pageTitle.isBlank()) "開いているページ：なし" else "開いているページ：$pageTitle", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted, maxLines = 1)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          ClawSecondaryButton(text = "← 戻る", onClick = { web?.let { if (it.canGoBack()) it.goBack() } })
          ClawPrimaryButton(text = "＋ このページの名前を、足す", onClick = { if (!onGoogleItself && !busy) nameDraft = nameFromTitle(pageTitle) })
          if (group != null) ClawSecondaryButton(text = "📥 このページから、メンバー一覧を取り込む", onClick = { importPage() })
        }
        if (onGoogleItself) Text("検索の結果から、データベースやホームページを開いてください。そのページで、上のボタンが使えます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    }
  }
}
