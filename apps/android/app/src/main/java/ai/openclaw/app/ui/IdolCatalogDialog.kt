package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class CatalogTitle(val source: String, val id: Long, val title: String, val adult: Boolean)

private data class CatalogRow(val key: String, val id: Long, val label: String, val imageUrl: String = "", val source: String = "", val title: String = "")

/** 一覧の1行の写真（ゲートウェイが取ってきた小さな画像）。写真がない人は、空の枠。 */
@Composable
internal fun CatalogThumb(viewModel: MainViewModel, url: String, size: androidx.compose.ui.unit.Dp = 64.dp) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, url) {
    value =
      if (url.isBlank()) null
      else
        runCatching {
          FanBookImageCache.attach(context)
          // the same address is the same picture: it is kept on the device after the first time
          val saved = FanBookImageCache.read("thumb:$url")
          if (saved != null) {
            BitmapFactory.decodeByteArray(saved, 0, saved.size)?.asImageBitmap()
          } else {
            val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.IdolMasterCatalogThumb, JSONObject().put("url", url).toString(), timeoutMs = 30_000))
            if (!res.optBoolean("found")) null
            else {
              val bytes = Base64.decode(res.getString("base64"), Base64.DEFAULT)
              FanBookImageCache.write("thumb:$url", bytes)
              BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
          }
        }.getOrNull()
  }
  val shape = RoundedCornerShape(10.dp)
  val modifier = Modifier.size(size).clip(shape)
  val shown = bitmap
  if (shown != null) Image(bitmap = shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
  else Row(modifier = modifier.background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))) {}
}

/** 写真つきの1行。押すと、選んだ／はずした。 */
@Composable
private fun CatalogRowCard(viewModel: MainViewModel, row: CatalogRow, on: Boolean, onToggle: () -> Unit, onImage: (() -> Unit)? = null) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(if (on) ClawTheme.colors.primary.copy(alpha = 0.18f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.5f))
        .clickable(onClick = onToggle)
        .padding(8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // 写真を押すと、全画面で見られる（写真のない行は、押すと、選ぶ）
    androidx.compose.foundation.layout.Box(modifier = if (onImage != null && row.imageUrl.isNotBlank()) Modifier.clickable(onClick = onImage) else Modifier) {
      CatalogThumb(viewModel, row.imageUrl, size = 88.dp)
    }
    Text((if (on) "● " else "○ ") + row.label, style = ClawTheme.type.body, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
  }
}

/** 写真を、全画面で見る。どこかを押すと、閉じる。 */
@Composable
private fun CatalogImageViewer(viewModel: MainViewModel, row: CatalogRow, onDismiss: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, row.imageUrl) {
    value =
      runCatching {
        FanBookImageCache.attach(context)
        val saved = FanBookImageCache.read("thumb:${row.imageUrl}")
        if (saved != null) {
          BitmapFactory.decodeByteArray(saved, 0, saved.size)?.asImageBitmap()
        } else {
          val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.IdolMasterCatalogThumb, JSONObject().put("url", row.imageUrl).toString(), timeoutMs = 30_000))
          if (!res.optBoolean("found")) null
          else {
            val bytes = Base64.decode(res.getString("base64"), Base64.DEFAULT)
            FanBookImageCache.write("thumb:${row.imageUrl}", bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
          }
        }
      }.getOrNull()
  }
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    androidx.compose.foundation.layout.Box(
      modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).clickable(onClick = onDismiss),
      contentAlignment = Alignment.Center,
    ) {
      val shown = bitmap
      if (shown != null) Image(bitmap = shown, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
      else Text("読み込んでいます…", style = ClawTheme.type.caption, color = androidx.compose.ui.graphics.Color.White)
      Text(
        row.label,
        style = ClawTheme.type.caption,
        color = androidx.compose.ui.graphics.Color.White,
        modifier = Modifier.align(Alignment.BottomStart).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)).padding(12.dp),
      )
    }
  }
}

internal val ANIME_CATEGORIES = setOf("アニメ", "アダルトアニメ")

