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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val COMIC_POLL_MS = 5_000L
private val COMIC_GENRES = listOf("異世界転生", "ハーレム", "ラブコメ", "ファンタジー", "学園", "エロ", "バトル", "日常")

private data class ComicCast(
  val characterId: String,
  val name: String,
  val look: String,
  val profile: String,
  val sheetJobId: String,
  val hasLora: Boolean,
)

private data class ComicEpisodeRow(val no: Int, val title: String, val summary: String, val jobId: String)

private data class ComicSeriesRow(
  val id: String,
  val title: String,
  val genre: String,
  val premise: String,
  val rating: String,
  val imageModel: String,
  val cast: List<ComicCast>,
  val episodes: List<ComicEpisodeRow>,
)

private fun parseSeries(o: JSONObject): ComicSeriesRow {
  val cast = o.optJSONArray("cast") ?: JSONArray()
  val eps = o.optJSONArray("episodes") ?: JSONArray()
  return ComicSeriesRow(
    id = o.optString("id"),
    title = o.optString("title"),
    genre = o.optString("genre"),
    premise = o.optString("premise"),
    rating = o.optString("rating", "adult"),
    imageModel = o.optString("imageModel", "wai").ifBlank { "wai" },
    cast =
      (0 until cast.length()).map { i ->
        val c = cast.getJSONObject(i)
        ComicCast(
          characterId = c.optString("characterId"),
          name = c.optString("name"),
          look = c.optString("look"),
          profile = c.optString("profile"),
          sheetJobId = c.optString("sheetJobId"),
          hasLora = c.optString("lora").isNotBlank(),
        )
      },
    episodes =
      (0 until eps.length()).map { i ->
        val e = eps.getJSONObject(i)
        ComicEpisodeRow(e.optInt("no"), e.optString("title"), e.optString("summary"), e.optString("jobId"))
      },
  )
}

private fun decodeComicBitmap(base64: String, maxWidth: Int = 1080): Bitmap? =
  try {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
  } catch (_: Exception) {
    null
  }

@Composable
private fun ComicChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .heightIn(min = 40.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
        .border(
          width = if (selected) 1.5.dp else 1.dp,
          color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f),
          shape = RoundedCornerShape(10.dp),
        )
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

