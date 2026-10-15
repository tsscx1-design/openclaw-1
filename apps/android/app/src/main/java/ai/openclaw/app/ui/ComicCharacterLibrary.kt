package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.convertUriToBase64DataUrl
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class LibraryChara(
  val id: String,
  val name: String,
  val look: String,
  val profile: String,
  val sheetJobId: String,
  val hasSheet: Boolean,
  val icon: Bitmap?,
  val loraStatus: String,
  val loraStep: String,
  val loraError: String,
  val trained: Boolean,
  val series: List<Pair<String, String>>,
  val specText: String,
  val turnaroundJobId: String,
  /** The setting sheet as stored (key -> text), for the editor. */
  val spec: Map<String, String> = emptyMap(),
  val motionProfile: Map<String, String> = emptyMap(),
  /** Her voice in motion comics: a voice-studio voice id, or a description. */
  val voiceId: String = "",
  val voiceCaption: String = "",
  /** Outfits as "名前: tags" lines. */
  val outfitsText: String = "",
  /** お気に入り登録済み（ホームに表示） */
  val isFavorite: Boolean = false,
  /** 図鑑のフォルダ（空 = 未分類）と、その中での順番（小さいほど先） */
  val folderId: String = "",
  val libraryOrder: Int = 0,
  /** このキャラに紐づくパートナーID（図鑑→パートナー同期） */
  val partnerId: String = "",
  /** 多人数シーン用: このキャラの登場人数設定（2〜4） */
  val sceneSlot: Int = 0,
  /** パートナー用の詳細シートの入力（項目名 -> 内容） */
  val sheetValues: Map<String, String> = emptyMap(),
  /** 設定画を描くときの絵のモデル（空ならゲートウェイの既定） */
  val imageModel: String = "",
  /** チャット画面のアバター（表情の絵）が作ってあるか */
  val hasAvatar: Boolean = false,
)

/** 絵のモデル（ImageModelCatalog.kt の全モデル）。 */
internal val LIBRARY_IMAGE_MODELS: List<Pair<String, String>> = IMAGE_MODELS_FLAT

/** The setting sheet's fields, grouped as shown in the editor (keys match the gateway's spec). */
internal val SPEC_GROUPS: List<Pair<String, List<Pair<String, String>>>> =
  listOf(
    "基本" to listOf(
      "gender" to "性別", "age" to "年齢", "heightCm" to "身長（cm）", "body" to "体型",
    ),
    "見た目（顔・髪・体）" to listOf(
      "hair" to "髪の色・長さ", "hairstyle" to "髪型（ツインテール・ポニーテールなど）",
      "eyes" to "目の色・形", "contactLens" to "カラコン・コンタクトの色",
      "faceType" to "顔の印象（童顔・大人びた・冷たい系・やさしい系など）",
      "skin" to "肌の色合い", "breastSize" to "胸のサイズ・形", "waistHip" to "ウエスト・ヒップラインの印象",
      "nippleShape" to "乳首の形",
      "nippleColor" to "乳首の色（ピンク・薄い色・濃い色・褐色など）",
      "features" to "特徴（エルフ耳・しっぽ・角・アホゲなど）",
      "bodyMarks" to "タトゥー・ほくろ・傷", "fragrance" to "香り",
      "legs" to "脚（細い・すらっと長い・むっちりなど）", "hips" to "お尻（小さめ・大きめ・豊満など）",
      "muscle" to "筋肉・引き締まり", "neckArms" to "首・肩・腕",
    ),
    "服装・アクセサリー" to listOf(
      "outfit" to "服装（トップス・ボトムス・ワンピースなど）",
      "underwear" to "下着（色・素材・デザイン）",
      "legwear" to "靴下・ニーハイ・ストッキング・ガーターベルト",
      "shoes" to "靴・ヒール・ブーツ",
      "accessories" to "アクセサリー（眼鏡・首輪・イヤリング・こうもりなど）",
      "nails" to "ネイル（色・デザイン）",
      "makeup" to "メイク・化粧（地雷系・ギャル・清楚系など）",
      "lipColor" to "口紅の色・リップ",
    ),
    "内面・プロフィール" to listOf(
      "personality" to "性格", "speech" to "口調", "firstPerson" to "一人称（わたし・僕・ウチ…）",
      "catchphrase" to "口癖", "likes" to "好きなもの", "dislikes" to "苦手なもの",
      "favoriteFood" to "好きな食べ物・飲み物",
      "occupation" to "職業・立場", "hobbies" to "趣味",
      "livingPlace" to "住んでいる場所", "skills" to "特技",
      "secret" to "秘密", "relations" to "関係・呼び方",
      "treasured" to "一番大切なもの・宝物",
      "background" to "生い立ち・設定",
    ),
    "成人向け（モーションコミック・成人向けの連載で使う）" to listOf(
      "adultLikes" to "好きなこと・好きな体位",
      "weakSpots" to "弱いところ・感じやすいところ",
      "kinks" to "性癖", "dominance" to "攻め・受け",
      "experience" to "性経験・経験度",
      "preferredPlay" to "好きなプレイの種類（SM・調教・バニラなど）",
      "breathPlay" to "首絞め・腹部圧迫・息詰めプレイの詳細",
      "adultUnderwear" to "ランジェリー・部屋着",
      "moanStyle" to "喘ぎ方・声の出し方",
      "climaxStyle" to "絶頂の仕方",
      "afterPlay" to "事後・余韻の様子",
      "afterWords" to "事後に言うこと・言葉",
      "secretKink" to "本当は好きだけど言えない性癖",
      "adultNg" to "苦手なこと・NG",
      "expressionPresets" to "表情差分プリセット（通常・照れ・アヘ顔・泣き顔・怒り顔 など、カンマ区切りで）",
    ),
    "禁止タグ（画像・動画生成の追加NG）" to listOf(
      "customNegative" to "追加の禁止タグ（英語・カンマ区切り）",
    ),
  )


