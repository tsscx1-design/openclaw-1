package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class IdolGroupRow(val group: String, val count: Int, val parent: String = "")

internal data class IdolCategoryRow(val name: String, val groups: List<IdolGroupRow>, val parent: String = "")

internal data class IdolMemberRow(
  val id: Int,
  val name: String,
  val tags: List<String>,
  val description: String,
  val hasPhoto: Boolean,
  val generation: String = "",
  val nickname: String = "",
  val age: Int? = null,
  val birthday: String = "",
  val hometown: String = "",
  val bloodType: String = "",
  val heightCm: Double? = null,
  val agency: String = "",
  val bio: String = "",
  val xUrl: String = "",
  val source: String = "",
  /** PC の写真ファイルの版（時刻の秒）。0 = 写真ファイルなし。端末に保存した写真の、古い新しいの印。 */
  val photoV: Long = 0,
  val myTags: List<String> = emptyList(),
  val hasMemo: Boolean = false,
) {
  /** 一覧で名前の下に出す短い行：期・年齢・出身地・タイプ。 */
  fun summary(): String =
    listOfNotNull(
      generation.takeIf { it.isNotBlank() },
      age?.let { "${it}歳" },
      hometown.takeIf { it.isNotBlank() },
      tags.takeIf { it.isNotEmpty() }?.joinToString("・"),
    ).joinToString("　")
}

/** 顔写真。あなたが PC の写真フォルダに置いた画像だけを、PC から読んで見せる（なければ、名前の頭文字）。 */
internal object IdolPhotos {
  // メモリは、画像の大きさ（バイト数）で数える（端末のメモリの 1/8 まで）
  private val cache =
    object : LruCache<String, Bitmap>(maxOf(8 * 1024, (Runtime.getRuntime().maxMemory() / 8 / 1024).toInt())) {
      override fun sizeOf(key: String, value: Bitmap): Int = maxOf(1, value.byteCount / 1024)
    }
  // 同時に PC へ頼む数（たくさんの写真を、一度に頼んで、詰まらせない）
  private val gate = kotlinx.coroutines.sync.Semaphore(4)

  private fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / maxSide)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
  }

  /** 写真の版（fileVersion）が同じなら、端末に保存した写真を、すぐ出す。違うときだけ、PC から取る（小さな JPEG）。 */
  suspend fun load(viewModel: MainViewModel, group: String, id: Int, maxSide: Int, version: Int = 0, fileVersion: Long = 0): Bitmap? {
    val key = "$group:$id:$maxSide:$fileVersion"
    cache.get(key)?.let { return it }
    return withContext(Dispatchers.IO) {
      FanBookImageCache.read("photo:$key")?.let { bytes -> decode(bytes, maxSide)?.also { cache.put(key, it) } }
        ?: gate.withPermit {
          runCatching {
            val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.IdolMasterPhoto, JSONObject().put("group", group).put("id", id).put("maxSide", maxSide).toString(), timeoutMs = 60_000))
            if (!res.optBoolean("found")) return@runCatching null
            val bytes = Base64.decode(res.optString("base64"), Base64.DEFAULT)
            FanBookImageCache.write("photo:$key", bytes)
            decode(bytes, maxSide)?.also { cache.put(key, it) }
          }.getOrNull()
        }
    }
  }

  /** 一覧を開いたときに、先頭の写真を、先に読んでおく（4枚ずつ、順に）。 */
  suspend fun prefetch(viewModel: MainViewModel, group: String, members: List<IdolMemberRow>, maxSide: Int) {
    kotlinx.coroutines.coroutineScope {
      members.filter { it.hasPhoto }.take(60).map { m -> async { load(viewModel, group, m.id, maxSide, 0, m.photoV) } }.forEach { it.await() }
    }
  }
}

