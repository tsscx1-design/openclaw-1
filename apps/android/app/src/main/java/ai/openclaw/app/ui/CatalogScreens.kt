package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 項目カタログ：キャラ図鑑・サンプル製造・各生成で使う項目（髪・服・表情など）を、分類ごとに見る。 */
@Composable
internal fun ItemCatalogScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  ItemDisplay.load(LocalContext.current)
  LaunchedEffect(Unit) { SampleStore.refresh(viewModel) }
  var query by remember { mutableStateOf("") }
  var open by remember { mutableStateOf(setOf<String>()) }
  val groups = CHARACTER_OPTION_CATALOG.map { genre -> genre.name to genre.parts.flatMap { it.items } } +
    (if (SampleStore.customItems.isEmpty()) emptyList() else listOf("追加した項目" to SampleStore.customItems.map { it.first to it.second }))
  val filtered =
    if (query.isBlank()) groups
    else groups.map { (name, items) -> name to items.filter { it.second.contains(query.trim(), ignoreCase = true) } }.filter { it.second.isNotEmpty() }

  ClawScaffold(contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 6.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
          Text(text = "📚 項目カタログ", style = ClawTheme.type.title, color = ClawTheme.colors.text, modifier = Modifier.weight(1f).padding(start = 8.dp))
        }
      }
      item {
        Text(
          text = "髪・服・表情・場所など、${groups.sumOf { it.second.size }}件の項目です。見本の絵をタップで拡大。足したい項目は、クリエに相談すると足せます。",
          style = ClawTheme.type.captionSmall,
          color = ClawTheme.colors.textMuted,
        )
      }
      item { ItemSizePicker(Modifier.fillMaxWidth()) }
      item { ClawTextField(value = query, onValueChange = { query = it }, placeholder = "🔍 名前で探す", label = "さがす", modifier = Modifier.fillMaxWidth()) }
      filtered.forEach { (name, items) ->
        val isOpen = query.isNotBlank() || name in open
        item(key = "head-$name") {
          Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.4f))
              .clickable { open = if (name in open) open - name else open + name }.padding(horizontal = 10.dp, vertical = 10.dp),
          ) {
            Text(text = "${if (isOpen) "▼" else "▶"} $name（${items.size}）", style = ClawTheme.type.caption, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
          }
        }
        if (isOpen) {
          item(key = "grid-$name") {
            ItemChoiceGrid(
              viewModel = viewModel,
              items = items.take(400).map { (id, label) -> ChoiceItem(id, label, (if (name == "追加した項目") "custom:" else "opt:") + id) },
              isSelected = { false },
              onClick = {},
            )
          }
        }
      }
    }
  }
}

private data class CustomMotionRow(val id: String, val label: String, val group: String, val isPose: Boolean)

