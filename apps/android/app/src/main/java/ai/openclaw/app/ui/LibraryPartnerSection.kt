package ai.openclaw.app.ui

import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 図鑑のキャラがパートナーとして話すための設定。会話の設定と、パートナー用の詳細シートの全項目。 */
@Composable
internal fun LibraryPartnerPanel(
  chara: LibraryChara,
  rpc: suspend (GatewayMethod, JSONObject) -> JSONObject,
  reload: suspend () -> Unit,
  onStatus: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val sheet = remember(chara.id, chara.sheetValues) { mutableStateMapOf<String, String>().apply { putAll(chara.sheetValues) } }
  var replyLength by remember(chara.id) { mutableStateOf("normal") }
  var emoji by remember(chara.id) { mutableStateOf("some") }
  var thinking by remember(chara.id) { mutableStateOf("auto") }
  var proactive by remember(chara.id) { mutableStateOf(false) }
  var selfie by remember(chara.id) { mutableStateOf(false) }
  var selfiePerDay by remember(chara.id) { mutableStateOf(2) }
  var selfieVideo by remember(chara.id) { mutableStateOf(false) }
  var loaded by remember(chara.id) { mutableStateOf(false) }

  LaunchedEffect(chara.id, chara.partnerId) {
    if (chara.partnerId.isBlank()) return@LaunchedEffect
    runCatching {
      val st = rpc(GatewayMethod.PartnerSettingsGet, JSONObject().put("partnerId", chara.partnerId)).optJSONObject("settings") ?: JSONObject()
      replyLength = st.optString("replyLength", "normal")
      emoji = st.optString("emoji", "some")
      thinking = st.optString("thinkingDefault", "auto")
      proactive = st.optBoolean("proactive", false)
      selfie = st.optBoolean("selfie", false)
      selfiePerDay = st.optInt("selfiePerDay", 2)
      selfieVideo = st.optBoolean("selfieVideo", false)
      loaded = true
    }
  }

  ClawPanel(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(text = "💬 パートナー（このキャラとチャット）", style = ClawTheme.type.section, color = ClawTheme.colors.text)
      Text(
        text = "お気に入りにすると、このキャラはパートナーとして話せます。図鑑の設定（見た目・性格・口調）と、下のパートナー用の設定を合わせて、チャットの人物像を作ります。図鑑の設定を保存すると、パートナーにも反映されます。",
        style = ClawTheme.type.captionSmall,
        color = ClawTheme.colors.textSubtle,
      )
      if (chara.partnerId.isBlank()) {
        ClawPrimaryButton(
          text = "パートナーとして話せるようにする",
          onClick = {
            scope.launch {
              runCatching {
                rpc(GatewayMethod.ComicCharacterUpdate, JSONObject().put("characterId", chara.id).put("isFavorite", true).put("ensurePartner", true))
                reload()
                onStatus("💬 ${chara.name}のパートナーを作りました。パートナー画面から話しかけられます。")
              }.onFailure { onStatus("パートナー作成のエラー: ${it.message}") }
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
      } else {
        Text(text = "💑 パートナーと連携済み。パートナー画面から話しかけられます。", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
        LibraryChoiceRow("返答の長さ", listOf("short" to "短く", "normal" to "ふつう", "long" to "長く"), replyLength) { replyLength = it }
        LibraryChoiceRow("絵文字", listOf("none" to "なし", "some" to "少し", "many" to "多め"), emoji) { emoji = it }
        LibraryChoiceRow("考え方", listOf("instant" to "すぐ答える", "auto" to "おまかせ", "deep" to "じっくり"), thinking) { thinking = it }
        LibraryChoiceRow("自分から話しかける", listOf("false" to "しない", "true" to "する"), proactive.toString()) { proactive = it == "true" }
        LibraryChoiceRow("📸 自撮りを送ってくる", listOf("false" to "しない", "true" to "する"), selfie.toString()) { selfie = it == "true" }
        if (selfie) {
          LibraryChoiceRow("1日の回数", listOf("1" to "1回", "2" to "2回", "4" to "4回", "6" to "6回"), selfiePerDay.toString()) { selfiePerDay = it.toInt() }
          LibraryChoiceRow("動画も送る", listOf("false" to "写真だけ", "true" to "ときどき動画"), selfieVideo.toString()) { selfieVideo = it == "true" }
          Text(
            text = "朝8時〜夜中2時の間に、気持ち・高まり・会話の場面に合わせて、このキャラの見た目で撮った写真を送ってきます。会話の途中（最後のやりとりから20分以内）と、ほかの生成の最中は送りません。動画は1本に数分〜十数分かかり、その間は会話AIが止まります。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textSubtle,
          )
          ClawSecondaryButton(
            text = "📸 今すぐ1枚送ってもらう",
            onClick = {
              scope.launch {
                runCatching {
                  rpc(GatewayMethod.PartnerSelfieSend, JSONObject().put("partnerId", chara.partnerId))
                  onStatus("📸 ${chara.name}が写真を撮っています。できあがるとチャットに届きます。")
                }.onFailure { onStatus("自撮りのエラー: ${it.message}") }
              }
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }
        ClawSecondaryButton(
          text = "会話の設定を保存",
          enabled = loaded,
          onClick = {
            scope.launch {
              runCatching {
                val settings = JSONObject().put("replyLength", replyLength).put("emoji", emoji).put("thinkingDefault", thinking).put("proactive", proactive)
                  .put("selfie", selfie).put("selfiePerDay", selfiePerDay).put("selfieVideo", selfieVideo)
                rpc(GatewayMethod.PartnerSettingsSet, JSONObject().put("partnerId", chara.partnerId).put("settings", settings))
                onStatus("会話の設定を保存しました")
              }.onFailure { onStatus("保存エラー: ${it.message}") }
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        Text(text = "モデル・声・朝の挨拶などの詳しい設定は、パートナー画面で変えられます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textSubtle)
      }

      Text(text = "📖 パートナー用の詳細シート（話し方・内面・過去・価値観など）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
      Text(
        text = "書いた項目だけが、チャットの人物像に入ります。図鑑の設定シートに同じ内容があるときは、ここに書いたものが優先されます。",
        style = ClawTheme.type.captionSmall,
        color = ClawTheme.colors.textSubtle,
      )
      PartnerSheetFields(sheet = sheet)
      ClawPrimaryButton(
        text = "パートナー用シートを保存",
        enabled = sheet.filterValues { it.isNotBlank() }.mapValues { it.value.trim() } != chara.sheetValues,
        onClick = {
          scope.launch {
            runCatching {
              val values = JSONObject()
              sheet.forEach { (k, v) -> if (v.isNotBlank()) values.put(k, v.trim()) }
              rpc(GatewayMethod.ComicCharacterUpdate, JSONObject().put("characterId", chara.id).put("sheetValues", values))
              reload()
              onStatus("パートナー用シートを保存しました")
            }.onFailure { onStatus("保存エラー: ${it.message}") }
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

/** パートナー用の詳細シートの全項目（カテゴリごとに開閉）。入力は項目名をキーに sheet へ入る。 */
@Composable
internal fun PartnerSheetFields(sheet: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>) {
  var openCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
  CHARACTER_CATEGORIES.forEach { category ->
    val open = category.title in openCategories
    val filled = category.items.count { sheet[it.label].orEmpty().isNotBlank() }
    Row(
      modifier = Modifier.fillMaxWidth().clickable { openCategories = if (open) openCategories - category.title else openCategories + category.title },
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(text = "${if (open) "▼" else "▶"} ${category.title}", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
      Text(text = if (filled > 0) "${filled}件入力済み" else "", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    }
    if (open) {
      category.items.forEach { item ->
        ClawTextField(
          value = sheet[item.label].orEmpty(),
          onValueChange = { sheet[item.label] = it },
          placeholder = item.hint,
          label = item.label,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun LibraryChoiceRow(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      options.forEach { (value, text) ->
        Box(
          modifier =
            Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (value == selected) ClawTheme.colors.primary.copy(alpha = 0.25f) else ClawTheme.colors.surfaceRaised)
              .clickable { onSelect(value) }
              .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Text(text, style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
        }
      }
    }
  }
}
