package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import android.content.Intent
import android.net.Uri
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class PersonImageRow(
  val id: String,
  val source: String,
  val title: String,
  val license: String,
  val creator: String,
  val thumbUrl: String,
  val imageUrl: String,
  val pageUrl: String,
)

/** 検索結果のページ名から、人物やキャラの名前らしい部分を取る（「初音ミク | サイト名」→「初音ミク」）。足す前に、画面で直せる。 */
internal fun nameFromTitle(title: String): String {
  val head = title.split(Regex("""\s*[|｜]\s*|\s+[-–—]\s+|\s*[：:]\s*""")).firstOrNull().orEmpty()
  return head.replace(Regex("""[【】\[\]「」『』]"""), "").replace(Regex("""(とは|の(プロフィール|画像|情報|キャラクター情報|wiki|Wiki))$"""), "").trim().take(60)
}

/**
 * 1人のページから、その人の画像を探して、写真にする。探す先は、Wikimedia Commons と Openverse（自由に使える画像の公式の窓口）。
 * 下へスクロールすると、続きが出る（終わりなし）。何枚でも選んで、まとめて保存できる。
 * 名前が同じ別人の画像が出ることがあるので、見て、選ぶ。選んだ画像の出どころとライセンスは、PC に控えを残す。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdolImageSearchDialog(
  viewModel: MainViewModel,
  group: String,
  member: IdolMemberRow,
  onDismiss: () -> Unit,
  onSaved: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val listState = rememberLazyListState()
  var query by remember { mutableStateOf(member.name) }
  var rows by remember { mutableStateOf<List<PersonImageRow>>(emptyList()) }
  var page by remember { mutableStateOf(0) }
  var hasMore by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }
  var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
  var confirmMain by remember { mutableStateOf<Boolean?>(null) }
  var webOpen by remember { mutableStateOf(false) }
  // true＝「Google Cloudで検索！」：Google（Custom Search API）の結果だけを出す
  var googleOnly by remember { mutableStateOf(false) }
  // 保存している間だけ、閉じられない。探している間は、いつでも、戻る・閉じるが使える
  var saving by remember { mutableStateOf(false) }
  // 検索の世代：検索し直したら、前の検索の続きを読まない
  var generation by remember { mutableStateOf(0) }

  fun loadPage(next: Int, fresh: Boolean) {
    if (busy) return
    saving = false
    busy = true
    if (fresh) message = ""
    val gen = generation
    scope.launch {
      runCatching {
        val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.IdolMasterImageSearch, JSONObject().put("group", group).put("id", member.id).put("query", query.trim()).put("page", next).toString(), timeoutMs = 120_000))
        val arr = res.optJSONArray("images")
        val more =
          (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.filter { !googleOnly || it.optString("source") == "google" }.map {
            PersonImageRow(it.optString("id"), it.optString("source"), it.optString("title"), it.optString("license"), it.optString("creator"), it.optString("thumbUrl"), it.optString("imageUrl"), it.optString("pageUrl"))
          }
        if (gen != generation) return@runCatching
        val known = (if (fresh) emptyList() else rows).map { it.id }.toSet()
        val added = more.filter { it.id !in known }
        rows = (if (fresh) emptyList() else rows) + added
        page = next
        hasMore = more.isNotEmpty()
        if (rows.isEmpty()) message = if (googleOnly) "Google の結果がありません（名前を変えてみてください。出ないときは、Google 側の設定が未完了の可能性があります：Custom Search API が有効か、~/.openclaw/.env の GOOGLE_CSE_KEY / GOOGLE_CSE_ID）" else "画像が見つかりませんでした（名前を変えて、探してみてください。英語の名前も試せます）"
      }.onFailure { message = "探せませんでした：${it.message}" }
      busy = false
    }
  }

  fun search() {
    generation += 1
    rows = emptyList()
    picked = emptySet()
    hasMore = false
    busy = false
    loadPage(1, fresh = true)
  }

  // 一番下の近くまで来たら、続きを読む
  LaunchedEffect(listState, rows.size, hasMore) {
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
      .distinctUntilChanged()
      .collect { last ->
        val total = listState.layoutInfo.totalItemsCount
        if (hasMore && !busy && rows.isNotEmpty() && last >= total - 3) loadPage(page + 1, fresh = false)
      }
  }

  fun save(firstIsMain: Boolean) {
    val chosen = rows.filter { it.id in picked }
    if (chosen.isEmpty() || busy) return
    saving = true
    busy = true
    message = ""
    scope.launch {
      runCatching {
        val images = JSONArray()
        chosen.forEach { images.put(JSONObject().put("url", it.imageUrl).put("title", it.title).put("license", it.license).put("creator", it.creator).put("source", it.source).put("pageUrl", it.pageUrl)) }
        JSONObject(viewModel.runGatewayRpc(GatewayMethod.IdolMasterImageSave, JSONObject().put("group", group).put("id", member.id).put("images", images).put("firstIsMain", firstIsMain).toString(), timeoutMs = 300_000))
      }
        .onSuccess { res ->
          val failed = res.optInt("failed")
          onSaved("「${member.name}」に画像を${res.optInt("saved")}枚入れました" + if (failed > 0) "（入れられなかった画像：${failed}枚）" else "")
        }
        .onFailure { message = "保存できませんでした：${it.message}" }
      busy = false
    }
  }

  if (webOpen) {
    IdolWebSearchDialog(viewModel = viewModel, group = group, member = member, query = query, onDismiss = { webOpen = false }, onSaved = { text -> webOpen = false; onSaved(text) })
  }

  val mode = confirmMain
  if (mode != null) {
    val chosen = rows.filter { it.id in picked }
    FanBookConfirmDialog(
      title = "${chosen.size}枚を保存しますか？",
      text =
        (if (mode) "1枚目は、メインの写真になります（いまのメインの写真は、置き換わります）。残りは、追加の写真です。" else "すべて、追加の写真として入れます（メインの写真は、そのままです）。") +
          "\n\nそれぞれの出どころとライセンスは、PC に控えを残します。個人で使う範囲にとどめてください。",
      confirmText = "保存する",
      onDismiss = { confirmMain = null },
      onConfirm = { confirmMain = null; save(mode) },
    )
  }

  AlertDialog(
    onDismissRequest = { if (!(busy && saving)) onDismiss() },
    title = { Text("「${member.name}」の画像を探す") },
    text = {
      LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wikimedia Commons・Openverse（自由に使える画像）と、TMDB（映画・ドラマの人物の写真）から探します。TMDB の写真には、出典の表示が必要です（This product uses the TMDB API but is not endorsed or certified by TMDB.）。下へスクロールすると、続きが出ます。何枚でも選んで、まとめて保存できます。名前が同じ別の人の画像が出ることがあります。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            ClawTextField(value = query, onValueChange = { query = it }, placeholder = "名前", label = "探す名前", modifier = Modifier.fillMaxWidth())
            ClawPrimaryButton(text = if (busy && rows.isEmpty() && !googleOnly) "探しています…" else "🔎 画像を探す", onClick = { if (query.isNotBlank()) { googleOnly = false; search() } }, modifier = Modifier.fillMaxWidth())
            // Google Cloud（Custom Search API）の結果だけを出す
            ClawPrimaryButton(text = if (busy && rows.isEmpty() && googleOnly) "Google で探しています…" else "☁️ Google Cloudで検索！", onClick = { if (query.isNotBlank()) { googleOnly = true; search() } }, modifier = Modifier.fillMaxWidth())
            // Google の画像検索を、このアプリの中で開く（長押しで、この人の写真にする）
            ClawPrimaryButton(text = "🌐 Google で画像検索（アプリの中で開く）", onClick = { if (query.isNotBlank()) webOpen = true }, modifier = Modifier.fillMaxWidth())
            // ブラウザで開く（気に入った画像は、ブラウザの共有から、このアプリへ送れる）
            ClawSecondaryButton(
              text = "ブラウザで開く（共有で送る）",
              onClick = {
                if (query.isNotBlank()) {
                  // ブラウザから「共有 → このアプリ」で戻ってきた画像は、チャットではなく、この人の写真にする（写真があれば、追加の写真）
                  FanBookShareInbox.arm(FanBookShareInbox.Target(group, member.id, member.name, member.hasPhoto))
                  val url = "https://www.google.com/search?tbm=isch&q=" + Uri.encode(query.trim())
                  runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { message = "ブラウザを開けませんでした：${it.message}" }
                }
              },
              modifier = Modifier.fillMaxWidth(),
            )
            Text("ブラウザで気に入った画像を長押しして、共有 → このアプリ を選ぶと、この人の写真に入ります（チャットには入りません。写真がある人は、追加の写真になります）。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            if (picked.isNotEmpty()) {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ClawPrimaryButton(text = "選んだ${picked.size}枚を保存（1枚目をメインに）", onClick = { if (!busy) confirmMain = true })
                ClawSecondaryButton(text = "すべて追加の写真に", onClick = { if (!busy) confirmMain = false })
                ClawSecondaryButton(text = "選びなおす", onClick = { picked = emptySet() })
              }
            }
          }
        }
        items(rows, key = { it.id }) { row ->
          val on = row.id in picked
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (on) ClawTheme.colors.primary.copy(alpha = 0.18f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.5f))
                .clickable { picked = if (on) picked - row.id else picked + row.id }
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CatalogThumb(viewModel, row.thumbUrl, size = 96.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text((if (on) "● " else "○ ") + row.title, style = ClawTheme.type.caption, color = ClawTheme.colors.text, maxLines = 2)
              Text("${row.license}・${when (row.source) { "commons" -> "Commons"; "tmdb" -> "TMDB"; "google" -> "Google"; else -> "Openverse" }}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            }
          }
        }
        item {
          Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (busy && rows.isNotEmpty()) Text("続きを読んでいます…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            if (!hasMore && rows.isNotEmpty() && !busy) Text("これで全部です（${rows.size}枚）。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
          }
        }
      }
    },
    confirmButton = { TextButton(enabled = !(busy && saving), onClick = onDismiss) { Text("閉じる") } },
  )
}
