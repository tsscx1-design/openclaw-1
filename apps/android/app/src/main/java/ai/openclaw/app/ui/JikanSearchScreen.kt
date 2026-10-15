package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private enum class FinderMode(val label: String) {
  Title("📚 作品から"),
  Name("🔎 キャラ名から"),
  Game("🎮 ゲーム"),
  Vndb("📖 VNDB"),
  Person("👤 人物"),
  Group("👥 グループ"),
  Image("🖼️ 画像から"),
}

/**
 * 「.jikan」の専用画面：作品名やキャラ名で探して、一覧から選ぶだけでキャラの設定が入る。
 * アニメ・マンガの資料は AniList（つながらないときは Jikan＝MyAnimeList）から取ります。
 * 「画像から」は waifu.im のタグで絵を探し、選んだ絵を参考写真にします。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JikanSearchDialog(
  viewModel: MainViewModel,
  onFill: (JSONObject) -> Unit,
  onImage: (String, Bitmap) -> Unit,
  onClose: () -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(FinderMode.Title) }
    var query by remember { mutableStateOf("") }
    // 作品の絞り込み（一般／アダルト／すべて）と並び順（人気／新しい順）。キャラの画像も参考写真にするか
    var titleAdult by remember { mutableStateOf("all") }
    var characterAdult by remember { mutableStateOf(false) }
    var titleSort by remember { mutableStateOf("popular") }
    var useImage by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var titles by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var openTitle by remember { mutableStateOf<JSONObject?>(null) }
    var characters by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedFill by remember { mutableStateOf<JSONObject?>(null) }
    var selectedImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedImage by remember { mutableStateOf(0) }
    var selectedName by remember { mutableStateOf("") }
    var selectedExtra by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf("") }
    var selectedUrl by remember { mutableStateOf("") }
    var selectedHit by remember { mutableStateOf<JSONObject?>(null) }
    var voiceLinks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var voiceMessage by remember { mutableStateOf("") }
    var voiceConsent by remember { mutableStateOf(false) }
    var importedVoice by remember { mutableStateOf<JSONObject?>(null) }
    // 画像から
    var adult by remember { mutableStateOf("general") }
    var allTags by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var tagFilter by remember { mutableStateOf("") }
    var pickedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var images by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var imagePage by remember { mutableStateOf(1) }

    suspend fun rpc(method: GatewayMethod, params: JSONObject, timeoutMs: Long = 60_000): JSONObject =
      JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = timeoutMs))

    fun search() {
      val listOnly = mode == FinderMode.Title && (titleAdult == "adult" || titleSort == "newest")
      if (busy || (query.isBlank() && !listOnly)) return
      scope.launch {
        busy = true
        message = ""
        openTitle = null
        selectedFill = null
        selectedImages = emptyList()
        selectedHit = null
        voiceLinks = emptyList()
        voiceMessage = ""
        importedVoice = null
        runCatching {
          if (mode == FinderMode.Title) {
            val titleParams = JSONObject().put("query", query.trim()).put("adult", titleAdult).put("sort", titleSort)
            val rows = rpc(GatewayMethod.ComicCharacterTitleSearch, titleParams).optJSONArray("titles")
            titles = (0 until (rows?.length() ?: 0)).map { rows!!.getJSONObject(it) }
            characters = emptyList()
            if (titles.isEmpty()) message = "作品が見つかりませんでした。別の書き方（英語名やひらがな）でも試してください。"
          } else {
            val lookup = JSONObject().put("query", query.trim())
            if (mode == FinderMode.Game) lookup.put("site", "game")
            if (mode == FinderMode.Vndb) lookup.put("site", "vndb")
            if (mode == FinderMode.Person) lookup.put("site", "person")
            if (mode == FinderMode.Group) lookup.put("site", "group")
            if (mode == FinderMode.Name && characterAdult) lookup.put("adult", "adult")
            val rows = rpc(GatewayMethod.ComicCharacterLookup, lookup).optJSONArray("hits")
            characters = (0 until (rows?.length() ?: 0)).map { rows!!.getJSONObject(it) }
            titles = emptyList()
            if (characters.isEmpty()) message = if (mode == FinderMode.Name && characterAdult) "成人向け作品に登場すると確認できるキャラが見つかりませんでした。作品名でも試してください。" else "キャラが見つかりませんでした。作品名とキャラ名を分けて、別の書き方でも試してください。"
          }
        }.onFailure { message = "検索できませんでした：${it.message}" }
        busy = false
      }
    }

    fun openWork(title: JSONObject) {
      if (busy) return
      scope.launch {
        busy = true
        message = ""
        selectedFill = null
        selectedImages = emptyList()
        runCatching {
          val params = JSONObject().put("source", title.optString("source")).put("id", title.optInt("id"))
          val rows = rpc(GatewayMethod.ComicCharacterTitleCharacters, params).optJSONArray("hits")
          characters = (0 until (rows?.length() ?: 0)).map { rows!!.getJSONObject(it) }
          openTitle = title
          if (characters.isEmpty()) message = "この作品のキャラ一覧を取れませんでした。"
        }.onFailure { message = "読み込めませんでした：${it.message}" }
        busy = false
      }
    }

    fun pickCharacter(hit: JSONObject) {
      if (busy) return
      scope.launch {
        busy = true
        selectedHit = null
        voiceLinks = emptyList()
        voiceMessage = ""
        importedVoice = null
        if (hit.optString("publicKind") == "group") {
          selectedFill = null
          selectedImages = emptyList()
          message = "メンバーの公開資料を読み込んでいます"
          runCatching {
            val params = JSONObject().put("query", hit.optString("name")).put("source", hit.optString("source")).put("groupId", hit.optInt("id"))
            val result = rpc(GatewayMethod.ComicCharacterLookup, params, timeoutMs = 300_000)
            val rows = result.optJSONArray("hits")
            characters = (0 until (rows?.length() ?: 0)).map { rows!!.getJSONObject(it) }
            message = if (characters.isEmpty()) "掲載メンバーの個別資料が見つかりません。人物検索で名前を指定してください。" else "${hit.optString("name")} の掲載メンバーから選んでください。過去の所属を含む場合があります。"
            val extra = result.optString("extra")
            if (extra.isNotBlank()) message += "\n$extra"
          }.onFailure { message = "メンバーを取得できませんでした：${it.message}" }
          busy = false
          return@launch
        }
        message = "「${hit.optString("name")}」を読み取っています（数秒かかります）"
        runCatching {
          val params = JSONObject().put("query", query.trim()).put("source", hit.optString("source")).put("id", hit.optInt("id"))
          if (hit.has("externalId")) params.put("externalId", hit.optString("externalId"))
          rpc(GatewayMethod.ComicCharacterLookupFill, params, timeoutMs = 300_000)
        }.onSuccess { result ->
          val fill = result.optJSONObject("fill")
          if (fill != null) {
            selectedFill = fill
            selectedHit = hit
            selectedName = hit.optString("name")
            selectedExtra = result.optString("extra")
            selectedUrl = result.optString("url")
            selectedSource = when (hit.optString("source")) { "jikan" -> "MyAnimeList / Jikan"; "anilist" -> "AniList"; "vndb" -> "VNDB"; "wikipedia-ja", "wikipedia-en" -> "Wikipedia / Wikidata"; else -> "びしょげDB" }
            val rows = result.optJSONArray("imageUrls")
            selectedImages = (0 until (rows?.length() ?: 0)).map { rows!!.optString(it) }.filter { it.startsWith("https://") }
            selectedImage = 0
            message = if (hit.optString("source") == "vndb") "VNDB の設定を取り込めます。画像は取得しません。声優名から声質は推測しません。" else "画像を選んで設定を取り込めます。声質は資料に明記された場合だけ入ります。"
          } else {
            message = "読み取れませんでした。"
          }
        }.onFailure { message = "読み取れませんでした：${it.message}" }
        busy = false
      }
    }

    fun importSelected() {
      val fill = selectedFill ?: return
      if (busy) return
      scope.launch {
        busy = true
        val url = selectedImages.getOrNull(selectedImage)
        val picture = if (useImage && url != null) runCatching { loadReferencePicture(url) }.getOrNull() else null
        if (picture != null) onImage(picture.first, picture.second)
        onFill(fill)
        busy = false
      }
    }

    fun findImages(page: Int) {
      if (busy) return
      scope.launch {
        busy = true
        message = ""
        runCatching {
          val params = JSONObject().put("adult", adult).put("page", page).put("tags", org.json.JSONArray(pickedTags.toList()))
          val rows = rpc(GatewayMethod.ComicCharacterWaifuImages, params).optJSONArray("images")
          images = (0 until (rows?.length() ?: 0)).map { rows!!.getJSONObject(it) }
          imagePage = page
          if (images.isEmpty()) message = "絵が見つかりませんでした。タグを減らして試してください。"
        }.onFailure { message = "探せませんでした：${it.message}" }
        busy = false
      }
    }

    fun findVoiceSamples() {
      val hit = selectedHit ?: return
      if (busy) return
      scope.launch {
        busy = true
        voiceLinks = emptyList()
        voiceConsent = false
        voiceMessage = "公式サイトの資料を確認しています…"
        runCatching {
          rpc(
            GatewayMethod.VoiceSampleSearch,
            JSONObject().put("source", hit.optString("source")).put("id", hit.optInt("id")).put("query", hit.optString("name")),
            timeoutMs = 90_000,
          )
        }.onSuccess { result ->
          val rows = result.optJSONArray("links")
          voiceLinks = (0 until (rows?.length() ?: 0)).map { rows!!.getJSONObject(it) }
            .filter { it.optString("url").startsWith("https://") || it.optString("url").startsWith("http://") }.take(12)
          voiceMessage = result.optString("note")
          if (voiceLinks.isEmpty()) voiceMessage = "音声資料の候補が見つかりませんでした。時間を置いて再検索してください。\n$voiceMessage"
        }.onFailure { voiceMessage = "音声資料を探せませんでした：${it.message}" }
        busy = false
      }
    }

    fun importVoiceSample(link: JSONObject) {
      val hit = selectedHit ?: return
      val url = link.optString("directAudioUrl")
      if (busy || !voiceConsent || !link.optBoolean("verified") || url.isBlank()) return
      scope.launch {
        busy = true
        voiceMessage = "選択した音声を取り込んで解析しています…"
        runCatching {
          rpc(GatewayMethod.VoiceSampleImport,
            JSONObject().put("source", hit.optString("source")).put("id", hit.optInt("id"))
              .put("url", url).put("consent", voiceConsent), timeoutMs = 360_000)
        }.onSuccess { result ->
          val analysis = result.optJSONObject("analysis")
          importedVoice = result.optJSONObject("voice")
          if (importedVoice?.optString("status") == "failed") {
            voiceMessage = "音声の保存に失敗しました：${importedVoice?.optString("error").orEmpty().ifBlank { result.optString("analysisError") }}\n空き容量を確認して再度取り込んでください。"
            return@onSuccess
          }
          val caption = analysis?.optString("caption").orEmpty()
          if (caption.isNotBlank()) {
            selectedFill = JSONObject(selectedFill?.toString() ?: "{}").put("voiceCaption", caption)
          }
          val warnings = analysis?.optJSONArray("warnings")
          val warningText = (0 until (warnings?.length() ?: 0)).map { warnings!!.optString(it) }.joinToString("\n")
          val analysisError = result.optString("analysisError")
          voiceMessage = listOf(if (caption.isNotBlank()) "音声を保存しました。解析した声の設定を編集できます。" else "音声を保存しました。",
            if (analysisError.isNotBlank()) "解析に失敗しました：$analysisError\n保存した音声は音声作成画面で確認・再解析できます。" else if (caption.isBlank()) "声の文字設定が得られませんでした。音声作成画面で録音を確認してください。" else "",
            warningText).filter { it.isNotBlank() }.joinToString("\n")
        }.onFailure { voiceMessage = "音声を取り込めませんでした：${it.message}" }
        busy = false
      }
    }

    fun pickImage(image: JSONObject) {
      if (busy) return
      scope.launch {
        busy = true
        message = "絵を読み込んでいます"
        runCatching { loadReferencePicture(image.optString("url")) }.onSuccess { (base64, bmp) -> onImage(base64, bmp) }
          .onFailure { message = "絵を読み込めませんでした：${it.message}" }
        busy = false
      }
    }

    LaunchedEffect(mode) {
      if (mode == FinderMode.Image && allTags.isEmpty()) {
        runCatching {
          val rows = rpc(GatewayMethod.ComicCharacterWaifuTags, JSONObject()).optJSONArray("tags")
          allTags = (0 until (rows?.length() ?: 0)).map { rows!!.getJSONObject(it) }
        }.onFailure { message = "タグを読み込めませんでした：${it.message}" }
      }
    }

    Box(modifier = Modifier.fillMaxSize().background(ClawTheme.colors.canvas)) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically) {
            ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = {
              if (openTitle != null) {
                openTitle = null
                characters = emptyList()
              } else {
                onClose()
              }
            })
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "🔎 作品・人物・グループから創る", style = ClawTheme.type.title, color = ClawTheme.colors.text)
          }
        }
        item {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            FinderMode.entries.forEach { m ->
              FinderChip(label = m.label, selected = mode == m, onClick = {
                if (busy) return@FinderChip
                mode = m
                message = ""
                openTitle = null
                characters = emptyList()
                titles = emptyList()
                selectedFill = null
                selectedImages = emptyList()
                selectedHit = null
                voiceLinks = emptyList()
                voiceMessage = ""
                importedVoice = null
              })
            }
          }
        }
        if (mode != FinderMode.Image) {
          item {
            Text(
              text = when (mode) {
                FinderMode.Title -> "作品名を入れて探し、作品を選ぶと、出ているキャラの一覧が出ます。キャラを選ぶと、設定が自動で入ります。"
                FinderMode.Game -> "美少女ゲームのキャラを、キャラ名・読み・声優名・作品名・メーカー名で探します（びしょげDB）。選ぶと、名前や紹介が入ります。見た目の説明は、書かれていないので、空欄のままです。"
                FinderMode.Vndb -> "ビジュアルノベルのキャラを名前・別名・作品名で探します。VNDB の名前・プロフィール・登場作品・声優情報を取り込みます。画像は取得しません。非商用向けの公開APIです。"
                FinderMode.Person -> "実在人物の公開プロフィールを探します。資料にない性格・私生活・声質は推測しません。"
                FinderMode.Group -> "アイドル・音楽・声優グループを探し、掲載メンバーの人物資料を選びます。現在の所属と一致しない場合があります。"
                else -> "キャラ名または作品名で探します。成人向けを選ぶと、その作品への登場を確認できたキャラを表示します。"
              },
              style = ClawTheme.type.captionSmall,
              color = ClawTheme.colors.textMuted,
            )
          }
          item {
            ClawTextField(
              value = query,
              onValueChange = { query = it },
              placeholder = when (mode) { FinderMode.Title -> "例: Re:ゼロ、鬼滅の刃"; FinderMode.Game -> "例: 澪、声優名、作品名"; FinderMode.Vndb -> "例: 牧瀬紅莉栖、STEINS;GATE"; FinderMode.Person -> "例: 水瀬いのり、米津玄師"; FinderMode.Group -> "例: TrySail、乃木坂46"; else -> "例: レム、Re:ゼロ レム" },
              label = when (mode) { FinderMode.Title -> "作品名"; FinderMode.Game -> "名前・声優・作品など"; FinderMode.Vndb -> "キャラ名・別名・作品名"; FinderMode.Person -> "人物名"; FinderMode.Group -> "グループ名"; else -> "キャラ名" },
              modifier = Modifier.fillMaxWidth(),
            )
          }
          if (mode == FinderMode.Title) {
            item {
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("all" to "すべて", "general" to "一般", "adult" to "アダルト").forEach { (id, label) ->
                  FinderChip(label = label, selected = titleAdult == id, modifier = Modifier.weight(1f), onClick = { titleAdult = id })
                }
              }
            }
            item {
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("popular" to "人気順", "newest" to "公開日が新しい順").forEach { (id, label) ->
                  FinderChip(label = label, selected = titleSort == id, modifier = Modifier.weight(1f), onClick = { titleSort = id })
                }
              }
            }
            item {
              Text(text = "名前を空にして「アダルト」か「新しい順」を選ぶと、作品を公開日順にならべます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            }
          }
          if (mode == FinderMode.Name) {
            item {
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                FinderChip(label = "すべて", selected = !characterAdult, modifier = Modifier.weight(1f), onClick = { characterAdult = false })
                FinderChip(label = "🔞 成人向け作品", selected = characterAdult, modifier = Modifier.weight(1f), onClick = { characterAdult = true })
              }
            }
          }
          if (mode == FinderMode.Title || mode == FinderMode.Name || mode == FinderMode.Person || mode == FinderMode.Group) {
            item { FinderChip(label = if (useImage) "🖼️ キャラの画像も参考写真にする：入" else "🖼️ キャラの画像も参考写真にする：切", selected = useImage, modifier = Modifier.fillMaxWidth(), onClick = { useImage = !useImage }) }
          }
          item { ClawSecondaryButton(text = if (busy) "お待ちください…" else "🔎 探す", onClick = { search() }, modifier = Modifier.fillMaxWidth()) }
        }
        if (message.isNotBlank()) item { Text(text = message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary) }

        if (selectedFill != null && mode != FinderMode.Image) {
          item { Text(text = if (selectedImages.isEmpty()) "$selectedName の設定" else "🖼️ $selectedName の画像から選択", style = ClawTheme.type.section, color = ClawTheme.colors.text) }
          item { Text(text = "資料元: $selectedSource", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted) }
          if (selectedUrl.startsWith("https://")) item {
            ClawSecondaryButton(text = "資料元を開く", onClick = { uriHandler.openUri(selectedUrl) })
          }
          if (selectedExtra.isNotBlank()) item { Text(text = selectedExtra, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted) }
          item {
            ClawSecondaryButton(text = if (busy) "お待ちください…" else "音声サンプルを探す", onClick = { findVoiceSamples() }, modifier = Modifier.fillMaxWidth())
          }
          if (voiceMessage.isNotBlank()) item { Text(text = voiceMessage, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted) }
          if (importedVoice != null) item {
            Text(text = "${if (importedVoice?.optString("status") == "failed") "保存に失敗した音声" else "音声作成画面に保存済み"}：${importedVoice?.optString("name").orEmpty()}\n音声ID: ${importedVoice?.optString("id").orEmpty()}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          }
          if (voiceLinks.any { it.optString("directAudioUrl").isNotBlank() && it.optBoolean("verified") }) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(checked = voiceConsent, onCheckedChange = { if (!busy) voiceConsent = it })
              Text(text = "掲載ライセンスと利用条件を確認し、この音声を使用する権利・許可があります", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted, modifier = Modifier.weight(1f))
            }
          }
          items(voiceLinks) { link ->
            val verifiedSite = link.optString("kind") == "official-site" && link.optBoolean("verified")
            val publicAudio = link.optString("kind") == "public-audio" && link.optBoolean("verified")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(text = if (publicAudio) "Commons公開音声資料（公式配布とは限りません）" else if (verifiedSite) "公式サイトとして掲載（音声の有無は未確認）" else "検索結果（未確認）", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
              ClawSecondaryButton(text = link.optString("title"), onClick = {
                runCatching { uriHandler.openUri(link.optString("url")) }
                  .onFailure { voiceMessage = "リンクを開けませんでした：${it.message}" }
              }, modifier = Modifier.fillMaxWidth())
              Text(text = link.optString("note"), style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
              if (publicAudio) {
                Text(text = "ライセンス: ${link.optString("license")}\n${link.optString("attribution")}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
                val licenseUrl = link.optString("licenseUrl")
                if (licenseUrl.startsWith("https://") || licenseUrl.startsWith("http://")) ClawSecondaryButton(text = "ライセンスを確認", onClick = {
                  runCatching { uriHandler.openUri(licenseUrl) }.onFailure { voiceMessage = "ライセンスを開けませんでした：${it.message}" }
                })
                ClawSecondaryButton(text = if (busy) "解析中…" else if (!voiceConsent) "上の利用同意を確認してください" else "この音声を取り込んで解析", onClick = { importVoiceSample(link) }, modifier = Modifier.fillMaxWidth())
              }
            }
          }
          item {
            ClawTextField(value = selectedFill?.optString("voiceCaption").orEmpty(), onValueChange = {
              selectedFill = JSONObject(selectedFill?.toString() ?: "{}").put("voiceCaption", it)
            }, label = "声の詳しい文字設定", placeholder = "音声を解析すると声の特徴が入ります。編集して取り込めます。", modifier = Modifier.fillMaxWidth())
          }
          if (selectedImages.isNotEmpty()) {
            items(selectedImages.chunked(3)) { row ->
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { url ->
                  val index = selectedImages.indexOf(url)
                  Column(modifier = Modifier.weight(1f)) {
                    FinderThumb(url = url, modifier = Modifier.fillMaxWidth(), onClick = { selectedImage = index })
                    if (selectedImage == index) Text("✓ 選択中", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
                  }
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
              }
            }
          }
          item { ClawSecondaryButton(text = if (busy) "お待ちください…" else "このキャラの設定を取り込む", onClick = { importSelected() }, modifier = Modifier.fillMaxWidth()) }
        }

        if (mode == FinderMode.Title && openTitle == null) {
          items(titles) { title ->
            val date = title.optString("startDate").ifBlank { if (title.optInt("year") > 0) "${title.optInt("year")}年" else "" }
            val sub = listOf(if (date.isNotBlank()) "公開 $date" else "", if (title.optBoolean("isAdult")) "🔞アダルト" else "", title.optString("englishTitle"), title.optString("kind")).filter { it.isNotBlank() }.joinToString("　")
            FinderRow(title = title.optString("title"), sub = sub, imageUrl = title.optString("coverUrl"), onClick = { openWork(title) })
          }
        }
        if (openTitle != null) {
          item { Text(text = "📚 ${openTitle!!.optString("title")} の登場キャラ（選ぶと設定が入ります）", style = ClawTheme.type.section, color = ClawTheme.colors.text) }
        }
        if (mode != FinderMode.Image) {
          items(characters) { hit ->
            val gender = when (hit.optString("gender")) { "Female" -> "女性"; "Male" -> "男性"; else -> "" }
            val works = hit.optJSONArray("works")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }.orEmpty().firstOrNull().orEmpty()
            val adultWork = hit.optJSONArray("adultWorks")?.optString(0).orEmpty()
            val sub = listOf(hit.optString("englishName"), if (adultWork.isNotBlank()) "🔞 $adultWork" else works, gender, hit.optString("age"), hit.optString("extra"), hit.optString("description").take(40)).filter { it.isNotBlank() }.joinToString("　")
            FinderRow(title = hit.optString("name"), sub = sub, imageUrl = hit.optString("imageUrl"), onClick = { pickCharacter(hit) })
          }
        }

        if (mode == FinderMode.Image) {
          item {
            Text(
              text = "waifu.im の絵をタグで探します。選んだ絵が「参考写真」になり、その絵のキャラの設定画を作れます。絵の作者の権利は作者にあるので、参考にだけ使います。",
              style = ClawTheme.type.captionSmall,
              color = ClawTheme.colors.textMuted,
            )
          }
          item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
              listOf("general" to "一般", "adult" to "成人向け", "all" to "すべて").forEach { (id, label) ->
                FinderChip(label = label, selected = adult == id, modifier = Modifier.weight(1f), onClick = { adult = id })
              }
            }
          }
          item {
            ClawTextField(value = tagFilter, onValueChange = { tagFilter = it }, placeholder = "タグを絞り込む（例: waifu、genshin）", label = "タグ", modifier = Modifier.fillMaxWidth())
          }
          item {
            val shown = allTags.filter { tagFilter.isBlank() || it.optString("slug").contains(tagFilter.trim(), true) || it.optString("name").contains(tagFilter.trim(), true) }
              .sortedByDescending { it.optString("slug") in pickedTags }.take(40)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              shown.forEach { tag ->
                val slug = tag.optString("slug")
                FinderChip(
                  label = "${tag.optString("name")}（${tag.optInt("count")}）",
                  selected = slug in pickedTags,
                  onClick = { pickedTags = if (slug in pickedTags) pickedTags - slug else pickedTags + slug },
                )
              }
            }
          }
          item {
            ClawSecondaryButton(
              text = if (busy) "お待ちください…" else "🖼️ 絵を探す" + if (pickedTags.isNotEmpty()) "（${pickedTags.size}タグ）" else "（タグなし・おまかせ）",
              onClick = { findImages(1) },
              modifier = Modifier.fillMaxWidth(),
            )
          }
          items(images.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
              row.forEach { image -> FinderThumb(url = image.optString("url"), modifier = Modifier.weight(1f), onClick = { pickImage(image) }) }
              repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
          }
          if (images.isNotEmpty()) {
            item {
              ClawSecondaryButton(text = "次の絵を見る（${imagePage + 1}ページ目）", onClick = { findImages(imagePage + 1) }, modifier = Modifier.fillMaxWidth())
            }
          }
        }
        item { Spacer(modifier = Modifier.heightIn(min = 24.dp)) }
      }
    }
  }
}

@Composable
private fun FinderChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .heightIn(min = 40.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
      color = if (selected) ClawTheme.colors.text else ClawTheme.colors.textMuted,
    )
  }
}

@Composable
private fun FinderRow(title: String, sub: String, onClick: () -> Unit, imageUrl: String = "") {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (imageUrl.startsWith("https://")) {
      val bmp by rememberRemoteBitmap(imageUrl, 160)
      Box(modifier = Modifier.width(52.dp).aspectRatio(0.75f).clip(RoundedCornerShape(6.dp)).background(ClawTheme.colors.surface), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      }
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = title, style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.text)
      if (sub.isNotBlank()) Text(text = sub, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    }
  }
}

/** 画像のアドレスから、縮小した絵を取る（失敗したら null のまま）。 */
@Composable
private fun rememberRemoteBitmap(url: String, maxSide: Int): androidx.compose.runtime.State<Bitmap?> =
  produceState<Bitmap?>(initialValue = null, url) {
    value = withContext(Dispatchers.IO) {
      runCatching {
        val bytes = URL(url).openStream().use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / maxSide)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
      }.getOrNull()
    }
  }

/** 参考写真にする絵を取る：長辺 1024 まで縮め、JPEG の base64 と絵を返す。 */
private suspend fun loadReferencePicture(url: String): Pair<String, Bitmap> =
  withContext(Dispatchers.IO) {
    val bytes = URL(url).openStream().use { it.readBytes() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1024)
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
      ?: error("絵を開けませんでした")
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP) to bmp
  }

/** 一覧の小さな絵。アプリが直接ダウンロードして縮小して見せます。 */
@Composable
private fun FinderThumb(url: String, modifier: Modifier, onClick: () -> Unit) {
  val bitmap by produceState<Bitmap?>(initialValue = null, url) {
    value = withContext(Dispatchers.IO) {
      runCatching {
        val bytes = URL(url).openStream().use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 360)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
      }.getOrNull()
    }
  }
  Box(
    modifier = modifier.aspectRatio(0.75f).clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    val bmp = bitmap
    if (bmp != null) {
      Image(bitmap = bmp.asImageBitmap(), contentDescription = "候補の絵", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
      Text(text = "…", color = ClawTheme.colors.textMuted)
    }
  }
}
