package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** ファンブックの、いまの画面。ホーム → 分類 → グループ（名前の一覧）→ 1人のページ、と深くなる。 */
private sealed interface MasterView {
  data object Home : MasterView

  data class Category(val name: String) : MasterView

  data class Group(val category: String, val group: String) : MasterView

  data class Member(val category: String, val group: String, val index: Int) : MasterView
}

/** グループの分類（48グループ・坂道シリーズ …）を選び直す。新しい名前を入れると、分類が増える。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdolCategoryMoveDialog(
  group: String,
  categories: List<String>,
  onDismiss: () -> Unit,
  onChoose: (String) -> Unit,
) {
  var newName by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("「$group」の分類") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("入れる分類を選ぶか、新しい分類の名前を入れてください。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          categories.forEach { name -> ClawSecondaryButton(text = name, onClick = { onChoose(name) }) }
        }
        ClawTextField(value = newName, onValueChange = { newName = it }, placeholder = "新しい分類（例: K-POP）", label = "新しい分類", modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = { TextButton(enabled = newName.isNotBlank(), onClick = { onChoose(newName.trim()) }) { Text("この名前の分類に入れる") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )
}

/** スマホで選んだ画像を、長辺 1600px 以下の JPEG にして、向き（回転）も直し、base64 にする。 */
private suspend fun readPickedPhoto(context: Context, uri: Uri): String? =
  withContext(Dispatchers.IO) {
    runCatching {
      val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1600)
      var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
      val orientation = runCatching { ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
      val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
      }
      if (degrees != 0f) bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
      val out = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
      Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
  }

/**
 * 設定マスターデータ（アイドルの名簿）の「ファンブック」。上に、分類のボタン（48グループ・坂道シリーズ …）。
 * 押すと全画面で、グループ → 名前（写真つき）→ 1人のページ、の順に見ながら、キャラ図鑑に作る人を選べる。
 * 分類・グループ・メンバーは、自由に足せる。写真のない人は、写真の場所を長押しして、スマホの写真を入れられる。
 */