/** Serial comics: works with a fixed cast, character sheets, episodes read as vertical strips. */
@Composable
internal fun ComicStudioScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var seriesList by remember { mutableStateOf<List<ComicSeriesRow>>(emptyList()) }
  var library by remember { mutableStateOf<List<LibraryChara>>(emptyList()) }
  var tab by remember { mutableStateOf("works") }
  // ナビゲーションから「キャラ図鑑」などで直接開かれたとき
  val deepTarget = ShellDeepLink.pending
  androidx.compose.runtime.LaunchedEffect(deepTarget) {
    val target = deepTarget ?: return@LaunchedEffect
    if (target.startsWith("comic:")) {
      tab = target.removePrefix("comic:")
      ShellDeepLink.pending = null
    }
  }
  var openId by remember { mutableStateOf<String?>(null) }
  var creating by remember { mutableStateOf(false) }
  var reading by remember { mutableStateOf<ComicEpisodeRow?>(null) }
  var status by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  // Jobs started here (episode, sheet, redraw) and their progress lines.
  val jobs = remember { mutableStateMapOf<String, JSONObject>() }
  val images = remember { mutableStateMapOf<String, Bitmap>() }

  suspend fun rpc(method: GatewayMethod, params: JSONObject, timeoutMs: Long = 30_000): JSONObject =
    JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = timeoutMs))

  suspend fun loadLibrary() {
    runCatching { library = parseLibrary(rpc(GatewayMethod.ComicCharacterList, JSONObject()).optJSONArray("characters")) }
  }

  suspend fun loadList() {
    runCatching {
      val arr = rpc(GatewayMethod.ComicSeriesList, JSONObject()).optJSONArray("series") ?: JSONArray()
      seriesList = (0 until arr.length()).map { parseSeries(arr.getJSONObject(it)) }
    }.onFailure { status = "読み込みエラー: ${it.message}" }
    loadLibrary()
  }

  suspend fun image(jobId: String, part: Int = 0): Bitmap? {
    val key = "$jobId#$part"
    images[key]?.let { return it }
    val cached = java.io.File(context.cacheDir, "comic-sheet-$jobId-$part.img")
    if (cached.isFile && cached.length() > 0L) {
      val image = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(cached.absolutePath) }
      if (image != null) return image.also { images[key] = it }
      cached.delete()
    }
    return runCatching {
      val res = rpc(GatewayMethod.MediaJobResult, JSONObject().put("jobId", jobId).put("part", part), 120_000)
      withContext(Dispatchers.IO) {
        val encoded = res.optString("base64")
        val image = decodeComicBitmap(encoded)
        if (image != null) cached.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
        image
      }
    }.getOrNull()?.also { images[key] = it }
  }

  fun track(job: JSONObject) {
    jobs[job.optString("jobId")] = job
  }

  LaunchedEffect(Unit) { loadList() }
  // Training runs for about an hour; its progress comes from the library.
  val training = library.any { it.loraStatus == "training" || it.loraStatus == "queued" }
  LaunchedEffect(training) {
    while (training) {
      delay(15_000)
      loadLibrary()
    }
  }
  // Polls running jobs; when one ends the series is reloaded (new episode, sheet, redraw).
  LaunchedEffect(jobs.size) {
    while (jobs.values.any { it.optString("status") == "queued" || it.optString("status") == "running" }) {
      delay(COMIC_POLL_MS)
      for (id in jobs.keys.toList()) {
        val polled = runCatching { rpc(GatewayMethod.MediaJobGet, JSONObject().put("jobId", id)) }
        val now = polled.getOrNull() ?: run {
          // Gateway が再起動などで忘れたジョブは、いくら待っても出てこない。「生成中」のままにしない。
          if (polled.exceptionOrNull()?.message?.contains("見つかりません") == true) {
            jobs[id] =
              JSONObject()
                .put("status", "error")
                .put("error", "Gateway の再起動などで、この作業の記録が消えました。もう一度作ってください")
          }
          null
        } ?: continue
        val before = jobs[id]?.optString("status")
        jobs[id] = now
        if (before != now.optString("status") && now.optString("status") == "done") {
          images.keys.filter { it.startsWith(now.optJSONObject("comic")?.optString("targetJobId").orEmpty() + "#") }.forEach { images.remove(it) }
          loadList()
        }
      }
    }
  }

  BackHandler(enabled = reading != null || openId != null || creating) {
    when {
      reading != null -> reading = null
      creating -> creating = false
      else -> openId = null
    }
  }

  val open = seriesList.firstOrNull { it.id == openId }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
    when {
      reading != null && open != null ->
        ComicReader(
          episode = reading!!,
          series = open,
          load = { part -> image(reading!!.jobId, part) },
          partsOf = {
            runCatching { rpc(GatewayMethod.MediaJobGet, JSONObject().put("jobId", reading!!.jobId)).optInt("parts", 1) }
              .getOrDefault(1)
          },
          onRedraw = { panel ->
            scope.launch {
              runCatching {
                track(rpc(GatewayMethod.ComicPanelRedraw, JSONObject().put("jobId", reading!!.jobId).put("panel", panel)))
                status = "${panel}コマ目を描き直しています。終わると自動で入れ替わります。"
              }.onFailure { status = "描き直しエラー: ${it.message}" }
            }
          },
          redrawing = jobs.values.any {
            it.optJSONObject("comic")?.optString("targetJobId") == reading!!.jobId &&
              it.optString("status") in setOf("queued", "running")
          },
          status = status,
          onBack = { reading = null },
        )
      creating ->
        ComicCreateForm(viewModel = viewModel, 
          busy = busy,
          status = status,
          onBack = { creating = false },
          onCreate = { params ->
            scope.launch {
              busy = true
              status = "AIが登場人物を考えています（30秒〜1分）…"
              runCatching {
                val series = parseSeries(rpc(GatewayMethod.ComicSeriesCreate, params, 300_000).getJSONObject("series"))
                loadList()
                creating = false
                openId = series.id
                status = "「${series.title}」を作りました。登場人物を確かめて、設定画を描いてください。"
              }.onFailure { status = "作れませんでした: ${it.message}" }
              busy = false
            }
          },
        )
      open != null ->
        ComicSeriesDetail(
          series = open,
          library = library,
          jobs = jobs.values.toList(),
          status = status,
          busy = busy,
          loadImage = { jobId -> image(jobId) },
          onBack = { openId = null },
          onRead = { reading = it },
          onSaveCast = { cast ->
            scope.launch {
              runCatching {
                rpc(GatewayMethod.ComicSeriesUpdate, JSONObject().put("seriesId", open.id).put("cast", cast))
                loadList()
                status = "登場人物を保存しました"
              }.onFailure { status = "保存エラー: ${it.message}" }
            }
          },
          onRegenerateCast = {
            scope.launch {
              busy = true
              status = "AIが登場人物を考え直しています…"
              runCatching {
                rpc(GatewayMethod.ComicSeriesUpdate, JSONObject().put("seriesId", open.id).put("regenerateCast", true), 300_000)
                loadList()
                status = "登場人物を作り直しました"
              }.onFailure { status = "作り直せませんでした: ${it.message}" }
              busy = false
            }
          },
          onAddFromLibrary = { characterId ->
            scope.launch {
              runCatching {
                rpc(GatewayMethod.ComicSeriesCast, JSONObject().put("seriesId", open.id).put("characterId", characterId))
                loadList()
                status = "図鑑から登場人物を加えました"
              }.onFailure { status = "エラー: ${it.message}" }
            }
          },
          onSheet = { name ->
            scope.launch {
              runCatching {
                track(rpc(GatewayMethod.ComicSheetGenerate, JSONObject().put("seriesId", open.id).put("name", name)))
                status = "${name}の設定画を描いています（1枚30秒ほど）"
              }.onFailure { status = "設定画エラー: ${it.message}" }
            }
          },
          onChooseSheet = { jobId ->
            scope.launch {
              runCatching {
                rpc(GatewayMethod.ComicSheetChoose, JSONObject().put("jobId", jobId))
                loadList()
                status = "設定画を決めました"
              }.onFailure { status = "エラー: ${it.message}" }
            }
          },
          onEpisode = { request, fast ->
            scope.launch {
              runCatching {
                track(
                  rpc(
                    GatewayMethod.ComicEpisodeGenerate,
                    JSONObject().put("seriesId", open.id).put("request", request).put("fast", fast),
                  ),
                )
                status = "次の話を作り始めました（20〜30分。その間チャットは止まります）"
              }.onFailure { status = "作れませんでした: ${it.message}" }
            }
          },
          estimate = { fast ->
            runCatching {
              val res =
                rpc(
                  GatewayMethod.MediaEstimate,
                  JSONObject().put("kind", "comic").put("comicTask", "episode").put("comicSeriesId", open.id)
                    .put("imageModel", open.imageModel).put("fast", fast),
                )
              val total = res.optInt("totalSec")
              if (total > 0) "完成まで 約${(total + 59) / 60}分" else ""
            }.getOrDefault("")
          },
          onDelete = {
            scope.launch {
              runCatching { rpc(GatewayMethod.ComicSeriesDelete, JSONObject().put("seriesId", open.id)) }
              openId = null
              loadList()
            }
          },
        )
      else ->
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(text = "📚 連載コミック", style = ClawTheme.type.title, color = ClawTheme.colors.text)
              Text(
                text = "同じ登場人物で、縦読みの話を続けて作ります",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
            }
            ClawIconButton(icon = Icons.Default.Refresh, contentDescription = "更新", onClick = { scope.launch { loadList() } })
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ComicChip(label = "📚 作品 ${seriesList.size}", selected = tab == "works", onClick = { tab = "works" }, modifier = Modifier.weight(1f))
            ComicChip(label = "👥 キャラ図鑑 ${library.size}", selected = tab == "library", onClick = { tab = "library" }, modifier = Modifier.weight(1f))
          }
          if (tab == "library") {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
              ComicCharacterLibrary(
                viewModel = viewModel,
                characters = library,
                reload = { loadLibrary() },
                loadImage = { id -> image(id) },
                track = { track(it) },
                candidateSheets = { cid ->
                  jobs.values.filter { it.optString("status") == "done" && it.optJSONObject("comic")?.optString("characterId") == cid }
                    .map { it.optString("jobId") }
                },
              )
            }
          } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              item {
                ClawPrimaryButton(
                  text = "＋ 新しい作品を作る",
                  onClick = { creating = true },
                  modifier = Modifier.fillMaxWidth(),
                )
              }
              if (status.isNotBlank()) {
                item { Text(text = status, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted) }
              }
              if (seriesList.isEmpty()) {
                item {
                  Text(
                    text = "まだ作品がありません。「新しい作品を作る」から始めてください。",
                    style = ClawTheme.type.body,
                    color = ClawTheme.colors.textMuted,
                  )
                }
              }
              items(seriesList, key = { it.id }) { s ->
                ClawPanel(modifier = Modifier.fillMaxWidth().clickable { openId = s.id }) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    // The cast's faces, from the library.
                    s.cast.take(4).forEach { c ->
                      CharaIcon(library.firstOrNull { it.id == c.characterId }, c.name, 34)
                      Spacer(modifier = Modifier.width(2.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                      Text(text = s.title, style = ClawTheme.type.section, color = ClawTheme.colors.text)
                      Text(
                        text = listOf(s.genre, if (s.rating == "adult") "成人向け" else "全年齢", "全${s.episodes.size}話")
                          .filter { it.isNotBlank() }.joinToString("・"),
                        style = ClawTheme.type.caption,
                        color = ClawTheme.colors.textMuted,
                      )
                    }
                  }
                }
              }
            }
          }
        }
    }
  }
}

