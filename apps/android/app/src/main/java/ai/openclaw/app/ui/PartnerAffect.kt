package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** 触れる場所（ゲートウェイの TouchZone と同じ id）。 */
internal val TOUCH_ZONE_CHOICES =
  listOf(
    "head" to "頭", "face" to "顔・口", "neck" to "首筋", "shoulder" to "肩・背中", "chest" to "胸",
    "belly" to "お腹", "waist" to "腰", "hips" to "お尻", "thighs" to "太もも", "legs" to "脚・足", "hands" to "手",
  )
internal val TOUCH_KIND_CHOICES = listOf("tap" to "タップ", "hold" to "長押し", "slide" to "スライド")

/** 絵のどの高さを触ったか（0 上 〜 1 下）から、触れた場所を決める。 */
internal fun touchZoneAt(ratio: Float): String =
  when {
    ratio < 0.10f -> "head"
    ratio < 0.22f -> "face"
    ratio < 0.30f -> "neck"
    ratio < 0.42f -> "chest"
    ratio < 0.55f -> "belly"
    ratio < 0.65f -> "waist"
    ratio < 0.75f -> "hips"
    ratio < 0.88f -> "thighs"
    else -> "legs"
  }

internal data class AffectState(
  val affection: Int = 30,
  val arousal: Int = 0,
  val moodLabel: String = "穏やか",
  val favorite: Set<String> = emptySet(),
  val weak: Set<String> = emptySet(),
  val dislike: Set<String> = emptySet(),
  val presets: List<TouchPresetUi> = emptyList(),
  /** いまの場面（place / outfit / pose / situation）。会話の中で AI が書き換える。 */
  val scene: Map<String, String> = emptyMap(),
)

/** 場面の項目（ゲートウェイの SCENE_FIELDS と同じ id）。 */
internal val SCENE_FIELD_CHOICES =
  listOf("place" to "場所", "outfit" to "服装", "pose" to "姿勢", "situation" to "状況")

internal data class TouchStepUi(val zone: String, val kind: String, val count: Int)
internal data class TouchPresetUi(val id: String, val name: String, val steps: List<TouchStepUi>)

private val MOOD_LABELS =
  mapOf(
    "calm" to "穏やか", "happy" to "うれしい", "shy" to "照れ", "aroused" to "火照り", "sulky" to "拗ね",
    "lonely" to "寂しい", "jealous" to "やきもち", "sleepy" to "眠い", "playful" to "いたずら",
  )

internal fun parseAffect(json: JSONObject): AffectState {
  val a = json.optJSONObject("affect") ?: json
  fun set(key: String) = a.optJSONObject("profile")?.optJSONArray(key)?.let { arr -> (0 until arr.length()).map { arr.optString(it) }.toSet() } ?: emptySet()
  val presets =
    a.optJSONObject("profile")?.optJSONArray("presets")?.let { arr ->
      (0 until arr.length()).map { i ->
        val p = arr.getJSONObject(i)
        val steps = p.optJSONArray("steps")
        TouchPresetUi(
          p.optString("id"),
          p.optString("name"),
          (0 until (steps?.length() ?: 0)).map { j -> steps!!.getJSONObject(j).let { TouchStepUi(it.optString("zone"), it.optString("kind"), it.optInt("count", 1)) } },
        )
      }
    } ?: emptyList()
  val scene =
    a.optJSONObject("scene")?.let { sc -> SCENE_FIELD_CHOICES.mapNotNull { (id, _) -> sc.optString(id).takeIf { it.isNotBlank() }?.let { id to it } }.toMap() } ?: emptyMap()
  return AffectState(a.optInt("affection", 30), a.optInt("arousal", 0), MOOD_LABELS[a.optString("mood")] ?: "穏やか", set("favorite"), set("weak"), set("dislike"), presets, scene)
}

/** 触られた結果と、いまの気持ちを、画面のあちこちで共有する。 */
internal object AffectBus {
  var partnerId by mutableStateOf("")
  var state by mutableStateOf(AffectState())

