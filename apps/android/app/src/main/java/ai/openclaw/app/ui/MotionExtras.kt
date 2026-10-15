package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** セリフの量（ゲートウェイの motionTalk と同じ id）。 */
internal val MOTION_TALK_CHOICES =
  listOf(
    "broken" to "ぶっ壊れ",
    "wordless" to "言葉にならない",
    "wild" to "やばい",
    "many" to "沢山",
    "normal" to "普通",
    "few" to "少ない",
    "silent" to "無口（喘ぎ・我慢声あり）",
    "tuna" to "マグロ",
  )

internal val MOTION_VOICE_STYLE_CHOICES =
  listOf(
    "oho" to "おほ声", "pig" to "豚声", "cry" to "泣き声", "yell" to "喚き", "scream" to "絶叫",
    "beast" to "鳴き声（獣）", "whisper" to "囁き", "melt" to "とろけ声", "growl" to "低い唸り",
  )

internal val MOTION_SFX_CHOICES = listOf("thrust" to "打ち付ける音", "spank" to "はたく音", "wet" to "濡れた音")

internal val MOTION_FLOW_CHOICES =
  listOf(
    "slow" to "ゆっくり", "fast" to "激しく", "build" to "高まる", "climax" to "絶頂",
    "cum" to "射精", "after" to "余韻", "fellatio" to "フェラ", "mouth" to "口内射精",
  )

internal val DEFAULT_FLOW_IDS = listOf("slow", "fast", "build", "climax", "cum", "after", "fellatio", "mouth")

/** 完成した動画（結合や引継ぎの候補）。 */
internal data class FinishedFilm(val jobId: String, val title: String, val isMotion: Boolean)

/** モーションコミックの追加設定を持つ（画面の再描画をまたいで残す）。 */
internal class MotionExtrasState {
  var talk by mutableStateOf("normal")
  val styles = mutableStateListOf<String>()
  val sfx = mutableStateListOf("thrust", "wet")
  var customFlow by mutableStateOf(false)
  val flow = mutableStateListOf<String>().apply { addAll(DEFAULT_FLOW_IDS) }
  var inheritJobId by mutableStateOf("")
  var inheritTitle by mutableStateOf("")
  var preview by mutableStateOf(true)

  fun writeTo(params: JSONObject) {
    if (talk != "normal") params.put("motionTalk", talk)
    if (styles.isNotEmpty()) params.put("motionVoiceStyles", JSONArray(styles.toList()))
    params.put("motionSfx", JSONArray(sfx.toList()))
    if (customFlow && flow.isNotEmpty()) params.put("motionFlow", JSONArray(flow.toList()))
    if (inheritJobId.isNotBlank()) params.put("motionInheritJobId", inheritJobId)
    params.put("motionPreview", preview)
  }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
      style = ClawTheme.type.caption.copy(fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
      color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.text,
    )
  }
}