@Composable
private fun ComicCreateForm(viewModel: MainViewModel, busy: Boolean, status: String, onBack: () -> Unit, onCreate: (JSONObject) -> Unit) {
  var title by remember { mutableStateOf("") }
  var premise by remember { mutableStateOf("") }
  var genres by remember { mutableStateOf(setOf("異世界転生")) }
  var adult by remember { mutableStateOf(true) }
  var castSize by remember { mutableStateOf(3) }  // 1=ヒロインのみ, 2=主人公+ヒロイン, 3=AIおまかせ
  var model by remember { mutableStateOf("wai") }
  LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "新しい作品", style = ClawTheme.type.title, color = ClawTheme.colors.text)
      }
    }
    item { ClawTextField(value = title, onValueChange = { title = it }, placeholder = "題名（例: 転生したら最強でした）", modifier = Modifier.fillMaxWidth()) }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "ジャンル（複数OK）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        COMIC_GENRES.chunked(4).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { g ->
              ComicChip(
                label = g,
                selected = g in genres,
                onClick = { genres = if (g in genres) genres - g else genres + g },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }
    item {
      ClawTextField(
        value = premise,
        onValueChange = { premise = it },
        placeholder = "どんな話？ 主人公・世界・目的など（例: ブラック企業の社員が過労で死に、魔法の世界の辺境の村に転生。チート魔法で村を救い、エルフや女騎士に好かれていく）",
        minLines = 5,
        modifier = Modifier.fillMaxWidth(),
      )
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ComicChip(label = "🔞 成人向け", selected = adult, onClick = { adult = true }, modifier = Modifier.weight(1f))
        ComicChip(label = "全年齢", selected = !adult, onClick = { adult = false }, modifier = Modifier.weight(1f))
      }
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "👥 登場人物の数", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        listOf(
          1 to "1人（ヒロインのみ）",
          2 to "2人（主人公＋ヒロイン）",
          3 to "AIにおまかせ（3〜5人）",
        ).chunked(2).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { (n, label) ->
              ComicChip(label = label, selected = castSize == n, onClick = { castSize = n }, modifier = Modifier.weight(1f))
            }
            if (row.size < 2) Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "🖼️ 絵のモデル", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        GenStyleSwitch(viewModel = viewModel)
        GroupedModelPicker(viewModel = viewModel, selected = model, onSelect = { model = it })
        ModelInfoCard(modelId = model)
      }
    }
    item {
      Text(
        text = "作ると、AIが設定から登場人物（名前・見た目・性格・口調）を3〜5人考えます。あとで直せます。",
        style = ClawTheme.type.caption,
        color = ClawTheme.colors.textMuted,
      )
    }
    item {
      ClawPrimaryButton(
        text = if (busy) "作っています…" else "✨ この設定で作る",
        enabled = !busy && (title.isNotBlank() || premise.isNotBlank()),
        onClick = {
          onCreate(
            JSONObject()
              .put("title", title.trim())
              .put("genre", genres.joinToString("・"))
              .put("premise", premise.trim())
              .put("rating", if (adult) "adult" else "general")
              .put("imageModel", model)
              .put("castSize", castSize),
          )
        },
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (status.isNotBlank()) item { Text(text = status, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted) }
  }
}