internal fun parseLibrary(arr: JSONArray?): List<LibraryChara> {
  val a = arr ?: return emptyList()
  return (0 until a.length()).map { i ->
    val o = a.getJSONObject(i)
    val iconB64 = o.optString("iconBase64")
    val ser = o.optJSONArray("series") ?: JSONArray()
    LibraryChara(
      id = o.optString("id"),
      name = o.optString("name"),
      look = o.optString("look"),
      profile = o.optString("profile"),
      sheetJobId = o.optString("sheetJobId"),
      hasSheet = o.optBoolean("hasSheet"),
      icon =
        if (iconB64.isBlank()) null
        else runCatching { Base64.decode(iconB64, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull(),
      loraStatus = o.optString("loraStatus", "none"),
      loraStep = o.optString("loraStep"),
      loraError = o.optString("loraError"),
      trained = o.optBoolean("trained"),
      isFavorite = o.optBoolean("isFavorite", false),
      folderId = o.optString("folderId"),
      libraryOrder = o.optInt("libraryOrder"),
      partnerId = o.optString("partnerId", ""),
      series = (0 until ser.length()).map { ser.getJSONObject(it).let { s -> s.optString("id") to s.optString("title") } },
      specText = o.optString("specText"),
      turnaroundJobId = o.optString("turnaroundJobId"),
      spec =
        o.optJSONObject("spec")?.let { s -> s.keys().asSequence().associateWith { k -> s.opt(k)?.toString().orEmpty() } }
          ?: emptyMap(),
      voiceId = o.optString("voiceId"),
      voiceCaption = o.optString("voiceCaption"),
      imageModel = o.optString("imageModel", ""),
      hasAvatar = o.optBoolean("hasAvatar", false),
      motionProfile = o.optJSONObject("motionProfile")?.let { s -> s.keys().asSequence().associateWith { k -> s.opt(k)?.toString().orEmpty() } } ?: emptyMap(),
      sheetValues =
        o.optJSONObject("sheetValues")?.let { s -> s.keys().asSequence().associateWith { k -> s.opt(k)?.toString().orEmpty() } }
          ?: emptyMap(),
      outfitsText =
        (o.optJSONArray("outfits") ?: JSONArray()).let { arr ->
          (0 until arr.length()).joinToString("\n") { i -> arr.getJSONObject(i).let { "${it.optString("name")}: ${it.optString("tags")}" } }
        },
    )
  }
}

/** Round face picture; the first letter of the name when there is no icon yet. */
@Composable
internal fun CharaIcon(chara: LibraryChara?, name: String, size: Int) {
  val icon = chara?.icon
  if (icon != null) {
    Image(
      bitmap = icon.asImageBitmap(),
      contentDescription = name,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(size.dp).clip(CircleShape),
    )
  } else {
    Box(
      modifier = Modifier.size(size.dp).clip(CircleShape).background(ClawTheme.colors.surfaceRaised),
      contentAlignment = Alignment.Center,
    ) {
      Text(text = name.take(1), style = ClawTheme.type.title.copy(fontSize = (size / 2.6).sp), color = ClawTheme.colors.textMuted)
    }
  }
}

internal fun loraBadge(c: LibraryChara): String =
  when {
    c.trained && c.loraStatus == "ready" -> "🎯 専用学習済み"
    c.loraStatus == "training" -> "⏳ ${c.loraStep.ifBlank { "学習中" }}"
    c.loraStatus == "queued" -> "⏳ 学習の順番待ち"
    c.loraStatus == "failed" -> "⚠️ 学習に失敗"
    c.hasSheet -> "設定画あり・未学習"
    else -> "設定画なし"
  }

/** The character library: everyone made for any comic, with icon, sheet and trained LoRA. */
@Composable
internal fun ComicCharacterLibrary(
  viewModel: MainViewModel,
  characters: List<LibraryChara>,
  reload: suspend () -> Unit,
  loadImage: suspend (String) -> Bitmap?,
  track: (JSONObject) -> Unit,
  candidateSheets: (String) -> List<String>,
) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var openId by remember { mutableStateOf<String?>(null) }
  var status by remember { mutableStateOf("") }
  var idolOpen by remember { mutableStateOf(false) }
  val sheets = remember { mutableStateMapOf<String, Bitmap>() }
  // ⑧ 検索バー
  var searchQuery by remember { mutableStateOf("") }
  val filteredCharacters = remember(characters, searchQuery) {
    if (searchQuery.isBlank()) characters
    else characters.filter { it.name.contains(searchQuery, ignoreCase = true) || it.profile.contains(searchQuery, ignoreCase = true) }
  }

  suspend fun rpc(method: GatewayMethod, params: JSONObject, timeoutMs: Long = 60_000): JSONObject =
    JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = timeoutMs))

  // ── フォルダ・並び順 ──
  var folders by remember { mutableStateOf<List<ChFolder>>(emptyList()) }
  var folderTick by remember { mutableStateOf(0) }
  // null = 一覧の最上段（フォルダと未分類のキャラ）。それ以外 = 開いているフォルダのID
  var currentFolder by remember { mutableStateOf<String?>(null) }
  var sortMode by remember { mutableStateOf("fav") }
  var reorderMode by remember { mutableStateOf(false) }
  var folderDialog by remember { mutableStateOf<String?>(null) }
  var moveTarget by remember { mutableStateOf<LibraryChara?>(null) }
  var confirmDeleteFolder by remember { mutableStateOf(false) }
  LaunchedEffect(folderTick) { folders = loadFolders(viewModel) }
  val folderIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    val target = currentFolder
    if (uri == null || target == null) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        val base64 = withContext(Dispatchers.IO) {
          val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("画像を開けませんでした")
          val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("画像を読めませんでした")
          bitmapToIconBase64(bmp)
        }
        rpc(GatewayMethod.ComicFolderUpdate, JSONObject().put("id", target).put("iconBase64", base64))
        folderTick += 1
      }.onFailure { status = "アイコンを付けられませんでした：${it.message}" }
    }
  }
  fun ordered(list: List<LibraryChara>): List<LibraryChara> =
    when (sortMode) {
      "manual" -> list.sortedBy { if (it.libraryOrder == 0) Int.MAX_VALUE else it.libraryOrder }
      "name" -> list.sortedBy { it.name }
      "newest" -> list
      else -> list.sortedByDescending { it.isFavorite }
    }
  val searching = searchQuery.isNotBlank()
  val folderNow = currentFolder
  val visibleCharacters = ordered(
    when {
      searching -> filteredCharacters
      folderNow != null -> characters.filter { it.folderId == folderNow }
      else -> characters.filter { c -> c.folderId.isBlank() || folders.none { it.id == c.folderId } }
    },
  )
  fun reorderCharacters(list: List<LibraryChara>, index: Int, delta: Int) {
    val to = index + delta
    if (to !in list.indices) return
    val ids = list.map { it.id }.toMutableList().also { java.util.Collections.swap(it, index, to) }
    scope.launch { runCatching { rpc(GatewayMethod.ComicCharacterReorder, JSONObject().put("ids", JSONArray(ids))); reload() } }
  }
  fun reorderFolders(index: Int, delta: Int) {
    val to = index + delta
    if (to !in folders.indices) return
    val ids = folders.map { it.id }.toMutableList().also { java.util.Collections.swap(it, index, to) }
    scope.launch { runCatching { rpc(GatewayMethod.ComicFolderReorder, JSONObject().put("ids", JSONArray(ids))); folderTick += 1 } }
  }
  // 3列のキャラの札（お気に入り星・フォルダへ移す・並べ替え）
  fun LazyListScope.charaGrid(list: List<LibraryChara>, prefix: String) {
    items(list.chunked(3), key = { row -> prefix + row.joinToString { it.id } }) { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { c ->
          Box(modifier = Modifier.weight(1f)) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier =
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                  .background(if (c.isFavorite) ClawTheme.colors.primary.copy(alpha = 0.08f) else ClawTheme.colors.surface)
                  .border(
                    width = if (c.isFavorite) 2.dp else 0.dp,
                    color = if (c.isFavorite) ClawTheme.colors.primary.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                  )
                  .clickable { openId = c.id }.padding(10.dp),
            ) {
              CharaIcon(c, c.name, 72)
              Spacer(modifier = Modifier.heightIn(min = 6.dp))
              Text(text = c.name, style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.text, maxLines = 1)
              Text(text = loraBadge(c), style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted, maxLines = 2, textAlign = TextAlign.Center)
              if (c.partnerId.isNotBlank()) {
                Text(text = "💑 パートナー連携済み", style = ClawTheme.type.captionSmall.copy(fontSize = 10.sp), color = ClawTheme.colors.primary, textAlign = TextAlign.Center)
              }
              if (reorderMode) {
                val index = list.indexOfFirst { it.id == c.id }
                ReorderButtons(
                  onLeft = if (index > 0) ({ reorderCharacters(list, index, -1) }) else null,
                  onRight = if (index in 0 until list.size - 1) ({ reorderCharacters(list, index, 1) }) else null,
                )
              }
            }
            // お気に入り星ボタン（右上）
            Box(
              modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                .size(28.dp).clip(CircleShape)
                .background(ClawTheme.colors.canvas.copy(alpha = 0.85f))
                .clickable {
                  scope.launch {
                    runCatching {
                      rpc(GatewayMethod.ComicCharacterUpdate, JSONObject().put("characterId", c.id).put("isFavorite", !c.isFavorite))
                      reload()
                    }
                  }
                },
              contentAlignment = Alignment.Center,
            ) {
              Text(text = if (c.isFavorite) "⭐" else "☆", fontSize = 16.sp)
            }
            // フォルダへ移すボタン（左上）
            Box(
              modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                .size(28.dp).clip(CircleShape)
                .background(ClawTheme.colors.canvas.copy(alpha = 0.85f))
                .clickable { moveTarget = c },
              contentAlignment = Alignment.Center,
            ) {
              Text(text = "📁", fontSize = 14.sp)
            }
          }
        }
        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
      }
    }
  }

  val open = characters.firstOrNull { it.id == openId }
  var outpaintSides by remember(openId) { mutableStateOf<Set<String>>(emptySet()) }
  var outpaintAmount by remember(openId) { mutableStateOf(0.5f) }
  var outpaintNote by remember(openId) { mutableStateOf("") }
  // 折りたたんだパネル（見出しをタップで開閉）
  var editorFolded by remember(openId) { mutableStateOf(setOf<String>()) }
  // 設定画の候補（PC に残っている分。10秒ごとに読み直す。設定画そのものも、この中に「現在の設定画」として入る）
  var pagerTick by remember(openId) { mutableStateOf(0) }
  var candidates by remember(openId) { mutableStateOf<List<CandidateItem>>(emptyList()) }
  LaunchedEffect(openId, open?.sheetJobId, pagerTick) {
    while (openId != null) {
      runCatching {
        val array = rpc(GatewayMethod.ComicCharacterCandidates, JSONObject().put("characterId", openId)).optJSONArray("candidates") ?: JSONArray()
        candidates =
          (0 until array.length()).map { index ->
            array.getJSONObject(index).let { o -> CandidateItem(o.optString("jobId"), o.optString("view", "front"), o.optBoolean("adult"), o.optBoolean("current"), o.optLong("createdAt")) }
          }
      }
      kotlinx.coroutines.delay(10_000)
    }
  }
  var sheetModel by remember(openId, open?.imageModel) { mutableStateOf(open?.imageModel.orEmpty().ifBlank { "wai" }) }
  // 立ち絵のポーズ（空 = 直立・腕は体の横）
  var sheetPose by remember(openId) { mutableStateOf("") }
  var editName by remember(openId) { mutableStateOf(open?.name.orEmpty()) }
  var editProfile by remember(openId) { mutableStateOf(open?.profile.orEmpty()) }
  var editLook by remember(openId) { mutableStateOf(open?.look.orEmpty()) }
  var confirmDelete by remember { mutableStateOf(false) }
  // The setting sheet being edited, and the 360° gallery (labels, then pictures as they load).
  val editSpec = remember(openId, open?.spec) { mutableStateMapOf<String, String>().apply { putAll(open?.spec.orEmpty()) } }
  val editMotionProfile = remember(openId, open?.motionProfile) { mutableStateMapOf<String, String>().apply { putAll(open?.motionProfile.orEmpty()) } }
  var editLookOptions by remember(openId, open?.spec) {
    mutableStateOf(open?.spec?.get("lookOptions").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
  }
  val editLookNotes = remember(openId, open?.spec) {
    mutableStateMapOf<String, String>().apply { putAll(parseCatalogLookNotes(open?.spec?.get("lookNotes").orEmpty())) }
  }
  val preservedLookNotes = remember(openId, open?.spec) { uncataloguedLookNotes(open?.spec?.get("lookNotes").orEmpty()) }
  var showLookCatalog by remember(openId) { mutableStateOf(false) }
  var showSheet by remember(openId) { mutableStateOf(false) }
  var editVoiceId by remember(openId, open?.voiceId) { mutableStateOf(open?.voiceId.orEmpty()) }
  var editVoiceCaption by remember(openId, open?.voiceCaption) { mutableStateOf(open?.voiceCaption.orEmpty()) }
  var editOutfits by remember(openId, open?.outfitsText) { mutableStateOf(open?.outfitsText.orEmpty()) }
  var voiceOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  var gallery by remember(openId) { mutableStateOf<List<Pair<String, String>>?>(null) }
  val galleryImages = remember(openId) { mutableStateMapOf<String, Bitmap>() }
  var zoomKey by remember { mutableStateOf<String?>(null) }
  var confirmTrain by remember { mutableStateOf(false) }
  var creating by remember { mutableStateOf(false) }
  var creatingBusy by remember { mutableStateOf(false) }
  var customFieldKey by remember { mutableStateOf("") }
  var customFieldValue by remember { mutableStateOf("") }

  val iconPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      val target = openId
      if (uri == null || target == null) return@rememberLauncherForActivityResult
      scope.launch {
        val dataUrl = withContext(Dispatchers.IO) { convertUriToBase64DataUrl(context, uri, 768) }
        if (dataUrl == null) {
          status = "この画像は読み込めませんでした"
          return@launch
        }
        runCatching {
          rpc(GatewayMethod.ComicCharacterUpdate, JSONObject().put("characterId", target).put("iconBase64", dataUrl.substringAfter(",")))
          reload()
          status = "アイコンを変えました"
        }.onFailure { status = "アイコンのエラー: ${it.message}" }
      }
    }

  if (creating) {
    CharacterCreatorForm(
      viewModel = viewModel,
      busy = creatingBusy,
      progress = status,
      onCancel = { creating = false },
      onCreate = { params, photos ->
        scope.launch {
          creatingBusy = true
          status = "写真と設定を準備しています…"
          runCatching {
            if (photos.isNotEmpty()) {
              val uploadId = java.util.UUID.randomUUID().toString()
              photos.forEachIndexed { index, uri ->
                status = "写真を送信しています（${index + 1}/${photos.size}）"
                val data = withContext(Dispatchers.IO) { convertUriToBase64DataUrl(context, uri, 1024) }
                  ?: error("${index + 1}枚目の写真を読み取れませんでした")
                rpc(GatewayMethod.ComicCharacterPhotoUpload, JSONObject()
                  .put("uploadId", uploadId)
                  .put("index", index)
                  .put("dataBase64", data.substringAfter(",")), 120_000)
              }
              params.put("photoUploadId", uploadId).put("photoCount", photos.size)
            }
            status = "写真の人物を解析しています…"
            val res = rpc(GatewayMethod.ComicCharacterCreate, params, 300_000L + photos.size * 120_000L)
            res.optJSONObject("job")?.let { track(it) }
            reload()
            openId = res.optJSONObject("character")?.optString("id")
            creating = false
            status = "キャラクターを創りました。設定画を描いています。"
          }.onFailure { status = "創れませんでした: ${it.message}" }
          creatingBusy = false
        }
      },
    )
    return
  }

  if (open == null) {
    folderDialog?.let { mode ->
      FolderNameDialog(
        title = if (mode == "new") "フォルダを作る" else "フォルダの名前を変える",
        initial = if (mode == "new") "" else folders.firstOrNull { it.id == currentFolder }?.name.orEmpty(),
        onDismiss = { folderDialog = null },
        onOk = { name ->
          folderDialog = null
          scope.launch {
            runCatching {
              if (mode == "new") rpc(GatewayMethod.ComicFolderCreate, JSONObject().put("name", name))
              else rpc(GatewayMethod.ComicFolderUpdate, JSONObject().put("id", currentFolder).put("name", name))
              folderTick += 1
            }.onFailure { status = "できませんでした：${it.message}" }
          }
        },
      )
    }
    moveTarget?.let { c ->
      FolderMoveDialog(
        characterName = c.name,
        folders = folders,
        currentId = c.folderId,
        onDismiss = { moveTarget = null },
        onPick = { folderId ->
          moveTarget = null
          scope.launch {
            runCatching {
              rpc(GatewayMethod.ComicCharacterMove, JSONObject().put("characterId", c.id).put("folderId", folderId))
              reload()
            }.onFailure { status = "移せませんでした：${it.message}" }
          }
        },
      )
    }
    if (confirmDeleteFolder) {
      AlertDialog(
        onDismissRequest = { confirmDeleteFolder = false },
        title = { Text("フォルダを削除しますか？") },
        text = { Text("中のキャラは消えず、未分類にもどります。") },
        confirmButton = {
          TextButton(onClick = {
            confirmDeleteFolder = false
            val id = currentFolder
            scope.launch {
              runCatching {
                if (id != null) rpc(GatewayMethod.ComicFolderDelete, JSONObject().put("id", id))
                currentFolder = null
                folderTick += 1
                reload()
              }.onFailure { status = "削除できませんでした：${it.message}" }
            }
          }) { Text("削除") }
        },
        dismissButton = { TextButton(onClick = { confirmDeleteFolder = false }) { Text("やめる") } },
      )
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (folderNow == null) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ClawPrimaryButton(text = "✨ キャラクターを創る（身長・体型から）", onClick = { status = ""; creating = true }, modifier = Modifier.fillMaxWidth())
            ClawSecondaryButton(text = "🎤 マスターデータから作る（グループ → 名前）", onClick = { status = ""; idolOpen = true }, modifier = Modifier.fillMaxWidth())
            if (idolOpen) {
              IdolMasterDialog(
                viewModel = viewModel,
                onDismiss = { idolOpen = false },
                onAdded = { added, existing ->
                  idolOpen = false
                  status = if (existing) "「$added」はすでに図鑑にあります" else "「$added」を図鑑に追加しました"
                  scope.launch { reload() }
                },
              )
            }
          }
        }
      }
      if (status.isNotBlank()) item { Text(text = status, style = ClawTheme.type.caption, color = ClawTheme.colors.primary) }
      if (folderNow == null) {
        item {
          Text(
            text = "作品で作った登場人物がここに集まります。設定画を決めて「専用学習」をすると、どの作品でも同じ顔・髪・服で描かれます。",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }
      }
      item {
        ClawTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = "🔍 キャラクターを検索",
          label = "検索",
          modifier = Modifier.fillMaxWidth()
        )
      }
      // 並び順と並べ替え、フォルダを作る
      item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("fav" to "⭐先頭", "name" to "名前順", "newest" to "新しい順", "manual" to "自由").forEach { (id, label) ->
              CreatorChip(label = label, selected = sortMode == id, onClick = { sortMode = id; if (id != "manual") reorderMode = false }, modifier = Modifier.weight(1f))
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            CreatorChip(
              label = if (reorderMode) "↕ 並べ替え中（◀▶で動かす）" else "↕ 並べ替える",
              selected = reorderMode,
              onClick = { reorderMode = !reorderMode; if (reorderMode) sortMode = "manual" },
              modifier = Modifier.weight(1f),
            )
            if (folderNow == null) {
              CreatorChip(label = "＋ フォルダを作る", selected = false, onClick = { folderDialog = "new" }, modifier = Modifier.weight(1f))
            }
          }
        }
      }
      // 開いているフォルダの見出し（名前・アイコン・削除）
      if (folderNow != null && !searching) {
        val folder = folders.firstOrNull { it.id == folderNow }
        item {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = { currentFolder = null })
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised), contentAlignment = Alignment.Center) {
              val icon = folder?.icon
              if (icon != null) Image(bitmap = icon.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp)) else Text("📁")
            }
            Text(text = folder?.name ?: "フォルダ", style = ClawTheme.type.title, color = ClawTheme.colors.text, modifier = Modifier.weight(1f), maxLines = 1)
          }
        }
        item {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            CreatorChip(label = "✏️ 名前", selected = false, onClick = { folderDialog = "rename" }, modifier = Modifier.weight(1f))
            CreatorChip(label = "🖼️ アイコン", selected = false, onClick = { folderIconPicker.launch("image/*") }, modifier = Modifier.weight(1f))
            CreatorChip(label = "🗑 削除", selected = false, onClick = { confirmDeleteFolder = true }, modifier = Modifier.weight(1f))
          }
        }
      }
      // 最上段：フォルダの一覧
      if (folderNow == null && !searching) {
        if (folders.isNotEmpty()) {
          items(folders.withIndex().toList().chunked(3), key = { row -> "f-" + row.joinToString { it.value.id } }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              row.forEach { (index, f) ->
                FolderCard(
                  folder = f,
                  count = characters.count { it.folderId == f.id },
                  reorder = reorderMode,
                  onOpen = { currentFolder = f.id },
                  onLeft = if (index > 0) ({ reorderFolders(index, -1) }) else null,
                  onRight = if (index < folders.size - 1) ({ reorderFolders(index, 1) }) else null,
                  modifier = Modifier.weight(1f),
                )
              }
              repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
          }
        }
        item {
          Text(
            text = "📂 未分類のキャラ（${visibleCharacters.size}人）　※ 各キャラの左上の 📁 でフォルダに入れられます",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }
      }
      if (visibleCharacters.isEmpty()) {
        item {
          Text(
            text = if (folderNow != null && !searching) "このフォルダは空です。キャラの左上の 📁 から入れてください。" else if (characters.isEmpty()) "まだ登場人物がいません。作品を作ると自動で入ります。" else "",
            style = ClawTheme.type.body,
            color = ClawTheme.colors.textMuted,
          )
        }
      }
      charaGrid(visibleCharacters, "c-")
    }
    return
  }

  LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = { openId = null })
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.clickable { iconPicker.launch("image/*") }) { CharaIcon(open, open.name, 64) }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(text = open.name, style = ClawTheme.type.title, color = ClawTheme.colors.text)
          Text(text = loraBadge(open), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        }
        ClawIconButton(icon = Icons.Default.Delete, contentDescription = "削除", onClick = { confirmDelete = true })
      }
    }
    item { Text(text = "アイコンを押すと、端末の写真から選び直せます（設定画を決めると顔から自動で作ります）。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textSubtle) }
    if (open.specText.isNotBlank()) {
      item {
        ClawPanel(modifier = Modifier.fillMaxWidth()) {
          Text(text = open.specText.replace(" / ", "\n"), style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        }
      }
    }
    if (status.isNotBlank()) item { Text(text = status, style = ClawTheme.type.caption, color = ClawTheme.colors.primary) }
    if (open.loraError.isNotBlank() && open.loraStatus == "failed") {
      item { Text(text = "学習のエラー: ${open.loraError}", style = ClawTheme.type.caption, color = ClawTheme.colors.danger) }
    }
    // 設定画は、下の候補（横スライド）の中に「現在の設定画」として出るので、別には出さない
    if (open.sheetJobId.isNotBlank() && candidates.none { it.current }) {
      item {
        LaunchedEffect(open.sheetJobId) { loadImage(open.sheetJobId)?.let { sheets[open.sheetJobId] = it } }
        sheets[open.sheetJobId]?.let { bmp ->
          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "${open.name}の設定画",
            contentScale = ContentScale.Fit,
            // タップで拡大できる
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).clip(RoundedCornerShape(10.dp)).clickable { zoomKey = "__sheet__" },
          )
          if (zoomKey == "__sheet__") {
            ZoomableImageDialog(bitmap = bmp.asImageBitmap(), title = "${open.name}の設定画", onDismiss = { zoomKey = null })
          }
        }
      }
    }
    if (open.turnaroundJobId.isNotBlank()) {
      item {
        LaunchedEffect(open.turnaroundJobId) { loadImage(open.turnaroundJobId)?.let { sheets[open.turnaroundJobId] = it } }
        sheets[open.turnaroundJobId]?.let { bmp ->
          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "${open.name}の3面図",
            contentScale = ContentScale.Fit,
            // タップで拡大できる
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { zoomKey = "__turnaround__" },
          )
          if (zoomKey == "__turnaround__") {
            ZoomableImageDialog(bitmap = bmp.asImageBitmap(), title = "${open.name}の3面図", onDismiss = { zoomKey = null })
          }
        }
      }
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FoldHeader("🎨 設定画・ターンアラウンド", folded = "🎨 設定画・ターンアラウンド" in editorFolded, onToggle = { editorFolded = if ("🎨 設定画・ターンアラウンド" in editorFolded) editorFolded - "🎨 設定画・ターンアラウンド" else editorFolded + "🎨 設定画・ターンアラウンド" })
        if ("🎨 設定画・ターンアラウンド" !in editorFolded) {
        // 描き直すときの絵のモデル（選んだものは、このキャラに覚えさせる）
        Text(text = "絵のモデル（描き直すときに使います）", style = ClawTheme.type.caption.copy(fontSize = 12.sp), color = ClawTheme.colors.textMuted)
        GenStyleSwitch(viewModel = viewModel)
        GroupedModelPicker(viewModel = viewModel, selected = sheetModel, onSelect = { sheetModel = it })
        // 選んだモデルの情報（成人向けの絵を、立ち絵から作る場合は、モデルに左右されません）
        ModelInfoCard(modelId = sheetModel, adultWanted = false)
        // 選んだモデルのサンプル（クリエの見た目で作った作例）
        SamplePreview(
          viewModel = viewModel,
          key = modelSampleKey(sheetModel),
          title = LIBRARY_IMAGE_MODELS.firstOrNull { it.first == sheetModel }?.second ?: sheetModel,
          prompt = KURIE_LOOK,
          modelId = sheetModel,
        )
        SamplePreview(
          viewModel = viewModel,
          key = modelSampleKey(sheetModel, adult = true),
          title = (LIBRARY_IMAGE_MODELS.firstOrNull { it.first == sheetModel }?.second ?: sheetModel) + "（成人向け）",
          prompt = "$KURIE_NUDE, standing, full body, front view",
          modelId = sheetModel,
        )
        // 設定画を広げる（アウトペイント）。広げたい方向を選ぶと、元の絵はそのままで、外側の続きを描く
        Text(text = "設定画を広げる（アウトペイント）", style = ClawTheme.type.caption.copy(fontSize = 12.sp), color = ClawTheme.colors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          listOf("top" to "上", "bottom" to "下", "left" to "左", "right" to "右").forEach { (side, label) ->
            ChoiceChip(
              label = label,
              selected = side in outpaintSides,
              onClick = { outpaintSides = if (side in outpaintSides) outpaintSides - side else outpaintSides + side },
              modifier = Modifier.weight(1f),
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          listOf(0.25f to "少し（25%）", 0.5f to "半分（50%）", 1f to "倍（100%）").forEach { (amount, label) ->
            ChoiceChip(label = label, selected = outpaintAmount == amount, onClick = { outpaintAmount = amount }, modifier = Modifier.weight(1f))
          }
        }
        ClawTextField(
          value = outpaintNote,
          onValueChange = { outpaintNote = it },
          placeholder = "例: 足元まで描く、背景は白のまま",
          label = "広げた部分の指定（日本語・任意）",
          modifier = Modifier.fillMaxWidth(),
        )
        ClawSecondaryButton(
          text = "🖼 設定画を広げる",
          enabled = outpaintSides.isNotEmpty() && open.hasSheet,
          onClick = {
            scope.launch {
              runCatching {
                val sides = JSONObject().also { o -> outpaintSides.forEach { o.put(it, outpaintAmount.toDouble()) } }
                track(rpc(GatewayMethod.ComicSheetGenerate, JSONObject().put("characterId", open.id).put("outpaint", sides).put("note", outpaintNote.trim())))
                status = "設定画を広げています（2〜3分）。できたら、下の候補から「この設定画にする」を押してください"
              }.onFailure { status = "広げるエラー: ${it.message}" }
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        // チャット画面で動くアバター（表情15枚。設定画から作る）
        Text(
          text = if (open.hasAvatar) "🎭 アバターの表情：作成済み（チャット画面で、読み上げに合わせて口が動きます）" else "🎭 アバターの表情：まだ作っていません",
          style = ClawTheme.type.caption.copy(fontSize = 12.sp),
          color = ClawTheme.colors.textMuted,
        )
        ClawSecondaryButton(
          text = if (open.hasAvatar) "🎭 アバターの表情を作り直す（約8分）" else "🎭 アバターの表情を作る（約8分）",
          enabled = open.hasSheet,
          onClick = {
            scope.launch {
              runCatching {
                track(rpc(GatewayMethod.ComicSheetGenerate, JSONObject().put("characterId", open.id).put("avatar", true)))
                status = "アバターの表情を作っています（7つの表情×口の開閉、まばたき）。終わると、チャット画面に出ます"
              }.onFailure { status = "アバターのエラー: ${it.message}" }
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        // ポーズ指定（一般も成人向けも、正面の立ち絵に効く）
        Text(text = "立ち絵のポーズ（空なら、まっすぐ立つ）", style = ClawTheme.type.caption.copy(fontSize = 12.sp), color = ClawTheme.colors.textMuted)
        ClawTextField(value = sheetPose, onValueChange = { sheetPose = it }, placeholder = "例: 腰に手を当てて立つ / ベッドに座って脚を開く", label = "ポーズ", modifier = Modifier.fillMaxWidth())
        listOf(
          listOf("腰に手" to "standing with hands on hips", "手を振る" to "standing and waving one hand", "座る" to "sitting on a chair", "膝をつく" to "kneeling on both knees"),
          listOf("横たわる" to "lying on her back on a bed", "四つん這い" to "on all fours, on hands and knees", "脚を開いて座る" to "sitting on the floor with her legs spread", "後ろ向き" to "standing with her back to the viewer, looking over her shoulder"),
        ).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { (label, tags) -> ChoiceChip(label = label, selected = sheetPose == tags, onClick = { sheetPose = if (sheetPose == tags) "" else tags }, modifier = Modifier.weight(1f)) }
          }
        }
        // ② 衣装選択チップ（outfitsTextから衣装名を解析して表示）
        val outfitNames = open.outfitsText
          .lines()
          .filter { it.startsWith("名前:") || it.startsWith("outfit:") || it.startsWith("name:") }
          .mapNotNull { it.substringAfter(":").trim().takeIf { s -> s.isNotBlank() } }
        if (outfitNames.isNotEmpty()) {
          Text(
            text = "衣装を選んでシート生成",
            style = ClawTheme.type.caption.copy(fontSize = 12.sp),
            color = ClawTheme.colors.textMuted,
          )
          var selectedOutfitForSheet by remember(open.id) { mutableStateOf("") }
          outfitNames.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
              row.forEach { name ->
                ChoiceChip(
                  label = name,
                  selected = selectedOutfitForSheet == name,
                  onClick = { selectedOutfitForSheet = if (selectedOutfitForSheet == name) "" else name },
                  modifier = Modifier.weight(1f),
                )
              }
              repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
          }
          if (selectedOutfitForSheet.isNotBlank()) {
            ClawSecondaryButton(
              text = "🎨 「$selectedOutfitForSheet」の立ち絵を描く",
              enabled = open.hasSheet,
              onClick = {
                scope.launch {
                  runCatching {
                    track(rpc(GatewayMethod.ComicSheetGenerate,
                      JSONObject().put("characterId", open.id).put("imageModel", sheetModel).put("outfit", selectedOutfitForSheet).put("pose", sheetPose)))
                    status = "🎨 「$selectedOutfitForSheet」の立ち絵を生成中…"
                  }.onFailure { status = "衣装立ち絵エラー: ${it.message}" }
                }
              },
              modifier = Modifier.fillMaxWidth(),
            )
            ClawSecondaryButton(
              text = "🔞 「$selectedOutfitForSheet」の成人向け立ち絵を描く",
              enabled = open.hasSheet,
              onClick = {
                scope.launch {
                  runCatching {
                    track(rpc(GatewayMethod.ComicSheetGenerate,
                      JSONObject().put("characterId", open.id).put("imageModel", sheetModel).put("outfit", selectedOutfitForSheet).put("adult", true).put("pose", sheetPose)))
                    status = "🔞 「$selectedOutfitForSheet」の成人向け立ち絵を生成中…"
                  }.onFailure { status = "衣装成人向けエラー: ${it.message}" }
                }
              },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
        // 行1: 正面立ち絵 / 三面図 / 360度
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          ClawSecondaryButton(
            text = if (open.hasSheet) "🎨 立ち絵を描き直す" else "🎨 立ち絵を描きます",
            onClick = {
              scope.launch {
                runCatching {
                  track(rpc(GatewayMethod.ComicSheetGenerate, JSONObject().put("characterId", open.id).put("imageModel", sheetModel).put("view", "front").put("pose", sheetPose)))
                  status = "正面立ち絵を描いています（30秒ほど）"
                }.onFailure { status = "立ち絵のエラー: ${it.message}" }
              }
            },
            modifier = Modifier.weight(1f),
          )
          ClawSecondaryButton(
            text = "📏 三面図",
            onClick = {
              scope.launch {
                runCatching {
                  track(rpc(GatewayMethod.ComicSheetGenerate, JSONObject().put("characterId", open.id).put("imageModel", sheetModel).put("view", "three_view")))
                  status = "三面図を描いています（30~60秒）…正面・側面・背面を横並び"
                }.onFailure { status = "三面図のエラー: ${it.message}" }
              }
            },
            modifier = Modifier.weight(1f),
          )
          ClawSecondaryButton(
            text = "🔄 360度",
            onClick = {
              scope.launch {
                runCatching {
                  track(rpc(GatewayMethod.ComicSheetGenerate, JSONObject().put("characterId", open.id).put("imageModel", sheetModel).put("view", "360")))
                  status = "360度ターンアラウンドを描いています（30~60秒）‥8方向グリッド"
                }.onFailure { status = "360度のエラー: ${it.message}" }
              }
            },
            modifier = Modifier.weight(1f),
          )
        }
        // 行2: 成人向け立ち絵 / 成人向け三面図 / 成人向け360度
        Text(text = "🔞 成人向け版", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          ClawSecondaryButton(
            text = "🔞 裸立ち絵",
            onClick = {
              scope.launch {
                runCatching {
                  track(rpc(GatewayMethod.ComicSheetGenerate, JSONObject().put("characterId", open.id).put("imageModel", sheetModel).put("view", "front").put("adult", true).put("pose", sheetPose)))
                  status = "成人向け立ち絵を描いています…"
                }.onFailure { status = "成人向け立ち絵のエラー: ${it.message}" }
              }
            },
            modifier = Modifier.weight(1f),
          )
          ClawSecondaryButton(
            text = "🔞 裸三面図",
            onClick = {
              scope.launch {
                runCatching {
                  track(rpc(GatewayMethod.ComicSheetGenerate, JSONObject().put("characterId", open.id).put("imageModel", sheetModel).put("view", "three_view").put("adult", true)))
                  status = "成人向け三面図を描いています…"
                }.onFailure { status = "成人向け三面図のエラー: ${it.message}" }
              }
            },
            modifier = Modifier.weight(1f),
          )
          ClawSecondaryButton(
            text = "🔞 裸360度",
            onClick = {
              scope.launch {
                runCatching {
                  track(rpc(GatewayMethod.ComicSheetGenerate, JSONObject().put("characterId", open.id).put("imageModel", sheetModel).put("view", "360").put("adult", true)))
                  status = "成人向け360度を描いています…"
                }.onFailure { status = "成人向け360度のエラー: ${it.message}" }
              }
            },
            modifier = Modifier.weight(1f),
          )
        }
        // 行3: 顔合わせ方式の選択
        var useIpAdapter by remember { mutableStateOf(false) }
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          ClawSecondaryButton(
            text = if (!useIpAdapter) "✅ 専用学習（高品質）" else "専用学習（高品質）",
            onClick = { useIpAdapter = false },
            modifier = Modifier.weight(1f),
          )
          ClawSecondaryButton(
            text = if (useIpAdapter) "✅ 顔合わせ（即時）" else "顔合わせ（即時）",
            onClick = { useIpAdapter = true },
            modifier = Modifier.weight(1f),
          )
        }
        Text(
          text = if (useIpAdapter)
            "⚡ 学習なし。生成のたびに設定画の顔を参照します。すぐ使えますが、精度は学習より少し低め。"
          else
            "🎯 約1時間の学習後、どの作品でも同じ顔で描かれます。最高品質。",
          style = ClawTheme.type.captionSmall,
          color = ClawTheme.colors.textMuted,
        )
        if (!useIpAdapter) {
          // LoRA学習ボタン（従来）
          ClawPrimaryButton(
            text = if (open.trained) "🎯 専用学習をやり直す" else "🎯 専用学習する",
            enabled = open.hasSheet && open.loraStatus !in setOf("training", "queued"),
            onClick = { confirmTrain = true },
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          // IP-Adapter顔合わせモード: characterIdにフラグをセットして保存
          ClawPrimaryButton(
            text = "⚡ 顔合わせモードを有効にする",
            enabled = open.hasSheet,
            onClick = {
              scope.launch {
                runCatching {
                  rpc(
                    GatewayMethod.ComicCharacterUpdate,
                    JSONObject()
                      .put("characterId", open.id)
                      .put("useIpAdapter", true),
                  )
                  reload()
                  status = "⚡ 顔合わせモードをONにしました。次の生成から参照画像を使います。"
                }.onFailure { status = "エラー: ${it.message}" }
              }
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }
        // 行4: 書き出し
        ClawSecondaryButton(
          text = "📤 設定シートを書き出す（JSON）",
          onClick = {
            scope.launch {
              runCatching {
                val spec = open.spec.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                val exportText = buildString {
                  appendLine("# ${open.name} の設定シート")
                  appendLine("名前: ${open.name}")
                  appendLine("ルック: ${open.look}")
                  appendLine("プロフィール: ${open.profile}")
                  appendLine("---")
                  appendLine(spec)
                }
                val file = java.io.File(context.getExternalFilesDir(null), "${open.name}_設定シート.txt")
                file.writeText(exportText, Charsets.UTF_8)
                status = "📄 書き出し完了: ${file.absolutePath}"
              }.onFailure { status = "書き出しエラー: ${it.message}" }
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        // 行5: チャット導線（パートナーを作って結びつける）
        ClawSecondaryButton(
          text = if (open.partnerId.isBlank()) "💬 このキャラとチャットできるようにする" else "💬 パートナーの設定を同期する",
          onClick = {
            scope.launch {
              runCatching {
                rpc(GatewayMethod.ComicCharacterUpdate, JSONObject().put("characterId", open.id).put("isFavorite", true).put("ensurePartner", true))
                reload()
                status = "💬 ${open.name}をパートナーと結びつけました。パートナー画面から話しかけてください。"
              }.onFailure { status = "チャット作成エラー: ${it.message}" }
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
      }
    }

    // 設定画の候補：PC に残っている分を、すべて横スライドで見られる（画面を離れても消えない）
    item(key = "candidates") {
      if (candidates.isNotEmpty()) {
        SheetCandidatePager(
          characterName = open.name,
          items = candidates,
          loadImage = loadImage,
          onChoose = { jobId ->
            scope.launch {
              runCatching {
                rpc(GatewayMethod.ComicSheetChoose, JSONObject().put("jobId", jobId))
                reload()
                pagerTick++
                status = "設定画を決めて、顔からアイコンを作りました"
              }.onFailure { status = "エラー: ${it.message}" }
            }
          },
          onDelete = { jobId ->
            scope.launch {
              runCatching {
                rpc(GatewayMethod.MediaJobDelete, JSONObject().put("jobId", jobId))
                pagerTick++
                status = "候補を消しました"
              }.onFailure { status = "エラー: ${it.message}" }
            }
          },
          onStatus = { status = it },
        )
      }
    }
    item {
      ClawPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FoldHeader("名前・性格・見た目", folded = "名前・性格・見た目" in editorFolded, onToggle = { editorFolded = if ("名前・性格・見た目" in editorFolded) editorFolded - "名前・性格・見た目" else editorFolded + "名前・性格・見た目" })
          if ("名前・性格・見た目" !in editorFolded) {
          ClawTextField(value = editName, onValueChange = { editName = it }, placeholder = "名前", modifier = Modifier.fillMaxWidth())
          ClawTextField(value = editProfile, onValueChange = { editProfile = it }, placeholder = "役割・性格・口調・一人称", minLines = 3, modifier = Modifier.fillMaxWidth())
          ClawTextField(value = editLook, onValueChange = { editLook = it }, placeholder = "見た目（英語のタグ）", minLines = 3, modifier = Modifier.fillMaxWidth())
          Text(
            text = "見た目を変えた場合は、設定画を描き直して学習し直すと反映が確実です。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textSubtle,
          )
          ClawPrimaryButton(
            text = "保存",
            enabled = editName.isNotBlank() && (editName != open.name || editProfile != open.profile || editLook != open.look),
            onClick = {
              scope.launch {
                runCatching {
                  rpc(
                    GatewayMethod.ComicCharacterUpdate,
                    JSONObject().put("characterId", open.id).put("name", editName.trim()).put("profile", editProfile.trim()).put("look", editLook.trim()),
                  )
                  reload()
                  status = "保存しました"
                }.onFailure { status = "保存エラー: ${it.message}" }
              }
            },
          )
        }
        }
      }
    }
    item {
      ClawPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FoldHeader("📋 設定シート", folded = "📋 設定シート" in editorFolded, onToggle = { editorFolded = if ("📋 設定シート" in editorFolded) editorFolded - "📋 設定シート" else editorFolded + "📋 設定シート" })
          if ("📋 設定シート" !in editorFolded) {
          Text(
            text = "ここに書いたことは、漫画とモーションコミックの台本、キャラクリとの会話に使われます。成人向けの欄は、モーションコミックと成人向けの連載だけが読みます。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textSubtle,
          )
          ClawSecondaryButton(
            text = if (showSheet) "閉じる" else "開いて見る・書く",
            onClick = { showSheet = !showSheet },
            modifier = Modifier.fillMaxWidth(),
          )
          if (showSheet) {
            SPEC_GROUPS.forEach { (group, fields) ->
              Text(text = group, style = ClawTheme.type.caption, color = ClawTheme.colors.text)
              fields.forEach { (key, label) ->
                ClawTextField(
                  value = editSpec[key].orEmpty(),
                  onValueChange = { editSpec[key] = it },
                  placeholder = label,
                  label = label,
                   minLines = if (key in setOf("background", "relations", "adultLikes", "kinks")) 2 else 1,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
            // ── カスタム項目（手動追加）────────────────────────────────
            val knownKeys = SPEC_GROUPS.flatMap { it.second }.map { it.first }.toSet() + setOf("lookOptions", "lookNotes")
            val customKeys = editSpec.keys.filter { it !in knownKeys && editSpec[it]?.isNotBlank() == true }
            if (customKeys.isNotEmpty()) {
              Text(
                text = "+ 独自の追加項目",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.text,
              )
              customKeys.forEach { key ->
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  ClawTextField(
                    value = editSpec[key].orEmpty(),
                    onValueChange = { editSpec[key] = it },
                    label = key,
                    placeholder = "内容",
                    modifier = Modifier.weight(1f),
                  )
                  ClawIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "削除",
                    onClick = { editSpec.remove(key) },
                  )
                }
              }
            }
            if (customFieldKey.isNotBlank()) {
              Text(
                text = "+ 新しい項目を追加",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.text,
              )
              ClawTextField(
                value = customFieldKey,
                onValueChange = { customFieldKey = it },
                label = "項目名",
                placeholder = "例: 首絞め・鼻ピアス・へそピアス・隠し設定",
                modifier = Modifier.fillMaxWidth(),
              )
              ClawTextField(
                value = customFieldValue,
                onValueChange = { customFieldValue = it },
                label = "内容",
                placeholder = "内容を入力",
                modifier = Modifier.fillMaxWidth(),
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawPrimaryButton(
                  text = "追加",
                  enabled = customFieldKey.isNotBlank(),
                  onClick = {
                    if (customFieldKey.isNotBlank()) {
                      editSpec[customFieldKey.trim()] = customFieldValue.trim()
                      customFieldKey = ""
                      customFieldValue = ""
                    }
                  },
                )
                ClawSecondaryButton(
                  text = "キャンセル",
                  onClick = { customFieldKey = ""; customFieldValue = "" },
                )
              }
            } else {
              ClawSecondaryButton(
                text = "+ 項目を手動で追加",
                onClick = { customFieldKey = " " },
                modifier = Modifier.fillMaxWidth(),
              )
            }
            Text(text = "服・アクセサリー・表情など", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
            Text(
              text = "キャラ図鑑の項目から選べます。選んだ内容は、このキャラの画像・漫画・モーションコミック生成に使われます。",
              style = ClawTheme.type.captionSmall,
              color = ClawTheme.colors.textMuted,
            )
            ClawSecondaryButton(
              text = if (showLookCatalog) "項目一覧を閉じる（${editLookOptions.size}件を選択中）" else "項目一覧から選ぶ（${editLookOptions.size}件を選択中）",
              onClick = { showLookCatalog = !showLookCatalog },
              modifier = Modifier.fillMaxWidth(),
            )
            if (showLookCatalog) {
              CharacterLibraryCatalogEditor(
                viewModel = viewModel,
                selected = editLookOptions,
                notes = editLookNotes,
                onSelected = { selected ->
                  editLookOptions = selected
                  if (selected.isEmpty()) editSpec.remove("lookOptions")
                  else editSpec["lookOptions"] = catalogOptionIdsText(selected)
                  val noteText = mergedCatalogLookNotes(selected, editLookNotes, preservedLookNotes)
                  if (noteText.isBlank()) editSpec.remove("lookNotes") else editSpec["lookNotes"] = noteText
                },
                onNote = { id, text ->
                  editLookNotes[id] = text
                  val noteText = mergedCatalogLookNotes(editLookOptions, editLookNotes, preservedLookNotes)
                  if (noteText.isBlank()) editSpec.remove("lookNotes") else editSpec["lookNotes"] = noteText
                },
              )
            }
            Text(text = "声（モーションコミックでこの声を使う）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
            CharacterMotionProfileEditor(values = editMotionProfile, onChange = { key, value -> if (value.isBlank()) editMotionProfile.remove(key) else editMotionProfile[key] = value })
            LaunchedEffect(Unit) {
              voiceOptions =
                runCatching {
                  val arr = rpc(GatewayMethod.VoiceList, JSONObject()).optJSONArray("voices") ?: JSONArray()
                  (0 until arr.length()).mapNotNull { i ->
                    val v = arr.getJSONObject(i)
                    if ((v.optJSONArray("files")?.length() ?: 0) > 0) v.optString("id") to v.optString("name") else null
                  }
                }.getOrDefault(emptyList())
            }
            (listOf("" to "✏️ 説明で決める") + voiceOptions).chunked(3).forEach { row ->
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (id, name) ->
                  ClawSecondaryButton(
                    text = (if (editVoiceId == id) "✓ " else "") + name,
                    onClick = { editVoiceId = id },
                    modifier = Modifier.weight(1f),
                  )
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
              }
            }
            if (editVoiceId.isBlank()) {
              ClawTextField(
                value = editVoiceCaption,
                onValueChange = { editVoiceCaption = it },
                placeholder = "例: 25歳くらいの大人の女性。低めで気だるげな声",
                label = "声の説明",
                modifier = Modifier.fillMaxWidth(),
              )
            }
            Text(text = "衣装（モーションコミックや設定画で選べる）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
            ClawTextField(
              value = editOutfits,
              onValueChange = { editOutfits = it },
              placeholder = "1行に1着「名前: 英語の服のタグ」\n例: 水着: white bikini, sandals\n例: ドレス: red evening gown, high heels",
              label = "衣装",
              minLines = 3,
              modifier = Modifier.fillMaxWidth(),
            )
            ClawPrimaryButton(
              text = "設定シートを保存",
              enabled = characterMotionProfileValid(editMotionProfile) && (editSpec.toMap() != open.spec || editMotionProfile.toMap() != open.motionProfile || editVoiceId != open.voiceId || editVoiceCaption != open.voiceCaption || editOutfits != open.outfitsText),
              onClick = {
                scope.launch {
                  runCatching {
                    val spec = JSONObject()
                    editSpec.forEach { (k, v) -> if (v.isNotBlank()) spec.put(k, v.trim()) }
                    rpc(
                      GatewayMethod.ComicCharacterUpdate,
                      JSONObject()
                        .put("characterId", open.id)
                        .put("spec", spec)
                        .put("motionProfile", JSONObject().also { result -> editMotionProfile.forEach { (key, value) -> result.put(key, value.toDouble()) } })
                        .put("voiceId", editVoiceId)
                        .put("voiceCaption", editVoiceCaption.trim())
                        .put("outfits", editOutfits),
                    )
                    reload()
                    status = "設定シートを保存しました"
                  }.onFailure { status = "保存エラー: ${it.message}" }
                }
              },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
        }
      }
    }
    item(key = "partner-panel-${open.id}") {
      LibraryPartnerPanel(
        chara = open,
        rpc = { method, params -> rpc(method, params) },
        reload = reload,
        onStatus = { status = it },
      )
    }
    item {
      ClawPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FoldHeader("🔄 360度ギャラリー", folded = "🔄 360度ギャラリー" in editorFolded, onToggle = { editorFolded = if ("🔄 360度ギャラリー" in editorFolded) editorFolded - "🔄 360度ギャラリー" else editorFolded + "🔄 360度ギャラリー" })
          if ("🔄 360度ギャラリー" !in editorFolded) {
          Text(
            text = "設定画・3面図と、専用学習で作った22枚（角度・表情・ポーズ・場面）を見られます。学習前は設定画と3面図だけです。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textSubtle,
          )
          if (gallery == null) {
            ClawSecondaryButton(
              text = "ギャラリーを開く",
              onClick = {
                scope.launch {
                  runCatching {
                    val arr = rpc(GatewayMethod.ComicCharacterGallery, JSONObject().put("characterId", open.id)).optJSONArray("items") ?: JSONArray()
                    val items = (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.optString("key") to o.optString("label") } }
                    gallery = items
                    for ((key, _) in items) {
                      val res = rpc(GatewayMethod.ComicCharacterImage, JSONObject().put("characterId", open.id).put("key", key))
                      val bytes = Base64.decode(res.optString("imageBase64"), Base64.DEFAULT)
                      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { galleryImages[key] = it }
                    }
                  }.onFailure { status = "ギャラリーのエラー: ${it.message}" }
                }
              },
              modifier = Modifier.fillMaxWidth(),
            )
          } else if (gallery!!.isEmpty()) {
            Text(text = "まだ絵がありません。設定画を描いてください。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          } else {
            gallery!!.chunked(3).forEach { row ->
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (key, label) ->
                  Column(modifier = Modifier.weight(1f).clickable { zoomKey = key }) {
                    val bmp = galleryImages[key]
                    if (bmp != null) {
                      androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                      )
                    } else {
                      Text(text = "読み込み中…", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textSubtle, modifier = Modifier.height(140.dp))
                    }
                    Text(text = label, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
                  }
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
              }
            }
          }
        }
        }
      }
    }
    if (open.series.isNotEmpty()) {
      item {
        Text(
          text = "登場する作品: " + open.series.joinToString("、") { it.second },
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textMuted,
        )
      }
    }
  }

  zoomKey?.let { key ->
    val bmp = galleryImages[key]
    AlertDialog(
      onDismissRequest = { zoomKey = null },
      title = { Text(gallery?.firstOrNull { it.first == key }?.second.orEmpty()) },
      text = {
        if (bmp != null) {
          androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
          )
        }
      },
      confirmButton = { TextButton(onClick = { zoomKey = null }) { Text("閉じる") } },
    )
  }

  if (confirmTrain) {
    AlertDialog(
      onDismissRequest = { confirmTrain = false },
      title = { Text("${open.name}を専用学習しますか？") },
      text = {
        Text(
          "設定画から学習用の絵を約23枚作り、${open.name}専用の追加モデルを学習します。\n" +
            "かかる時間: 約1時間（その間チャットと生成は止まります）\n" +
            "終わると、どの作品でも${open.name}が同じ見た目で描かれます。",
          color = ClawTheme.colors.textMuted,
        )
      },
      confirmButton = {
        ClawPrimaryButton(
          text = "学習する",
          onClick = {
            confirmTrain = false
            scope.launch {
              runCatching {
                track(rpc(GatewayMethod.ComicCharacterTrain, JSONObject().put("characterId", open.id)))
                reload()
                status = "学習を始めました。進み具合はここに出ます。"
              }.onFailure { status = "学習を始められませんでした: ${it.message}" }
            }
          },
        )
      },
      dismissButton = { ClawSecondaryButton(text = "やめる", onClick = { confirmTrain = false }) },
    )
  }
  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("${open.name}を図鑑から削除") },
      text = { Text("設定画・アイコン・学習したモデルが消えます。作品の中の名前と設定は残ります。") },
      confirmButton = {
        ClawPrimaryButton(
          text = "削除する",
          onClick = {
            confirmDelete = false
            scope.launch {
              runCatching { rpc(GatewayMethod.ComicCharacterDelete, JSONObject().put("characterId", open.id)) }
              openId = null
              reload()
            }
          },
        )
      },
      dismissButton = { ClawSecondaryButton(text = "やめる", onClick = { confirmDelete = false }) },
    )
  }
}

private val BREAST_PICKS = listOf("控えめ", "普通", "大きめ", "巨乳", "爆乳", "超乳")
private val WAIST_PICKS = listOf("細いウエスト", "くびれ強め", "普通", "むっちり")
private val HIPS_PICKS = listOf("小さめ", "普通", "大きめ", "豊満")
private val LEGS_PICKS = listOf("細い", "すらっと長い", "むっちり", "引き締まった")
private val MUSCLE_PICKS = listOf("なし", "うっすら腹筋", "筋肉質", "アスリート体型")

/** 選ぶと、下の入力欄にその言葉が入る。もう一度押すと消える。入力欄は自由に書き換えられる。 */
@Composable
private fun QuickPick(label: String, picks: List<String>, current: String, onPick: (String) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(text = label, style = ClawTheme.type.caption, color = ClawTheme.colors.text)
    picks.chunked(3).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { pick ->
          CreatorChip(
            label = pick,
            selected = current == pick,
            onClick = { onPick(if (current == pick) "" else pick) },
            modifier = Modifier.weight(1f),
          )
        }
        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
      }
    }
  }
}

private val BODY_TYPES = listOf(
  "スリム", "細め・標準", "普通", "グラマー", "爆乳・巨乳", "むっちり",
  "筋肉質", "ぽっちゃり", "豊満・肉感", "低身長・細め", "低身長・グラマー", "高身長・スレンダー",
)

/** The character creator: everything about a new character, in Japanese; height in cm. */
@Composable
private fun CharacterCreatorForm(viewModel: MainViewModel, busy: Boolean, progress: String, onCancel: () -> Unit, onCreate: (JSONObject, List<Uri>) -> Unit) {
  // サンプル画像製造の結果（見本の絵・既定の指示）を読んでおく
  LaunchedEffect(Unit) { SampleStore.refresh(viewModel) }
  val context = LocalContext.current
  ItemDisplay.load(context)
  val scope = rememberCoroutineScope()
  // 画像からキャラ作成
  var refImageUri by remember { mutableStateOf<Uri?>(null) }
  var refImageInlineBase64 by remember { mutableStateOf<String?>(null) }
  var refImageBitmap  by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
  // 最初の写真を人物の基準にする。写真は作成時に1枚ずつ送信する。
  var extraPhotos by remember { mutableStateOf<List<Pair<Uri, android.graphics.Bitmap>>>(emptyList()) }
  var photoMode by remember { mutableStateOf("subject") }
  var photoRoles by remember { mutableStateOf(listOf("顔", "髪", "服")) }
  var photoQuality by remember { mutableStateOf("normal") }
  var analyzingRef   by remember { mutableStateOf(false) }
  var refAnalysisMsg by remember { mutableStateOf("") }
  // 写真からの作り方：絵柄（実写のまま／アニメ風／イラスト風）と、描く精度
  var photoStyle by remember { mutableStateOf("photo") }
  var photoSource by remember { mutableStateOf("photo") }
  var photoEngine by remember { mutableStateOf("precise") }
  val refImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch(Dispatchers.IO) {
      try {
        val dataUrl = convertUriToBase64DataUrl(context, uri, 1024) ?: return@launch
        val bytes = android.util.Base64.decode(dataUrl.substringAfter(","), android.util.Base64.DEFAULT)
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        withContext(Dispatchers.Main) {
          refImageUri = uri
          refImageInlineBase64 = null
          refImageBitmap = bmp
          refAnalysisMsg = "🖼️ 写真を読み込みました。下で絵柄と描く方法を選んで、名前を入れて「キャラクターを創る」を押してください。"
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) { refAnalysisMsg = "画像の読み込みに失敗しました: ${e.message}" }
      }
    }
  }

  val extraPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch(Dispatchers.IO) {
      try {
        val dataUrl = convertUriToBase64DataUrl(context, uri, 1024) ?: return@launch
        val bytes = android.util.Base64.decode(dataUrl.substringAfter(","), android.util.Base64.DEFAULT)
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@launch
        withContext(Dispatchers.Main) {
          extraPhotos = extraPhotos + (uri to android.graphics.Bitmap.createScaledBitmap(bmp, 160, 160, true))
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) { refAnalysisMsg = "画像の読み込みに失敗しました: ${e.message}" }
      }
    }
  }

  // 折りたたんだ項目（見出しをタップで開閉）
  var folded by remember { mutableStateOf(setOf<String>()) }
  var name by remember { mutableStateOf("") }
  var gender by remember { mutableStateOf("女性") }
  var age by remember { mutableStateOf("22") }
  var height by remember { mutableStateOf("160") }
  var body by remember { mutableStateOf("普通") }
  // 見た目（顔・髪・体）
  var hair by remember { mutableStateOf("") }
  var hairstyle by remember { mutableStateOf("") }
  var eyes by remember { mutableStateOf("") }
  var contactLens by remember { mutableStateOf("") }
  var faceType by remember { mutableStateOf("") }
  var skin by remember { mutableStateOf("") }
  var breastSize by remember { mutableStateOf("") }
  var waistHip by remember { mutableStateOf("") }
  var features by remember { mutableStateOf("") }
  var bodyMarks by remember { mutableStateOf("") }
  var fragrance by remember { mutableStateOf("") }
  var legs by remember { mutableStateOf("") }
  var hips by remember { mutableStateOf("") }
  var muscle by remember { mutableStateOf("") }
  var neckArms by remember { mutableStateOf("") }
  var lookOptions by remember { mutableStateOf<Set<String>>(emptySet()) }
  var openGenres by remember { mutableStateOf<Set<String>>(emptySet()) }
  val partnerSheet = remember { mutableStateMapOf<String, String>() }
  var lookNotes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  // 自分で追加した項目のうち、選んだもの（id）
  var customPicked by remember { mutableStateOf<Set<String>>(emptySet()) }
  // 服装・アクセサリー
  var outfit by remember { mutableStateOf("") }
  var underwear by remember { mutableStateOf("") }
  var legwear by remember { mutableStateOf("") }
  var shoes by remember { mutableStateOf("") }
  var accessories by remember { mutableStateOf("") }
  var nails by remember { mutableStateOf("") }
  var makeup by remember { mutableStateOf("") }
  var lipColor by remember { mutableStateOf("") }
  // 内面・プロフィール
  var personality by remember { mutableStateOf("") }
  var speech by remember { mutableStateOf("") }
  var firstPerson by remember { mutableStateOf("") }
  var catchphrase by remember { mutableStateOf("") }
  var likes by remember { mutableStateOf("") }
  var dislikes by remember { mutableStateOf("") }
  var favoriteFood by remember { mutableStateOf("") }
  var occupation by remember { mutableStateOf("") }
  var hobbies by remember { mutableStateOf("") }
  var livingPlace by remember { mutableStateOf("") }
  var skills by remember { mutableStateOf("") }
  var secret by remember { mutableStateOf("") }
  var relations by remember { mutableStateOf("") }
  var treasured by remember { mutableStateOf("") }
  var background by remember { mutableStateOf("") }
  // 成人向け
  var adultLikes by remember { mutableStateOf("") }
  var weakSpots by remember { mutableStateOf("") }
  var nippleShape by remember { mutableStateOf("") }
  var nippleColor by remember { mutableStateOf("") }
  var kinks by remember { mutableStateOf("") }
  var dominance by remember { mutableStateOf("") }
  var experience by remember { mutableStateOf("") }
  var preferredPlay by remember { mutableStateOf("") }
  var adultUnderwear by remember { mutableStateOf("") }
  var moanStyle by remember { mutableStateOf("") }
  var climaxStyle by remember { mutableStateOf("") }
  var afterPlay by remember { mutableStateOf("") }
  var afterWords by remember { mutableStateOf("") }
  var secretKink by remember { mutableStateOf("") }
  var adultNg by remember { mutableStateOf("") }
  var customNegative by remember { mutableStateOf("") }
  // オプション
  // 設定画タイプ: "front" / "three_view" / "360" / "front_adult" / "three_view_adult" / "360_adult"
  var turnaround by remember { mutableStateOf("front") }
  // 立ち絵のポーズ（空 = まっすぐ立つ）。写真から創るときも、同じポーズ指定が効く
  var newPose by remember { mutableStateOf("") }
  var model by remember { mutableStateOf("wai") }
  // 作品・キャラ名で検索（AniList / MyAnimeList）
  var jikanOpen by remember { mutableStateOf(false) }
  // 声（キャラと一緒に決める）：声スタジオの声を選ぶか、説明で決める
  var newVoiceId by remember { mutableStateOf("") }
  var newVoiceCaption by remember { mutableStateOf("") }
  var newVoiceOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  LaunchedEffect(Unit) {
    newVoiceOptions =
      runCatching {
        val arr = JSONObject(viewModel.runGatewayRpc(GatewayMethod.VoiceList, "{}", timeoutMs = 60_000)).optJSONArray("voices") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
          val v = arr.getJSONObject(i)
          if ((v.optJSONArray("files")?.length() ?: 0) > 0) v.optString("id") to v.optString("name") else null
        }
      }.getOrDefault(emptyList())
  }
  var lookupMsg by remember { mutableStateOf("") }
  if (jikanOpen) {
    JikanSearchDialog(
      viewModel = viewModel,
      onFill = { fill ->
        fun pick(key: String): String = fill.optString(key).trim()
        if (pick("name").isNotEmpty()) name = pick("name")
        if (pick("gender").isNotEmpty()) gender = pick("gender")
        if (pick("age").isNotEmpty()) age = pick("age")
        if (pick("hair").isNotEmpty()) hair = pick("hair")
        if (pick("hairstyle").isNotEmpty()) hairstyle = pick("hairstyle")
        if (pick("eyes").isNotEmpty()) eyes = pick("eyes")
        if (pick("outfit").isNotEmpty()) outfit = pick("outfit")
        if (pick("features").isNotEmpty()) features = pick("features")
        if (pick("skin").isNotEmpty()) skin = pick("skin")
        if (pick("body").isNotEmpty()) body = pick("body")
        if (pick("accessories").isNotEmpty()) accessories = pick("accessories")
        if (pick("personality").isNotEmpty()) personality = pick("personality")
        if (pick("background").isNotEmpty()) background = pick("background")
        if (pick("speech").isNotEmpty()) speech = pick("speech")
        if (pick("firstPerson").isNotEmpty()) firstPerson = pick("firstPerson")
        if (pick("voiceCaption").isNotEmpty()) newVoiceCaption = pick("voiceCaption")
        lookupMsg = "✅ 「${pick("name")}」の設定を、下の欄に入れました。足りないところは書き足してください。"
        jikanOpen = false
      },
      onImage = { base64, bmp ->
        refImageUri = null
        refImageInlineBase64 = base64
        refImageBitmap = bmp
        refAnalysisMsg = "🖼️ 選んだ絵を参考写真にしました。下で絵柄と描く方法を選んで、名前を入れて「キャラクターを創る」を押してください。"
        jikanOpen = false
      },
      onClose = { jikanOpen = false },
    )
  }
  val ageNum = age.toIntOrNull() ?: 0
  LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onCancel)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "✨ キャラクターを創る", style = ClawTheme.type.title, color = ClawTheme.colors.text)
      }
    }
    item { Text(text = "書いたことは全部、絵の指示と人物設定に使います。空欄はAIが設定に合わせて補います。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted) }
    if (progress.isNotBlank()) item { Text(text = progress, style = ClawTheme.type.caption, color = ClawTheme.colors.primary) }

    // ── 項目の表示の大きさ ──
    item { ItemSizePicker(Modifier.fillMaxWidth()) }

    // ── 🖼️ 写真からキャラ作成 ──
    item {
      ClawPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(text = "🖼️ 写真からキャラ作成（顔写真・全身写真）", style = ClawTheme.type.section, color = ClawTheme.colors.text)
          Text(
            text = "顔写真や全身写真を選ぶと、その人と同じ顔・髪・服の、全身の設定画を作ります。下の設定は、あとで絵を作り直すときや、会話に使います（写真が優先されます）。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textMuted,
          )
          if (refAnalysisMsg.isNotBlank()) {
            Text(text = refAnalysisMsg, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
          }
          if (refImageBitmap != null) {
            Text(text = "元の画像は？（切り替えです。自動では判別しません）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
              listOf("photo" to "実写（写真）", "anime" to "アニメ・イラスト").forEach { (id, label) ->
                CreatorChip(label = label, selected = photoSource == id, onClick = { photoSource = id }, modifier = Modifier.weight(1f))
              }
            }
            Text(text = "どんな絵にする？", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
              listOf("photo" to "実写にする", "anime" to "アニメにする", "illustration" to "イラスト風").forEach { (id, label) ->
                CreatorChip(label = label, selected = photoStyle == id, onClick = { photoStyle = id }, modifier = Modifier.weight(1f))
              }
            }
            Text(
              text = when {
                photoSource == "photo" && photoStyle == "photo" -> "実写 → 実写：写真の人を、そのまま全身の写真にします。"
                photoSource == "photo" && photoStyle == "anime" -> "実写 → アニメ：写真の人を、アニメの絵にします。"
                photoSource == "anime" && photoStyle == "photo" -> "アニメ → 実写：アニメのキャラを、実在の人のような写真にします。"
                photoSource == "anime" && photoStyle == "anime" -> "アニメ → アニメ：アニメのキャラを、同じ姿のまま、整った設定画にします。"
                photoSource == "anime" -> "アニメ → イラスト風：同じキャラを、やわらかい色のイラストにします。"
                else -> "実写 → イラスト風：写真の人を、やわらかい色のイラストにします。"
              },
              style = ClawTheme.type.captionSmall,
              color = ClawTheme.colors.textMuted,
            )
            Text(text = "写真を追加（枚数制限なし・1枚目が本人の基準）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
              extraPhotos.forEachIndexed { index, (_, bmp) ->
                Box {
                  androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "追加の写真${index + 2}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                  )
                  Box(
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(ClawTheme.colors.canvas.copy(alpha = 0.9f))
                      .clickable { extraPhotos = extraPhotos.filterIndexed { i, _ -> i != index } },
                    contentAlignment = Alignment.Center,
                  ) { Text("✕", fontSize = 12.sp) }
                }
              }
              ClawSecondaryButton(text = "＋ 写真を追加", onClick = { extraPhotoPicker.launch("image/*") })
            }
            if (extraPhotos.isNotEmpty()) {
              Text(text = "複数の写真の使い方", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("subject" to "同じ人物", "blend" to "混ぜる", "roles" to "役割分担").forEach { (id, label) ->
                  CreatorChip(label = label, selected = photoMode == id, onClick = { photoMode = id }, modifier = Modifier.weight(1f))
                }
              }
              if (photoMode == "roles") {
                (0..extraPhotos.size).forEach { i ->
                  Text(text = if (i == 0) "1枚目（最初の写真）は" else "${i + 1}枚目は", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("顔", "髪", "服", "体型", "雰囲気").forEach { role ->
                      CreatorChip(
                        label = role,
                        selected = photoRoles.getOrNull(i) == role,
                        onClick = { photoRoles = photoRoles.toMutableList().also { list -> while (list.size <= i) list.add("顔"); list[i] = role } },
                        modifier = Modifier.weight(1f),
                      )
                    }
                  }
                }
              }
              Text(text = "各写真を解析し、1枚目を本人の基準にします。残りから顔・全身・服などを補える写真を選び、Qwen 2.1 は最大10枚、Edit 2509 は最大3枚を直接参照します。絵柄の変換で完全な一致を保証するものではありません。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            }
            Text(text = "仕上がりの画質", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
              listOf("normal" to "ふつう", "high" to "高画質（拡大と整えを足す・＋1〜2分）").forEach { (id, label) ->
                CreatorChip(label = label, selected = photoQuality == id, onClick = { photoQuality = id }, modifier = Modifier.weight(1f))
              }
            }
            Text(text = "描く方法（顔を写真に近づけたいほど、時間がかかります）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
            listOf(
              "fast" to "速い（約2分・写真との一致は弱め）",
              "precise" to "高精度（約5分・おすすめ）",
              "edit2509" to "顔の保持を重視する別モデル（Edit 2509・最大3枚）",
              "ipadapter" to "画像の特徴を参照（IP-Adapter Plus・顔と全身を参照）",
            ).forEach { (id, label) ->
              CreatorChip(label = label, selected = photoEngine == id, onClick = { photoEngine = id }, modifier = Modifier.fillMaxWidth())
            }
            if (photoEngine == "ipadapter") {
              Text(text = "1枚目と、その写真の顔の拡大を参照します。「混ぜる」は先頭4枚を使います。部位ごとの指定にはQwenを選んでください。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            }
            Text(
              text = "写真は、PC内のAIが先に読み取って（髪・目・肌・服・小物）、設定に自動で入れます。書いた設定は、そのまま優先されます。",
              style = ClawTheme.type.captionSmall,
              color = ClawTheme.colors.textMuted,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val bmp = refImageBitmap
            if (bmp != null) {
              androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "参考写真",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(80.dp)
                  .clip(RoundedCornerShape(8.dp)),
              )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              ClawSecondaryButton(
                text = if (refImageBitmap == null) "🖼️ 写真を選択する" else "🔄 別の写真に変える",
                onClick = { refImagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
              )
              if (analyzingRef) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ClawTheme.colors.primary, strokeWidth = 2.dp)
                  Text(text = refAnalysisMsg, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
                }
              }
            }
          }
        }
      }
    }

    // ── 🔎 .jikan：アニメ・マンガのキャラから創る ──
    item {
      ClawPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(text = "🔎 作品・人物・グループから探す", style = ClawTheme.type.section, color = ClawTheme.colors.text)
          Text(
            text = "作品やキャラの検索に加え、実在人物とアイドル・音楽・声優グループの公開資料も探せます。資料にある設定を下の欄に入れ、画像を参考写真として選べます。グループは掲載メンバーから選びます。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textMuted,
          )
          if (lookupMsg.isNotBlank()) Text(text = lookupMsg, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
          ClawSecondaryButton(text = "🔎 検索を開く", onClick = { jikanOpen = true }, modifier = Modifier.fillMaxWidth())
        }
      }
    }

    // ── 🎙️ 声 ──
    item {
      ClawPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(text = "🎙️ 声（キャラと一緒に決める）", style = ClawTheme.type.section, color = ClawTheme.colors.text)
          Text(
            text = "声スタジオで作った声や、クローンした声を選べます。選ばないときは、説明で決めます。あとで変えられます。パートナーにしたときも、この声が引き継がれます。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textMuted,
          )
          (listOf("" to "✏️ 説明で決める") + newVoiceOptions).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
              row.forEach { (id, label) ->
                CreatorChip(label = (if (newVoiceId == id) "✓ " else "") + label, selected = newVoiceId == id, onClick = { newVoiceId = id }, modifier = Modifier.weight(1f))
              }
              repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
          }
          if (newVoiceId.isBlank()) {
            ClawTextField(value = newVoiceCaption, onValueChange = { newVoiceCaption = it }, placeholder = "例: 25歳くらいの大人の女性。低めで気だるげな声", label = "声の説明", modifier = Modifier.fillMaxWidth())
          }
        }
      }
    }

    // ── 基本 ──
    item { FoldHeader("📌 基本", folded = "📌 基本" in folded, onToggle = { folded = if ("📌 基本" in folded) folded - "📌 基本" else folded + "📌 基本" }) }
    if ("📌 基本" !in folded) {
    item { ClawTextField(value = name, onValueChange = { name = it }, placeholder = "名前（例: リリア）", label = "名前", modifier = Modifier.fillMaxWidth()) }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("女性", "男性", "その他").forEach { g ->
          CreatorChip(label = g, selected = gender == g, onClick = { gender = g }, modifier = Modifier.weight(1f))
        }
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = age, onValueChange = { age = it.filter(Char::isDigit).take(3) }, placeholder = "年齢", label = "年齢", modifier = Modifier.weight(1f))
        ClawTextField(
          value = height,
          onValueChange = {
            val digits = it.filter(Char::isDigit).take(3)
            height = if ((digits.toIntOrNull() ?: 0) <= 200) digits else "200"
          },
          placeholder = "160",
          label = "身長（cm・最大200）",
          modifier = Modifier.weight(1f),
        )
      }
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "体型", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        BODY_TYPES.chunked(3).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { b -> CreatorChip(label = b, selected = body == b, onClick = { body = b }, modifier = Modifier.weight(1f)) }
          }
        }
      }
    }
    }

    // ── 見た目（顔・髪・体） ──
    item { FoldHeader("👁 見た目（顔・髪・体）", folded = "👁 見た目（顔・髪・体）" in folded, onToggle = { folded = if ("👁 見た目（顔・髪・体）" in folded) folded - "👁 見た目（顔・髪・体）" else folded + "👁 見た目（顔・髪・体）" }) }
    if ("👁 見た目（顔・髪・体）" !in folded) {
    item { ClawTextField(value = hair, onValueChange = { hair = it }, placeholder = "髪の色・長さ（例: 腰まである銀髪のストレート）", label = "髪の色・長さ", modifier = Modifier.fillMaxWidth()) }
    item { ClawTextField(value = hairstyle, onValueChange = { hairstyle = it }, placeholder = "髪型（例: ツインテール、お団子、左片寄せ、前髪ぱっつん）", label = "髪型", modifier = Modifier.fillMaxWidth()) }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = eyes, onValueChange = { eyes = it }, placeholder = "例: 青い目・少したれ目", label = "目の色・形", modifier = Modifier.weight(1f))
        ClawTextField(value = contactLens, onValueChange = { contactLens = it }, placeholder = "例: 赤いカラコン・ハートカラコン", label = "カラコン・コンタクト", modifier = Modifier.weight(1f))
      }
    }
    // 顔の印象
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "顔の印象", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        listOf("童顔・あどけない", "大人びた", "クール・冷たい系", "やさしい・柔らか系", "キリっとした", "ミステリアス").chunked(3).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { f -> CreatorChip(label = f, selected = faceType == f, onClick = { faceType = if (faceType == f) "" else f }, modifier = Modifier.weight(1f)) }
          }
        }
        // 自由入力で上書き可
        ClawTextField(value = faceType, onValueChange = { faceType = it }, placeholder = "自由入力（例: 垂れ目がちで柔らかい印象）", modifier = Modifier.fillMaxWidth())
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = skin, onValueChange = { skin = it }, placeholder = "例: 色白 / 日焼け / 褐色", label = "肌", modifier = Modifier.weight(1f))
        ClawTextField(value = breastSize, onValueChange = { breastSize = it }, placeholder = "例: 大きめ・Eカップ・爆乳", label = "胸のサイズ・形", modifier = Modifier.weight(1f))
      }
    }
    item { QuickPick("胸", BREAST_PICKS, breastSize) { breastSize = it } }
    item { QuickPick("ウエスト", WAIST_PICKS, waistHip) { waistHip = it } }
    item { QuickPick("お尻", HIPS_PICKS, hips) { hips = it } }
    item { QuickPick("脚", LEGS_PICKS, legs) { legs = it } }
    item { QuickPick("筋肉", MUSCLE_PICKS, muscle) { muscle = it } }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = hips, onValueChange = { hips = it }, placeholder = "例: 大きめ・丸い", label = "お尻（自由入力）", modifier = Modifier.weight(1f))
        ClawTextField(value = legs, onValueChange = { legs = it }, placeholder = "例: むっちりした太もも", label = "脚（自由入力）", modifier = Modifier.weight(1f))
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = muscle, onValueChange = { muscle = it }, placeholder = "例: うっすら腹筋", label = "筋肉（自由入力）", modifier = Modifier.weight(1f))
        ClawTextField(value = neckArms, onValueChange = { neckArms = it }, placeholder = "例: 細い首・なだらかな肩", label = "首・肩・腕", modifier = Modifier.weight(1f))
      }
    }
    item { ClawTextField(value = waistHip, onValueChange = { waistHip = it }, placeholder = "例: 細いウエスト・くびれがくっきり・大きめヒップ", label = "ウエスト・ヒップラインの印象", modifier = Modifier.fillMaxWidth()) }
    item { ClawTextField(value = features, onValueChange = { features = it }, placeholder = "特徴（例: エルフ耳、泣きぼくろ、角、ネコ耳、アホゲ）", label = "特徴（耳・しっぽ・角など）", modifier = Modifier.fillMaxWidth()) }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = bodyMarks, onValueChange = { bodyMarks = it }, placeholder = "例: 左腰にタトゥー、右目の下にほくろ", label = "タトゥー・ほくろ・傷", modifier = Modifier.weight(1f))
        ClawTextField(value = fragrance, onValueChange = { fragrance = it }, placeholder = "例: バニラの香り・石鹸系の香水", label = "香り", modifier = Modifier.weight(1f))
      }
    }
    }

    // ── 服装・アクセサリー ──
    item { FoldHeader("👗 服装・アクセサリー", folded = "👗 服装・アクセサリー" in folded, onToggle = { folded = if ("👗 服装・アクセサリー" in folded) folded - "👗 服装・アクセサリー" else folded + "👗 服装・アクセサリー" }) }
    if ("👗 服装・アクセサリー" !in folded) {
    item { ClawTextField(value = outfit, onValueChange = { outfit = it }, placeholder = "服装（例: 白と紫の魔導士のローブ、金の飾り、杖）", label = "服装（トップス・ボトムス・ワンピースなど）", minLines = 2, modifier = Modifier.fillMaxWidth()) }
    item { ClawTextField(value = underwear, onValueChange = { underwear = it }, placeholder = "下着（例: レースの白いブラ・ショーツ、赤いコーデ）", label = "下着（色・素材・デザイン）", modifier = Modifier.fillMaxWidth()) }
    item { ClawTextField(value = legwear, onValueChange = { legwear = it }, placeholder = "例: 黒いガーターベルト付きストッキング、白のニーハイ", label = "靴下・ニーハイ・ストッキング・ガーターベルト", modifier = Modifier.fillMaxWidth()) }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = shoes, onValueChange = { shoes = it }, placeholder = "例: 黒のピンヒール、メイドサンダル", label = "靴・ヒール・ブーツ", modifier = Modifier.weight(1f))
        ClawTextField(value = nails, onValueChange = { nails = it }, placeholder = "例: 赤ネイル、フレンチネイル", label = "ネイル", modifier = Modifier.weight(1f))
      }
    }
    item { ClawTextField(value = accessories, onValueChange = { accessories = it }, placeholder = "例: 黒ぶち眼鏡、銀の首輪、赤いリボン、こうもり型ヘアピン", label = "アクセサリー（眼鏡・首輪・イヤリング・こうもりなど）", modifier = Modifier.fillMaxWidth()) }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = makeup, onValueChange = { makeup = it }, placeholder = "例: 地雷系メイク（青クマ・涙袋）、ギャルメイク、清楚系", label = "メイク・化粧", modifier = Modifier.weight(1f))
        ClawTextField(value = lipColor, onValueChange = { lipColor = it }, placeholder = "例: 真っ赤なルージュ、ベビーピンクのグロス", label = "口紅の色・リップ", modifier = Modifier.weight(1f))
      }
    }
    }

    // ── 内面・プロフィール ──
    item { FoldHeader("💬 内面・プロフィール", folded = "💬 内面・プロフィール" in folded, onToggle = { folded = if ("💬 内面・プロフィール" in folded) folded - "💬 内面・プロフィール" else folded + "💬 内面・プロフィール" }) }
    if ("💬 内面・プロフィール" !in folded) {
    item { ClawTextField(value = personality, onValueChange = { personality = it }, placeholder = "性格（例: 物静かで知的。主人公の前でだけ甘える）", label = "性格", minLines = 2, modifier = Modifier.fillMaxWidth()) }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = speech, onValueChange = { speech = it }, placeholder = "例: 丁寧語、〜ですわ", label = "口調", modifier = Modifier.weight(2f))
        ClawTextField(value = firstPerson, onValueChange = { firstPerson = it }, placeholder = "私", label = "一人称", modifier = Modifier.weight(1f))
      }
    }
    item { ClawTextField(value = catchphrase, onValueChange = { catchphrase = it }, placeholder = "口癖（例: ふふ、またやらかしたんですか？）", label = "口癖", modifier = Modifier.fillMaxWidth()) }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = likes, onValueChange = { likes = it }, placeholder = "好きなもの", label = "好きなもの", modifier = Modifier.weight(1f))
        ClawTextField(value = dislikes, onValueChange = { dislikes = it }, placeholder = "苦手なもの", label = "苦手なもの", modifier = Modifier.weight(1f))
      }
    }
    item { ClawTextField(value = favoriteFood, onValueChange = { favoriteFood = it }, placeholder = "例: チョコレートケーキ、赤ワイン", label = "好きな食べ物・飲み物", modifier = Modifier.fillMaxWidth()) }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = occupation, onValueChange = { occupation = it }, placeholder = "例: 宮廷魔法師、図書館司書", label = "職業・立場", modifier = Modifier.weight(1f))
        ClawTextField(value = hobbies, onValueChange = { hobbies = it }, placeholder = "例: 読書、副業的剣術", label = "趣味", modifier = Modifier.weight(1f))
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = livingPlace, onValueChange = { livingPlace = it }, placeholder = "例: 宮城内の図書館附近の小部屋", label = "住んでいる場所", modifier = Modifier.weight(1f))
        ClawTextField(value = skills, onValueChange = { skills = it }, placeholder = "例: 沖縄、陰陽術の問調製作", label = "特技", modifier = Modifier.weight(1f))
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = secret, onValueChange = { secret = it }, placeholder = "秘密", label = "秘密", modifier = Modifier.weight(1f))
        ClawTextField(value = treasured, onValueChange = { treasured = it }, placeholder = "宝物", label = "一番大切なもの", modifier = Modifier.weight(1f))
      }
    }
    item { ClawTextField(value = relations, onValueChange = { relations = it }, placeholder = "例: 主人公のことは「局長」と呼ぶ", label = "関係・呼び方", modifier = Modifier.fillMaxWidth()) }
    item { ClawTextField(value = background, onValueChange = { background = it }, placeholder = "生い立ち・設定（例: 300歳のエルフ。森の村の守り手）", label = "生い立ち・設定", minLines = 2, modifier = Modifier.fillMaxWidth()) }
    }

    // ── 成人向け ──
    item { FoldHeader("🔞 成人向けの設定（モーションコミックで使う）", folded = "🔞 成人向けの設定（モーションコミックで使う）" in folded, onToggle = { folded = if ("🔞 成人向けの設定（モーションコミックで使う）" in folded) folded - "🔞 成人向けの設定（モーションコミックで使う）" else folded + "🔞 成人向けの設定（モーションコミックで使う）" }) }
    if ("🔞 成人向けの設定（モーションコミックで使う）" !in folded) {
    item { ClawTextField(value = adultLikes, onValueChange = { adultLikes = it }, placeholder = "好きなこと・体位（例: 騎乗位、感じると腰が動く）", label = "好きなこと・好きな体位", modifier = Modifier.fillMaxWidth()) }
    item { ClawTextField(value = weakSpots, onValueChange = { weakSpots = it }, placeholder = "感じやすいところ（例: 耳、乳首が特に弱い）", label = "弱いところ・感じやすいところ", modifier = Modifier.fillMaxWidth()) }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = nippleShape, onValueChange = { nippleShape = it }, placeholder = "例: 大きめ・コリっと・薄いピンク", label = "乳首の形", modifier = Modifier.weight(1f))
        ClawTextField(value = nippleColor, onValueChange = { nippleColor = it }, placeholder = "例: ピンク・薄桃色・褐色", label = "乳首の色", modifier = Modifier.weight(1f))
      }
    }
    item { ClawTextField(value = kinks, onValueChange = { kinks = it }, placeholder = "性癖（例: 言葉責め、見られることが好き）", label = "性癖", modifier = Modifier.fillMaxWidth()) }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = dominance, onValueChange = { dominance = it }, placeholder = "例: 受けだが自分から貼り付くことも", label = "攻め・受け", modifier = Modifier.weight(1f))
        ClawTextField(value = experience, onValueChange = { experience = it }, placeholder = "例: 経験豊富・主人公一筋で経験なし", label = "性経験・経験度", modifier = Modifier.weight(1f))
      }
    }
    item { ClawTextField(value = preferredPlay, onValueChange = { preferredPlay = it }, placeholder = "好きなプレイ（例: SM、調教、バニラ、リバース）", label = "好きなプレイの種類", modifier = Modifier.fillMaxWidth()) }
    item { ClawTextField(value = adultUnderwear, onValueChange = { adultUnderwear = it }, placeholder = "ランジェリー・部屋着（例: シアーな黒リボンボディスーツ）", label = "ランジェリー・部屋着", modifier = Modifier.fillMaxWidth()) }
    item { ClawTextField(value = moanStyle, onValueChange = { moanStyle = it }, placeholder = "喘ぎ方（例: 最初は静かだが、激しくなると声が出る）", label = "喘ぎ方・声の出し方", modifier = Modifier.fillMaxWidth()) }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawTextField(value = climaxStyle, onValueChange = { climaxStyle = it }, placeholder = "例: 全身が震え、無言で達する", label = "絶頂の仕方", modifier = Modifier.weight(1f))
        ClawTextField(value = afterPlay, onValueChange = { afterPlay = it }, placeholder = "例: ぎゅっとりして甘える", label = "事後の様子", modifier = Modifier.weight(1f))
      }
    }
    item { ClawTextField(value = afterWords, onValueChange = { afterWords = it }, placeholder = "事後に言うこと（例: 「もっと、してくれる？」）", label = "事後に言うこと・言葉", modifier = Modifier.fillMaxWidth()) }
    item { ClawTextField(value = secretKink, onValueChange = { secretKink = it }, placeholder = "本当は好きだけど言えない性癖", label = "本当は好きだけど言えない性癖", modifier = Modifier.fillMaxWidth()) }
    item { ClawTextField(value = adultNg, onValueChange = { adultNg = it }, placeholder = "NG（例: 粗暴な扱い、痛いことは苦手）", label = "苦手なこと・NG", modifier = Modifier.fillMaxWidth()) }
    }

    // ── 禁止タグ ──
    item { FoldHeader("⛔ 禁止タグ（画像・動画生成の追加NG）", folded = "⛔ 禁止タグ（画像・動画生成の追加NG）" in folded, onToggle = { folded = if ("⛔ 禁止タグ（画像・動画生成の追加NG）" in folded) folded - "⛔ 禁止タグ（画像・動画生成の追加NG）" else folded + "⛔ 禁止タグ（画像・動画生成の追加NG）" }) }
    if ("⛔ 禁止タグ（画像・動画生成の追加NG）" !in folded) {
    item { Text(text = "英語のタグをカンマ区切りで入力。入力したタグはこのキャラクターの絵・動画生成時に自動でネガティブに入ります。後から追加や削除もできます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textSubtle) }
    item { ClawTextField(value = customNegative, onValueChange = { customNegative = it }, placeholder = "例: glasses, twin tails, childlike, short hair", label = "追加の禁止タグ（英語・カンマ区切り）", minLines = 2, modifier = Modifier.fillMaxWidth()) }
    }

    // ── 外見オプション（ピアス・眼鏡・猫耳など。選んだものはこの子の絵にずっと付く） ──
    item { FoldHeader("💠 外見オプション（いくつでも）", folded = "💠 外見オプション（いくつでも）" in folded, onToggle = { folded = if ("💠 外見オプション（いくつでも）" in folded) folded - "💠 外見オプション（いくつでも）" else folded + "💠 外見オプション（いくつでも）" }) }
    if ("💠 外見オプション（いくつでも）" !in folded) {
    item {
      Text(
        text = "選んだものは、この子の絵にずっと付きます。大ジャンルを押すと開きます。「1つだけ」の分類は選び直すと置き換わり、「いくつでも」の分類は選ぶと下に次の欄が出ます。選んだ項目の下に出る入力欄には、「ひざから下」「赤色」などの条件を、日本語で書けます。拘束や状態など、場面ごとのオプションは、生成スタジオのモーションコミックで選びます。",
        style = ClawTheme.type.captionSmall,
        color = ClawTheme.colors.textMuted,
      )
    }
    CHARACTER_OPTION_CATALOG.forEach { genre ->
      item(key = "look-genre-${genre.name}") {
        val chosen = genre.parts.sumOf { part -> part.items.count { it.first in lookOptions } }
        val open = genre.name in openGenres
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth().clickable { openGenres = if (open) openGenres - genre.name else openGenres + genre.name },
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(text = "${if (open) "▼" else "▶"} ${genre.name}", style = ClawTheme.type.section, color = ClawTheme.colors.text)
            Text(text = if (chosen > 0) "${chosen}件を選択中" else "", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          }
          if (open) {
            genre.parts.forEach { part ->
              LookPartEditor(
                viewModel = viewModel,
                part = part,
                selected = lookOptions,
                notes = lookNotes,
                onSelected = { lookOptions = it },
                onNote = { id, text -> lookNotes = lookNotes + (id to text) },
              )
            }
          }
        }
      }
    }
    }

    // ── 自分で追加した項目（サンプル画像製造・クリエのチャットで足したもの）──
    item { FoldHeader("➕ 自分で追加した項目", folded = "➕ 自分で追加した項目" in folded, onToggle = { folded = if ("➕ 自分で追加した項目" in folded) folded - "➕ 自分で追加した項目" else folded + "➕ 自分で追加した項目" }) }
    if ("➕ 自分で追加した項目" !in folded) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          if (SampleStore.customItems.isEmpty()) {
            Text(text = "まだありません。サンプル画像製造の「追加した項目」か、クリエのチャットで足せます。", style = scaledItemStyle(ClawTheme.type.captionSmall), color = ClawTheme.colors.textMuted)
          }
          SampleStore.customItems.forEach { (id, label, instruction) ->
            CreatorChip(label = label, selected = id in customPicked, onClick = { customPicked = if (id in customPicked) customPicked - id else customPicked + id }, modifier = Modifier.fillMaxWidth())
            Text(text = instruction, style = scaledItemStyle(ClawTheme.type.captionSmall), color = ClawTheme.colors.textMuted)
          }
        }
      }
    }

    // ── パートナー用の詳細シート（話し方・内面・過去・価値観など。チャットのときに使う） ──
    item { FoldHeader("📖 パートナー用の詳細シート（チャット用・任意）", folded = "📖 パートナー用の詳細シート（チャット用・任意）" in folded, onToggle = { folded = if ("📖 パートナー用の詳細シート（チャット用・任意）" in folded) folded - "📖 パートナー用の詳細シート（チャット用・任意）" else folded + "📖 パートナー用の詳細シート（チャット用・任意）" }) }
    if ("📖 パートナー用の詳細シート（チャット用・任意）" !in folded) {
    item {
      Text(
        text = "お気に入りにしてチャットするときに使う設定です。書いた項目だけが、チャットの人物像に入ります。あとから図鑑でも書けます。",
        style = ClawTheme.type.captionSmall,
        color = ClawTheme.colors.textMuted,
      )
    }
    item(key = "partner-sheet-fields") { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { PartnerSheetFields(sheet = partnerSheet) } }
    }

    // ── 設定画・モデル ──
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "🎨 最初に描く絵", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        // 通常版（立ち絵 / 三面図 / 360度）
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          listOf(
            "front"      to "立ち絵（正面）",
            "three_view" to "三面図",
            "360"        to "360度",
          ).forEach { (id, label) ->
            CreatorChip(
              label = label,
              selected = turnaround == id,
              onClick = { turnaround = id },
              modifier = Modifier.weight(1f),
            )
          }
        }
        // 成人向け版
        Text(text = "🔞 成人向け裸体版も同時に作る", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          listOf(
            "front_adult"      to "🔞 裸立ち絵",
            "three_view_adult" to "🔞 裸三面図",
            "360_adult"        to "🔞 裸360度",
          ).forEach { (id, label) ->
            CreatorChip(
              label = label,
              selected = turnaround == id,
              onClick = { turnaround = id },
              modifier = Modifier.weight(1f),
            )
          }
        }
        if (turnaround.removeSuffix("_adult") == "front") {
          ClawTextField(value = newPose, onValueChange = { newPose = it }, placeholder = "例: 腰に手を当てて立つ / ベッドに座って脚を開く（空ならまっすぐ立つ）", label = "ポーズ（立ち絵・写真からでも効きます）", modifier = Modifier.fillMaxWidth())
        }
        Text(text = "📌 三面図・360度はモーションコミック時の時間削減に使われます。一度作れば何度でも再利用可能。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textSubtle)
        Text(text = "🖥 絵のモデル", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        GenStyleSwitch(viewModel = viewModel)
        GroupedModelPicker(viewModel = viewModel, selected = model, onSelect = { model = it })
        // 選んだモデルの情報。裸立ち絵を選んでいるのに、不得意なモデルのときは、おすすめへ切り替えられる
        ModelInfoCard(modelId = model, adultWanted = turnaround.endsWith("_adult"), onPick = { model = it })
        // 選んだモデルのサンプル（クリエの見た目で作った作例）
        SamplePreview(
          viewModel = viewModel,
          key = modelSampleKey(model),
          title = IMAGE_MODELS_FLAT.firstOrNull { it.first == model }?.second ?: model,
          prompt = KURIE_LOOK,
          modelId = model,
        )
        SamplePreview(
          viewModel = viewModel,
          key = modelSampleKey(model, adult = true),
          title = (IMAGE_MODELS_FLAT.firstOrNull { it.first == model }?.second ?: model) + "（成人向け）",
          prompt = "$KURIE_NUDE, standing, full body, front view",
          modelId = model,
        )
      }
    }

    item {
      ClawPrimaryButton(
        text = if (busy) "AIが見た目を考えています…" else "✨ このキャラクターを創る",
        enabled = !busy && name.isNotBlank(),
        onClick = {
          val spec = JSONObject()
            .put("gender", gender)
            .put("body", body)
          // 年齢は入力のまま送る。断るかどうかは Gateway の dist/blocked-terms.json（禁止する年齢）で決まる。
          if (ageNum > 0) spec.put("age", ageNum)
          height.toIntOrNull()?.takeIf { it in 50..200 }?.let { spec.put("heightCm", it) }
          if (lookOptions.isNotEmpty()) spec.put("lookOptions", lookOptions.joinToString(","))
          listOf(
            // 見た目（顔・髪・体）
            "hair" to hair, "hairstyle" to hairstyle, "eyes" to eyes,
            "contactLens" to contactLens, "faceType" to faceType, "skin" to skin,
            "breastSize" to breastSize, "waistHip" to waistHip,
            "legs" to legs, "hips" to hips, "muscle" to muscle, "neckArms" to neckArms,
            "features" to features, "bodyMarks" to bodyMarks, "fragrance" to fragrance,
            // 服装・アクセサリー
            "outfit" to outfit, "underwear" to underwear, "legwear" to legwear,
            "shoes" to shoes, "accessories" to accessories, "nails" to nails,
            "makeup" to makeup, "lipColor" to lipColor,
            // 内面・プロフィール
            "personality" to personality, "speech" to speech, "firstPerson" to firstPerson,
            "catchphrase" to catchphrase, "likes" to likes, "dislikes" to dislikes,
            "favoriteFood" to favoriteFood, "occupation" to occupation, "hobbies" to hobbies,
            "livingPlace" to livingPlace, "skills" to skills, "secret" to secret,
            "relations" to relations, "treasured" to treasured, "background" to background,
            // 成人向け
            "adultLikes" to adultLikes, "weakSpots" to weakSpots,
            "nippleShape" to nippleShape, "nippleColor" to nippleColor,
            "kinks" to kinks,
            "dominance" to dominance, "experience" to experience, "preferredPlay" to preferredPlay,
            "adultUnderwear" to adultUnderwear, "moanStyle" to moanStyle, "climaxStyle" to climaxStyle,
            "afterPlay" to afterPlay, "afterWords" to afterWords,
            "secretKink" to secretKink, "adultNg" to adultNg,
            // 禁止タグ
            "customNegative" to customNegative,
            "lookNotes" to listOf(lookNotesText(lookOptions, lookNotes))
              .plus(SampleStore.customItems.filter { it.first in customPicked }.map { "${it.second}：${it.third}" })
              .filter { it.isNotBlank() }
              .joinToString(" / "),
          ).filter { it.second.isNotBlank() }.forEach { spec.put(it.first, it.second.trim()) }
          // 描く設定画タイプを分解して送信
          val baseView = turnaround.removeSuffix("_adult").ifBlank { "front" }
          val isAdultSheet = turnaround.endsWith("_adult")
          val params = JSONObject()
            .put("name", name.trim())
            .put("spec", spec)
            .put("view", baseView)         // "front" / "three_view" / "360"
            .put("adult", isAdultSheet)    // 裸体版も作るか
            .put("pose", newPose.trim())
            .put("sheetValues", JSONObject().also { o -> partnerSheet.forEach { (k, v) -> if (v.isNotBlank()) o.put(k, v.trim()) } })
            .put("imageModel", model)
            .put("voiceId", newVoiceId)
            .put("voiceCaption", newVoiceCaption.trim())
            // 旧 turnaround flag も互換維持
            .put("turnaround", baseView != "front")
          // 参考写真が選択されている場合はBase64を添付（ゲートウェイでimg2img/Vision解析に利用）
          if (refImageBitmap != null) {
            params.put("photoStyle", photoStyle)
            params.put("photoSource", photoSource)
            params.put("photoEngine", photoEngine)
            params.put("photoMode", photoMode)
            params.put("photoRoles", JSONArray(photoRoles.take(1 + extraPhotos.size)))
            params.put("photoQuality", photoQuality)
          }
          refImageInlineBase64?.let { params.put("refImageBase64", it) }
          onCreate(params, (refImageUri?.let { listOf(it) } ?: emptyList()) + extraPhotos.map { photo -> photo.first })

        },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

private val CATALOG_OPTION_LABELS: Map<String, String> =
  (CHARACTER_OPTION_CATALOG.flatMap { it.parts }.flatMap { it.items } + LEGACY_OPTION_ITEMS).toMap()

private fun parseCatalogLookNotes(raw: String): Map<String, String> {
  val idsByLabel = CATALOG_OPTION_LABELS.entries.associate { it.value to it.key }
  return raw.split(" / ").mapNotNull { item ->
    val pieces = item.split('：', limit = 2)
    val id = idsByLabel[pieces.firstOrNull()?.trim()] ?: return@mapNotNull null
    val note = pieces.getOrNull(1)?.trim().orEmpty()
    if (note.isBlank()) null else id to note
  }.toMap()
}

private fun uncataloguedLookNotes(raw: String): List<String> {
  val knownLabels = CATALOG_OPTION_LABELS.values.toSet()
  return raw.split(" / ").map { it.trim() }.filter { item ->
    item.isNotBlank() && item.substringBefore('：').trim() !in knownLabels
  }
}

private fun catalogOptionIdsText(selected: Set<String>): String =
  (CHARACTER_OPTION_CATALOG.flatMap { it.parts }.flatMap { it.items } + LEGACY_OPTION_ITEMS)
    .map { it.first }
    .filter { it in selected }
    .joinToString(",")

private fun mergedCatalogLookNotes(selected: Set<String>, notes: Map<String, String>, preserved: List<String>): String =
  (listOf(lookNotesText(selected, notes)) + preserved).filter { it.isNotBlank() }.joinToString(" / ")

private data class CatalogFilter(val label: String, val keywords: List<String>)

private val CHARACTER_CATALOG_FILTERS =
  listOf(
    CatalogFilter("服・衣装", listOf("衣装", "トップス", "ボトムス", "ドレス", "アウター", "ファッション", "水着", "ランジェリー", "下着", "靴", "レッグウェア", "コスプレ", "生地", "柄", "作業着")),
    CatalogFilter("アクセサリー", listOf("アクセサリー", "持ち物", "小道具", "装備品", "帽子", "頭", "手・腕", "バッグ", "髪飾り", "眼鏡", "手袋", "マスク", "ピアス")),
    CatalogFilter("表情", listOf("表情", "目線", "しぐさ", "ポーズ")),
    CatalogFilter("髪・顔", listOf("髪", "顔", "瞳", "目・眉", "鼻・口", "メイク", "化粧")),
    CatalogFilter("体・特徴", listOf("体", "肌", "手・爪・足", "種族", "獣人", "身体", "タトゥー", "ボディ")),
    CatalogFilter("成人向け", listOf("成人", "アダルト", "SM", "フェチ", "拘束", "プレイ", "露出", "淫紋")),
    CatalogFilter("すべて", emptyList()),
  )

@Composable
private fun CharacterLibraryCatalogEditor(
  viewModel: MainViewModel,
  selected: Set<String>,
  notes: Map<String, String>,
  onSelected: (Set<String>) -> Unit,
  onNote: (String, String) -> Unit,
) {
  var filterLabel by remember { mutableStateOf("服・衣装") }
  var query by remember { mutableStateOf("") }
  var openGenres by remember { mutableStateOf<Set<String>>(emptySet()) }
  val activeFilter = CHARACTER_CATALOG_FILTERS.first { it.label == filterLabel }
  val trimmedQuery = query.trim()
  val filteredGenres =
    CHARACTER_OPTION_CATALOG.mapNotNull { genre ->
      if (trimmedQuery.isEmpty()) {
        if (activeFilter.keywords.isNotEmpty() && activeFilter.keywords.none { it in genre.name }) return@mapNotNull null
        return@mapNotNull genre
      }
      if (genre.name.contains(trimmedQuery, ignoreCase = true)) return@mapNotNull genre
      val parts = genre.parts.mapNotNull { part ->
        if (part.name.contains(trimmedQuery, ignoreCase = true)) part
        else part.copy(items = part.items.filter { (_, label) -> label.contains(trimmedQuery, ignoreCase = true) })
          .takeIf { it.items.isNotEmpty() }
      }
      genre.copy(parts = parts).takeIf { it.parts.isNotEmpty() }
    }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    CHARACTER_CATALOG_FILTERS.chunked(3).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { filter ->
          CreatorChip(
            label = filter.label,
            selected = filterLabel == filter.label,
            onClick = { filterLabel = filter.label; openGenres = emptySet() },
            modifier = Modifier.weight(1f),
          )
        }
        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
      }
    }
    ClawTextField(
      value = query,
      onValueChange = { query = it; openGenres = emptySet() },
      placeholder = "例: 赤いドレス、眼鏡、笑顔、ポニーテール",
      label = "項目を検索",
      modifier = Modifier.fillMaxWidth(),
    )
    if (filteredGenres.isEmpty()) {
      Text(text = "該当する項目がありません", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
    }
    filteredGenres.forEach { genre ->
      val chosen = genre.parts.sumOf { part -> part.items.count { it.first in selected } }
      val isOpen = genre.name in openGenres
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().clickable { openGenres = if (isOpen) openGenres - genre.name else openGenres + genre.name },
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(text = "${if (isOpen) "▼" else "▶"} ${genre.name}", style = ClawTheme.type.section, color = ClawTheme.colors.text)
          Text(text = if (chosen > 0) "${chosen}件" else "", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        }
        if (isOpen) {
          genre.parts.forEach { part ->
            LookPartEditor(
              viewModel = viewModel,
              part = part,
              selected = selected,
              notes = notes,
              onSelected = onSelected,
              onNote = onNote,
            )
          }
        }
      }
    }
  }
}