@Composable
private fun SectionLabel(text: String, note: String = "") {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(text = text, style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
    if (note.isNotBlank()) Text(text = note, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
  }
}

/** セリフの量・声の種類・効果音・カットの流れ・前作からの引継ぎ・確認用の絵。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MotionExtrasPanel(state: MotionExtrasState, finished: List<FinishedFilm>) {
  var open by remember { mutableStateOf(false) }
  ClawSecondaryButton(
    text = if (open) "▲ セリフ・声・効果音・流れの設定を閉じる" else "▼ セリフ・声・効果音・流れ・引継ぎの設定",
    onClick = { open = !open },
    modifier = Modifier.fillMaxWidth(),
  )
  if (!open) return
  Column(
    modifier = Modifier.fillMaxWidth().background(ClawTheme.colors.canvas, RoundedCornerShape(12.dp)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    SectionLabel("セリフの量", "量に合わせて、声の数と、口や体の動きも変わります。")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      MOTION_TALK_CHOICES.forEach { (id, label) -> Chip(label, state.talk == id, { state.talk = id }) }
    }

    SectionLabel("声の種類（いくつでも）", "選んだ声を、セリフと息づかいに混ぜます。選ばなければ、いつもの声です。")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      MOTION_VOICE_STYLE_CHOICES.forEach { (id, label) ->
        Chip(label, id in state.styles, { if (id in state.styles) state.styles.remove(id) else state.styles.add(id) })
      }
    }

    SectionLabel("効果音", "カットの下に流れます。")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      MOTION_SFX_CHOICES.forEach { (id, label) ->
        Chip(label, id in state.sfx, { if (id in state.sfx) state.sfx.remove(id) else state.sfx.add(id) })
      }
    }

    SectionLabel("カットの流れ", "標準は ゆっくり→激しく→高まる→絶頂→射精→余韻→フェラ→口内射精。自由にすると、順番と繰り返しを自分で決められます。")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      Chip("標準の流れ", !state.customFlow, { state.customFlow = false }, Modifier.weight(1f))
      Chip("自由に決める", state.customFlow, { state.customFlow = true }, Modifier.weight(1f))
    }
    if (state.customFlow) {
      Text(
        text = if (state.flow.isEmpty()) "（まだ空です。下から足してください）" else state.flow.mapIndexed { i, id -> "${i + 1}. ${MOTION_FLOW_CHOICES.firstOrNull { it.first == id }?.second ?: id}" }.joinToString("　"),
        style = ClawTheme.type.captionSmall,
        color = ClawTheme.colors.text,
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MOTION_FLOW_CHOICES.forEach { (id, label) -> Chip("＋$label", false, { if (state.flow.size < 40) state.flow.add(id) }) }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        ClawSecondaryButton(text = "最後を消す", onClick = { if (state.flow.isNotEmpty()) state.flow.removeAt(state.flow.lastIndex) }, modifier = Modifier.weight(1f))
        ClawSecondaryButton(text = "標準に戻す", onClick = { state.flow.clear(); state.flow.addAll(DEFAULT_FLOW_IDS) }, modifier = Modifier.weight(1f))
      }
    }

    SectionLabel("前作から引き継ぐ", "完成したモーションコミックの出演者・声・見た目・場所をそのまま使います（キャラや話がずれないように）。")
    val motions = finished.filter { it.isMotion }
    if (motions.isEmpty()) {
      Text(text = "引き継げる完成作品は、まだありません。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    } else {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("引き継がない", state.inheritJobId.isBlank(), { state.inheritJobId = ""; state.inheritTitle = "" })
        motions.take(12).forEach { film ->
          Chip(film.title.take(18), state.inheritJobId == film.jobId, { state.inheritJobId = film.jobId; state.inheritTitle = film.title })
        }
      }
    }

    SectionLabel("確認用の絵", "最初に絵を1枚だけ作って止まります。よければ「この絵で動画を作る」で続きます。")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      Chip("先に1枚確認する", state.preview, { state.preview = true }, Modifier.weight(1f))
      Chip("確認せず続ける", !state.preview, { state.preview = false }, Modifier.weight(1f))
    }
  }
}

/** つなぎの長さ（秒）。 */
private val BRIDGE_SECONDS = listOf(3, 6, 10, 15, 20, 30, 45, 60)

/** 目安：つなぎ目1つの時間（ゲートウェイの joinEstimateSec と同じ式）。 */
private fun joinEstimateMinutes(count: Int, seconds: Int): Int {
  val segments = maxOf(1, Math.round(seconds / 4.0).toInt())
  val frames = minOf(81, maxOf(33, Math.round(seconds.toDouble() / segments * 16).toInt() + 1))
  val perCut = 330 + frames * 8
  val total = maxOf(0, count - 1) * (segments * perCut + 150 + (segments - 1) * 60) + 240
  return (total + 59) / 60
}

/** 完成した動画を順にえらび、間をAIが解析してつないで1本にする。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MotionJoinPanel(viewModel: MainViewModel, finished: List<FinishedFilm>, onStarted: (String) -> Unit) {
  val scope = rememberCoroutineScope()
  var open by remember { mutableStateOf(false) }
  val order = remember { mutableStateListOf<String>() }
  var seconds by remember { mutableStateOf(6) }
  var message by remember { mutableStateOf("") }
  ClawSecondaryButton(
    text = if (open) "▲ 動画をつなぐを閉じる" else "▼ 🔗 完成した動画をつなぐ（間をAIが作ります）",
    onClick = { open = !open },
    modifier = Modifier.fillMaxWidth(),
  )
  if (!open) return
  Column(
    modifier = Modifier.fillMaxWidth().background(ClawTheme.colors.canvas, RoundedCornerShape(12.dp)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = "前の動画の最後の絵と、次の動画の最初の絵をAIが解析して、間の動き（体位の移動・抜き差し・場所の移動など）を作って入れます。つないだ結果も動画なので、さらにつなげます。",
      style = ClawTheme.type.captionSmall,
      color = ClawTheme.colors.textMuted,
    )
    SectionLabel("つなぐ動画（タップした順につながります）")
    if (finished.isEmpty()) {
      Text(text = "完成した動画が、まだありません。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      finished.forEach { film ->
        val pos = order.indexOf(film.jobId)
        Chip(
          label = (if (pos >= 0) "${pos + 1}. " else "") + film.title.take(20),
          selected = pos >= 0,
          onClick = { if (pos >= 0) order.removeAt(pos) else order.add(film.jobId) },
        )
      }
    }
    SectionLabel("つなぎの長さ", "長いほど、ゆっくり自然に移動します。約4秒ごとに1つの動き（絵と動画）を作ります。")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      BRIDGE_SECONDS.forEach { s -> Chip("${s}秒", seconds == s, { seconds = s }) }
    }
    if (order.size >= 2) {
      Text(
        text = "${order.size}本をつなぎます。つなぎ目 ${order.size - 1}か所・合計の目安 約${joinEstimateMinutes(order.size, seconds)}分（生成の時間です）。",
        style = ClawTheme.type.caption,
        color = ClawTheme.colors.primary,
      )
    }
    if (message.isNotBlank()) Text(text = message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
    ClawPrimaryButton(
      text = "つないで1本にする",
      enabled = order.size >= 2,
      onClick = {
        scope.launch {
          runCatching {
            val params =
              JSONObject()
                .put("kind", "motion")
                .put("prompt", "（動画をつなぐ）")
                .put("motionJoinJobIds", JSONArray(order.toList()))
                .put("motionBridgeSeconds", seconds)
            viewModel.runGatewayRpc(GatewayMethod.MediaGenerate, params.toString(), timeoutMs = 30_000)
          }.fold(
            { message = "つなぎ始めました。完成まで画面を離れても、アプリを閉じても続きます。"; order.clear(); onStarted("join") },
            { message = "始められませんでした：${it.message}" },
          )
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

/** 完成した動画の途中から、同じ人物・同じ場所のまま別の体位に切り替えて続きを作り、間をAIがつなぐ。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MotionBranchPanel(viewModel: MainViewModel, finished: List<FinishedFilm>, onStarted: () -> Unit) {
  val scope = rememberCoroutineScope()
  var open by remember { mutableStateOf(false) }
  var filmId by remember { mutableStateOf("") }
  var atSec by remember { mutableStateOf(0) }
  var poseId by remember { mutableStateOf("") }
  var poseQuery by remember { mutableStateOf("") }
  var seconds by remember { mutableStateOf(6) }
  var message by remember { mutableStateOf("") }
  ClawSecondaryButton(
    text = if (open) "▲ 途中から別の体位へを閉じる" else "▼ 🔀 途中から別の体位に切り替えて続ける",
    onClick = { open = !open },
    modifier = Modifier.fillMaxWidth(),
  )
  if (!open) return
  val motions = finished.filter { it.isMotion }
  val poses = MotionCatalog.poses.filter { poseQuery.isBlank() || it.second.contains(poseQuery.trim(), ignoreCase = true) || it.third.contains(poseQuery.trim(), ignoreCase = true) }.take(30)
  Column(
    modifier = Modifier.fillMaxWidth().background(ClawTheme.colors.canvas, RoundedCornerShape(12.dp)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = "選んだ動画を、指定した秒のところで区切り、同じ人物・同じ場所のまま別の体位で続きを作ります。間は、前後の絵をAIが解析して、体位の移動や抜き差しを作ってつなぎます。",
      style = ClawTheme.type.captionSmall,
      color = ClawTheme.colors.textMuted,
    )
    SectionLabel("元の動画（完成したモーションコミック）")
    if (motions.isEmpty()) Text(text = "完成したモーションコミックが、まだありません。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      motions.take(12).forEach { film -> Chip(film.title.take(18), filmId == film.jobId, { filmId = film.jobId }) }
    }
    SectionLabel("区切る場所", "この秒まで残して、そこから切り替えます。")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(0 to "最後から", 10 to "10秒", 20 to "20秒", 30 to "30秒", 45 to "45秒", 60 to "1分", 90 to "1分30秒", 120 to "2分").forEach { (s, label) -> Chip(label, atSec == s, { atSec = s }) }
    }
    SectionLabel("次の体位")
    ai.openclaw.app.ui.design.ClawTextField(value = poseQuery, onValueChange = { poseQuery = it }, placeholder = "🔍 体位の名前（例: バック、騎乗）", label = "さがす", modifier = Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      poses.forEach { (id, label, _) -> Chip(label, poseId == id, { poseId = id }) }
    }
    SectionLabel("つなぎの長さ")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(3, 6, 10, 15, 20).forEach { s -> Chip("${s}秒", seconds == s, { seconds = s }) }
    }
    if (message.isNotBlank()) Text(text = message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
    ClawPrimaryButton(
      text = "別の体位で続きを作ってつなぐ",
      enabled = filmId.isNotBlank() && poseId.isNotBlank(),
      onClick = {
        scope.launch {
          runCatching {
            val body = JSONObject().put("jobId", filmId).put("template", poseId).put("seconds", seconds)
            if (atSec > 0) body.put("atSec", atSec)
            JSONObject(viewModel.runGatewayRpc(GatewayMethod.MotionBranch, body.toString(), timeoutMs = 30_000))
          }.fold(
            { message = it.optString("text").ifBlank { "始めました" } + " 画面を離れても、アプリを閉じても続きます。"; onStarted() },
            { message = "始められませんでした：${it.message}" },
          )
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