@Composable
private fun ComicSeriesDetail(
  series: ComicSeriesRow,
  library: List<LibraryChara>,
  jobs: List<JSONObject>,
  status: String,
  busy: Boolean,
  loadImage: suspend (String) -> Bitmap?,
  onBack: () -> Unit,
  onRead: (ComicEpisodeRow) -> Unit,
  onSaveCast: (JSONArray) -> Unit,
  onRegenerateCast: () -> Unit,
  onAddFromLibrary: (String) -> Unit,
  onSheet: (String) -> Unit,
  onChooseSheet: (String) -> Unit,
  onEpisode: (String, Boolean) -> Unit,
  estimate: suspend (Boolean) -> String,
  onDelete: () -> Unit,
) {
  var request by remember(series.id) { mutableStateOf("") }
  var fast by remember(series.id) { mutableStateOf(false) }
  var editing by remember(series.id) { mutableStateOf<Map<String, Pair<String, String>>>(emptyMap()) }
  var confirmDelete by remember { mutableStateOf(false) }
  var picking by remember { mutableStateOf(false) }
  var estimateText by remember { mutableStateOf("") }
  val thumbs = remember { mutableStateMapOf<String, Bitmap>() }
  LaunchedEffect(series.id, fast, jobs.size) { estimateText = estimate(fast) }

  val running = jobs.filter { it.optString("status") in setOf("queued", "running") && it.optJSONObject("comic")?.optString("seriesId") == series.id }
  // Finished sheets of this series that are not yet chosen, newest first.
  val sheets =
    jobs.filter {
      it.optString("status") == "done" && it.optJSONObject("comic")?.optString("seriesId") == series.id &&
        it.optJSONObject("comic")?.optString("character").orEmpty().isNotBlank()
    }

  LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(text = series.title, style = ClawTheme.type.title, color = ClawTheme.colors.text)
          Text(
            text = listOf(series.genre, if (series.rating == "adult") "成人向け" else "全年齢").filter { it.isNotBlank() }.joinToString("・"),
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }
        ClawIconButton(icon = Icons.Default.Delete, contentDescription = "作品を削除", onClick = { confirmDelete = true })
      }
    }
    if (series.premise.isNotBlank()) {
      item { Text(text = series.premise, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, maxLines = 6) }
    }
    if (status.isNotBlank()) item { Text(text = status, style = ClawTheme.type.caption, color = ClawTheme.colors.primary) }
    running.forEach { job ->
      item {
        val what =
          when (job.optJSONObject("comic")?.let { if (it.has("character")) "sheet" else if (it.has("targetJobId")) "redraw" else "episode" } ?: "episode") {
            "sheet" -> "設定画"
            "redraw" -> "コマの描き直し"
            else -> "新しい話"
          }
        Text(
          text = "⏳ $what: ${job.optString("stage").ifBlank { "順番待ち" }}",
          style = ClawTheme.type.caption.copy(fontSize = 13.sp),
          color = ClawTheme.colors.primary,
        )
      }
    }

    item { Text(text = "登場人物", style = ClawTheme.type.section, color = ClawTheme.colors.text) }
    items(series.cast, key = { "${series.id}-${it.name}" }) { c ->
      val edit = editing[c.name]
      ClawPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            val entry = library.firstOrNull { it.id == c.characterId }
            CharaIcon(entry, c.name, 56)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = c.name + (entry?.let { "　" + loraBadge(it) } ?: ""),
                style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = ClawTheme.colors.text,
              )
              Text(text = c.profile, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            }
          }
          if (edit != null) {
            ClawTextField(
              value = edit.first,
              onValueChange = { editing = editing + (c.name to (it to edit.second)) },
              placeholder = "性格・口調",
              minLines = 2,
              modifier = Modifier.fillMaxWidth(),
            )
            ClawTextField(
              value = edit.second,
              onValueChange = { editing = editing + (c.name to (edit.first to it)) },
              placeholder = "見た目（英語のタグ: 1girl, long silver hair, ...）",
              minLines = 3,
              modifier = Modifier.fillMaxWidth(),
            )
          } else {
            Text(text = c.look, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textSubtle, maxLines = 3)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClawSecondaryButton(text = if (c.sheetJobId.isBlank()) "🎨 設定画を描く" else "🎨 描き直す", onClick = { onSheet(c.name) })
            ClawSecondaryButton(
              text = if (edit == null) "✏️ 直す" else "やめる",
              onClick = { editing = if (edit == null) editing + (c.name to (c.profile to c.look)) else editing - c.name },
            )
          }
          // Candidate sheets for this character: tap one to keep it.
          sheets.filter {
            val comic = it.optJSONObject("comic")
            (comic?.optString("characterId").orEmpty().ifBlank { null } ?: comic?.optString("character")) in setOf(c.characterId, c.name) &&
              it.optString("jobId") != c.sheetJobId
          }
            .take(3)
            .forEach { job ->
              val id = job.optString("jobId")
              LaunchedEffect(id) { loadImage(id)?.let { thumbs[id] = it } }
              thumbs[id]?.let { bmp ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "候補",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(8.dp)),
                  )
                  ClawPrimaryButton(text = "この設定画にする", onClick = { onChooseSheet(id) })
                }
              }
            }
        }
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (editing.isNotEmpty()) {
          ClawPrimaryButton(
            text = "登場人物を保存",
            onClick = {
              val arr = JSONArray()
              series.cast.forEach { c ->
                val e = editing[c.name]
                arr.put(
                  JSONObject().put("name", c.name).put("profile", e?.first ?: c.profile).put("look", e?.second ?: c.look),
                )
              }
              onSaveCast(arr)
              editing = emptyMap()
            },
          )
        }
        ClawSecondaryButton(text = "🔄 登場人物をAIで作り直す", enabled = !busy && series.episodes.isEmpty(), onClick = onRegenerateCast)
      }
    }
    item {
      ClawSecondaryButton(
        text = "👥 図鑑から登場人物を加える",
        enabled = library.any { l -> series.cast.none { it.characterId == l.id } },
        onClick = { picking = true },
      )
    }

    item { Text(text = "話（全${series.episodes.size}話）", style = ClawTheme.type.section, color = ClawTheme.colors.text) }
    items(series.episodes.reversed(), key = { it.jobId }) { e ->
      ClawPanel(modifier = Modifier.fillMaxWidth().clickable { onRead(e) }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(text = "第${e.no}話 ${e.title}", style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.text)
          Text(text = e.summary, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, maxLines = 3)
          Text(text = "▶ 読む", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
        }
      }
    }
    item {
      ClawPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(text = "第${series.episodes.size + 1}話を作る", style = ClawTheme.type.section, color = ClawTheme.colors.text)
          ClawTextField(
            value = request,
            onValueChange = { request = it },
            placeholder = "どんな話にする？（空欄ならAIにおまかせ。例: 女騎士ミラと出会って決闘する）",
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ComicChip(label = "きれい", selected = !fast, onClick = { fast = false }, modifier = Modifier.weight(1f))
            ComicChip(label = "速い（DMD2）", selected = fast, onClick = { fast = true }, modifier = Modifier.weight(1f))
          }
          if (series.cast.any { it.sheetJobId.isBlank() }) {
            Text(
              text = "設定画がまだの登場人物がいます。先に描いて決めておくと、見た目を確かめてから作れます。",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted,
            )
          }
          if (estimateText.isNotBlank()) Text(text = "⏱ $estimateText（24コマ）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
          ClawPrimaryButton(
            text = "✨ 次の話を作る",
            enabled = running.none { it.optJSONObject("comic")?.has("character") != true && it.optJSONObject("comic")?.has("targetJobId") != true },
            onClick = {
              onEpisode(request.trim(), fast)
              request = ""
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }

  if (picking) {
    AlertDialog(
      onDismissRequest = { picking = false },
      title = { Text("図鑑から加える") },
      text = {
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(library.filter { l -> series.cast.none { it.characterId == l.id } }, key = { it.id }) { l ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                picking = false
                onAddFromLibrary(l.id)
              }.padding(6.dp),
            ) {
              CharaIcon(l, l.name, 44)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(text = l.name, style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.text)
                Text(text = loraBadge(l), style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
              }
            }
          }
        }
      },
      confirmButton = { ClawSecondaryButton(text = "閉じる", onClick = { picking = false }) },
    )
  }
  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("作品を削除") },
      text = { Text("「${series.title}」の設定と登場人物を削除します。作った話は保存庫に残ります。") },
      confirmButton = { ClawPrimaryButton(text = "削除する", onClick = { confirmDelete = false; onDelete() }) },
      dismissButton = { ClawSecondaryButton(text = "やめる", onClick = { confirmDelete = false }) },
    )
  }
}