/** モーション・体位カタログ：体位とオプションを分類ごとに見る。自分で足したものは、ここで足す・消すこともできる。 */
@Composable
internal fun MotionCatalogScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  ItemDisplay.load(LocalContext.current)
  val scope = rememberCoroutineScope()
  var tab by remember { mutableStateOf("pose") }
  var query by remember { mutableStateOf("") }
  var open by remember { mutableStateOf(setOf<String>()) }
  var custom by remember { mutableStateOf<List<CustomMotionRow>>(emptyList()) }
  var message by remember { mutableStateOf("") }
  var showAdd by remember { mutableStateOf(false) }
  var addLabel by remember { mutableStateOf("") }
  var addGroup by remember { mutableStateOf("") }
  var addBase by remember { mutableStateOf("") }
  var addText by remember { mutableStateOf("") }
  var addIsPose by remember { mutableStateOf(true) }

  suspend fun reload() {
    MotionCatalog.load(viewModel)
    SampleStore.refresh(viewModel)
    runCatching {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MotionCustomList, "{}", timeoutMs = 30_000))
      val poses = res.optJSONArray("poses")
      val options = res.optJSONArray("options")
      custom =
        (0 until (poses?.length() ?: 0)).map { poses!!.getJSONObject(it).let { o -> CustomMotionRow(o.optString("id"), o.optString("label"), o.optString("group"), true) } } +
          (0 until (options?.length() ?: 0)).map { options!!.getJSONObject(it).let { o -> CustomMotionRow(o.optString("id"), o.optString("label"), o.optString("group"), false) } }
    }
  }
  LaunchedEffect(Unit) { reload() }

  val customIds = custom.map { it.id }.toSet()
  val groups: List<Pair<String, List<Pair<String, String>>>> =
    if (tab == "pose") MotionCatalog.poses.groupBy { it.third }.map { (g, rows) -> g to rows.map { it.first to it.second } }
    else MotionCatalog.options
  val prefix = if (tab == "pose") "pose:" else "motion:"
  val filtered =
    if (query.isBlank()) groups
    else groups.map { (g, items) -> g to items.filter { it.second.contains(query.trim(), ignoreCase = true) } }.filter { it.second.isNotEmpty() }

  ClawScaffold(contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 6.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
          Text(text = "🎞 モーション・体位カタログ", style = ClawTheme.type.title, color = ClawTheme.colors.text, modifier = Modifier.weight(1f).padding(start = 8.dp))
        }
      }
      item {
        Text(
          text = "モーションコミックと動画で使う体位とオプションです（体位${MotionCatalog.poses.size}件・オプション${MotionCatalog.options.sumOf { it.second.size }}件）。新しいものは、クリエに相談しながら足せます。ここからも足せます。",
          style = ClawTheme.type.captionSmall,
          color = ClawTheme.colors.textMuted,
        )
      }
      if (message.isNotBlank()) item { Text(text = message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary) }
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          listOf("pose" to "🛏 体位", "option" to "🎛 オプション").forEach { (id, label) ->
            Box(
              modifier = Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
                .background(if (tab == id) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
                .clickable { tab = id }.padding(8.dp),
              contentAlignment = Alignment.Center,
            ) { Text(text = label, style = ClawTheme.type.caption, color = ClawTheme.colors.text) }
          }
        }
      }
      item { ItemSizePicker(Modifier.fillMaxWidth()) }
      item { ClawTextField(value = query, onValueChange = { query = it }, placeholder = "🔍 名前で探す", label = "さがす", modifier = Modifier.fillMaxWidth()) }
      item { ClawSecondaryButton(text = if (showAdd) "▲ 足す欄を閉じる" else "➕ 体位・オプションを足す", onClick = { showAdd = !showAdd }, modifier = Modifier.fillMaxWidth()) }
      if (showAdd) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
              ClawSecondaryButton(text = (if (addIsPose) "✓ " else "") + "体位", onClick = { addIsPose = true }, modifier = Modifier.weight(1f))
              ClawSecondaryButton(text = (if (!addIsPose) "✓ " else "") + "オプション", onClick = { addIsPose = false }, modifier = Modifier.weight(1f))
            }
            ClawTextField(value = addLabel, onValueChange = { addLabel = it }, placeholder = "名前", label = "名前", modifier = Modifier.fillMaxWidth())
            ClawTextField(value = addGroup, onValueChange = { addGroup = it }, placeholder = "分類（省くと「自分で追加」）", label = "分類", modifier = Modifier.fillMaxWidth())
            if (addIsPose) {
              ClawTextField(value = addBase, onValueChange = { addBase = it }, placeholder = "似ている体位のid（例: missionary。省くと正常位）", label = "土台にする体位", modifier = Modifier.fillMaxWidth())
              ClawTextField(value = addText, onValueChange = { addText = it }, placeholder = "姿勢（英語のタグ）", label = "姿勢", minLines = 2, modifier = Modifier.fillMaxWidth())
            } else {
              ClawTextField(value = addText, onValueChange = { addText = it }, placeholder = "絵のタグ（英語）", label = "絵のタグ", minLines = 2, modifier = Modifier.fillMaxWidth())
            }
            ClawPrimaryButton(
              text = "足す",
              onClick = {
                scope.launch {
                  val body = JSONObject().put("kind", if (addIsPose) "pose" else "option").put("label", addLabel).put("group", addGroup)
                  if (addIsPose) body.put("base", addBase.trim()).put("position", addText) else body.put("tags", addText)
                  message = runCatching { JSONObject(viewModel.runGatewayRpc(GatewayMethod.MotionCustomAdd, body.toString(), timeoutMs = 30_000)) }
                    .fold({ "✅ 「${it.optString("label")}」を足しました" }, { "足せませんでした：${it.message}" })
                  addLabel = ""; addText = ""
                  reload()
                }
              },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
      filtered.forEach { (name, items) ->
        val key = "$tab/$name"
        val isOpen = query.isNotBlank() || key in open
        item(key = "head-$key") {
          Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.4f))
              .clickable { open = if (key in open) open - key else open + key }.padding(horizontal = 10.dp, vertical = 10.dp),
          ) {
            Text(text = "${if (isOpen) "▼" else "▶"} $name（${items.size}）", style = ClawTheme.type.caption, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
          }
        }
        if (isOpen) {
          item(key = "grid-$key") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              ItemChoiceGrid(viewModel = viewModel, items = items.map { (id, label) -> ChoiceItem(id, label, prefix + id) }, isSelected = { false }, onClick = {})
              items.filter { it.first in customIds }.forEach { (id, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                  Text(text = "自分で足した：$label", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted, modifier = Modifier.weight(1f))
                  ClawSecondaryButton(
                    text = "消す",
                    onClick = { scope.launch { runCatching { viewModel.runGatewayRpc(GatewayMethod.MotionCustomRemove, JSONObject().put("id", id).toString(), timeoutMs = 30_000) }; reload() } },
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