/** 外見オプションの小分類1つ分。プルダウンで選び、選んだ項目ごとに、すぐ下の入力欄で条件や追加の指定を書く。 */
@Composable
private fun LookPartEditor(
  viewModel: MainViewModel,
  part: CharacterOptionPart,
  selected: Set<String>,
  notes: Map<String, String>,
  onSelected: (Set<String>) -> Unit,
  onNote: (String, String) -> Unit,
) {
  // 見本の絵がある項目には、一覧で「🖼」が付く
  val labels = part.items.toMap().mapValues { (id, label) -> if (SampleStore.hasSample("opt:$id")) "$label 🖼" else label }
  val chosen = part.items.map { it.first }.filter { it in selected }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = "${part.name}（${if (part.single) "1つだけ" else "いくつでも"}）",
      style = ClawTheme.type.caption,
      color = ClawTheme.colors.text,
    )
    @Composable
    fun NoteField(id: String) {
      val sampleKey = "opt:$id"
      // サンプル画像製造で決めた既定の指示を、まだ何も書いていなければ入れる
      LaunchedEffect(id, SampleStore.defaults[sampleKey]) {
        val default = SampleStore.defaults[sampleKey]
        if (!default.isNullOrBlank() && notes[id].isNullOrBlank()) onNote(id, default)
      }
      // 選んだ項目の見本（クリエ）
      var sample by remember(sampleKey, SampleStore.available[sampleKey]) { mutableStateOf<Bitmap?>(null) }
      LaunchedEffect(sampleKey, SampleStore.available[sampleKey]) { sample = SampleStore.bitmap(viewModel, sampleKey) }
      sample?.let { bmp ->
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = "${labels[id] ?: id}の見本",
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(10.dp)),
        )
        Text(text = "見本（クリエ）", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      }
      // 選んだ項目の説明（どう見えるか）
      SampleStore.descriptions[sampleKey]?.takeIf { it.isNotBlank() }?.let {
        Text(text = "説明：$it", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      }
      ClawTextField(
        value = notes[id].orEmpty(),
        onValueChange = { onNote(id, it) },
        placeholder = "例: ひざから下、赤色、露出を多めに",
        label = "${labels[id] ?: id}の条件・追加の指定（日本語）",
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (part.single) {
      LookOptionDropdown(
        viewModel = viewModel,
        title = part.name,
        current = chosen.firstOrNull(),
        labels = labels,
        choices = part.items.map { it.first },
        emptyLabel = "選ばない",
        onPick = { onSelected(chooseCharacterOption(selected, part, chosen.firstOrNull(), it)) },
      )
      chosen.firstOrNull()?.let { NoteField(it) }
    } else {
      chosen.forEach { id ->
        LookOptionDropdown(
          viewModel = viewModel,
          title = part.name,
          current = id,
          labels = labels,
          choices = part.items.map { it.first }.filter { it == id || it !in selected },
          emptyLabel = "外す",
          onPick = { onSelected(chooseCharacterOption(selected, part, id, it)) },
        )
        NoteField(id)
      }
      val rest = part.items.map { it.first }.filter { it !in selected }
      if (rest.isNotEmpty()) {
        LookOptionDropdown(
          viewModel = viewModel,
          title = part.name,
          current = null,
          labels = labels,
          choices = rest,
          emptyLabel = if (chosen.isEmpty()) "選ぶ" else "もう1つ選ぶ",
          onPick = { if (it != null) onSelected(chooseCharacterOption(selected, part, null, it)) },
        )
      }
    }
  }
}