@Composable
private fun ComicReader(
  episode: ComicEpisodeRow,
  series: ComicSeriesRow,
  load: suspend (Int) -> Bitmap?,
  partsOf: suspend () -> Int,
  onRedraw: (Int) -> Unit,
  redrawing: Boolean,
  status: String,
  onBack: () -> Unit,
  scriptLines: List<String> = emptyList(),
  viewModel: ai.openclaw.app.MainViewModel? = null,
  partnerId: String = "",
) {
  var parts by remember(episode.jobId) { mutableStateOf(0) }
  val strips = remember(episode.jobId) { mutableStateMapOf<Int, Bitmap>() }
  var askPanel by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  // 口パク同期
  val isVoicePlaying by MotionComicVoicePlayer.isPlaying.collectAsStateWithLifecycle()
  val mouthShape by MotionComicVoicePlayer.mouthShape.collectAsStateWithLifecycle()
  var playingLineIndex by remember { mutableStateOf<Int?>(-1) }
  var panelText by remember { mutableStateOf("") }
  // Reload after a redraw finishes (the strips on the PC were rebuilt).
  LaunchedEffect(episode.jobId, redrawing) {
    if (redrawing) return@LaunchedEffect
    strips.clear()
    parts = partsOf()
    for (i in 0 until parts) load(i)?.let { strips[i] = it }
  }
  LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "第${episode.no}話 ${episode.title}", style = ClawTheme.type.section, color = ClawTheme.colors.text)
          Text(text = series.title, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        }
        ClawSecondaryButton(text = if (redrawing) "描き直し中…" else "コマを描き直す", enabled = !redrawing, onClick = { askPanel = true })
      }
    }
    if (status.isNotBlank()) item { Text(text = status, style = ClawTheme.type.caption, color = ClawTheme.colors.primary) }
    if (parts == 0 || strips.isEmpty()) {
      item { Text(text = "読み込み中…", style = ClawTheme.type.body, color = ClawTheme.colors.textMuted, modifier = Modifier.padding(24.dp)) }
    }
    items((0 until parts).toList(), key = { "${episode.jobId}-$it" }) { i ->
      strips[i]?.let { bmp ->
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = "第${episode.no}話 ${i + 1}",
          contentScale = ContentScale.FillWidth,
          modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1)),
        )
      }
    }
    // ── セリフ折りたたみパネル ──
    if (scriptLines.isNotEmpty()) {
      item {
        var showScript by remember { mutableStateOf(false) }
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
          ClawSecondaryButton(
            text = if (showScript) "▲ セリフを閉じる" else "▼ セリフを確認する（${scriptLines.size}行）",
            onClick = { showScript = !showScript },
            modifier = Modifier.fillMaxWidth(),
          )
          if (showScript) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              scriptLines.forEachIndexed { index, line ->
                val isThisLinePlaying = playingLineIndex == index && isVoicePlaying
                Row(
                  modifier = Modifier.fillMaxWidth()
                    .background(
                      if (isThisLinePlaying) ClawTheme.colors.primary.copy(alpha = 0.1f)
                      else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .padding(vertical = 2.dp),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.Top,
                ) {
                  Text(
                    text = "${index + 1}.",
                    style = ClawTheme.type.captionSmall,
                    color = ClawTheme.colors.textMuted,
                    modifier = Modifier.width(20.dp).padding(top = 2.dp),
                  )
                  // 口パク表示（再生中のみ）
                  if (isThisLinePlaying) {
                    Text(
                      text = when (mouthShape) {
                        MotionComicVoicePlayer.MouthShape.A -> "😮"
                        MotionComicVoicePlayer.MouthShape.I -> "😬"
                        MotionComicVoicePlayer.MouthShape.U -> "😙"
                        MotionComicVoicePlayer.MouthShape.E -> "😐"
                        MotionComicVoicePlayer.MouthShape.O -> "😮"
                        MotionComicVoicePlayer.MouthShape.BREATH -> "😮‍💨"
                        else -> "😶"
                      },
                      fontSize = 14.sp,
                    )
                  }
                  Text(
                    text = line,
                    style = ClawTheme.type.caption.copy(
                      color = if (isThisLinePlaying) ClawTheme.colors.primary else ClawTheme.colors.text
                    ),
                    color = if (isThisLinePlaying) ClawTheme.colors.primary else ClawTheme.colors.text,
                    modifier = Modifier.weight(1f),
                  )
                  // 🔊 読み上げボタン（PCボイス設定済みの場合）
                  if (viewModel != null && partnerId.isNotBlank()) {
                    Box(
                      modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                          if (isThisLinePlaying) ClawTheme.colors.primary else ClawTheme.colors.surfaceRaised
                        )
                        .clickable {
                          if (isThisLinePlaying) {
                            MotionComicVoicePlayer.stop()
                            playingLineIndex = -1
                          } else {
                            playingLineIndex = index
                            scope.launch {
                              try {
                                val params = org.json.JSONObject()
                                  .put("partnerId", partnerId)
                                  .put("text", line)
                                val res = org.json.JSONObject(
                                  viewModel.runGatewayRpc(
                                    ai.openclaw.app.gateway.GatewayMethod.PartnerTtsSynthesize,
                                    params.toString(),
                                    timeoutMs = 180_000,
                                  )
                                )
                                val audioB64 = res.optString("audioBase64")
                                if (audioB64.isNotBlank()) {
                                  MotionComicVoicePlayer.speakWithLipSync(
                                    context, audioB64, line, 2000L
                                  )
                                }
                              } catch (e: Exception) {
                                playingLineIndex = -1
                              }
                            }
                          }
                        },
                      contentAlignment = Alignment.Center,
                    ) {
                      Text(
                        text = if (isThisLinePlaying) "⏹" else "🔊",
                        fontSize = 12.sp,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
  if (askPanel) {
    AlertDialog(
      onDismissRequest = { askPanel = false },
      title = { Text("何コマ目を描き直しますか？") },
      text = {
        Column {
          Text("上から数えたコマの番号（1〜24）。絵だけを描き直し、台詞はそのままです。", color = ClawTheme.colors.textMuted)
          ClawTextField(value = panelText, onValueChange = { panelText = it.filter(Char::isDigit).take(2) }, placeholder = "例: 5")
        }
      },
      confirmButton = {
        ClawPrimaryButton(
          text = "描き直す",
          enabled = (panelText.toIntOrNull() ?: 0) in 1..30,
          onClick = {
            askPanel = false
            onRedraw(panelText.toInt())
            panelText = ""
          },
        )
      },
      dismissButton = { ClawSecondaryButton(text = "やめる", onClick = { askPanel = false }) },
    )
  }
}