  /** アバターが追加で上げる高まりの段階（弱い場所を触られたとき）。使ったら 0 に戻す。 */
  var pendingBoost by mutableStateOf(0)

  suspend fun refresh(viewModel: MainViewModel) {
    val pid = partnerId.ifBlank { return }
    runCatching { state = parseAffect(JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerAffectGet, JSONObject().put("partnerId", pid).toString(), timeoutMs = 15_000))) }
  }

  suspend fun touch(viewModel: MainViewModel, zone: String, kind: String, count: Int = 1) {
    val pid = partnerId.ifBlank { return }
    runCatching {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerAffectTouch, JSONObject().put("partnerId", pid).put("zone", zone).put("kind", kind).put("count", count).toString(), timeoutMs = 15_000))
      state = parseAffect(res)
      pendingBoost = res.optInt("boost", 0)
    }
  }
}

/** 好感度・高まり・気分を、細い帯で出す。押すと、触り方の設定が開く。 */
@Composable
internal fun AffectBar(viewModel: MainViewModel) {
  var open by remember { mutableStateOf(false) }
  var sceneOpen by remember { mutableStateOf(false) }
  val s = AffectBus.state
  Row(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.5f)).clickable { open = true }.padding(horizontal = 10.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = "💗 好感度 ${s.affection}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.text)
    Text(text = "🌡 高まり ${s.arousal}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.text)
    Text(text = "気分 ${s.moodLabel}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
    Text(text = "触り方 ▶", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
  }
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.35f)).clickable { sceneOpen = true }.padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "📍 " + (SCENE_FIELD_CHOICES.mapNotNull { (id, _) -> s.scene[id] }.joinToString("・").ifBlank { "場面はまだ決まっていません" }),
      style = ClawTheme.type.captionSmall,
      color = if (s.scene.isEmpty()) ClawTheme.colors.textMuted else ClawTheme.colors.text,
      maxLines = 2,
      modifier = Modifier.weight(1f),
    )
    Text(text = "場面 ▶", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
  }
  if (open) TouchProfileDialog(viewModel) { open = false }
  if (sceneOpen) SceneDialog(viewModel) { sceneOpen = false }
}

