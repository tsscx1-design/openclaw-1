package ai.openclaw.app.ui

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** 一覧の1行。 */
internal data class SampleRowItem(
  val key: String,
  val label: String,
  val group: String,
  val custom: Boolean = false,
  val instruction: String = "",
  /** 小分類（外見オプションだけ。例：脚の欠損・義足）。空なら小分類なし。 */
  val part: String = "",
  /** 日本語の説明（まとめて追加した項目だけ）。 */
  val ja: String = "",
  /** 説明の出典（タイトルとURL）。ネットで調べた項目だけ。 */
  val sources: List<Pair<String, String>> = emptyList(),
  /** 保存されている分類の全文（「大分類／小分類」）。直すときに使う。 */
  val rawGenre: String = "",
)

/** お気に入りの項目。端末に覚えておき、一覧では上に出し、「お気に入りだけ」でしぼれる。 */
internal object SampleFavorites {
  private const val PREFS = "sample_favorites"
  private const val KEY = "keys"
  var keys by mutableStateOf<Set<String>>(emptySet())
    private set
  private var loaded = false

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    keys = runCatching { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) }.getOrNull()?.toSet() ?: emptySet()
  }

  fun toggle(context: android.content.Context, key: String) {
    keys = if (key in keys) keys - key else keys + key
    runCatching { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putStringSet(KEY, keys).apply() }
  }
}

/** 大きな区切り（外見オプション／モーションコミック／体位／自分で追加）と、その中の小さな区切り。 */
internal data class SampleSection(val title: String, val groups: List<Pair<String, List<SampleRowItem>>>)

/** サーバーの一覧（sample.list の subjects）から、画面の区切りを作る。 */
internal fun buildSampleSections(subjects: JSONObject): List<SampleSection> {
  fun JSONArray?.objects(): List<JSONObject> = this?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } } ?: emptyList()
  val options =
    subjects.optJSONArray("options").objects().groupBy { it.optString("genre") }.map { (genre, rows) ->
      genre to rows.map { SampleRowItem(it.optString("key"), it.optString("label"), genre, part = it.optString("part")) }
    }
  val motion =
    subjects.optJSONArray("motion").objects().groupBy { it.optString("group") }.map { (group, rows) ->
      group to rows.map { SampleRowItem(it.optString("key"), it.optString("label"), group) }
    }
  val poses = listOf("体位" to subjects.optJSONArray("poses").objects().map { SampleRowItem(it.optString("key"), it.optString("label"), "体位") })
  val custom =
    // 「大分類／小分類」と保存されている分類は、大分類でまとめて、小分類でさらに分ける
    subjects.optJSONArray("custom").objects().groupBy { it.optString("genre").substringBefore("／") }.map { (genre, rows) ->
      genre to rows.map { row ->
        val sources = row.optJSONArray("sources").objects().map { it.optString("title") to it.optString("url") }.filter { it.second.startsWith("http") }
        val full = row.optString("genre")
        SampleRowItem("custom:${row.optString("id")}", row.optString("label"), genre, custom = true, instruction = row.optString("instruction"), part = full.substringAfter("／", ""), ja = row.optString("ja"), sources = sources, rawGenre = full)
      }
    }
  return listOf(
    SampleSection("🧍 キャラの外見オプション", options),
    SampleSection("🎞 モーションコミックのオプション", motion),
    SampleSection("🛏 体位", poses),
    SampleSection("➕ 自分で追加した項目", custom),
  )
}

/** ページ：名前、含める大きな区切りの番号、説明。キャラ図鑑用／モーション用／追加した項目に分ける。 */
internal val SAMPLE_PAGES: List<Triple<String, List<Int>, String>> =
  listOf(
    Triple("🧍 キャラ図鑑用", listOf(0), "キャラ作成・キャラ図鑑の設定（顔・髪・体・服・靴・メイク・アクセサリー・表情・拘束など）の見本です。"),
    Triple("🎞 モーション用", listOf(1, 2), "モーションコミックで選ぶオプション（拘束・SM・グッズ・状態・表情）と、体位の見本です。"),
    Triple("➕ 追加した項目", listOf(3), "自分で名前と指示を付けて足した項目です。右上の「＋ 追加する」から作れます。"),
  )

