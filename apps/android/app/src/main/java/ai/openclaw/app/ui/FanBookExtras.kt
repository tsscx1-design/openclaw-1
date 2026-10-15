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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private suspend fun MainViewModel.call(method: GatewayMethod, params: JSONObject = JSONObject(), timeoutMs: Long = 60_000): JSONObject =
  JSONObject(runGatewayRpc(method, params.toString(), timeoutMs = timeoutMs))

/** 1人の追加の写真（メインの写真のほかに、何枚も）。押すと「メインにする／消す」。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FanBookExtraPhotos(
  viewModel: MainViewModel,
  group: String,
  member: IdolMemberRow,
  photoVersion: Int,
  onChanged: (String) -> Unit,
  onAddFromPhone: () -> Unit,
  onAddFromShare: () -> Unit,
  onAddFromUrl: () -> Unit = {},
) {
  val scope = rememberCoroutineScope()
  var reload by remember { mutableStateOf(0) }
  var items by remember(group, member.id) { mutableStateOf<List<Int>>(emptyList()) }
  var picked by remember { mutableStateOf<Int?>(null) }
  var confirmRemove by remember { mutableStateOf<Int?>(null) }

  LaunchedEffect(group, member.id, photoVersion, reload) {
    runCatching {
      val arr = viewModel.call(GatewayMethod.IdolMasterPhotoExtras, JSONObject().put("group", group).put("id", member.id).put("action", "list")).optJSONArray("items")
      items = (0 until (arr?.length() ?: 0)).map { arr!!.getInt(it) }
    }
  }

  fun act(action: String, index: Int, ok: String) {
    scope.launch {
      runCatching { viewModel.call(GatewayMethod.IdolMasterPhotoExtras, JSONObject().put("group", group).put("id", member.id).put("action", action).put("index", index)) }
        .onSuccess { reload += 1; onChanged(ok) }
        .onFailure { onChanged("できませんでした：${it.message}") }
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("追加の写真（${items.size}枚）", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
    if (items.isNotEmpty()) {
      Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { index -> ExtraThumb(viewModel, group, member, index, photoVersion + reload) { picked = index } }
      }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      ClawSecondaryButton(text = "＋ 追加の写真（スマホから）", onClick = onAddFromPhone)
      ClawSecondaryButton(text = "📥 共有から追加", onClick = onAddFromShare)
      ClawSecondaryButton(text = "🔗 URL から追加", onClick = onAddFromUrl)
    }
  }

  val target = picked
  if (target != null) {
    AlertDialog(
      onDismissRequest = { picked = null },
      title = { Text("追加の写真 $target") },
      text = { Text("メインの写真にすると、いまのメインの写真は、追加の写真に回ります（消えません）。", style = ClawTheme.type.caption, color = ClawTheme.colors.text) },
      confirmButton = { TextButton(onClick = { picked = null; act("promote", target, "メインの写真にしました") }) { Text("メインにする") } },
      dismissButton = { TextButton(onClick = { picked = null; confirmRemove = target }) { Text("消す") } },
    )
  }
  val removing = confirmRemove
  if (removing != null) {
    FanBookConfirmDialog(
      title = "この写真を消しますか？",
      text = "追加の写真 $removing を、消します。元に戻せません。",
      confirmText = "消す",
      onDismiss = { confirmRemove = null },
      onConfirm = { confirmRemove = null; act("remove", removing, "写真を消しました") },
    )
  }
}

@Composable
internal fun ExtraThumb(viewModel: MainViewModel, group: String, member: IdolMemberRow, index: Int, version: Int, onClick: () -> Unit) {
  val bitmap by produceState<ImageBitmap?>(initialValue = null, group, member.id, index, version) {
    value =
      runCatching {
        val res = viewModel.call(GatewayMethod.IdolMasterPhotoExtras, JSONObject().put("group", group).put("id", member.id).put("action", "get").put("index", index))
        if (!res.optBoolean("found")) null
        else {
          val bytes = Base64.decode(res.getString("base64"), Base64.DEFAULT)
          val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
        }
      }.getOrNull()
  }
  val shape = RoundedCornerShape(10.dp)
  val shown = bitmap
  Box(modifier = Modifier.size(width = 84.dp, height = 112.dp).clip(shape).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    if (shown != null) Image(bitmap = shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().size(width = 84.dp, height = 112.dp))
    else Text("…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
  }
}

/** 1人のメモと、自分のタグ（名簿とは別に保存。取り込みで上書きされない）。 */
@Composable
internal fun FanBookNoteEditor(viewModel: MainViewModel, group: String, member: IdolMemberRow, onSaved: (String) -> Unit) {
  val scope = rememberCoroutineScope()
  var memo by remember(group, member.id) { mutableStateOf("") }
  var tags by remember(group, member.id) { mutableStateOf("") }
  var loaded by remember(group, member.id) { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }

  LaunchedEffect(group, member.id) {
    runCatching {
      val res = viewModel.call(GatewayMethod.IdolMasterNoteGet, JSONObject().put("group", group).put("id", member.id))
      memo = res.optString("memo")
      val arr = res.optJSONArray("tags")
      tags = (0 until (arr?.length() ?: 0)).joinToString("、") { arr!!.getString(it) }
    }
    loaded = true
  }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("📝 メモとタグ（自分用）", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
    ClawTextField(value = memo, onValueChange = { memo = it.take(2000) }, placeholder = "好きなところ、ライブで会った日など", label = "メモ", modifier = Modifier.fillMaxWidth())
    ClawTextField(value = tags, onValueChange = { tags = it }, placeholder = "「、」か「,」で区切る（例: 最推し、ロック）", label = "自分のタグ", modifier = Modifier.fillMaxWidth())
    ClawSecondaryButton(
      text = if (busy) "保存しています…" else "メモとタグを保存",
      onClick = {
        if (busy || !loaded) return@ClawSecondaryButton
        busy = true
        scope.launch {
          runCatching {
            viewModel.call(GatewayMethod.IdolMasterNoteSet, JSONObject().put("group", group).put("id", member.id).put("memo", memo).put("tags", JSONArray(tags.split(Regex("[,、，\\s]+")).filter { it.isNotBlank() })))
          }
            .onSuccess { onSaved("メモとタグを保存しました") }
            .onFailure { onSaved("保存できませんでした：${it.message}") }
          busy = false
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

/** ファンブック全体の検索：名前・愛称・グループ・出身地・期・事務所・タイプ・メモ・タグ。 */
@Composable
internal fun FanBookSearchPanel(viewModel: MainViewModel, onOpen: (group: String, id: Int) -> Unit) {
  val scope = rememberCoroutineScope()
  var query by remember { mutableStateOf("") }
  var hits by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }

  fun search() {
    if (busy || query.isBlank()) return
    busy = true
    message = ""
    scope.launch {
      runCatching {
        val arr = viewModel.call(GatewayMethod.IdolMasterSearch, JSONObject().put("query", query.trim())).optJSONArray("hits")
        hits = (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }
        if (hits.isEmpty()) message = "見つかりませんでした"
      }.onFailure { message = "探せませんでした：${it.message}" }
      busy = false
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("🔎 ファンブック全体から探す", style = ClawTheme.type.body, color = ClawTheme.colors.text)
    Text("名前・愛称・グループ・出身地・期・事務所・メモ・タグで探せます。言葉をスペースで分けると、全部に合う人だけが出ます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    ClawTextField(value = query, onValueChange = { query = it }, placeholder = "例: たかみな 東京", label = "探す言葉", modifier = Modifier.fillMaxWidth())
    ClawPrimaryButton(text = if (busy) "探しています…" else "探す", onClick = { search() }, modifier = Modifier.fillMaxWidth())
    hits.forEach { hit ->
      val group = hit.optString("group")
      val id = hit.optInt("id")
      val row = IdolMemberRow(id = id, name = hit.optString("name"), tags = emptyList(), description = "", hasPhoto = hit.optBoolean("photo"))
      Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.5f)).clickable { onOpen(group, id) }.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IdolPhotoBox(viewModel, group, row, Modifier.size(width = 54.dp, height = 72.dp), maxSide = 200)
        Column(modifier = Modifier.weight(1f)) {
          Text(row.name + hit.optString("nickname").takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty(), style = ClawTheme.type.body, color = ClawTheme.colors.text)
          Text("$group・${hit.optString("matched")}で一致", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        }
      }
    }
    if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
  }
}

/** 同じ人に見える人（スペース・中黒・旧字体の違いを無視）を探して、1人にまとめる。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FanBookDuplicatesDialog(viewModel: MainViewModel, onDismiss: () -> Unit, onMerged: (String) -> Unit) {
  val scope = rememberCoroutineScope()
  var clusters by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
  var loaded by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf<Pair<JSONObject, Int>?>(null) }
  var reload by remember { mutableStateOf(0) }

  LaunchedEffect(reload) {
    runCatching {
      val arr = viewModel.call(GatewayMethod.IdolMasterDuplicates).optJSONArray("clusters")
      clusters = (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }
    }.onFailure { message = "調べられませんでした：${it.message}" }
    loaded = true
  }

  val pending = confirm
  if (pending != null) {
    val (cluster, keepIndex) = pending
    val members = cluster.getJSONArray("members")
    val keep = members.getJSONObject(keepIndex)
    FanBookConfirmDialog(
      title = "「${keep.optString("group")}」の「${keep.optString("name")}」を残しますか？",
      text = "ほかの人の空の項目を、この人に足して、写真・メモ・タグもまとめます。ほかの人は、ファンブックから見えなくなります（同じ人をまた足すと、元に戻ります）。",
      confirmText = "まとめる",
      onDismiss = { confirm = null },
      onConfirm = {
        confirm = null
        busy = true
        scope.launch {
          runCatching {
            val others = JSONArray()
            for (i in 0 until members.length()) if (i != keepIndex) others.put(JSONObject().put("group", members.getJSONObject(i).optString("group")).put("id", members.getJSONObject(i).optInt("id")))
            viewModel.call(GatewayMethod.IdolMasterMerge, JSONObject().put("keep", JSONObject().put("group", keep.optString("group")).put("id", keep.optInt("id"))).put("remove", others))
          }
            .onSuccess { message = "まとめました"; reload += 1; onMerged("同じ人をまとめました") }
            .onFailure { message = "まとめられませんでした：${it.message}" }
          busy = false
        }
      },
    )
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("重複の整理") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("名前が同じに見える人を並べます。残したい人を選ぶと、1人にまとめます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        if (!loaded) Text("調べています…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        else if (clusters.isEmpty()) Text("重複は見つかりませんでした。", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        clusters.forEach { cluster ->
          val members = cluster.getJSONArray("members")
          Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.5f)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(cluster.optString("name"), style = ClawTheme.type.body, color = ClawTheme.colors.text)
            for (i in 0 until members.length()) {
              val m = members.getJSONObject(i)
              ClawSecondaryButton(
                text = "「${m.optString("group")}」の「${m.optString("name")}」を残す" + if (m.optBoolean("photo")) "（写真あり）" else "",
                onClick = { if (!busy) confirm = cluster to i },
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    },
    confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("閉じる") } },
  )
}

/** 図鑑に作るときに引き継ぐ画像（メインの写真と追加の写真から、選ぶ）。 */
data class FanBookCreatePhotos(val useMain: Boolean, val extras: List<Int>, val makeSheet: Boolean)

@Composable
internal fun FanBookPhotoPicker(
  viewModel: MainViewModel,
  group: String,
  member: IdolMemberRow,
  photoVersion: Int,
  useMain: Boolean,
  onUseMain: (Boolean) -> Unit,
  selected: Set<Int>,
  onToggle: (Int) -> Unit,
) {
  var items by remember(group, member.id) { mutableStateOf<List<Int>>(emptyList()) }
  LaunchedEffect(group, member.id, photoVersion) {
    runCatching {
      val arr = viewModel.call(GatewayMethod.IdolMasterPhotoExtras, JSONObject().put("group", group).put("id", member.id).put("action", "list")).optJSONArray("items")
      items = (0 until (arr?.length() ?: 0)).map { arr!!.getInt(it) }
    }
  }
  Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (member.hasPhoto) {
      CheckedBox(useMain, { onUseMain(!useMain) }) {
        IdolPhotoBox(viewModel, group, member, Modifier.size(width = 84.dp, height = 112.dp), maxSide = 300, photoVersion = photoVersion)
      }
    }
    items.forEach { index ->
      CheckedBox(index in selected, { onToggle(index) }) { ExtraThumb(viewModel, group, member, index, photoVersion) { onToggle(index) } }
    }
  }
  if (!member.hasPhoto && items.isEmpty()) Text("写真がまだありません。上の「写真を入れる」「画像を探す」「共有」で入れると、ここで選べます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
}

@Composable
private fun CheckedBox(checked: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
  Box(modifier = Modifier.clickable(onClick = onClick)) {
    content()
    Box(
      modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (checked) ClawTheme.colors.primary else ClawTheme.colors.surfaceRaised.copy(alpha = 0.85f)),
      contentAlignment = Alignment.Center,
    ) {
      Text(if (checked) "✓" else "", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
    }
  }
}

/** 公式サイトの一覧と比べた、メンバーの変化（新しく入った人・いなくなった人）。1日に1回、自動で確認する。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FanBookWatchDialog(viewModel: MainViewModel, onDismiss: () -> Unit, onChanged: (String) -> Unit) {
  val scope = rememberCoroutineScope()
  var results by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
  var checkedAt by remember { mutableStateOf(0L) }
  var running by remember { mutableStateOf(false) }
  var loaded by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }
  var reload by remember { mutableStateOf(0) }

  LaunchedEffect(reload) {
    runCatching {
      val res = viewModel.call(GatewayMethod.IdolMasterWatchStatus)
      checkedAt = res.optLong("checkedAt")
      running = res.optBoolean("running")
      val arr = res.optJSONArray("results")
      results = (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }
    }.onFailure { message = "読めませんでした：${it.message}" }
    loaded = true
    // 確認中は、少し待って、読み直す
    if (running) {
      kotlinx.coroutines.delay(5000)
      reload += 1
    }
  }

  fun names(o: JSONObject, key: String): List<String> = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()

  fun apply(group: String, action: String, list: List<String>, ok: String) {
    scope.launch {
      runCatching { viewModel.call(GatewayMethod.IdolMasterWatchApply, JSONObject().put("group", group).put("action", action).put("names", JSONArray(list))) }
        .onSuccess { message = ok; reload += 1; onChanged(ok) }
        .onFailure { message = "できませんでした：${it.message}" }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("メンバーの変化") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("公式サイトの一覧と、ファンブックを比べます（1日に1回、自動で確認します）。新しく載った人と、載らなくなった人が、ここに出ます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        Text(
          if (checkedAt > 0) "最後の確認：" + java.text.SimpleDateFormat("M月d日 H:mm", java.util.Locale.JAPAN).format(java.util.Date(checkedAt)) else "まだ確認していません",
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.text,
        )
        ClawSecondaryButton(
          text = if (running) "確認しています…" else "🔄 いま確認する",
          onClick = {
            if (running) return@ClawSecondaryButton
            running = true
            scope.launch {
              runCatching { viewModel.call(GatewayMethod.IdolMasterWatchCheck) }
                .onFailure { message = "始められませんでした：${it.message}"; running = false }
              kotlinx.coroutines.delay(5000)
              reload += 1
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        if (loaded && results.isEmpty() && !running) Text("変化はありません。", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        results.forEach { r ->
          val group = r.optString("group")
          val added = names(r, "added")
          val left = names(r, "left")
          Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.5f)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(group, style = ClawTheme.type.body, color = ClawTheme.colors.text)
            if (r.optString("error").isNotBlank()) Text("確認できませんでした：${r.optString("error")}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            if (added.isNotEmpty()) {
              Text("新しく載った人（${added.size}人）：" + added.take(12).joinToString("、") + if (added.size > 12) " …" else "", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ClawPrimaryButton(text = "名前を追加する", onClick = { apply(group, "add", added, "「$group」に${added.size}人を追加しました") })
                ClawSecondaryButton(text = "無視する", onClick = { apply(group, "ignore", added, "無視しました") })
              }
            }
            if (left.isNotEmpty()) {
              Text("載らなくなった人（${left.size}人）：" + left.take(12).joinToString("、") + if (left.size > 12) " …" else "", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ClawSecondaryButton(text = "卒業として外す", onClick = { apply(group, "leave", left, "「$group」から${left.size}人を外しました") })
                ClawSecondaryButton(text = "無視する", onClick = { apply(group, "ignore", left, "無視しました") })
              }
            }
          }
        }
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
  )
}

/** 名前でインターネット（ウィキペディア・ウィキクォート・TMDB）を調べて、プロフィール・経歴・作品・話し方・声を、下書きにする。読んで直して、図鑑のキャラとメモに登録できる。 */
@Composable
internal fun FanBookResearchDialog(viewModel: MainViewModel, group: String, member: IdolMemberRow, onDismiss: () -> Unit, onDone: (String) -> Unit) {
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf(member.name) }
  var busy by remember { mutableStateOf(false) }
  var searched by remember { mutableStateOf(false) }
  var found by remember { mutableStateOf(true) }
  var intro by remember { mutableStateOf("") }
  var career by remember { mutableStateOf("") }
  var works by remember { mutableStateOf("") }
  var speech by remember { mutableStateOf("") }
  var voice by remember { mutableStateOf("") }
  var sources by remember { mutableStateOf<List<String>>(emptyList()) }
  var toCharacter by remember { mutableStateOf(true) }
  var toMemo by remember { mutableStateOf(true) }
  var message by remember { mutableStateOf("") }

  fun research() {
    if (busy || name.isBlank()) return
    busy = true
    message = ""
    scope.launch {
      runCatching { viewModel.call(GatewayMethod.IdolMasterResearch, JSONObject().put("group", group).put("id", member.id).put("name", name.trim()), timeoutMs = 300_000) }
        .onSuccess { res ->
          searched = true
          found = res.optBoolean("found")
          val f = res.optJSONObject("fields")
          intro = f?.optString("intro").orEmpty()
          career = f?.optString("career").orEmpty()
          works = f?.optString("works").orEmpty()
          speech = f?.optString("speech").orEmpty()
          voice = f?.optString("voice").orEmpty()
          val arr = res.optJSONArray("sources")
          sources = (0 until (arr?.length() ?: 0)).map { i -> arr!!.getJSONObject(i).let { o -> "${o.optString("label")}：${o.optString("url")}" } }
        }
        .onFailure { message = "調べられませんでした：${it.message}" }
      busy = false
    }
  }

  fun save() {
    if (busy) return
    busy = true
    message = ""
    scope.launch {
      runCatching {
        val fields = JSONObject().put("intro", intro).put("career", career).put("works", works).put("speech", speech).put("voice", voice)
        viewModel.call(GatewayMethod.IdolMasterResearchApply, JSONObject().put("group", group).put("id", member.id).put("fields", fields).put("toCharacter", toCharacter).put("toMemo", toMemo))
      }
        .onSuccess { res ->
          val saved = res.optJSONArray("saved")
          onDone("「${member.name}」の調べた内容を、" + (0 until (saved?.length() ?: 0)).joinToString("と") { i -> saved!!.getString(i) } + "に登録しました")
        }
        .onFailure { message = "登録できませんでした：${it.message}" }
      busy = false
    }
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("「${member.name}」をネットで調べる") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("名前で、ウィキペディア・ウィキクォート・TMDB を調べて、書かれている内容だけから、まとめます（想像で足しません）。発言の引用は、取り込みません。読んで、直してから、登録してください。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        ClawTextField(value = name, onValueChange = { name = it }, placeholder = "名前", label = "調べる名前（芸名など、変えられます）", modifier = Modifier.fillMaxWidth())
        ClawPrimaryButton(text = if (busy && !searched) "調べています…（1分ほど）" else "🌐 調べる", onClick = { research() }, modifier = Modifier.fillMaxWidth())
        if (searched && !found) Text("資料が見つかりませんでした。名前を変えて、もう一度、試してください。", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
        if (searched && found) {
          ClawTextField(value = intro, onValueChange = { intro = it }, placeholder = "", label = "自己紹介", modifier = Modifier.fillMaxWidth())
          ClawTextField(value = career, onValueChange = { career = it }, placeholder = "", label = "経歴", modifier = Modifier.fillMaxWidth())
          ClawTextField(value = works, onValueChange = { works = it }, placeholder = "", label = "主な作品", modifier = Modifier.fillMaxWidth())
          ClawTextField(value = speech, onValueChange = { speech = it }, placeholder = "", label = "話し方の特徴", modifier = Modifier.fillMaxWidth())
          ClawTextField(value = voice, onValueChange = { voice = it }, placeholder = "資料に声の記述がないと、空です", label = "声の特徴（キャラの声になります）", modifier = Modifier.fillMaxWidth())
          ClawSecondaryButton(text = if (toCharacter) "● 図鑑のキャラに登録（プロフィールと声）" else "○ 図鑑のキャラに登録（プロフィールと声）", onClick = { toCharacter = !toCharacter }, modifier = Modifier.fillMaxWidth())
          ClawSecondaryButton(text = if (toMemo) "● メモにも残す" else "○ メモにも残す", onClick = { toMemo = !toMemo }, modifier = Modifier.fillMaxWidth())
          ClawPrimaryButton(text = if (busy) "登録しています…" else "この内容で登録する", onClick = { if (toCharacter || toMemo) save() }, modifier = Modifier.fillMaxWidth())
          sources.forEach { Text("出典　$it", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted) }
        }
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    },
    confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("閉じる") } },
  )
}

/**
 * 1人のページの写真の場所：横にスライドして、実写の写真と、図鑑に作ったキャラの絵（設定画・3面図など）を、見られる。
 * 図鑑にキャラがまだないときは、写真だけ。下に、いま見ているものの名前と、点の目印が出る。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun FanBookPhotoPager(
  viewModel: MainViewModel,
  group: String,
  member: IdolMemberRow,
  photoVersion: Int,
  onLongClickPhoto: () -> Unit,
) {
  var characterName by remember(group, member.id) { mutableStateOf("") }
  var characterId by remember(group, member.id) { mutableStateOf("") }
  var items by remember(group, member.id) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  LaunchedEffect(group, member.id, photoVersion) {
    runCatching {
      val res = viewModel.call(GatewayMethod.IdolMasterMemberCharacter, JSONObject().put("group", group).put("id", member.id))
      if (res.optBoolean("found")) {
        characterId = res.optString("characterId")
        characterName = res.optString("name")
        val arr = res.optJSONArray("items")
        items = (0 until (arr?.length() ?: 0)).map { i -> arr!!.getJSONObject(i).let { o -> o.optString("key") to o.optString("label") } }
      } else {
        characterId = ""
        items = emptyList()
      }
    }
  }
  val pageCount = 1 + items.size
  val pager = androidx.compose.foundation.pager.rememberPagerState { pageCount }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    androidx.compose.foundation.pager.HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) { page ->
      if (page == 0) {
        IdolPhotoBox(viewModel, group, member, Modifier.fillMaxWidth().aspectRatio(3f / 4f), maxSide = 900, photoVersion = photoVersion, onLongClick = onLongClickPhoto)
      } else {
        val key = items[page - 1].first
        val picture by produceState<ImageBitmap?>(initialValue = null, characterId, key) {
          value =
            runCatching {
              val res = viewModel.call(GatewayMethod.ComicCharacterImage, JSONObject().put("characterId", characterId).put("key", key), timeoutMs = 90_000)
              val bytes = Base64.decode(res.getString("imageBase64"), Base64.DEFAULT)
              BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
          val shown = picture
          if (shown != null) Image(bitmap = shown, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
          else Text("読み込み中…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        }
      }
    }
    val label = if (pager.currentPage == 0) "実写の写真" else "図鑑のキャラ「$characterName」：${items.getOrNull(pager.currentPage - 1)?.second.orEmpty()}"
    Text(label, style = ClawTheme.type.caption, color = ClawTheme.colors.text)
    if (pageCount > 1) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(pageCount) { i ->
          Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (i == pager.currentPage) ClawTheme.colors.primary else ClawTheme.colors.surfaceRaised))
        }
        Text("　横にスライドで切り替え", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      }
    } else if (characterId.isBlank()) {
      Text("「🎤 キャラ図鑑に作る」を押すと、ここに、キャラの絵が出ます（横にスライドで切り替え）。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    }
  }
}