/** いまの場面を見て、直す・消す。会話の AI が書き換えた内容もここに出る。 */
@Composable
internal fun SceneDialog(viewModel: MainViewModel, onClose: () -> Unit) {
  val scope = rememberCoroutineScope()
  val values = remember { mutableStateListOf(*Array(SCENE_FIELD_CHOICES.size) { "" }) }
  var message by remember { mutableStateOf("") }
  LaunchedEffect(AffectBus.partnerId) {
    AffectBus.refresh(viewModel)
    SCENE_FIELD_CHOICES.forEachIndexed { i, (id, _) -> values[i] = AffectBus.state.scene[id].orEmpty() }
  }
  fun send(body: JSONObject, done: String) {
    scope.launch {
      message =
        runCatching {
          AffectBus.state = parseAffect(JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerSceneSet, body.put("partnerId", AffectBus.partnerId).toString(), timeoutMs = 15_000)))
          SCENE_FIELD_CHOICES.forEachIndexed { i, (id, _) -> values[i] = AffectBus.state.scene[id].orEmpty() }
        }.fold({ done }, { "保存できませんでした：${it.message}" })
    }
  }
  Dialog(onDismissRequest = onClose) {
    Column(
      modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ClawTheme.colors.canvas).padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(text = "📍 いまの場面", style = ClawTheme.type.title, color = ClawTheme.colors.text)
      Text(
        text = "会話の流れで AI が自動で書き換えます。自撮りや、生成スタジオで「この場面を使う」を選んだ絵にも入ります。",
        style = ClawTheme.type.captionSmall,
        color = ClawTheme.colors.textMuted,
      )
      SCENE_FIELD_CHOICES.forEachIndexed { i, (_, label) ->
        ClawTextField(value = values[i], onValueChange = { values[i] = it }, placeholder = label, label = label, modifier = Modifier.fillMaxWidth())
      }
      if (message.isNotBlank()) Text(text = message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClawPrimaryButton(
          text = "保存",
          onClick = {
            val body = JSONObject()
            SCENE_FIELD_CHOICES.forEachIndexed { i, (id, _) -> body.put(id, values[i].trim()) }
            send(body, "保存しました")
          },
          modifier = Modifier.weight(1f),
        )
        ClawSecondaryButton(text = "全部消す", onClick = { send(JSONObject().put("clear", true), "場面を消しました") }, modifier = Modifier.weight(1f))
      }
      ClawSecondaryButton(text = "閉じる", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun ZoneChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier
        .heightIn(min = 36.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
        .border(if (selected) 1.5.dp else 1.dp, if (selected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center,
  ) { Text(text = label, style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.text) }
}

/** 好きな場所・弱い場所・嫌な場所と、触り方のプリセット（場所と触り方の並び）を決めて、再生する。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TouchProfileDialog(viewModel: MainViewModel, onClose: () -> Unit) {
  val scope = rememberCoroutineScope()
  val favorite = remember { mutableStateListOf<String>() }
  val weak = remember { mutableStateListOf<String>() }
  val dislike = remember { mutableStateListOf<String>() }
  val presets = remember { mutableStateListOf<TouchPresetUi>() }
  var message by remember { mutableStateOf("") }
  var draftName by remember { mutableStateOf("") }
  val draftSteps = remember { mutableStateListOf<TouchStepUi>() }
  var stepZone by remember { mutableStateOf("chest") }
  var stepKind by remember { mutableStateOf("tap") }
  var stepCount by remember { mutableStateOf(1) }

  LaunchedEffect(AffectBus.partnerId) {
    AffectBus.refresh(viewModel)
    val s = AffectBus.state
    favorite.clear(); favorite.addAll(s.favorite)
    weak.clear(); weak.addAll(s.weak)
    dislike.clear(); dislike.addAll(s.dislike)
    presets.clear(); presets.addAll(s.presets)
  }

  fun save() {
    scope.launch {
      val body =
        JSONObject().put("partnerId", AffectBus.partnerId)
          .put("favorite", JSONArray(favorite.toList())).put("weak", JSONArray(weak.toList())).put("dislike", JSONArray(dislike.toList()))
          .put(
            "presets",
            JSONArray(presets.map { p -> JSONObject().put("id", p.id).put("name", p.name).put("steps", JSONArray(p.steps.map { JSONObject().put("zone", it.zone).put("kind", it.kind).put("count", it.count) })) }),
          )
      message = runCatching { viewModel.runGatewayRpc(GatewayMethod.PartnerAffectProfileSet, body.toString(), timeoutMs = 15_000) }.fold({ "保存しました" }, { "保存できませんでした：${it.message}" })
      AffectBus.refresh(viewModel)
    }
  }

  /** 3つの欄で、場所はどれか1つにだけ入る。 */
  fun cycle(zone: String) {
    when {
      zone in favorite -> { favorite.remove(zone); weak.add(zone) }
      zone in weak -> { weak.remove(zone); dislike.add(zone) }
      zone in dislike -> dislike.remove(zone)
      else -> favorite.add(zone)
    }
  }

  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Box(modifier = Modifier.fillMaxSize().background(ClawTheme.colors.canvas)) {
      LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(text = "🖐 触り方の設定", style = ClawTheme.type.title, color = ClawTheme.colors.text) }
        item {
          Text(
            text = "場所を押すたびに「好き → 弱い → 嫌 → なし」と変わります。弱い場所は、触れると強く感じます。嫌な場所は、拗ねて好感度が下がります。会話にもアバターの反応にも反映されます。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textMuted,
          )
        }
        item {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TOUCH_ZONE_CHOICES.forEach { (id, label) ->
              val mark = when (id) { in favorite -> "💗 "; in weak -> "🔥 "; in dislike -> "✖ "; else -> "" }
              ZoneChip(mark + label, id in favorite || id in weak || id in dislike) { cycle(id) }
            }
          }
        }
        item { Text(text = "💗 好き　🔥 弱い　✖ 嫌", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted) }

        item { Text(text = "触り方のプリセット", style = ClawTheme.type.caption.copy(fontSize = 14.sp), color = ClawTheme.colors.text) }
        presets.forEachIndexed { index, preset ->
          item(key = "preset-${preset.id}-$index") {
            Column(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surface).padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(text = preset.name, style = ClawTheme.type.body, color = ClawTheme.colors.text)
              Text(
                text = preset.steps.joinToString(" → ") { st -> (TOUCH_ZONE_CHOICES.firstOrNull { it.first == st.zone }?.second ?: st.zone) + (TOUCH_KIND_CHOICES.firstOrNull { it.first == st.kind }?.second ?: "") + if (st.count > 1) "×${st.count}" else "" },
                style = ClawTheme.type.captionSmall,
                color = ClawTheme.colors.textMuted,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ClawPrimaryButton(
                  text = "再生",
                  onClick = {
                    scope.launch {
                      message = "「${preset.name}」を再生しています…"
                      for (st in preset.steps) {
                        AffectBus.touch(viewModel, st.zone, st.kind, st.count)
                        delay(700)
                      }
                      message = "終わりました。気分：${AffectBus.state.moodLabel}　高まり ${AffectBus.state.arousal}"
                    }
                  },
                )
                ClawSecondaryButton(text = "消す", onClick = { presets.removeAt(index); save() })
              }
            }
          }
        }
        item {
          Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.4f)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(text = "新しいプリセットを作る", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
            ClawTextField(value = draftName, onValueChange = { draftName = it }, placeholder = "名前（例: ゆっくり焦らす）", label = "名前", modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              TOUCH_ZONE_CHOICES.forEach { (id, label) -> ZoneChip(label, stepZone == id) { stepZone = id } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              TOUCH_KIND_CHOICES.forEach { (id, label) -> ZoneChip(label, stepKind == id) { stepKind = id } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
              ZoneChip("－", false) { stepCount = maxOf(1, stepCount - 1) }
              Text(text = "${stepCount}回", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
              ZoneChip("＋", false) { stepCount = minOf(20, stepCount + 1) }
              ClawSecondaryButton(text = "この触り方を足す", onClick = { draftSteps.add(TouchStepUi(stepZone, stepKind, stepCount)) }, modifier = Modifier.weight(1f))
            }
            if (draftSteps.isNotEmpty()) {
              Text(
                text = draftSteps.joinToString(" → ") { st -> (TOUCH_ZONE_CHOICES.firstOrNull { it.first == st.zone }?.second ?: st.zone) + (TOUCH_KIND_CHOICES.firstOrNull { it.first == st.kind }?.second ?: "") + if (st.count > 1) "×${st.count}" else "" },
                style = ClawTheme.type.captionSmall,
                color = ClawTheme.colors.primary,
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              ClawSecondaryButton(text = "並びを消す", onClick = { draftSteps.clear() })
              ClawPrimaryButton(
                text = "プリセットに保存",
                enabled = draftSteps.isNotEmpty() && draftName.isNotBlank(),
                onClick = {
                  presets.add(TouchPresetUi("preset-${System.currentTimeMillis()}", draftName.trim(), draftSteps.toList()))
                  draftSteps.clear(); draftName = ""
                  save()
                },
              )
            }
          }
        }
        if (message.isNotBlank()) item { Text(text = message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary) }
        item {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ClawPrimaryButton(text = "場所の設定を保存", onClick = { save() }, modifier = Modifier.weight(1f))
            ClawSecondaryButton(text = "閉じる", onClick = onClose, modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}