internal data class IdolSourcePreset(val group: String, val url: String, val readable: Boolean, val note: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdolMasterScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var groups by remember { mutableStateOf<List<IdolGroupRow>>(emptyList()) }
  var categories by remember { mutableStateOf<List<IdolCategoryRow>>(emptyList()) }
  var rosterFile by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  var tick by remember { mutableStateOf(0) }
  var view by remember { mutableStateOf<MasterView>(MasterView.Home) }
  var moving by remember { mutableStateOf<String?>(null) }
  var bulkOpen by remember { mutableStateOf(false) }
  var fillGroup by remember { mutableStateOf<String?>(null) }
  var officialOpen by remember { mutableStateOf(false) }
  var sourceGroup by remember { mutableStateOf<String?>(null) }
  var sourceUrl by remember { mutableStateOf("") }
  var presets by remember { mutableStateOf<List<IdolSourcePreset>>(emptyList()) }
  var catalogCategory by remember { mutableStateOf<String?>(null) }
  var catalogGroup by remember { mutableStateOf<String?>(null) }
  var columns by remember { mutableStateOf(readFanBookColumns(context)) }
  var imageTarget by remember { mutableStateOf<IdolMemberRow?>(null) }
  var duplicatesOpen by remember { mutableStateOf(false) }
  var watchOpen by remember { mutableStateOf(false) }
  var researchTarget by remember { mutableStateOf<IdolMemberRow?>(null) }
  var urlPhotoTarget by remember { mutableStateOf<Pair<IdolMemberRow, Boolean>?>(null) }
  var watchCount by remember { mutableStateOf(0) }
  var queueOpen by remember { mutableStateOf(false) }
  var mergeOpen by remember { mutableStateOf(false) }
  var queueActive by remember { mutableStateOf(0) }
  var pendingOpenId by remember { mutableStateOf<Int?>(null) }
  var photoAsExtra by remember { mutableStateOf(false) }
  val shareTarget by FanBookShareInbox.armed.collectAsState()
  val sharedUris by FanBookShareInbox.incoming.collectAsState()
  var homeReorder by remember { mutableStateOf(false) }
  var viewSettings by remember { mutableStateOf(false) }
  var backupOpen by remember { mutableStateOf(false) }
  var groupMenu by remember { mutableStateOf<String?>(null) }
  var categoryMenu by remember { mutableStateOf<String?>(null) }
  var searchOpen by remember { mutableStateOf(false) }
  var addCategoryOpen by remember { mutableStateOf(false) }
  var addGroupOpen by remember { mutableStateOf(false) }
  // 検索して、メンバー／グループを足す（グループの画面・分類の画面のボタン）
  var webAdd by remember { mutableStateOf<WebAddTarget?>(null) }
  // Google でサイト検索（アプリの中で開く）。ホームの「取り込みの状況」の横のボタン、または、検索の入口から
  var siteSearch by remember { mutableStateOf<SiteSearchStart?>(null) }
  // 分類・グループのアイコン（スマホの写真から選ぶ）。鍵は、「category:名前」「group:名前」
  var iconKeyPending by remember { mutableStateOf<String?>(null) }
  // グループの中に、サブグループを足す
  var addSubOpen by remember { mutableStateOf(false) }
  var addMemberOpen by remember { mutableStateOf(false) }

  // 開いているグループのメンバー（一覧と、1人のページで共有する）
  var members by remember { mutableStateOf<List<IdolMemberRow>>(emptyList()) }
  var loadingMembers by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf("") }
  var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
  var busy by remember { mutableStateOf(false) }
  var memberMessage by remember { mutableStateOf("") }
  var photoVersion by remember { mutableStateOf(0) }
  var photoTarget by remember { mutableStateOf<IdolMemberRow?>(null) }

  LaunchedEffect(Unit) { FanBookImageCache.attach(context) }

  LaunchedEffect(tick) {
    runCatching {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.IdolMasterGroups, "{}", timeoutMs = 30_000))
      val array = res.getJSONArray("groups")
      groups = (0 until array.length()).map { array.getJSONObject(it) }.map { IdolGroupRow(it.optString("group"), it.optInt("count")) }
      rosterFile = res.optJSONObject("roster")?.optString("file").orEmpty()
      val cats = res.optJSONArray("categories")
      categories =
        (0 until (cats?.length() ?: 0)).map { cats!!.getJSONObject(it) }.map { c ->
          val gs = c.optJSONArray("groups")
          IdolCategoryRow(c.optString("name"), (0 until (gs?.length() ?: 0)).map { gs!!.getJSONObject(it) }.map { IdolGroupRow(it.optString("group"), it.optInt("count"), it.optString("parent")) }, c.optString("parent"))
        }
    }.onFailure { message = "名簿を読めませんでした（ゲートウェイを新しくしたか確認してください）" }
  }

  LaunchedEffect(tick) {
    runCatching {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.IdolMasterWatchStatus, "{}", timeoutMs = 20_000))
      val arr = res.optJSONArray("results")
      watchCount = (0 until (arr?.length() ?: 0)).count { i -> val o = arr!!.getJSONObject(i); (o.optJSONArray("added")?.length() ?: 0) + (o.optJSONArray("left")?.length() ?: 0) > 0 }
    }
  }

  // 取り込みの待機：動いている件数を見て、終わったら名簿と写真を読み直す
  LaunchedEffect(Unit) {
    var before = 0
    while (true) {
      runCatching {
        val arr = JSONObject(viewModel.runGatewayRpc(GatewayMethod.IdolMasterSourceQueueList, "{}", timeoutMs = 20_000)).optJSONArray("queue")
        val active = (0 until (arr?.length() ?: 0)).count { arr!!.getJSONObject(it).optString("status") in setOf("waiting", "running") }
        if (before > active) {
          tick += 1
          photoVersion += 1
        }
        before = active
        queueActive = active
      }
      delay(if (queueActive > 0) 3000 else 8000)
    }
  }

  LaunchedEffect(Unit) {
    runCatching {
      val list = JSONObject(viewModel.runGatewayRpc(GatewayMethod.IdolMasterSourceList, "{}", timeoutMs = 30_000)).optJSONArray("presets")
      presets =
        (0 until (list?.length() ?: 0)).map { list!!.getJSONObject(it) }.map {
          IdolSourcePreset(it.optString("group"), it.optString("url"), it.optBoolean("readable", true), it.optString("note"))
        }
    }
  }

  val openGroup: String? =
    when (val v = view) {
      is MasterView.Group -> v.group
      is MasterView.Member -> v.group
      else -> null
    }
  LaunchedEffect(openGroup, tick) {
    if (openGroup == null) return@LaunchedEffect
    loadingMembers = true
    runCatching { fetchIdolMembers(viewModel, openGroup) }
      .onSuccess {
        members = it
        // 先頭の写真を、先に読んでおく（端末に保存して、次からは、すぐ出る）
        launch { runCatching { IdolPhotos.prefetch(viewModel, openGroup, it, 420) } }
      }
      .onFailure { memberMessage = "メンバーを読めませんでした：${it.message}" }
    loadingMembers = false
  }

  LaunchedEffect(members, pendingOpenId) {
    val id = pendingOpenId ?: return@LaunchedEffect
    val v = view as? MasterView.Group ?: return@LaunchedEffect
    val index = members.indexOfFirst { it.id == id }
    if (index >= 0) {
      pendingOpenId = null
      view = MasterView.Member(v.category, v.group, index)
    }
  }

  // スマホの写真を選ぶ（写真のない人の場所の長押し、または 1人のページのボタン）
  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      val target = photoTarget
      photoTarget = null
      if (uri == null || target == null || openGroup == null) return@rememberLauncherForActivityResult
      scope.launch {
        memberMessage = "写真を入れています…"
        val base64 = readPickedPhoto(context, uri)
        if (base64 == null) {
          memberMessage = "その画像を読めませんでした"
          return@launch
        }
        val asExtra = photoAsExtra
        photoAsExtra = false
        runCatching {
          if (asExtra) {
            viewModel.runGatewayRpc(
              GatewayMethod.IdolMasterPhotoExtras,
              JSONObject().put("group", openGroup).put("id", target.id).put("action", "add").put("mime", "image/jpeg").put("base64", base64).toString(),
              timeoutMs = 60_000,
            )
          } else {
            viewModel.runGatewayRpc(
              GatewayMethod.IdolMasterPhotoSet,
              JSONObject().put("group", openGroup).put("id", target.id).put("mime", "image/jpeg").put("base64", base64).toString(),
              timeoutMs = 60_000,
            )
          }
        }
          .onSuccess {
            memberMessage = if (asExtra) "「${target.name}」の追加の写真を入れました" else "「${target.name}」の写真を入れました"
            photoVersion += 1
            tick += 1
          }
          .onFailure { memberMessage = "写真を入れられませんでした：${it.message}" }
      }
    }
  // 写真を何枚かまとめて選ぶ。profileFirst=true：1枚目をプロフィールにして、残りはストック（追加写真）／false：全部ストック
  var manyTarget by remember { mutableStateOf<IdolMemberRow?>(null) }
  var manyProfileFirst by remember { mutableStateOf(false) }
  val manyPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
      val target = manyTarget
      val profileFirst = manyProfileFirst
      manyTarget = null
      manyProfileFirst = false
      val group = openGroup
      if (uris.isEmpty() || target == null || group == null) return@rememberLauncherForActivityResult
      scope.launch {
        var profile = 0
        var stocked = 0
        var failed = 0
        for ((i, uri) in uris.withIndex()) {
          memberMessage = "写真を入れています…（${i + 1} / ${uris.size}）"
          val base64 = readPickedPhoto(context, uri)
          if (base64 == null) {
            failed += 1
            continue
          }
          val asProfile = profileFirst && profile == 0
          runCatching {
            if (asProfile) {
              viewModel.runGatewayRpc(GatewayMethod.IdolMasterPhotoSet, JSONObject().put("group", group).put("id", target.id).put("mime", "image/jpeg").put("base64", base64).toString(), timeoutMs = 60_000)
            } else {
              viewModel.runGatewayRpc(GatewayMethod.IdolMasterPhotoExtras, JSONObject().put("group", group).put("id", target.id).put("action", "add").put("mime", "image/jpeg").put("base64", base64).toString(), timeoutMs = 60_000)
            }
          }
            .onSuccess { if (asProfile) profile += 1 else stocked += 1 }
            .onFailure { failed += 1 }
        }
        memberMessage = "「${target.name}」：" + listOfNotNull(
          if (profile > 0) "プロフィール${profile}枚" else null,
          if (stocked > 0) "ストック${stocked}枚" else null,
          if (failed > 0) "入れられなかった画像${failed}枚" else null,
        ).joinToString("、")
        photoVersion += 1
        tick += 1
      }
    }
  fun pickMany(member: IdolMemberRow, profileFirst: Boolean) {
    manyTarget = member
    manyProfileFirst = profileFirst
    manyPicker.launch("image/*")
  }
  val iconPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      val key = iconKeyPending
      iconKeyPending = null
      if (uri == null || key == null) return@rememberLauncherForActivityResult
      scope.launch {
        val base64 = readPickedPhoto(context, uri)
        message = if (base64 != null && FanBookIcons.saveFromBase64(context, key, base64)) "アイコンを設定しました" else "その画像を読めませんでした"
      }
    }
  fun pickPhoto(member: IdolMemberRow, extra: Boolean = false) {
    photoTarget = member
    photoAsExtra = extra
    photoPicker.launch("image/*")
  }

  // 他のアプリから共有された画像を、待っていた人の写真にする（1枚目＝モードどおり、2枚目以降＝追加の写真）
  LaunchedEffect(sharedUris) {
    val target = shareTarget ?: return@LaunchedEffect
    if (sharedUris.isEmpty()) return@LaunchedEffect
    val uris = FanBookShareInbox.consume()
    FanBookShareInbox.disarm()
    imageTarget = null // 画像検索の画面から出てきたときは、その画面を閉じる
    memberMessage = "共有された画像を入れています…"
    var saved = 0
    for ((i, uri) in uris.withIndex()) {
      val base64 = readPickedPhoto(context, uri) ?: continue
      val extra = target.asExtra || i > 0
      runCatching {
        if (extra) {
          viewModel.runGatewayRpc(GatewayMethod.IdolMasterPhotoExtras, JSONObject().put("group", target.group).put("id", target.id).put("action", "add").put("mime", "image/jpeg").put("base64", base64).toString(), timeoutMs = 60_000)
        } else {
          viewModel.runGatewayRpc(GatewayMethod.IdolMasterPhotoSet, JSONObject().put("group", target.group).put("id", target.id).put("mime", "image/jpeg").put("base64", base64).toString(), timeoutMs = 60_000)
        }
      }.onSuccess { saved += 1 }.onFailure { memberMessage = "入れられませんでした：${it.message}" }
    }
    if (saved > 0) memberMessage = "「${target.name}」に、共有された画像を${saved}枚入れました"
    photoVersion += 1
    tick += 1
  }

  fun runEdit(method: GatewayMethod, params: JSONObject, ok: String, after: () -> Unit = {}) {
    scope.launch {
      runCatching { viewModel.runGatewayRpc(method, params.toString(), timeoutMs = 60_000) }
        .onSuccess { message = ok; tick += 1; after() }
        .onFailure { message = "できませんでした：${it.message}" }
    }
  }

  // グループの中のグループ（サブグループ）は、親グループの中に出す。分類の一覧には、親だけを出し、人数は、中の分を足す
  fun tidyCategory(c: IdolCategoryRow): IdolCategoryRow {
    val kids = c.groups.filter { it.parent.isNotBlank() }.groupBy { it.parent }
    return c.copy(groups = c.groups.filter { it.parent.isBlank() }.map { g -> g.copy(count = g.count + (kids[g.group]?.sumOf { it.count } ?: 0)) })
  }

  fun rootOf(name: String): String = categories.firstOrNull { it.name == name }?.parent?.takeIf { it.isNotBlank() } ?: name
  val currentRoot: String =
    when (val v = view) {
      is MasterView.Category -> rootOf(v.name)
      is MasterView.Group -> rootOf(v.category)
      is MasterView.Member -> rootOf(v.category)
      MasterView.Home -> ""
    }
  val terms = fanBookTerms(currentRoot)

  fun goUp() {
    when (val v = view) {
      is MasterView.Member -> view = MasterView.Group(v.category, v.group)
      is MasterView.Group -> {
        val parentGroup = categories.firstOrNull { it.name == v.category }?.groups?.firstOrNull { it.group == v.group }?.parent.orEmpty()
        view = if (parentGroup.isNotBlank()) MasterView.Group(v.category, parentGroup) else MasterView.Category(v.category)
        members = emptyList()
        query = ""
        selected = emptySet()
        memberMessage = ""
      }
      is MasterView.Category -> {
        val parent = categories.firstOrNull { it.name == v.name }?.parent.orEmpty()
        view = if (parent.isNotBlank()) MasterView.Category(parent) else MasterView.Home
      }
      MasterView.Home -> onBack()
    }
  }
  BackHandler(enabled = view != MasterView.Home) { goUp() }

  fun createMembers(group: String, targets: List<Pair<IdolMemberRow, String>>, photos: FanBookCreatePhotos? = null) {
    if (busy || targets.isEmpty()) return
    busy = true
    memberMessage = ""
    scope.launch {
      var added = 0
      var existing = 0
      var attached = 0
      var failed = ""
      for ((member, name) in targets) {
        val params = JSONObject().put("group", group).put("id", member.id).put("name", name)
        // 画像を選んだとき（1人のページ）はその画像、選ばないとき（まとめて作る）は、メインの写真を引き継ぐ
        if (photos != null) {
          val list = org.json.JSONArray()
          if (photos.useMain) list.put(JSONObject().put("kind", "main"))
          photos.extras.forEach { list.put(JSONObject().put("kind", "extra").put("index", it)) }
          params.put("photos", list).put("makeSheet", photos.makeSheet)
        }
        runCatching { JSONObject(viewModel.runGatewayRpc(GatewayMethod.IdolMasterImport, params.toString(), timeoutMs = 120_000)) }
          .onSuccess { attached += it.optInt("photosAttached"); if (it.optBoolean("existing")) existing += 1 else added += 1 }
          .onFailure { failed = "「$name」を作れませんでした：${it.message}" }
      }
      memberMessage =
        buildString {
          if (targets.size == 1 && added + existing == 1) append(if (existing == 1) "「${targets[0].second}」は、すでに図鑑にあります" else "「${targets[0].second}」を、キャラ図鑑に追加しました")
          else append("キャラ図鑑に、${added}人を追加しました" + if (existing > 0) "（すでにあった人：${existing}人）" else "")
          if (attached > 0) append("（写真${attached}枚を引き継ぎました）")
          if (failed.isNotBlank()) append("　$failed")
        }
      selected = emptySet()
      busy = false
    }
  }

  val namesVersion = FanBookNames.version
  fun shown(key: String, original: String): String = if (namesVersion >= 0) FanBookNames.label(context, key) ?: original else original
  val titleText =
    when (val v = view) {
      MasterView.Home -> "🎤 マスターデータ"
      is MasterView.Category -> "📖 ${shown("category:${v.name}", v.name)}"
      is MasterView.Group -> "📖 ${shown("group:${v.group}", v.group)}"
      is MasterView.Member -> "📖 ${shown("group:${v.group}", v.group)}"
    }

  ClawScaffold(contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 6.dp)) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = { goUp() })
        Text(text = titleText, style = ClawTheme.type.title, color = ClawTheme.colors.text, modifier = Modifier.weight(1f).padding(start = 8.dp))
        // 一覧の設定（並べ方・タグ・説明）は、ふだんは閉じておく。右上の歯車で開く
        if (view is MasterView.Category || view is MasterView.Group) {
          ClawIconButton(icon = Icons.Filled.Settings, contentDescription = if (viewSettings) "設定を閉じる" else "設定を開く", onClick = { viewSettings = !viewSettings })
        }
      }
      if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      when (val v = view) {
        MasterView.Home -> {
          LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
              ClawPanel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("ファンブック（名簿 ${groups.sumOf { it.count }}人）", style = ClawTheme.type.body, color = ClawTheme.colors.text)
                  Text("分類を押すと、全画面で、グループ → 名前（写真つき）→ 1人のページの順に見られます。見ながら、キャラ図鑑に作る人を選べます。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                  if (categories.isEmpty()) Text("読み込み中…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                  Text("長押しすると、並び替え・大項目の中に入れる・名前を変える・削除ができます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
                  FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    categories.filter { it.parent.isBlank() }.forEach { category ->
                      val inner = categories.filter { it.parent == category.name }
                      val people = category.groups.sumOf { it.count } + inner.sumOf { c -> c.groups.sumOf { it.count } }
                      val groupCount = category.groups.size + inner.sumOf { it.groups.size }
                      FanBookTile(
                        title = "📖 ${category.name}",
                        subtitle = "${people}人・${groupCount}グループ" + if (inner.isNotEmpty()) "・${inner.size}分類" else "",
                        hint = "開く",
                        onClick = { view = MasterView.Category(category.name) },
                        onLongClick = { categoryMenu = category.name },
                        modifier = Modifier.width(170.dp),
                        onBefore = if (homeReorder) ({ runEdit(GatewayMethod.IdolMasterCategoryEdit, JSONObject().put("name", category.name).put("action", "move").put("direction", -1), "「${category.name}」を動かしました") }) else null,
                        onAfter = if (homeReorder) ({ runEdit(GatewayMethod.IdolMasterCategoryEdit, JSONObject().put("name", category.name).put("action", "move").put("direction", 1), "「${category.name}」を動かしました") }) else null,
                      )
                    }
                    ClawSecondaryButton(text = "＋ 分類を追加", onClick = { addCategoryOpen = true })
                    ClawSecondaryButton(text = if (homeReorder) "✔ 並び替えを終える" else "↕ 並び替え", onClick = { homeReorder = !homeReorder })
                    ClawSecondaryButton(text = "💾 バックアップと復元", onClick = { backupOpen = true })
                    ClawSecondaryButton(text = "🧹 重複の整理", onClick = { duplicatesOpen = true })
                    ClawSecondaryButton(text = "🔗 ファンブックを合体", onClick = { mergeOpen = true })
                    ClawSecondaryButton(text = if (watchCount > 0) "🔔 メンバーの変化（${watchCount}件）" else "🔔 メンバーの変化", onClick = { watchOpen = true })
                    ClawSecondaryButton(text = if (queueActive > 0) "📥 取り込みの状況（実行中・待機 ${queueActive}件）" else "📥 取り込みの状況", onClick = { queueOpen = true })
                    ClawSecondaryButton(text = "🌐 Googleでサイト検索（アプリの中で開く）", onClick = { siteSearch = SiteSearchStart(null, null) })
                  }
                }
              }
            }
            item {
              ClawPanel {
                FanBookSearchPanel(
                  viewModel = viewModel,
                  onOpen = { group, id ->
                    val cat = categories.firstOrNull { c -> c.groups.any { it.group == group } }?.name ?: "その他"
                    members = emptyList()
                    pendingOpenId = id
                    view = MasterView.Group(cat, group)
                  },
                )
              }
            }
            item {
              ClawPanel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("名簿を補う（空の項目だけ。いまの値は上書きしません）", style = ClawTheme.type.body, color = ClawTheme.colors.text)
                  Text("どれも、先に「調べる」だけを行い、結果を見てから「反映する」を押す2段階です。反映の前に、名簿を自動でバックアップします。URL を入れる必要は、ありません。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                  ClawSecondaryButton(text = "AKB48 の公式サイトから取り込む（誕生日・期・愛称・出身地・写真）", onClick = { officialOpen = true })
                  ClawSecondaryButton(text = "Wikidata でまとめて補う（名前から探す）", onClick = { bulkOpen = true })
                  ClawSecondaryButton(text = "Wikidata で1人探して、名簿に入れる", onClick = { searchOpen = true })
                  Text("グループごとに補うときは、グループの一覧にある「このグループを補う」が、手軽です。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
                }
              }
            }
            item {
              ClawPanel {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text("写真を入れるには", style = ClawTheme.type.body, color = ClawTheme.colors.text)
                  Text("グループの一覧で、写真のない人の写真の場所を長押しすると、スマホの写真を選んで入れられます。1人のページの「📷 写真を入れる」でも、入れられます。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                  val base = if (rosterFile.isNotBlank()) rosterFile.substringBeforeLast('\\').ifBlank { rosterFile } else "（PC の）.openclaw\\media\\idol-master"
                  Text("PC に直接入れる場合: $base\\photos\\<グループ名>\\<名前>.jpg", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
                }
              }
            }
          }
        }
        is MasterView.Category -> {
          val category = categories.firstOrNull { it.name == v.name }
          if (category == null) Text("分類が見つかりません", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          else ClawSecondaryButton(text = "🔎 検索して、グループを足す（データベース＋Google）", onClick = { webAdd = WebAddTarget(v.name, null) }, modifier = Modifier.fillMaxWidth())
          if (category != null) FanBookCategoryView(
            category = tidyCategory(category),
            subCategories = categories.filter { it.parent == v.name },
            columns = columns,
            onColumns = { n -> columns = n; writeFanBookColumns(context, n) },
            onOpenGroup = { group -> view = MasterView.Group(v.name, group) },
            onGroupMenu = { group -> groupMenu = group },
            onOpenCategory = { name -> view = MasterView.Category(name) },
            onCategoryMenu = { name -> categoryMenu = name },
            onAddGroup = { addGroupOpen = true },
            onCatalog = { catalogGroup = null; catalogCategory = v.name },
            terms = terms,
            onMoveGroup = { group, direction -> runEdit(GatewayMethod.IdolMasterGroupEdit, JSONObject().put("group", group).put("action", "move").put("direction", direction), "「$group」を動かしました") },
            onMoveCategory = { name, direction -> runEdit(GatewayMethod.IdolMasterCategoryEdit, JSONObject().put("name", name).put("action", "move").put("direction", direction), "「$name」を動かしました") },
            showSettings = viewSettings,
            onMoveGroupTo = { group, index -> runEdit(GatewayMethod.IdolMasterGroupEdit, JSONObject().put("group", group).put("action", "moveTo").put("index", index), "「$group」を動かしました") },
          )
        }
        is MasterView.Group -> {
          ClawSecondaryButton(text = "🔎 検索して、メンバーを足す（データベース＋Google）", onClick = { webAdd = WebAddTarget(v.category, v.group) }, modifier = Modifier.fillMaxWidth())
          run {
            val inCategory = categories.firstOrNull { it.name == v.category }?.groups.orEmpty()
            val me = inCategory.firstOrNull { it.group == v.group }
            val kids = inCategory.filter { it.parent == v.group }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
              if (me != null && me.parent.isNotBlank()) ClawSecondaryButton(text = "↑ 「${shown("group:${me.parent}", me.parent)}」へ", onClick = { query = ""; selected = emptySet(); memberMessage = ""; view = MasterView.Group(v.category, me.parent) })
              kids.forEach { k -> ClawSecondaryButton(text = "📂 ${shown("group:${k.group}", k.group)}（${k.count}人）", onClick = { query = ""; selected = emptySet(); memberMessage = ""; view = MasterView.Group(v.category, k.group) }) }
              if (me == null || me.parent.isBlank()) ClawSecondaryButton(text = "＋ サブグループを足す", onClick = { addSubOpen = true })
            }
          }
          FanBookGroupView(
            viewModel = viewModel,
            group = v.group,
            members = members,
            loading = loadingMembers,
            query = query,
            onQuery = { query = it },
            selected = selected,
            onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
            onOpen = { index -> memberMessage = ""; view = MasterView.Member(v.category, v.group, index) },
            onCreateSelected = { createMembers(v.group, members.filter { it.id in selected }.map { it to it.name }) },
            busy = busy,
            photoVersion = photoVersion,
            onPickPhoto = { member -> pickPhoto(member) },
            onSearchImage = { member -> imageTarget = member },
            onPhotoFromUrl = { member, extra -> urlPhotoTarget = member to extra },
            onPickMany = { member, profileFirst -> pickMany(member, profileFirst) },
            onAddMember = { addMemberOpen = true },
            onFill = { fillGroup = v.group },
            onSource = { sourceUrl = ""; sourceGroup = v.group },
            columns = columns,
            onColumns = { n -> columns = n; writeFanBookColumns(context, n) },
            onCatalog = { catalogGroup = v.group; catalogCategory = v.category },
            onDeleteMembers = { list ->
              scope.launch {
                var failed = 0
                for (one in list) {
                  runCatching { viewModel.runGatewayRpc(GatewayMethod.IdolMasterMemberRemove, JSONObject().put("group", v.group).put("id", one.id).toString(), timeoutMs = 30_000) }.onFailure { failed += 1 }
                }
                selected = emptySet()
                memberMessage = "${list.size - failed}人を、ファンブックから消しました" + if (failed > 0) "（消せなかった人：${failed}人）" else ""
                tick += 1
              }
            },
            terms = terms,
            showSettings = viewSettings,
            official = presets.firstOrNull { it.group.equals(v.group, ignoreCase = true) },
            onOfficial = { sourceUrl = presets.firstOrNull { it.group.equals(v.group, ignoreCase = true) }?.url.orEmpty(); sourceGroup = v.group },
          )
          if (memberMessage.isNotBlank()) Text(memberMessage, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
        }
        is MasterView.Member -> {
          FanBookMemberView(
            viewModel = viewModel,
            group = v.group,
            members = members,
            index = v.index,
            onIndex = { index -> memberMessage = ""; view = MasterView.Member(v.category, v.group, index) },
            busy = busy,
            message = memberMessage,
            photoVersion = photoVersion,
            onPickPhoto = { member -> pickPhoto(member) },
            onCreate = { member, name, photos -> createMembers(v.group, listOf(member to name), photos) },
            onSearchImage = { member -> imageTarget = member },
            onResearch = { member -> researchTarget = member },
            onPhotoFromUrl = { member, extra -> urlPhotoTarget = member to extra },
            onPickMany = { member, profileFirst -> pickMany(member, profileFirst) },
            onAddExtraFromPhone = { member -> pickPhoto(member, extra = true) },
            onShareToPhoto = { member, extra -> FanBookShareInbox.arm(FanBookShareInbox.Target(v.group, member.id, member.name, extra)) },
            shareWaiting = shareTarget?.let { it.group == v.group && it.id == members.getOrNull(v.index)?.id } == true,
            onCancelShare = { FanBookShareInbox.disarm() },
            onNote = { text -> memberMessage = text; photoVersion += 1; tick += 1 },
            onDelete = { member ->
              runEdit(GatewayMethod.IdolMasterMemberRemove, JSONObject().put("group", v.group).put("id", member.id), "「${member.name}」を、ファンブックから消しました") {
                view = MasterView.Group(v.category, v.group)
              }
            },
          )
        }
      }
    }
  }

  val menuGroup = groupMenu
  if (menuGroup != null) {
    FanBookGroupMenu(
      group = menuGroup,
      onDismiss = { groupMenu = null },
      onMove = { direction -> runEdit(GatewayMethod.IdolMasterGroupEdit, JSONObject().put("group", menuGroup).put("action", "move").put("direction", direction), "「$menuGroup」を動かしました") },
      onChangeCategory = { groupMenu = null; moving = menuGroup },
      hasIcon = FanBookIcons.has(context, "group:$menuGroup"),
      onPickIcon = { iconKeyPending = "group:$menuGroup"; groupMenu = null; iconPicker.launch("image/*") },
      onClearIcon = { FanBookIcons.clear(context, "group:$menuGroup"); groupMenu = null; message = "アイコンをやめました" },
      label = FanBookNames.label(context, "group:$menuGroup").orEmpty(),
      onLabel = { text -> if (text.isBlank()) FanBookNames.clear(context, "group:$menuGroup") else FanBookNames.set(context, "group:$menuGroup", text); groupMenu = null; message = "表示の名前を変えました（この端末だけ）" },
      onDelete = { groupMenu = null; runEdit(GatewayMethod.IdolMasterGroupEdit, JSONObject().put("group", menuGroup).put("action", "remove"), "「$menuGroup」を、ファンブックから消しました") },
    )
  }
  val menuCategory = categoryMenu
  if (menuCategory != null) {
    val entry = categories.firstOrNull { it.name == menuCategory }
    val fixed = menuCategory in setOf("歌手", "アイドル", "アニメ", "アダルトアニメ", "俳優", "グラビアアイドル", "AV女優", "モデル", "地下アイドル", "Vチューバー", "YouTuber", "その他")
    FanBookCategoryMenu(
      name = menuCategory,
      fixed = fixed,
      parent = entry?.parent.orEmpty(),
      topNames = categories.filter { it.parent.isBlank() }.map { it.name },
      hasIcon = FanBookIcons.has(context, "category:$menuCategory"),
      onPickIcon = { iconKeyPending = "category:$menuCategory"; categoryMenu = null; iconPicker.launch("image/*") },
      onClearIcon = { FanBookIcons.clear(context, "category:$menuCategory"); categoryMenu = null; message = "アイコンをやめました" },
      label = FanBookNames.label(context, "category:$menuCategory").orEmpty(),
      onLabel = { text -> if (text.isBlank()) FanBookNames.clear(context, "category:$menuCategory") else FanBookNames.set(context, "category:$menuCategory", text); categoryMenu = null; message = "表示の名前を変えました（この端末だけ）" },
      onDismiss = { categoryMenu = null },
      onMove = { direction -> runEdit(GatewayMethod.IdolMasterCategoryEdit, JSONObject().put("name", menuCategory).put("action", "move").put("direction", direction), "「$menuCategory」を動かしました") },
      onParent = { top ->
        categoryMenu = null
        runEdit(GatewayMethod.IdolMasterCategoryEdit, JSONObject().put("name", menuCategory).put("action", "parent").put("value", top), if (top.isBlank()) "「$menuCategory」を一番上に出しました" else "「$menuCategory」を「$top」の中に入れました") { view = MasterView.Home }
      },
      onRename = { newName ->
        categoryMenu = null
        runEdit(GatewayMethod.IdolMasterCategoryEdit, JSONObject().put("name", menuCategory).put("action", "rename").put("value", newName), "名前を「$newName」に変えました") { view = MasterView.Home }
      },
      onDelete = {
        categoryMenu = null
        runEdit(GatewayMethod.IdolMasterCategoryEdit, JSONObject().put("name", menuCategory).put("action", "remove"), "分類「$menuCategory」を消しました") { view = MasterView.Home }
      },
    )
  }
  val movingGroup = moving
  if (movingGroup != null) {
    IdolCategoryMoveDialog(
      group = movingGroup,
      categories = (categories.map { it.name } + listOf("その他")).distinct(),
      onDismiss = { moving = null },
      onChoose = { name ->
        moving = null
        scope.launch {
          runCatching { viewModel.runGatewayRpc(GatewayMethod.IdolMasterCategorySet, JSONObject().put("group", movingGroup).put("category", name).toString(), timeoutMs = 30_000) }
            .onSuccess { message = "「$movingGroup」を「$name」に入れました"; tick += 1; view = MasterView.Home }
            .onFailure { message = "分類を変えられませんでした：${it.message}" }
        }
      },
    )
  }
  if (addCategoryOpen) {
    FanBookNameDialog(
      title = "分類を追加",
      hint = "「48グループ」「坂道シリーズ」のような、グループをまとめる箱です。",
      label = "分類の名前（例: K-POP）",
      confirmText = "追加する",
      onDismiss = { addCategoryOpen = false },
      onConfirm = { name ->
        addCategoryOpen = false
        scope.launch {
          runCatching { viewModel.runGatewayRpc(GatewayMethod.IdolMasterCategoryAdd, JSONObject().put("name", name).toString(), timeoutMs = 30_000) }
            .onSuccess { message = "分類「$name」を追加しました"; tick += 1 }
            .onFailure { message = "追加できませんでした：${it.message}" }
        }
      },
    )
  }
  val currentCategory = (view as? MasterView.Category)?.name
  val subParent = view as? MasterView.Group
  if (addSubOpen && subParent != null) {
    FanBookNameDialog(
      title = "「${shown("group:${subParent.group}", subParent.group)}」の中に、サブグループを足す",
      hint = "細かく分けたい名前を入れます（例: チームA）。メンバーは、足したサブグループの中で、足せます。",
      label = "サブグループの名前",
      confirmText = "足す",
      onDismiss = { addSubOpen = false },
      onConfirm = { name ->
        addSubOpen = false
        scope.launch {
          runCatching { viewModel.runGatewayRpc(GatewayMethod.IdolMasterGroupAdd, JSONObject().put("category", subParent.category).put("group", name).put("parent", subParent.group).toString(), timeoutMs = 30_000) }
            .onSuccess { message = "サブグループ「$name」を足しました"; memberMessage = message; tick += 1 }
            .onFailure { message = "足せませんでした：${it.message}"; memberMessage = message }
        }
      },
    )
  }
  webAdd?.let { target ->
    FanBookSearchHubDialog(
      target = target,
      onDatabase = { catalogGroup = target.group; catalogCategory = target.category; webAdd = null },
      onSite = { siteSearch = SiteSearchStart(target.category, target.group); webAdd = null },
      onDismiss = { webAdd = null },
    )
  }
  siteSearch?.let { start ->
    FanBookSiteSearchDialog(
      viewModel = viewModel,
      categories = categories,
      start = start,
      onDismiss = { siteSearch = null; tick += 1; photoVersion += 1 },
      onChanged = { text -> siteSearch = null; message = text; memberMessage = text; tick += 1; photoVersion += 1 },
    )
  }
  if (addGroupOpen && currentCategory != null) {
    FanBookNameDialog(
      title = "「$currentCategory」にグループを追加",
      hint = "グループの名前を入れます（日本語でも大丈夫）。メンバーは、追加したグループの中で、足せます。",
      label = "グループの名前（例: 乃木坂46）",
      confirmText = "追加する",
      onDismiss = { addGroupOpen = false },
      onConfirm = { name ->
        addGroupOpen = false
        scope.launch {
          runCatching { viewModel.runGatewayRpc(GatewayMethod.IdolMasterGroupAdd, JSONObject().put("category", currentCategory).put("group", name).toString(), timeoutMs = 30_000) }
            .onSuccess { message = "グループ「$name」を追加しました"; tick += 1 }
            .onFailure { message = "追加できませんでした：${it.message}" }
        }
      },
    )
  }
  if (addMemberOpen && openGroup != null) {
    FanBookAddMemberDialog(
      group = openGroup,
      onDismiss = { addMemberOpen = false },
      onConfirm = { name, nickname, generation, birthday, hometown, description ->
        addMemberOpen = false
        scope.launch {
          runCatching {
            viewModel.runGatewayRpc(
              GatewayMethod.IdolMasterMemberAdd,
              JSONObject().put("group", openGroup).put("name", name).put("nickname", nickname).put("generation", generation).put("birthday", birthday).put("hometown", hometown).put("description", description).toString(),
              timeoutMs = 30_000,
            )
          }
            .onSuccess { memberMessage = "「$name」を追加しました"; tick += 1 }
            .onFailure { memberMessage = "追加できませんでした：${it.message}" }
        }
      },
    )
  }
  if (bulkOpen || fillGroup != null) {
    IdolWikidataBulkDialog(
      viewModel = viewModel,
      groups = groups.map { it.group },
      initialGroup = fillGroup,
      onDismiss = { bulkOpen = false; fillGroup = null; tick += 1 },
      onApplied = { text -> message = text; memberMessage = text; tick += 1 },
    )
  }
  if (mergeOpen) {
    FanBookMergeDialog(
      viewModel = viewModel,
      groups = groups.map { it.group },
      categories = categories.map { it.name },
      onDismiss = { mergeOpen = false },
      onDone = { text -> mergeOpen = false; message = text; memberMessage = text; tick += 1; photoVersion += 1 },
    )
  }
  if (queueOpen) {
    IdolSourceQueueDialog(viewModel = viewModel, onDismiss = { queueOpen = false; tick += 1; photoVersion += 1 })
  }
  val sourceOf = sourceGroup
  if (sourceOf != null) {
    IdolSourceDialog(
      viewModel = viewModel,
      group = sourceOf,
      initialUrl = sourceUrl,
      onDismiss = { sourceGroup = null; sourceUrl = ""; tick += 1; photoVersion += 1 },
      onApplied = { text -> memberMessage = text; message = text; tick += 1; photoVersion += 1 },
    )
  }
  val imageOf = imageTarget
  if (imageOf != null && openGroup != null) {
    IdolImageSearchDialog(
      viewModel = viewModel,
      group = openGroup,
      member = imageOf,
      onDismiss = { imageTarget = null; FanBookShareInbox.disarm() },
      onSaved = { text -> imageTarget = null; FanBookShareInbox.disarm(); memberMessage = text; photoVersion += 1; tick += 1 },
    )
  }
  val urlPhoto = urlPhotoTarget
  if (urlPhoto != null && openGroup != null) {
    val (target, extra) = urlPhoto
    FanBookNameDialog(
      title = if (extra) "「${target.name}」の追加の写真（URL）" else "「${target.name}」の写真（URL）",
      hint = "画像そのもののアドレス、または、その人が載っているページのアドレス（https://…）を、貼ってください。ページのときは、そのページの写真を使います（ページの決まりと、ボット対策は、守ります）。",
      label = "画像・ページの URL",
      confirmText = "写真にする",
      onDismiss = { urlPhotoTarget = null },
      onConfirm = { url ->
        urlPhotoTarget = null
        memberMessage = "URL から写真を取っています…"
        scope.launch {
          runCatching { viewModel.runGatewayRpc(GatewayMethod.IdolMasterPhotoFromUrl, JSONObject().put("group", openGroup).put("id", target.id).put("url", url).put("asExtra", extra).toString(), timeoutMs = 120_000) }
            .onSuccess { memberMessage = if (extra) "「${target.name}」の追加の写真を入れました" else "「${target.name}」の写真を入れました"; photoVersion += 1; tick += 1 }
            .onFailure { memberMessage = "写真にできませんでした：${it.message}" }
        }
      },
    )
  }
  val researchOf = researchTarget
  if (researchOf != null && openGroup != null) {
    FanBookResearchDialog(
      viewModel = viewModel,
      group = openGroup,
      member = researchOf,
      onDismiss = { researchTarget = null },
      onDone = { text -> researchTarget = null; memberMessage = text; tick += 1 },
    )
  }
  if (watchOpen) {
    FanBookWatchDialog(
      viewModel = viewModel,
      onDismiss = { watchOpen = false; tick += 1 },
      onChanged = { text -> message = text; tick += 1 },
    )
  }
  if (duplicatesOpen) {
    FanBookDuplicatesDialog(
      viewModel = viewModel,
      onDismiss = { duplicatesOpen = false },
      onMerged = { text -> message = text; tick += 1; photoVersion += 1 },
    )
  }
  if (backupOpen) {
    FanBookBackupDialog(
      viewModel = viewModel,
      onDismiss = { backupOpen = false },
      onRestored = { text -> backupOpen = false; message = text; view = MasterView.Home; tick += 1; photoVersion += 1 },
    )
  }
  val catalogOf = catalogCategory
  if (catalogOf != null) {
    IdolCatalogDialog(
      viewModel = viewModel,
      category = catalogOf,
      terms = fanBookTerms(rootOf(catalogOf)),
      defaultGroup = catalogGroup ?: categories.firstOrNull { it.name == catalogOf }?.groups?.firstOrNull()?.group ?: catalogOf,
      onDismiss = { catalogCategory = null; catalogGroup = null; tick += 1; photoVersion += 1 },
      onAdded = { text -> message = text; tick += 1; photoVersion += 1 },
    )
  }
  if (officialOpen) {
    IdolOfficialDialog(
      viewModel = viewModel,
      group = "AKB48",
      onDismiss = { officialOpen = false; tick += 1; photoVersion += 1 },
      onApplied = { text -> message = text; tick += 1; photoVersion += 1 },
    )
  }
  if (searchOpen) {
    IdolWikidataDialog(
      viewModel = viewModel,
      onDismiss = { searchOpen = false },
      onAdded = { text -> searchOpen = false; message = text; tick += 1 },
    )
  }
}