/** ページごとの大きな区切りを取り出す。 */
internal fun pageSections(sections: List<SampleSection>, page: Int): List<SampleSection> =
  SAMPLE_PAGES.getOrNull(page)?.second?.mapNotNull { sections.getOrNull(it) } ?: emptyList()

/** ページごとの項目数。 */
internal fun pageCounts(sections: List<SampleSection>): List<Int> =
  SAMPLE_PAGES.indices.map { page -> pageSections(sections, page).sumOf { section -> section.groups.sumOf { it.second.size } } }

/** 検索のことばで行をしぼる（ことばが空なら全部）。名前のほか、日本語の説明と指示でも探す。 */
internal fun filterSampleRows(rows: List<SampleRowItem>, query: String): List<SampleRowItem> {
  val word = query.trim()
  return if (word.isEmpty()) rows else rows.filter { it.label.contains(word, ignoreCase = true) || it.ja.contains(word, ignoreCase = true) || it.instruction.contains(word, ignoreCase = true) }
}

/** お気に入りを上に出す（それ以外の並び順は、そのまま）。「お気に入りだけ」のときは、それ以外を除く。 */
internal fun arrangeSampleRows(rows: List<SampleRowItem>, favorites: Set<String>, favoritesOnly: Boolean): List<SampleRowItem> =
  (if (favoritesOnly) rows.filter { it.key in favorites } else rows).sortedByDescending { it.key in favorites }