/**
 * データベースから探して、ファンブックに追加する。
 *  - アニメ／アダルトアニメ：作品（AniList・Jikan）を探す → その作品のキャラを選ぶ → 作品名のグループに入る。
 *  - それ以外：俳優・歌手・グラビアアイドルを Wikidata から探す（性別・年齢で絞る／ランダム）→ 選ぶ → 追加先のグループに入る。
 * 何も書き込まず一覧（写真つき）を出し、選んだ人だけを追加する。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdolCatalogDialog(
  viewModel: MainViewModel,
  category: String,
  defaultGroup: String,
  onDismiss: () -> Unit,
  onAdded: (String) -> Unit,
  terms: FanBookTerms = FanBookTerms("グループ", "メンバー"),
) {
  val scope = rememberCoroutineScope()
  val anime = category in ANIME_CATEGORIES
  val adultWork = category == "アダルトアニメ"

  var urlAddOpen by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }
  var picked by remember { mutableStateOf<Set<String>>(emptySet()) }

  // 作品
  var titles by remember { mutableStateOf<List<CatalogTitle>>(emptyList()) }
  var work by remember { mutableStateOf<CatalogTitle?>(null) }
  var byName by remember { mutableStateOf(false) }
  var found by remember { mutableStateOf<List<CatalogRow>>(emptyList()) }
  var characters by remember { mutableStateOf<List<CatalogRow>>(emptyList()) }

  // 人物
  var kind by remember { mutableStateOf(when (category) { "俳優" -> "actor"; "グラビアアイドル" -> "gravure"; "モデル" -> "model"; "AV女優" -> "av"; "地下アイドル", "アイドル" -> "idol"; "Vチューバー" -> "vtuber"; "YouTuber" -> "youtuber"; else -> "singer" }) }
  var gender by remember { mutableStateOf("any") }
  var src by remember { mutableStateOf("wikidata") }
  var ageMin by remember { mutableStateOf("") }
  var ageMax by remember { mutableStateOf("") }
  var group by remember { mutableStateOf(defaultGroup) }
  var people by remember { mutableStateOf<List<CatalogRow>>(emptyList()) }
  var offset by remember { mutableStateOf(0) }
  // 女性アイドルグループ（Wikipedia の一覧）を探して、メンバーごと追加する
  var groupMode by remember { mutableStateOf(category == "アイドル") }
  var groups by remember { mutableStateOf<List<CatalogRow>>(emptyList()) }
  var groupOffset by remember { mutableStateOf(0) }
  var groupGender by remember { mutableStateOf("any") }
  var includeAdult by remember { mutableStateOf(false) }
  var order by remember { mutableStateOf("popular") }

  // Google Cloud（登録したサイト）の結果。データベースを探すたびに、同じ名前で、同時に探して、同じ画面に並べる
  var web by remember { mutableStateOf<List<CatalogRow>>(emptyList()) }
  var webBusy by remember { mutableStateOf(false) }
  var webNote by remember { mutableStateOf("") }
  var webPicked by remember { mutableStateOf<Set<String>>(emptySet()) }
  var webGroup by remember { mutableStateOf(defaultGroup) }
  var webAsGroup by remember { mutableStateOf(false) }
  var webDraft by remember { mutableStateOf<List<String>?>(null) }
  var webPage by remember { mutableStateOf(1) }
  var webHasMore by remember { mutableStateOf(false) }
  // データ元：both＝データベース＋Google／db＝データベースのみ／google＝Google のみ
  var sourceMode by remember { mutableStateOf("both") }
  // 名簿に書き込んでいる間だけ、閉じられない。検索中は、いつでも、戻る・閉じるが使える
  var saving by remember { mutableStateOf(false) }
  // 全画面で見ている写真
  var viewer by remember { mutableStateOf<CatalogRow?>(null) }

  suspend fun rpc(method: GatewayMethod, params: JSONObject): JSONObject =
    JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = 300_000))

  fun loadWeb(page: Int, fresh: Boolean) {
    val q = query.trim()
    if (fresh) webPicked = emptySet()
    if (q.isBlank()) {
      web = emptyList()
      webNote = ""
      webHasMore = false
      return
    }
    webBusy = true
    webNote = ""
    scope.launch {
      runCatching {
        val res = rpc(GatewayMethod.IdolMasterWebSearch, JSONObject().put("query", q).put("page", page))
        val arr = res.optJSONArray("images")
        val rows =
          (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.map {
            CatalogRow("web:" + it.optString("id"), 0, it.optString("title") + it.optString("creator").takeIf { c -> c.isNotBlank() }?.let { c -> "（$c）" }.orEmpty(), it.optString("thumbUrl"), "web", it.optString("title"))
          }
        val known = (if (fresh) emptyList() else web).map { it.key }.toSet()
        web = (if (fresh) emptyList() else web) + rows.filter { it.key !in known }
        webPage = page
        webHasMore = rows.isNotEmpty()
        if (web.isEmpty()) webNote = "Google の結果は、ありませんでした"
      }.onFailure {
        if (fresh) web = emptyList()
        webNote = "Google は、探せませんでした：${it.message}"
      }
      webBusy = false
    }
  }

  fun searchWeb() = loadWeb(1, fresh = true)

  // Google の結果の名前を、メンバー（足し先のグループの中）か、グループ（この分類の中）として足す
  fun addWeb(names: List<String>) {
    val list = names.map { it.trim() }.filter { it.isNotBlank() }
    if (list.isEmpty() || busy) return
    saving = true
    busy = true
    scope.launch {
      var ok = 0
      var failed = 0
      val target = webGroup.trim()
      // 足し先のグループが、まだなければ作る（すでにあるときは、エラーになるだけで、何も変わらない）
      if (!webAsGroup && target.isNotBlank()) runCatching { rpc(GatewayMethod.IdolMasterGroupAdd, JSONObject().put("category", category).put("group", target)) }
      for (one in list) {
        runCatching {
          if (webAsGroup) rpc(GatewayMethod.IdolMasterGroupAdd, JSONObject().put("category", category).put("group", one))
          else rpc(GatewayMethod.IdolMasterMemberAdd, JSONObject().put("group", target).put("name", one))
        }.onSuccess { ok += 1 }.onFailure { failed += 1 }
      }
      busy = false
      webDraft = null
      webPicked = emptySet()
      onAdded((if (webAsGroup) "「$category」にグループを${ok}件" else "「$target」に${ok}人を") + "足しました" + if (failed > 0) "（足せなかったもの：${failed}件。同じ名前が、すでにあるときも、足せません）" else "")
    }
  }

  fun searchTitles() {
    if (sourceMode != "db") searchWeb()
    if (sourceMode == "google") return
    saving = false
    busy = true
    message = ""
    scope.launch {
      runCatching {
        val p = JSONObject().put("query", query.trim()).put("adult", if (adultWork) "adult" else "general").put("sort", "popular")
        val arr = rpc(GatewayMethod.ComicCharacterTitleSearch, p).optJSONArray("titles")
        titles =
          (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.map {
            val title = it.optString("title").ifBlank { it.optString("englishTitle") }
            CatalogTitle(it.optString("source"), it.optLong("id"), title, it.optBoolean("isAdult"))
          }
        if (titles.isEmpty()) message = "作品が見つかりませんでした"
      }.onFailure { message = "探せませんでした：${it.message}" }
      busy = false
    }
  }

  fun openWork(t: CatalogTitle) {
    work = t
    picked = emptySet()
    characters = emptyList()
    saving = false
    busy = true
    message = ""
    scope.launch {
      runCatching {
        val res = rpc(GatewayMethod.IdolMasterCatalogCharacters, JSONObject().put("source", t.source).put("id", t.id).put("adult", adultWork))
        val arr = res.optJSONArray("characters")
        val hidden = res.optInt("hiddenUnder18")
        val totalFound = res.optInt("totalFound")
        characters =
          (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.map {
            val age = it.optString("age")
            val gen = it.optString("gender")
            val label = listOf(it.optString("name"), gen.ifBlank { null }, age.takeIf { a -> a.isNotBlank() }?.let { a -> "${a}歳" }).filterNotNull().joinToString("・")
            CatalogRow(it.optLong("id").toString(), it.optLong("id"), label, it.optString("imageUrl"))
          }
        message =
          when {
            characters.isEmpty() && hidden > 0 -> "この作品のキャラ${totalFound}人は、すべて年齢が18歳未満と書かれているので、出していません（キャラがいないのではありません）"
            characters.isEmpty() -> "この作品のキャラが、見つかりませんでした（作品のキャラが、サイトに登録されていません）"
            hidden > 0 -> "ほかに${hidden}人は、年齢が18歳未満と書かれているので、出していません"
            else -> ""
          }
      }.onFailure { message = "キャラを読めませんでした：${it.message}" }
      busy = false
    }
  }

  fun searchByName() {
    if (sourceMode != "db") searchWeb()
    if (sourceMode == "google") return
    saving = false
    busy = true
    message = ""
    scope.launch {
      runCatching {
        val res = rpc(GatewayMethod.IdolMasterCatalogCharSearch, JSONObject().put("query", query.trim()).put("adult", adultWork))
        val arr = res.optJSONArray("characters")
        val hidden = res.optInt("hiddenUnder18")
        found =
          (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.map {
            val age = it.optString("age")
            val label = listOf(it.optString("name"), it.optString("work").ifBlank { null }, age.takeIf { a -> a.isNotBlank() }?.let { a -> "${a}歳" }).filterNotNull().joinToString("・")
            CatalogRow("${it.optString("source")}:${it.optLong("id")}", it.optLong("id"), label, it.optString("imageUrl"), it.optString("source"))
          }
        picked = emptySet()
        message =
          when {
            found.isEmpty() && hidden > 0 -> "見つかったキャラ${hidden}人は、すべて年齢が18歳未満と書かれているので、出していません（キャラがいないのではありません）"
            found.isEmpty() -> "キャラが見つかりませんでした（この名前の、キャラ自体が見つかりません）"
            hidden > 0 -> "ほかに${hidden}人は、年齢が18歳未満と書かれているので、出していません"
            else -> ""
          }
      }.onFailure { message = "探せませんでした：${it.message}" }
      busy = false
    }
  }

  fun searchPeople(random: Boolean, more: Boolean = false) {
    if (!more && !random && sourceMode != "db") searchWeb()
    if (sourceMode == "google") return
    saving = false
    busy = true
    message = ""
    val nextOffset = if (more) offset + 30 else 0
    scope.launch {
      runCatching {
        val p = JSONObject().put("kind", kind).put("gender", gender).put("random", random).put("offset", nextOffset).put("source", src).put("adult", includeAdult).put("order", order)
        if (query.isNotBlank()) p.put("query", query.trim())
        if (src != "wikipedia") {
          ageMin.toIntOrNull()?.let { p.put("ageMin", it) }
          ageMax.toIntOrNull()?.let { p.put("ageMax", it) }
        }
        val arr = rpc(GatewayMethod.IdolMasterCatalogPeople, p).optJSONArray("people")
        val rows =
          (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.map {
            val g = when (it.optString("gender")) { "female" -> "女性"; "male" -> "男性"; else -> "" }
            if (it.optString("source") in setOf("tmdb", "musicbrainz", "anilist")) {
              val age = if (it.has("age")) "${it.optInt("age")}歳" else null
              CatalogRow(it.optString("key"), 0, listOf(it.optString("name"), age, g.ifBlank { null }, it.optString("description").ifBlank { null }).filterNotNull().joinToString("・"), it.optString("imageUrl"), it.optString("source"), it.optString("title"))
            } else if (it.optString("source") == "wikipedia") CatalogRow(it.optString("key"), 0, it.optString("name"), it.optString("imageUrl"), "wikipedia", it.optString("title")) else CatalogRow(it.optString("qid"), 0, listOf(it.optString("name"), if (it.has("age")) "${it.optInt("age")}歳" else null, g.ifBlank { null }).filterNotNull().joinToString("・"), it.optString("imageUrl"))
          }
        offset = nextOffset
        people = if (more) people + rows else rows
        if (rows.isEmpty()) message = "条件に合う人が、見つかりませんでした"
      }.onFailure { message = "探せませんでした：${it.message}" }
      busy = false
    }
  }

  fun searchGroups(random: Boolean, more: Boolean = false) {
    if (!more && !random && sourceMode != "db") searchWeb()
    if (sourceMode == "google") return
    saving = false
    busy = true
    message = ""
    val next = if (more) groupOffset + 30 else 0
    scope.launch {
      runCatching {
        val p = JSONObject().put("random", random).put("offset", next).put("gender", groupGender)
        if (query.isNotBlank()) p.put("query", query.trim())
        val arr = rpc(GatewayMethod.IdolMasterCatalogGroups, p).optJSONArray("groups")
        val rows =
          (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.map {
            CatalogRow(it.optString("qid"), 0, it.optString("name") + it.optString("description").takeIf { d -> d.isNotBlank() }?.let { d -> "（$d）" }.orEmpty(), it.optString("imageUrl"), "group", it.optString("name"))
          }
        groupOffset = next
        groups = if (more) groups + rows.filter { r -> groups.none { g -> g.key == r.key } } else rows
        if (rows.isEmpty()) message = "グループが見つかりませんでした"
      }.onFailure { message = "探せませんでした：${it.message}" }
      busy = false
    }
  }

  fun add() {
    saving = true
    busy = true
    message = ""
    scope.launch {
      runCatching {
        if (!anime && groupMode) {
          // 選んだグループを、1つずつ、メンバーごと追加する（グループごとに、数十秒かかる）
          var added = 0
          var failed = ""
          val chosen = groups.filter { it.key in picked }
          for ((i, g) in chosen.withIndex()) {
            message = "「${g.title}」を追加しています…（${i + 1}/${chosen.size}）"
            runCatching { rpc(GatewayMethod.IdolMasterCatalogAddGroup, JSONObject().put("qid", g.key).put("name", g.title).put("gender", groupGender).put("category", if (category == "歌手") "アイドル" else category)) }
              .onSuccess { added += it.optInt("added") + it.optInt("updated") }
              .onFailure { failed += "「${g.title}」：${it.message}　" }
          }
          picked = emptySet()
          message = failed
          busy = false
          onAdded("グループ${chosen.size}件から、${added}人を追加しました" + if (failed.isNotBlank()) "（追加できなかったグループがあります）" else "")
          return@launch
        }
        val p = JSONObject().put("category", category)
        if (anime && byName) {
          p.put("kind", "found").put("adult", adultWork)
          p.put("found", org.json.JSONArray(found.filter { it.key in picked }.map { JSONObject().put("source", it.source).put("id", it.id) }))
        } else if (anime) {
          val w = work ?: return@launch
          p.put("kind", "characters").put("group", w.title).put("source", w.source).put("workId", w.id).put("adult", adultWork)
          p.put("ids", org.json.JSONArray(characters.filter { it.key in picked }.map { it.id }))
        } else {
          if (src == "tmdb" || src == "musicbrainz" || src == "anilist") {
            p.put("kind", "external").put("source", src).put("personKind", kind).put("group", group.trim())
            p.put("ids", org.json.JSONArray(people.filter { it.key in picked }.map { it.title }))
          } else if (src == "wikipedia") {
            p.put("kind", "wikipedia").put("personKind", kind).put("group", group.trim())
            p.put("items", org.json.JSONArray(people.filter { it.key in picked }.map { JSONObject().put("title", it.title).put("imageUrl", it.imageUrl) }))
          } else {
            p.put("kind", "people").put("personKind", kind).put("group", group.trim()).put("qids", org.json.JSONArray(people.filter { it.key in picked }.map { it.key }))
          }
        }
        val res = rpc(GatewayMethod.IdolMasterCatalogAdd, p)
        val text = "追加 ${res.optInt("added")}人、更新 ${res.optInt("updated")}人" + if (res.optInt("skipped") > 0) "（条件に合わず見送り ${res.optInt("skipped")}人）" else ""
        picked = emptySet()
        onAdded(text)
      }.onFailure { message = "追加できませんでした：${it.message}" }
      busy = false
    }
  }

  if (urlAddOpen) {
    IdolUrlAddDialog(
      viewModel = viewModel,
      category = category,
      defaultGroup = defaultGroup,
      onDismiss = { urlAddOpen = false },
      onQueued = { text -> urlAddOpen = false; onAdded(text) },
    )
  }

  val draft = webDraft
  if (draft != null) {
    AlertDialog(
      onDismissRequest = { if (!busy) webDraft = null },
      title = { Text(if (webAsGroup) "「$category」に足すグループ名" else "「${webGroup.trim()}」に足す名前") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("ページの名前から取りました。直してから足してください（空にした行は、足しません）。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          draft.forEachIndexed { i, one ->
            ClawTextField(value = one, onValueChange = { v -> webDraft = draft.toMutableList().also { it[i] = v } }, placeholder = "名前", modifier = Modifier.fillMaxWidth())
          }
        }
      },
      confirmButton = { TextButton(enabled = !busy, onClick = { addWeb(draft) }) { Text(if (busy) "足しています…" else "足す") } },
      dismissButton = { TextButton(enabled = !busy, onClick = { webDraft = null }) { Text("やめる") } },
    )
  }

  val rows = if (anime && byName) found else if (anime) characters else if (groupMode) groups else people
  val canAdd = picked.isNotEmpty() && !busy && (!anime || byName || work != null) && (anime || groupMode || group.isNotBlank())

  val viewing = viewer
  if (viewing != null) CatalogImageViewer(viewModel, viewing) { viewer = null }

  val locked = busy && saving
  Dialog(onDismissRequest = { if (!locked) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
    Column(modifier = Modifier.fillMaxSize().background(ClawTheme.colors.surface).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (anime) "「$category」を作品から探す（Google も同時に）" else "「$category」を探す（データベース＋Google）", style = ClawTheme.type.body, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
        TextButton(enabled = !locked, onClick = onDismiss) { Text("閉じる") }
      }
      Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // データ元
        Text("データ元：", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("both" to "データベース＋Google", "db" to "データベースのみ", "google" to "Google のみ").forEach { (k, label) ->
            ClawSecondaryButton(text = if (sourceMode == k) "● $label" else label, onClick = { sourceMode = k })
          }
        }
        ClawSecondaryButton(text = "🔗 URL から追加（ページの URL を入れるだけ）", onClick = { urlAddOpen = true }, modifier = Modifier.fillMaxWidth())
        if (anime) {
          Text(
            "作品名で探して（AniList・Jikan）、作品のキャラを選ぶと、作品名のグループに入ります。" + if (adultWork) "アダルトの作品です。年齢が18歳未満と書かれているキャラは、出しません（年齢が書かれていないキャラは出ます）。" else "",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textMuted,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ClawSecondaryButton(text = if (!byName) "● 作品から探す" else "作品から探す", onClick = { byName = false; picked = emptySet() })
            ClawSecondaryButton(text = if (byName) "● キャラの名前から探す" else "キャラの名前から探す", onClick = { byName = true; picked = emptySet() })
          }
          if (byName) {
            ClawTextField(value = query, onValueChange = { query = it }, placeholder = "キャラの名前（作品名を足すと絞れます）", label = "キャラの名前", modifier = Modifier.fillMaxWidth())
            ClawPrimaryButton(text = if (busy) "探しています…" else "🔎 キャラを探す", onClick = { if (!busy && query.isNotBlank()) searchByName() }, modifier = Modifier.fillMaxWidth())
          } else {
          ClawTextField(value = query, onValueChange = { query = it }, placeholder = "作品名（空なら、人気の作品）", label = "作品", modifier = Modifier.fillMaxWidth())
          ClawPrimaryButton(text = if (busy && work == null) "探しています…" else "🔎 作品を探す", onClick = { if (!busy) searchTitles() }, modifier = Modifier.fillMaxWidth())
          if (work == null) {
            titles.forEach { t -> ClawSecondaryButton(text = t.title + if (t.adult) "（18+）" else "", onClick = { if (!busy) openWork(t) }, modifier = Modifier.fillMaxWidth()) }
          } else {
            Text("作品：${work!!.title}", style = ClawTheme.type.body, color = ClawTheme.colors.text)
            ClawSecondaryButton(text = "← 作品を選び直す", onClick = { work = null; characters = emptyList(); picked = emptySet() })
          }
          }
        } else {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ClawSecondaryButton(text = if (!groupMode) "● 人を探す" else "人を探す", onClick = { groupMode = false; picked = emptySet() })
            ClawSecondaryButton(text = if (groupMode) "● グループを探す（まるごと追加）" else "グループを探す（まるごと追加）", onClick = { groupMode = true; picked = emptySet() })
          }
          if (groupMode) {
            Text(
              "グループ名で探します（名前を入れると、どんなグループでも探せます。空のときは、日本のアイドルグループの一覧）。選ぶと、Wikidata に登録されている現在のメンバーを、グループごと「${if (category == "歌手") "アイドル" else category}」に追加します。メンバーの登録が少ないグループもあります。",
              style = ClawTheme.type.captionSmall,
              color = ClawTheme.colors.textMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf("any" to "メンバー：全員", "female" to "女性のみ", "male" to "男性のみ").forEach { (k, label) ->
                ClawSecondaryButton(text = if (groupGender == k) "● $label" else label, onClick = { groupGender = k; groups = emptyList(); picked = emptySet() })
              }
            }
            ClawTextField(value = query, onValueChange = { query = it }, placeholder = "グループ名（例: =LOVE、嵐、Perfume）", label = "グループ名", modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              ClawPrimaryButton(text = if (busy && groups.isEmpty()) "探しています…" else "🔎 グループを探す", onClick = { if (!busy) searchGroups(random = false) })
              ClawSecondaryButton(text = "🎲 ランダムに見る", onClick = { if (!busy) searchGroups(random = true) })
            }
          } else {
          Text(
            if (src == "wikidata") "Wikidata から探します。年齢で絞ると、誕生日のわかる人だけになります。グラビアアイドルは、18歳未満と書かれた人は出ません。Vチューバーや地下アイドルは載っている人が少ないので、「Wikipedia」に切り替えると増えます。"
            else if (src == "wikipedia") "Wikipedia のカテゴリの一覧から探します。人数が多いです。性別は絞れますが、年齢は絞れません。写真は、ページに写真がある人だけに付きます。グループ（団体）が混ざることがあります。"
            else if (src == "tmdb") "TMDB（映画・ドラマの人物のデータベース）から探します。名前で探すか、空のとき人気順です。誕生日・出身地・代表作・写真が付きます。This product uses the TMDB API but is not endorsed or certified by TMDB."
            else if (src == "anilist") "AniList から、声優などのスタッフを探します。名前で探すか、空のとき人気順です。誕生日（年月日が分かる人だけ）・出身地・写真が付きます。"
            else "MusicBrainz（音楽のデータベース）から、歌手・ミュージシャンを探します。名前を入れて探します。誕生日・出身地が分かる人に付きます（写真はありません）。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textMuted,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("wikidata" to "データ元：Wikidata（年齢で絞れる）", "wikipedia" to "Wikipedia（人数が多い）", "tmdb" to "TMDB（俳優・写真つき）", "anilist" to "AniList（声優）", "musicbrainz" to "MusicBrainz（歌手）").forEach { (k, label) ->
              ClawSecondaryButton(text = if (src == k) "● $label" else label, onClick = { src = k; people = emptyList(); picked = emptySet() })
            }
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "全部", "singer" to "歌手", "idol" to "地下アイドル", "actor" to "俳優", "gravure" to "グラビア", "av" to "AV女優", "model" to "モデル", "vtuber" to "Vチューバー", "youtuber" to "YouTuber").forEach { (k, label) ->
              ClawSecondaryButton(text = if (kind == k) "● $label" else label, onClick = { kind = k })
            }
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("any" to "性別：指定なし", "female" to "女性", "male" to "男性").forEach { (k, label) ->
              ClawSecondaryButton(text = if (gender == k) "● $label" else label, onClick = { gender = k })
            }
          }
          if (src != "wikipedia") FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ClawTextField(value = ageMin, onValueChange = { ageMin = it.filter(Char::isDigit).take(3) }, placeholder = "18", label = "年齢 から", modifier = Modifier.fillMaxWidth(0.45f))
            ClawTextField(value = ageMax, onValueChange = { ageMax = it.filter(Char::isDigit).take(3) }, placeholder = "上限なし", label = "年齢 まで", modifier = Modifier.fillMaxWidth(0.45f))
          }
          if (src == "tmdb") {
            ClawSecondaryButton(
              text = if (includeAdult) "● アダルトの人も含める（誕生日が分かり、18歳以上の人だけ）" else "○ アダルトの人も含める（誕生日が分かり、18歳以上の人だけ）",
              onClick = { includeAdult = !includeAdult; people = emptyList(); picked = emptySet() },
              modifier = Modifier.fillMaxWidth(),
            )
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("popular" to "有名な順", "young" to "若い順（新人が多い）").forEach { (k, label) ->
              ClawSecondaryButton(text = if (order == k) "● $label" else label, onClick = { order = k; people = emptyList(); picked = emptySet() })
            }
          }
          ClawTextField(value = query, onValueChange = { query = it }, placeholder = "名前の一部（空なら、条件に合う人）", label = "名前", modifier = Modifier.fillMaxWidth())
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ClawPrimaryButton(text = if (busy && people.isEmpty()) "探しています…" else "🔎 探す（有名な順）", onClick = { if (!busy) searchPeople(random = false) })
            ClawSecondaryButton(text = "🎲 ランダムに見る", onClick = { if (!busy) searchPeople(random = true) })
          }
          ClawTextField(value = group, onValueChange = { group = it }, placeholder = "追加先のグループ", label = "追加先の${terms.group}", modifier = Modifier.fillMaxWidth())
          }
        }

        if (rows.isNotEmpty()) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ClawSecondaryButton(text = "全員を選ぶ", onClick = { picked = rows.map { it.key }.toSet() })
            ClawSecondaryButton(text = "選びなおす", onClick = { picked = emptySet() })
          }
          Text("${rows.size}${if (groupMode && !anime) "グループ" else "人"}（${picked.size}件を選択中）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
          rows.forEach { r ->
            val on = r.key in picked
            CatalogRowCard(viewModel, r, on, onToggle = { picked = if (on) picked - r.key else picked + r.key }, onImage = { viewer = r })
          }
          if (!anime) ClawSecondaryButton(text = "もっと見る", onClick = { if (!busy) { if (groupMode) searchGroups(random = false, more = true) else searchPeople(random = false, more = true) } }, modifier = Modifier.fillMaxWidth())
        }
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)

        // Google Cloud（登録したサイト）の結果：上の検索と、同時に探した分。ページの名前だけが取れる
        if (web.isNotEmpty() || webBusy || webNote.isNotBlank()) {
          Text("🌐 Google Cloud（登録したサイト）の結果", style = ClawTheme.type.body, color = ClawTheme.colors.text)
          if (webBusy) Text("Google を探しています…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          if (webNote.isNotBlank()) Text(webNote, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
          if (web.isNotEmpty()) {
            Text("${web.size}件（${webPicked.size}件を選択中）。取れるのは、ページの名前だけです。プロフィールは、足したあとに「📥 補う」で補えます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            web.forEach { r ->
              val on = r.key in webPicked
              CatalogRowCard(viewModel, r, on, onToggle = { webPicked = if (on) webPicked - r.key else webPicked + r.key }, onImage = { viewer = r })
            }
            if (webHasMore && !webBusy) ClawSecondaryButton(text = "もっと見る（Google）", onClick = { loadWeb(webPage + 1, fresh = false) }, modifier = Modifier.fillMaxWidth())
            if (!webHasMore && !webBusy) Text("Google の結果は、これで全部です（${web.size}件）。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              ClawSecondaryButton(text = if (!webAsGroup) "● メンバーとして足す" else "メンバーとして足す", onClick = { webAsGroup = false })
              ClawSecondaryButton(text = if (webAsGroup) "● グループとして足す" else "グループとして足す", onClick = { webAsGroup = true })
            }
            if (!webAsGroup) ClawTextField(value = webGroup, onValueChange = { webGroup = it }, placeholder = "足し先のグループ", label = "足し先の${terms.group}", modifier = Modifier.fillMaxWidth())
            ClawPrimaryButton(
              text = "＋ Google の結果${webPicked.size}件を足す（名前を直せます）",
              onClick = { if (!busy && webPicked.isNotEmpty() && (webAsGroup || webGroup.isNotBlank())) webDraft = web.filter { it.key in webPicked }.map { nameFromTitle(it.title) } },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
      if (sourceMode != "google") TextButton(enabled = canAdd, onClick = { add() }, modifier = Modifier.fillMaxWidth()) { Text(if (picked.isEmpty()) "追加する" else if (groupMode && !anime) "${picked.size}グループを追加する" else "${picked.size}人を追加する") }
    }
  }
}