@Composable
private fun LookOptionDropdown(
  viewModel: MainViewModel,
  title: String,
  current: String?,
  labels: Map<String, String>,
  choices: List<String>,
  emptyLabel: String,
  onPick: (String?) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = (44 * ItemDisplay.scale).dp)
          .clip(RoundedCornerShape(10.dp))
          .background(if (current != null) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
          .clickable { expanded = true }
          .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = if (current != null) labels[current] ?: current else emptyLabel,
        style = ClawTheme.type.body.copy(fontSize = (13 * ItemDisplay.scale).sp, fontWeight = if (current != null) FontWeight.Bold else FontWeight.Medium),
        color = if (current != null) ClawTheme.colors.text else ClawTheme.colors.textMuted,
      )
      Text(text = "▼", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    }
    if (expanded) {
      // 全画面の一覧：検索・列の数・大きさ・見本の絵（名前の横に小さく、タップで全画面）
      ItemPickerDialog(
        viewModel = viewModel,
        title = title,
        items = choices.filter { it != current }.map { ChoiceItem(it, labels[it]?.removeSuffix(" 🖼") ?: it, "opt:$it") },
        current = current,
        clearLabel = emptyLabel,
        onPick = { expanded = false; onPick(it) },
        onDismiss = { expanded = false },
      )
    }
  }
}

@Composable
private fun CreatorChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .heightIn(min = 40.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
      color = if (selected) ClawTheme.colors.text else ClawTheme.colors.textMuted,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .heightIn(min = 44.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(
          if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f),
        )
        .border(
          width = if (selected) 1.5.dp else 1.dp,
          color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f),
          shape = RoundedCornerShape(10.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal),
      color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.text,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
  }
}

/** タップで開け閉めできる見出し。 */
@Composable
private fun FoldHeader(title: String, folded: Boolean, onToggle: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle).padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = title, style = scaledItemStyle(ClawTheme.type.section), color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
    Text(text = if (folded) "▶ 開く" else "▼ たたむ", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
  }
}