/**
 * サンプル画像製造。クリエの立ち絵（または成人向け立ち絵）を元に、項目ごとに「その項目を身につけた／
 * そうなったクリエ」の絵を作って、設定を選ぶときの見本にする。指示を書いて作り直せ、指示は既定にできる。
 * 無い項目は、名前と指示を入れて自分で追加できる。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SampleFactoryScreen(viewModel: MainViewModel, onBack: () -> Unit, initialPage: Int = 0) {
  BackHandler(onBack = onBack)
  val scope = rememberCoroutineScope()
  ItemDisplay.load(androidx.compose.ui.platform.LocalContext.current)
  SampleFavorites.load(androidx.compose.ui.platform.LocalContext.current)
  var favoritesOnly by remember { mutableStateOf(false) }
  // えらんだジャンル（null なら全部）と、作成の状態でのしぼりこみ（all / missing / made）
  var genreFilter by remember { mutableStateOf<String?>(null) }
  var statusFilter by remember { mutableStateOf("all") }

  var sections by remember { mutableStateOf<List<SampleSection>>(emptyList()) }
  var ownerName by remember { mutableStateOf("") }
  var hasStanding by remember { mutableStateOf(false) }
  var hasAdultBase by remember { mutableStateOf(false) }
  var base by remember { mutableStateOf("normal") }
  // 作る方法（モデル）。標準は Qwen Image 2.1
  var engine by remember { mutableStateOf("fast") }
  var query by remember { mutableStateOf("") }
  // 開いている小さな区切り（初めは全部たたんである）と、いま見ているページ
  var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
  var page by remember { mutableStateOf(initialPage) }
  var status by remember { mutableStateOf("") }
  var editing by remember { mutableStateOf<SampleRowItem?>(null) }
  var adding by remember { mutableStateOf<SampleRowItem?>(null) }
  var bulkOpen by remember { mutableStateOf(false) }
  var bulkBusy by remember { mutableStateOf(false) }
  var zoomKey by remember { mutableStateOf<String?>(null) }
  // まとめて作る前の確認：区切りの名前と、未作成の項目
  var batchAsk by remember { mutableStateOf<Pair<String, List<SampleRowItem>>?>(null) }

  suspend fun rpc(method: GatewayMethod, params: JSONObject, timeoutMs: Long = 60_000): JSONObject =
    JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = timeoutMs))

  suspend fun reload() {
    val res = SampleStore.refresh(viewModel) ?: run {
      status = "PC から一覧を読めませんでした（ゲートウェイを新しくしたか確認してください）"
      return
    }
    sections = buildSampleSections(res.getJSONObject("subjects"))
    val owner = res.optJSONObject("owner")
    ownerName = owner?.optString("name").orEmpty()
    hasStanding = owner?.optBoolean("hasStandingPicture") == true
    hasAdultBase = owner?.optBoolean("hasAdultBase") == true
  }

  LaunchedEffect(Unit) { reload() }

  // 生成は画面とは別（SampleJobs）で進む。他のタブへ移っても、最後まで続く。
  val activeJobs = SampleJobs.active
  val working: String? = activeJobs.firstOrNull()?.label
  LaunchedEffect(SampleJobs.finished.intValue) { if (SampleJobs.finished.intValue > 0) reload() }
  val shownStatus =
    if (activeJobs.isNotEmpty()) {
      "作っています：${activeJobs.first().label}（${activeJobs.first().stage}）" + if (activeJobs.size > 1) "　順番待ち ${activeJobs.size - 1} 件" else ""
    } else if (status.isNotBlank()) status else SampleJobs.message.value

  /** 区切りの未作成をまとめて作る。PCは1つの仕事として、2枚のGPUで分けて作る。 */
  fun makeBatch(groupKey: String, groupName: String, rows: List<SampleRowItem>) {
    val params = JSONObject().put("base", base).put("engine", engine)
    SampleJobs.enqueueBatch(viewModel, "batch:$groupKey", "$groupName（未作成 ${rows.size} 件をまとめて）", rows.map { it.key }, params)
  }

  fun makeSample(row: SampleRowItem, note: String?) {
    val params = JSONObject().put("key", row.key).put("base", base).put("engine", engine)
    if (note != null) params.put("note", note)
    SampleJobs.enqueue(viewModel, row.key, row.label, GatewayMethod.SampleGenerate, params)
  }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
          Column(modifier = Modifier.padding(start = 8.dp)) {
            Text("🖼 サンプル画像製造", style = ClawTheme.type.title, color = ClawTheme.colors.text)
            Text("設定を選ぶときに出る見本の絵を作ります", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
        }
      }
      item {
        ClawPanel(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              if (ownerName.isBlank()) "見本のモデル：（図鑑にクリエがいません）" else "見本のモデル：$ownerName　${if (hasStanding) "" else "（立ち絵が未設定）"}",
              style = ClawTheme.type.body,
              color = ClawTheme.colors.text,
            )
            Text("元にする絵", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              FactoryChip("立ち絵", base == "normal") { base = "normal" }
              FactoryChip("成人向け立ち絵", base == "adult") { base = "adult" }
            }
            Text("作る方法（モデル）", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              FactoryChip("標準：Qwen Image 2.1（約30秒）", engine == "fast") { engine = "fast" }
              FactoryChip("高精度：Qwen Image 2.1（約5分）", engine == "precise") { engine = "precise" }
              FactoryChip("別モデル：Qwen Image Edit 2509（初回は約11GBの取得）", engine == "edit2509") { engine = "edit2509" }
            }
            Text(
              when (engine) {
                "precise" -> "ステップを増やして、指示に忠実に描きます。手足をなくすなど、大きな変更に向きます。"
                "edit2509" -> "別の画像編集AIです。顔の保持が強いモデルで、初めて使うときに約11GBを取得します。"
                else -> "いつもの速い設定です。"
              },
              style = ClawTheme.type.captionSmall,
              color = ClawTheme.colors.textMuted,
            )
            if (base == "adult") {
              Text(
                if (hasAdultBase) "成人向けの基準画像：あります（立ち絵から作った服なしの絵）" else "成人向けの基準画像：まだありません。先に作ってください（約2分半）。",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
              ClawSecondaryButton(
                text = if (hasAdultBase) "成人向けの基準画像を作り直す" else "成人向けの基準画像を作る",
                enabled = !SampleJobs.isActive("adult-base") && hasStanding,
                onClick = { SampleJobs.enqueue(viewModel, "adult-base", "成人向けの基準画像", GatewayMethod.SampleAdultBaseGenerate, JSONObject()) },
                modifier = Modifier.fillMaxWidth(),
              )
            }
            ClawTextField(value = query, onValueChange = { query = it }, placeholder = "項目をさがす（名前・説明で。例: ブーツ）", modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              FactoryChip("★ お気に入りだけ（${SampleFavorites.keys.size}）", favoritesOnly) { favoritesOnly = !favoritesOnly }
            }
            ClawSecondaryButton(text = "📝 項目をまとめて追加（AIが分類）", onClick = { bulkOpen = true })
            ItemSizePicker(Modifier.fillMaxWidth())
            if (shownStatus.isNotBlank()) Text(shownStatus, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
          }
        }
      }

      item(key = "pages") {
        val counts = pageCounts(sections)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SAMPLE_PAGES.forEachIndexed { index, (title, _) -> FactoryChip("$title（${counts[index]}）", page == index) { page = index; genreFilter = null } }
        }
        Text(SAMPLE_PAGES[page].third, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      }

      // 項目が多いので、ジャンルを1つえらんで、そこだけ見られる。作成の状態でもしぼれる
      item(key = "genres") {
        val groupNames = pageSections(sections, page).flatMap { section -> section.groups.map { it.first to it.second.size } }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            FactoryChip("全部のジャンル", genreFilter == null) { genreFilter = null }
            for ((name, count) in groupNames) FactoryChip("$name（$count）", genreFilter == name) { genreFilter = if (genreFilter == name) null else name }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FactoryChip("すべて", statusFilter == "all") { statusFilter = "all" }
            FactoryChip("未作成だけ", statusFilter == "missing") { statusFilter = "missing" }
            FactoryChip("作成ずみだけ", statusFilter == "made") { statusFilter = "made" }
          }
        }
      }

      for (section in pageSections(sections, page)) {
        item(key = "section-${section.title}") {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(section.title, style = ClawTheme.type.section, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
            if (section.title.startsWith("➕")) {
              ClawSecondaryButton(text = "＋ 追加する", onClick = { adding = SampleRowItem("", "", "自分で追加", custom = true) })
            }
          }
        }
        for ((groupName, allRows) in section.groups) {
          if (genreFilter != null && genreFilter != groupName) continue
          val rows =
            arrangeSampleRows(filterSampleRows(allRows, query), SampleFavorites.keys, favoritesOnly).filter {
              when (statusFilter) {
                "missing" -> !SampleStore.hasSample(it.key)
                "made" -> SampleStore.hasSample(it.key)
                else -> true
              }
            }
          if (rows.isEmpty()) continue
          val groupKey = "${section.title}/$groupName"
          // ジャンルをえらんだときは、そのジャンルを開いて見せる
          val open = query.isNotBlank() || genreFilter == groupName || groupKey in expanded
          item(key = "group-$groupKey") {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { expanded = if (groupKey in expanded) expanded - groupKey else expanded + groupKey }.padding(vertical = 6.dp),
            ) {
              Text("$groupName（${rows.count { SampleStore.hasSample(it.key) }}/${rows.size}）", style = scaledItemStyle(ClawTheme.type.caption), color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
              // 未作成だけをまとめて作る（1つずつ作る方法も残してある）
              val missing = rows.filter { !SampleStore.hasSample(it.key) && !SampleJobs.isActive(it.key) }
              if (missing.isNotEmpty()) {
                TextButton(onClick = { batchAsk = groupKey to missing }) { Text("未作成 ${missing.size} 件をまとめて作る", style = ClawTheme.type.captionSmall) }
              }
              Text(if (open) "▼" else "▶", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            }
          }
          if (open) {
            // 大ジャンルの中を、小分類（例：脚の欠損・義足、靴、ソックス）に分けて、これもたためる
            rows.groupBy { it.part }.forEach { (part, partRows) ->
              val partKey = "$groupKey/$part"
              val partOpen = part.isBlank() || query.isNotBlank() || partKey in expanded
              if (part.isNotBlank()) {
                item(key = "part-$partKey") {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp).clip(RoundedCornerShape(8.dp)).clickable { expanded = if (partKey in expanded) expanded - partKey else expanded + partKey }.padding(vertical = 5.dp),
                  ) {
                    Text("${if (partOpen) "▼" else "▶"} $part（${partRows.count { SampleStore.hasSample(it.key) }}/${partRows.size}）", style = scaledItemStyle(ClawTheme.type.captionSmall), color = ClawTheme.colors.textMuted)
                  }
                }
              }
              if (partOpen) {
                // 「横にならべる列の数」（1〜3列）に合わせて、横に並べる
                val columns = ItemDisplay.columns
                items(partRows.chunked(columns), key = { chunk -> "row-" + chunk.joinToString { it.key } }) { chunk ->
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    chunk.forEach { row ->
                      Box(modifier = Modifier.weight(1f)) {
                        SampleRow(
                          viewModel = viewModel,
                          row = row,
                          busy = SampleJobs.isActive(row.key),
                          working = SampleJobs.isActive(row.key),
                          compact = columns > 1,
                          onMake = { makeSample(row, null) },
                          onEdit = { editing = row },
                          onZoom = { _ -> zoomKey = row.key },
                        )
                      }
                    }
                    repeat(columns - chunk.size) { Spacer(modifier = Modifier.weight(1f)) }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  editing?.let { row ->
    var note by remember(row.key) { mutableStateOf(SampleStore.defaults[row.key].orEmpty()) }
    AlertDialog(
      onDismissRequest = { editing = null },
      title = { Text(row.label) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("この絵への指示（日本語・自由入力）。例：赤色、ひざから下、露出を多めに", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          ClawTextField(value = note, onValueChange = { note = it }, placeholder = "指示を書く", minLines = 3, modifier = Modifier.fillMaxWidth())
          if (row.custom) Text("元の指示：${row.instruction}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        }
      },
      confirmButton = {
        Column(horizontalAlignment = Alignment.End) {
          TextButton(enabled = !SampleJobs.isActive(row.key), onClick = { editing = null; makeSample(row, note.trim()) }) { Text("この指示で作る") }
          TextButton(onClick = {
            scope.launch {
              runCatching { rpc(GatewayMethod.SampleDefaultSet, JSONObject().put("key", row.key).put("note", note.trim())) }
                .onSuccess { status = if (note.isBlank()) "既定の指示を消しました：${row.label}" else "既定にしました：${row.label}"; reload() }
                .onFailure { status = "保存できませんでした：${it.message}" }
              editing = null
            }
          }) { Text(if (note.isBlank()) "既定を消す" else "既定として保存") }
          if (row.custom) {
            TextButton(onClick = {
              val custom = row
              editing = null
              adding = custom
            }) { Text("名前・元の指示を直す") }
          }
          if (SampleStore.hasSample(row.key)) {
            TextButton(onClick = {
              scope.launch {
                runCatching { rpc(GatewayMethod.SampleDelete, JSONObject().put("key", row.key)) }
                SampleStore.forget(row.key)
                editing = null
                reload()
              }
            }) { Text("サンプル絵を消す") }
          }
        }
      },
      dismissButton = { TextButton(onClick = { editing = null }) { Text("閉じる") } },
    )
  }

  batchAsk?.let { (groupKey, rows) ->
    val groupName = groupKey.substringAfterLast("/")
    AlertDialog(
      onDismissRequest = { batchAsk = null },
      title = { Text("未作成をまとめて作る") },
      text = {
        Text(
          "「$groupName」の見本がまだない ${rows.size} 件を、続けて作ります。\n" +
            "すでにある見本は作り直しません。画質の設定（元にする絵・作る方法）は、いま選んでいるものを使います。\n" +
            "PCは2枚のGPUで分けて作るので、1つずつ作るより速く、作っている間は会話AIが止まります。\n" +
            "目安：1件あたり約1分（合計 約${rows.size}分）。",
        )
      },
      confirmButton = {
        TextButton(onClick = {
          makeBatch(groupKey, groupName, rows)
          batchAsk = null
        }) { Text("作る") }
      },
      dismissButton = { TextButton(onClick = { batchAsk = null }) { Text("やめる") } },
    )
  }

  if (bulkOpen) {
    var bulkText by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { if (!bulkBusy) bulkOpen = false },
      title = { Text("項目をまとめて追加") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("「、」「/」「・」「改行」で区切って書いてください。メインAIが大分類と小分類を選び、画像に反映できる具体的な見た目の指示を作ります。知らない言葉はネットで調べます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          ClawTextField(value = bulkText, onValueChange = { bulkText = it }, placeholder = "例: ナイトプール、水面の照明、ラッシュガード", minLines = 4, modifier = Modifier.fillMaxWidth())
          if (bulkBusy) Text("読み取り中です…（言葉が多いほど時間がかかります）", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
        }
      },
      confirmButton = {
        TextButton(enabled = bulkText.isNotBlank() && !bulkBusy, onClick = {
          scope.launch {
            bulkBusy = true
            runCatching { rpc(GatewayMethod.SampleCustomBulk, JSONObject().put("text", bulkText), timeoutMs = 900_000) }
              .onSuccess { res ->
                val rows = res.optJSONArray("results")
                val lines = (0 until (rows?.length() ?: 0)).map { i ->
                  val r = rows!!.getJSONObject(i)
                  val mark = when (r.optString("status")) { "added" -> "✅"; "duplicate" -> "（すでにあります）"; else -> "❌" }
                  val detail = r.optString("instruction").take(80)
                  "$mark ${r.optString("label")} → ${r.optString("genre")}：$detail" + if (r.optBoolean("searched")) "（ネットで調べました）" else ""
                }
                status = lines.joinToString("\n")
                bulkText = ""
                bulkOpen = false
                reload()
              }
              .onFailure { status = "追加できませんでした：${it.message}" }
            bulkBusy = false
          }
        }) { Text("追加") }
      },
      dismissButton = { TextButton(enabled = !bulkBusy, onClick = { bulkOpen = false }) { Text("やめる") } },
    )
  }

  adding?.let { row ->
    var genre by remember(row.key) { mutableStateOf(if (row.key.isBlank()) "自分で追加" else row.rawGenre.ifBlank { row.group }) }
    var label by remember(row.key) { mutableStateOf(row.label) }
    var instruction by remember(row.key) { mutableStateOf(row.instruction) }
    val isNew = row.key.isBlank()
    AlertDialog(
      onDismissRequest = { adding = null },
      title = { Text(if (isNew) "項目を追加する" else "項目を直す") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ClawTextField(value = genre, onValueChange = { genre = it }, placeholder = "例: 自分で追加", label = "分類", modifier = Modifier.fillMaxWidth())
          ClawTextField(value = label, onValueChange = { label = it }, placeholder = "例: 猫耳メイド服", label = "名前", modifier = Modifier.fillMaxWidth())
          ClawTextField(value = instruction, onValueChange = { instruction = it }, placeholder = "どんな絵にするか（日本語でも英語でも）", label = "指示", minLines = 3, modifier = Modifier.fillMaxWidth())
        }
      },
      confirmButton = {
        TextButton(enabled = label.isNotBlank() && instruction.isNotBlank(), onClick = {
          scope.launch {
            runCatching {
              if (isNew) rpc(GatewayMethod.SampleCustomAdd, JSONObject().put("genre", genre).put("label", label).put("instruction", instruction))
              else rpc(GatewayMethod.SampleCustomUpdate, JSONObject().put("id", row.key.removePrefix("custom:")).put("genre", genre).put("label", label).put("instruction", instruction))
            }.onSuccess { status = if (isNew) "追加しました：$label" else "直しました：$label"; reload() }
              .onFailure { status = "保存できませんでした：${it.message}" }
            adding = null
          }
        }) { Text(if (isNew) "追加" else "保存") }
      },
      dismissButton = {
        Row {
          if (!isNew) {
            TextButton(onClick = {
              scope.launch {
                runCatching { rpc(GatewayMethod.SampleCustomDelete, JSONObject().put("id", row.key.removePrefix("custom:"))) }
                SampleStore.forget(row.key)
                adding = null
                reload()
              }
            }) { Text("この項目を消す") }
          }
          TextButton(onClick = { adding = null }) { Text("やめる") }
        }
      },
    )
  }

  zoomKey?.let { start ->
    // 作ってあるサンプルだけを、画面の並び順で、左右にスライドして見られる
    val made = sections.flatMap { it.groups }.flatMap { it.second }.distinctBy { it.key }.filter { SampleStore.hasSample(it.key) }.map { it.key to it.label }
    SampleGalleryDialog(viewModel = viewModel, items = made, startKey = start, onDismiss = { zoomKey = null })
  }
}

@Composable
private fun SampleRow(
  viewModel: MainViewModel,
  row: SampleRowItem,
  busy: Boolean,
  working: Boolean,
  onMake: () -> Unit,
  onEdit: () -> Unit,
  onZoom: (Bitmap) -> Unit,
  compact: Boolean = false,
) {
  val has = SampleStore.hasSample(row.key)
  val scale = ItemDisplay.scale
  val context = androidx.compose.ui.platform.LocalContext.current
  val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
  val favorite = row.key in SampleFavorites.keys
  val star = if (favorite) "★" else "☆"
  var bitmap by remember(row.key, SampleStore.available[row.key]) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(row.key, SampleStore.available[row.key]) { bitmap = if (has) SampleStore.bitmap(viewModel, row.key) else null }
  ClawPanel(modifier = Modifier.fillMaxWidth()) {
    if (compact) {
      // 2〜3列のときは、縦に並べる：見本の絵、名前、ボタン
      Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
          modifier = Modifier.size((56 * scale).dp).clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).clickable(enabled = bitmap != null) { bitmap?.let(onZoom) },
          contentAlignment = Alignment.Center,
        ) {
          val shown = bitmap
          if (shown != null) Image(bitmap = shown.asImageBitmap(), contentDescription = row.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
          else Text(if (working) "⏳" else "未作成", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        }
        Text(row.label, style = scaledItemStyle(ClawTheme.type.body.copy(fontSize = 13.sp)), color = ClawTheme.colors.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          ClawSecondaryButton(text = star, onClick = { SampleFavorites.toggle(context, row.key) })
          ClawSecondaryButton(text = if (has) "作り直す" else "作る", enabled = !busy, onClick = onMake)
          ClawSecondaryButton(text = "指示", onClick = onEdit)
        }
      }
      return@ClawPanel
    }
    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(
        modifier = Modifier.size((72 * scale).dp).clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).clickable(enabled = bitmap != null) { bitmap?.let(onZoom) },
        contentAlignment = Alignment.Center,
      ) {
        val shown = bitmap
        if (shown != null) {
          Image(bitmap = shown.asImageBitmap(), contentDescription = row.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
          Text(if (working) "⏳" else "未作成", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(row.label, style = scaledItemStyle(ClawTheme.type.body), color = ClawTheme.colors.text)
        (SampleStore.descriptions[row.key]?.takeIf { it.isNotBlank() } ?: row.ja.takeIf { it.isNotBlank() })?.let {
          Text("説明：$it", style = scaledItemStyle(ClawTheme.type.captionSmall), color = ClawTheme.colors.textMuted, maxLines = 4)
        }
        // ネットで調べた項目は、読んだページ（出典）を押して開ける
        row.sources.take(3).forEach { (title, url) ->
          Text(
            "出典：${title.take(40)}",
            style = scaledItemStyle(ClawTheme.type.captionSmall),
            color = ClawTheme.colors.primary,
            maxLines = 1,
            modifier = Modifier.clickable { runCatching { uriHandler.openUri(url) } },
          )
        }
        SampleStore.defaults[row.key]?.takeIf { it.isNotBlank() }?.let {
          Text("既定の指示：$it", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted, maxLines = 2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ClawSecondaryButton(text = star, onClick = { SampleFavorites.toggle(context, row.key) })
          ClawSecondaryButton(text = if (has) "作り直す" else "作る", enabled = !busy, onClick = onMake)
          ClawSecondaryButton(text = "指示", onClick = onEdit)
        }
      }
    }
  }
}

@Composable
private fun FactoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier
        .heightIn(min = 44.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
        .border(width = if (selected) 1.5.dp else 1.dp, color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f), shape = RoundedCornerShape(10.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, style = ClawTheme.type.body, color = ClawTheme.colors.text)
  }
}