@Composable
internal fun IdolPhoto(viewModel: MainViewModel, group: String, member: IdolMemberRow, sizeDp: Int, maxSide: Int) {
  var bitmap by remember(group, member.id) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(group, member.id) { if (member.hasPhoto) bitmap = IdolPhotos.load(viewModel, group, member.id, maxSide) }
  Box(
    modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)),
    contentAlignment = Alignment.Center,
  ) {
    val shown = bitmap
    if (shown != null) Image(bitmap = shown.asImageBitmap(), contentDescription = member.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    else Text(member.name.take(1), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
  }
}

/**
 * 設定マスターデータから、グループ → 名前の順にえらんで、キャラ図鑑に追加する。
 * 名前は、追加の前に直せる。追加したあとの一覧の読み直しは、呼んだ側（onAdded）がする。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdolMasterDialog(
  viewModel: MainViewModel,
  onDismiss: () -> Unit,
  /** 追加できた（名前, すでに図鑑にあった場合は true）。 */
  onAdded: (String, Boolean) -> Unit,
) {
  val scope = rememberCoroutineScope()
  var groups by remember { mutableStateOf<List<IdolGroupRow>>(emptyList()) }
  var categories by remember { mutableStateOf<List<IdolCategoryRow>>(emptyList()) }
  var group by remember { mutableStateOf<String?>(null) }
  var members by remember { mutableStateOf<List<IdolMemberRow>>(emptyList()) }
  var picked by remember { mutableStateOf<IdolMemberRow?>(null) }
  var name by remember { mutableStateOf("") }
  var query by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }

  suspend fun rpc(method: GatewayMethod, params: JSONObject): JSONObject =
    JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = 60_000))

  var wikidataOpen by remember { mutableStateOf(false) }
  var groupsTick by remember { mutableStateOf(0) }
  LaunchedEffect(groupsTick) {
    runCatching {
      val res = rpc(GatewayMethod.IdolMasterGroups, JSONObject())
      val array = res.getJSONArray("groups")
      groups = (0 until array.length()).map { array.getJSONObject(it) }.map { IdolGroupRow(it.optString("group"), it.optInt("count")) }
      val cats = res.optJSONArray("categories")
      categories =
        (0 until (cats?.length() ?: 0)).map { cats!!.getJSONObject(it) }.map { c ->
          val gs = c.optJSONArray("groups")
          IdolCategoryRow(c.optString("name"), (0 until (gs?.length() ?: 0)).map { gs!!.getJSONObject(it) }.map { IdolGroupRow(it.optString("group"), it.optInt("count")) })
        }
    }.onFailure { message = "グループを読めませんでした（ゲートウェイを新しくしたか確認してください）" }
  }
  var bulkOpen by remember { mutableStateOf(false) }
  if (bulkOpen) {
    IdolWikidataBulkDialog(
      viewModel = viewModel,
      groups = groups.map { it.group },
      onDismiss = { bulkOpen = false; groupsTick += 1 },
      onApplied = { text -> message = text; groupsTick += 1 },
    )
  }
  if (wikidataOpen) {
    IdolWikidataDialog(
      viewModel = viewModel,
      onDismiss = { wikidataOpen = false },
      onAdded = { text ->
        wikidataOpen = false
        message = text
        groupsTick += 1
      },
    )
  }

  fun openGroup(name: String) {
    group = name
    members = emptyList()
    query = ""
    message = "読み込み中です…"
    scope.launch {
      runCatching {
        val array = rpc(GatewayMethod.IdolMasterMembers, JSONObject().put("group", name)).getJSONArray("members")
        members =
          (0 until array.length()).map { array.getJSONObject(it) }.map { row ->
            val tags = row.optJSONArray("tags")
            IdolMemberRow(
              id = row.optInt("id"),
              name = row.optString("name"),
              tags = (0 until (tags?.length() ?: 0)).map { tags!!.getString(it) },
              description = row.optString("description"),
              hasPhoto = row.optBoolean("photo"),
              generation = row.optString("generation"),
              nickname = row.optString("nickname"),
              age = if (row.has("age")) row.optInt("age") else null,
              birthday = row.optString("birthday"),
              hometown = row.optString("hometown"),
              bloodType = row.optString("bloodType"),
              heightCm = if (row.has("heightCm")) row.optDouble("heightCm") else null,
              agency = row.optString("agency"),
              bio = row.optString("bio"),
              xUrl = row.optString("xUrl"),
              source = row.optString("source"),
            )
          }
        message = ""
      }.onFailure { message = "メンバーを読めませんでした：${it.message}" }
    }
  }

  val shown = members.filter {
    query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.description.contains(query.trim(), ignoreCase = true) || it.tags.any { tag -> tag.contains(query.trim(), ignoreCase = true) }
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(if (picked != null) "図鑑に追加する" else if (group != null) "$group の名前をえらぶ" else "グループをえらぶ") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val current = picked
        val currentGroup = group
        when {
          current != null -> {
            if (currentGroup != null) IdolPhoto(viewModel, currentGroup, current, sizeDp = 160, maxSide = 480)
            Text("${currentGroup ?: ""}：${current.name}", style = ClawTheme.type.body, color = ClawTheme.colors.text)
            if (current.tags.isNotEmpty()) Text("タイプ：${current.tags.joinToString("・")}", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            val facts =
              listOfNotNull(
                current.nickname.takeIf { it.isNotBlank() }?.let { "愛称：$it" },
                current.generation.takeIf { it.isNotBlank() }?.let { "期：$it" },
                current.age?.let { "年齢：${it}歳" },
                current.birthday.takeIf { it.isNotBlank() }?.let { "誕生日：$it" },
                current.hometown.takeIf { it.isNotBlank() }?.let { "出身地：$it" },
                current.bloodType.takeIf { it.isNotBlank() }?.let { "血液型：${it}型" },
                current.heightCm?.let { "身長：${it.toString().removeSuffix(".0")}cm" },
                current.agency.takeIf { it.isNotBlank() }?.let { "事務所：$it" },
              )
            facts.forEach { Text(it, style = ClawTheme.type.caption, color = ClawTheme.colors.text) }
            // 止めはしない。事実の表示だけ：年齢が空だと、年齢の設定（禁止する年齢の範囲）は、この人には効かない
            if (current.age == null) Text("年齢が名簿にありません（年齢の設定は、年齢がわかる人にだけ効きます。誕生日を名簿に入れると、効きます）", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
            if (current.bio.isNotBlank()) Text("略歴：${current.bio}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            if (current.xUrl.isNotBlank()) {
              val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
              Text("X を開く", style = ClawTheme.type.caption, color = ClawTheme.colors.primary, modifier = Modifier.clickable { runCatching { uriHandler.openUri(current.xUrl) } })
            }
            if (current.source.isNotBlank()) Text("出典：${current.source}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            Text("見た目：${current.description}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            ClawTextField(value = name, onValueChange = { name = it }, placeholder = "図鑑での名前", label = "名前（変えられます）", modifier = Modifier.fillMaxWidth())
          }
          currentGroup == null -> {
            if (categories.isEmpty()) {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { row -> ClawSecondaryButton(text = "${row.group}（${row.count}）", onClick = { openGroup(row.group) }) }
              }
            } else {
              // 分類（48グループ・坂道シリーズ …）ごとに、グループを並べる。
              categories.filter { it.groups.isNotEmpty() }.forEach { category ->
                Text(category.name, style = ClawTheme.type.caption, color = ClawTheme.colors.text)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  category.groups.forEach { row -> ClawSecondaryButton(text = "${row.group}（${row.count}）", onClick = { openGroup(row.group) }) }
                }
              }
            }
            ClawSecondaryButton(text = "🔎 Wikidata でさがして、名簿に追加", onClick = { wikidataOpen = true }, modifier = Modifier.fillMaxWidth())
            ClawSecondaryButton(text = "📥 名簿の空の項目を、Wikidata で補う", onClick = { bulkOpen = true }, modifier = Modifier.fillMaxWidth())
          }
          else -> {
            val currentGroupName: String = currentGroup
            ClawTextField(value = query, onValueChange = { query = it }, placeholder = "名前・タイプでさがす", modifier = Modifier.fillMaxWidth())
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              items(shown, key = { it.id }) { row ->
                Row(
                  modifier = Modifier.fillMaxWidth().clickable { picked = row; name = row.name; message = "" }.padding(vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  IdolPhoto(viewModel, currentGroupName, row, sizeDp = 48, maxSide = 200)
                  Column {
                    Text(if (row.nickname.isNotBlank()) "${row.name}（${row.nickname}）" else row.name, style = ClawTheme.type.body, color = ClawTheme.colors.text)
                    row.summary().takeIf { it.isNotBlank() }?.let { Text(it, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted) }
                  }
                }
              }
            }
          }
        }
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    },
    confirmButton = {
      val member = picked
      val target = group
      if (member != null && target != null) {
        TextButton(enabled = !busy && name.isNotBlank(), onClick = {
          scope.launch {
            busy = true
            runCatching { rpc(GatewayMethod.IdolMasterImport, JSONObject().put("group", target).put("id", member.id).put("name", name.trim())) }
              .onSuccess { res -> onAdded(res.optString("name", name.trim()), res.optBoolean("existing")) }
              .onFailure { message = "追加できませんでした：${it.message}" }
            busy = false
          }
        }) { Text("図鑑に追加") }
      }
    },
    dismissButton = {
      TextButton(enabled = !busy, onClick = {
        when {
          picked != null -> picked = null
          group != null -> { group = null; members = emptyList(); message = "" }
          else -> onDismiss()
        }
      }) { Text(if (picked != null || group != null) "戻る" else "閉じる") }
    },
  )
}
