package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class WikidataCandidateRow(val qid: String, val label: String, val description: String)

private data class WikidataPersonRow(
  val qid: String,
  val name: String,
  val description: String,
  val birthday: String,
  val age: Int?,
  val bloodType: String,
  val hometown: String,
  val heightCm: Double?,
  val xUrl: String,
  val photoPageUrl: String,
  val groups: List<String>,
  val occupations: List<String>,
  val wikipediaUrl: String,
  val sourceUrl: String,
)

private fun JSONObject.strings(key: String): List<String> {
  val array = optJSONArray(key) ?: return emptyList()
  return (0 until array.length()).map { array.getString(it) }
}

/**
 * Wikidata（誰でも自由に使える公開データ）で、1人ずつ名前をさがして、内容を見てから、名簿に追加する。
 * 勝手に集めることはしない。追加した行には、出典（Wikidata の番号）が残る。
 */
@Composable
internal fun IdolWikidataDialog(
  viewModel: MainViewModel,
  onDismiss: () -> Unit,
  /** 名簿に書けた（画面に出す一言）。 */
  onAdded: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val uriHandler = LocalUriHandler.current
  var query by remember { mutableStateOf("") }
  var candidates by remember { mutableStateOf<List<WikidataCandidateRow>>(emptyList()) }
  var person by remember { mutableStateOf<WikidataPersonRow?>(null) }
  var group by remember { mutableStateOf("") }
  var name by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }

  suspend fun rpc(method: GatewayMethod, params: JSONObject): JSONObject =
    JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = 90_000))

  fun search() {
    if (query.isBlank() || busy) return
    busy = true
    message = "さがしています…"
    scope.launch {
      runCatching {
        val array = rpc(GatewayMethod.IdolMasterWikidataSearch, JSONObject().put("query", query.trim())).getJSONArray("candidates")
        candidates = (0 until array.length()).map { array.getJSONObject(it) }.map { WikidataCandidateRow(it.optString("qid"), it.optString("label"), it.optString("description")) }
        message = if (candidates.isEmpty()) "見つかりませんでした。別の書き方（漢字・かな・英語）で、もう一度さがしてください。" else ""
      }.onFailure { message = "さがせませんでした：${it.message}" }
      busy = false
    }
  }

  fun open(candidate: WikidataCandidateRow) {
    if (busy) return
    busy = true
    message = "内容を読んでいます…"
    scope.launch {
      runCatching {
        val p = rpc(GatewayMethod.IdolMasterWikidataPreview, JSONObject().put("qid", candidate.qid)).getJSONObject("person")
        val row =
          WikidataPersonRow(
            qid = p.optString("qid"),
            name = p.optString("name"),
            description = p.optString("description"),
            birthday = p.optString("birthday"),
            age = if (p.has("age")) p.optInt("age") else null,
            bloodType = p.optString("bloodType"),
            hometown = p.optString("hometown"),
            heightCm = if (p.has("heightCm")) p.optDouble("heightCm") else null,
            xUrl = p.optString("xUrl"),
            photoPageUrl = p.optString("photoPageUrl"),
            groups = p.strings("groups"),
            occupations = p.strings("occupations"),
            wikipediaUrl = p.optString("wikipediaUrl"),
            sourceUrl = p.optString("sourceUrl"),
          )
        person = row
        name = row.name
        group = row.groups.firstOrNull() ?: ""
        message = ""
      }.onFailure { message = "読めませんでした：${it.message}" }
      busy = false
    }
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(if (person != null) "名簿に追加する" else "Wikidata でさがす") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val current = person
        if (current == null) {
          Text("人の名前を入れて、さがします。見つかった人から1人をえらぶと、内容を見てから、名簿に追加できます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          ClawTextField(value = query, onValueChange = { query = it }, placeholder = "名前（例：岩立沙穂）", modifier = Modifier.fillMaxWidth())
          ClawSecondaryButton(text = "さがす", enabled = query.isNotBlank() && !busy, onClick = { search() }, modifier = Modifier.fillMaxWidth())
          LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(candidates, key = { it.qid }) { row ->
              Column(modifier = Modifier.fillMaxWidth().clickable { open(row) }.padding(vertical = 6.dp)) {
                Text(row.label, style = ClawTheme.type.body, color = ClawTheme.colors.text)
                if (row.description.isNotBlank()) Text(row.description, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
              }
            }
          }
        } else {
          Text(current.name, style = ClawTheme.type.body, color = ClawTheme.colors.text)
          if (current.description.isNotBlank()) Text(current.description, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          val facts =
            listOfNotNull(
              current.birthday.takeIf { it.isNotBlank() }?.let { "誕生日：$it" },
              current.age?.let { "年齢：${it}歳" },
              current.bloodType.takeIf { it.isNotBlank() }?.let { "血液型：${it}型" },
              current.hometown.takeIf { it.isNotBlank() }?.let { "出身地：$it" },
              current.heightCm?.let { "身長：${it.toString().removeSuffix(".0")}cm" },
              current.groups.takeIf { it.isNotEmpty() }?.let { "所属：${it.joinToString("・")}" },
              current.occupations.takeIf { it.isNotEmpty() }?.let { "職業：${it.joinToString("・")}" },
            )
          if (facts.isEmpty()) Text("Wikidata には、使える項目が入っていませんでした（名前だけが追加されます）。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          facts.forEach { Text(it, style = ClawTheme.type.caption, color = ClawTheme.colors.text) }
          Text("※ 入っていない項目は、Wikidata に、まだ書かれていません（空のまま追加されます）。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          if (current.photoPageUrl.isNotBlank()) {
            Text("写真：あり（Wikimedia Commons。使う前に、ライセンスを確認してください）", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary, modifier = Modifier.clickable { runCatching { uriHandler.openUri(current.photoPageUrl) } })
          }
          if (current.xUrl.isNotBlank()) Text("X：${current.xUrl}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          Text("出典：Wikidata ${current.qid}（CC0）", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary, modifier = Modifier.clickable { runCatching { uriHandler.openUri(current.sourceUrl) } })
          ClawTextField(value = group, onValueChange = { group = it }, placeholder = "例：AKB48、K-POP、俳優（空なら『未分類』）", label = "グループ・分類", modifier = Modifier.fillMaxWidth())
          ClawTextField(value = name, onValueChange = { name = it }, placeholder = "名簿での名前", label = "名前", modifier = Modifier.fillMaxWidth())
          Text("同じグループ・同じ名前の人が、すでに名簿にいるときは、その行が置き換わります（元の名簿は、自動でバックアップされます）。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        }
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    },
    confirmButton = {
      val current = person
      if (current != null) {
        TextButton(enabled = !busy && name.isNotBlank(), onClick = {
          busy = true
          scope.launch {
            runCatching {
              rpc(GatewayMethod.IdolMasterWikidataImport, JSONObject().put("qid", current.qid).put("group", group.trim()).put("name", name.trim()))
            }.onSuccess { res ->
              val verb = if (res.optString("action") == "updated") "置き換えました" else "追加しました"
              onAdded("「${res.optString("name", name.trim())}」を名簿に${verb}（${res.optString("group")}）")
            }.onFailure { message = "追加できませんでした：${it.message}" }
            busy = false
          }
        }) { Text("名簿に追加") }
      }
    },
    dismissButton = {
      TextButton(enabled = !busy, onClick = { if (person != null) person = null else onDismiss() }) { Text(if (person != null) "戻る" else "閉じる") }
    },
  )
}
