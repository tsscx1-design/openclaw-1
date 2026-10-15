package ai.openclaw.app.ui

import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

internal data class VoiceTimelineDraft(
  val atSec: String = "0",
  val kind: String = "line",
  val text: String = "",
  val sfx: String = "wet",
  val volume: String = "1.0",
)

internal class ProductionControlsState {
  var expanded by mutableStateOf(false)
  var gpuMode by mutableStateOf("fast")
  var qualityMode by mutableStateOf("off")
  /** キャラ図鑑から選んだキャラ（最大4人）。選んだだけで「固定」になる。 */
  val characterIds = mutableStateListOf<String>()
  var characterOutfit by mutableStateOf("")
  var keepFace by mutableStateOf(true)
  var keepBody by mutableStateOf(true)
  var keepHair by mutableStateOf(true)
  var keepOutfit by mutableStateOf(false)
  var sceneOn by mutableStateOf(false)
  var place by mutableStateOf("")
  var camera by mutableStateOf("")
  var pose by mutableStateOf("")
  var wardrobe by mutableStateOf("")
  var continuity by mutableStateOf("")
  var branchParentJobId by mutableStateOf("")
  var branchName by mutableStateOf("")
  var repairSourceJobId by mutableStateOf("")
  var repairPart by mutableStateOf(0)
  var repairRegion by mutableStateOf("face")
  var repairStrength by mutableStateOf("0.55")
  var timelineOn by mutableStateOf(false)
  val timeline = mutableStateListOf(VoiceTimelineDraft())

  fun clearSourceActions() {
    branchParentJobId = ""
    branchName = ""
    repairSourceJobId = ""
    repairPart = 0
  }

  fun beginBranch(jobId: String) {
    clearSourceActions()
    branchParentJobId = jobId
    sceneOn = true
    expanded = true
  }

  fun beginRepair(jobId: String, part: Int = 0) {
    clearSourceActions()
    repairSourceJobId = jobId
    repairPart = part
    expanded = true
  }

  fun writeTo(params: JSONObject, kind: String, prompt: String) {
    params.put("gpuMode", gpuMode)
    params.put("qualityMode", qualityMode)
    if (characterIds.isNotEmpty()) {
      params.put(
        "characterLock",
        JSONObject()
          .put("characterIds", JSONArray(characterIds.take(4)))
          .put("outfit", if (characterIds.size == 1) characterOutfit.trim() else "")
          .put("keepFace", keepFace)
          .put("keepBody", keepBody)
          .put("keepHair", keepHair)
          .put("keepOutfit", keepOutfit),
      )
    }
    if (sceneOn || branchParentJobId.isNotBlank()) {
      params.put(
        "sceneState",
        JSONObject()
          .put("inheritJobId", branchParentJobId)
          .put("place", place.trim())
          .put("camera", camera.trim())
          .put("pose", pose.trim())
          .put("wardrobe", wardrobe.trim())
          .put("continuity", continuity.trim()),
      )
    }
    if (branchParentJobId.isNotBlank()) {
      params.put(
        "branch",
        JSONObject().put("parentJobId", branchParentJobId).put("name", branchName.trim()),
      )
    }
    if (kind == "image" && repairSourceJobId.isNotBlank()) {
      val region =
        when (repairRegion) {
          "face" -> doubleArrayOf(0.28, 0.05, 0.44, 0.42)
          "hands" -> doubleArrayOf(0.08, 0.28, 0.84, 0.52)
          "chest" -> doubleArrayOf(0.20, 0.23, 0.60, 0.40)
          "groin" -> doubleArrayOf(0.18, 0.48, 0.64, 0.42)
          else -> doubleArrayOf(0.0, 0.0, 1.0, 1.0)
        }
      params.put(
        "repair",
        JSONObject()
          .put("sourceJobId", repairSourceJobId)
          .put("part", repairPart)
          .put(
            "region",
            JSONObject()
              .put("x", region[0])
              .put("y", region[1])
              .put("width", region[2])
              .put("height", region[3]),
          )
          .put("instruction", prompt.trim())
          .put("strength", repairStrength.toDoubleOrNull() ?: 0.55),
      )
    }
    if (kind == "voice" && timelineOn) {
      val cues = JSONArray()
      timeline.forEach { cue ->
        if (cue.kind == "line" && cue.text.isNotBlank()) {
          cues.put(
            JSONObject()
              .put("atSec", cue.atSec.toDoubleOrNull() ?: 0.0)
              .put("kind", "line")
              .put("text", cue.text.trim())
              .put("volume", cue.volume.toDoubleOrNull() ?: 1.0),
          )
        } else if (cue.kind == "sfx") {
          cues.put(
            JSONObject()
              .put("atSec", cue.atSec.toDoubleOrNull() ?: 0.0)
              .put("kind", "sfx")
              .put("sfx", cue.sfx)
              .put("volume", cue.volume.toDoubleOrNull() ?: 1.0),
          )
        }
      }
      if (cues.length() > 0) params.put("voiceTimeline", cues)
    }
  }
}

@Composable
private fun ProductionChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.clickable(onClick = onClick),
    shape = RoundedCornerShape(9.dp),
    color = if (selected) ClawTheme.colors.primary.copy(alpha = 0.18f) else ClawTheme.colors.surface,
  ) {
    Text(
      text = label,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
      style = ClawTheme.type.caption.copy(fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
      color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
    )
  }
}

/**
 * キャラ図鑑のキャラを選ぶ（最大4人。タップで選ぶ／外す）。選んだキャラの見た目が絵に反映され、学習済みの
 * LoRAがあれば、SDXL系のモデルで使われる。1人だけ選んだときは、そのキャラの衣装も選べる。
 */
@Composable
internal fun CharacterPicker(state: ProductionControlsState, characters: List<Triple<String, String, List<String>>>) {
  if (characters.isEmpty()) return
  Text(
    "キャラ図鑑から選ぶ（${state.characterIds.size}/4人）",
    style = ClawTheme.type.caption,
    color = ClawTheme.colors.text,
  )
  characters.chunked(2).forEach { row ->
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      row.forEach { (id, name, _) ->
        ProductionChip(
          name,
          id in state.characterIds,
          {
            if (id in state.characterIds) state.characterIds.remove(id)
            else if (state.characterIds.size < 4) state.characterIds.add(id)
            state.characterOutfit = ""
          },
          Modifier.weight(1f),
        )
      }
      if (row.size == 1) Text("", modifier = Modifier.weight(1f))
    }
  }
  val outfits =
    if (state.characterIds.size == 1) characters.firstOrNull { it.first == state.characterIds[0] }?.third.orEmpty() else emptyList()
  if (outfits.isNotEmpty()) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      (listOf("" to "いつもの衣装") + outfits.map { it to it }).take(4).forEach { (id, label) ->
        ProductionChip(label, state.characterOutfit == id, { state.characterOutfit = id }, Modifier.weight(1f))
      }
    }
  }
  Text(
    if (state.characterIds.isEmpty()) "選ぶと、そのキャラの見た目で作ります。2人以上も選べます。"
    else "選んだキャラの見た目を反映します。学習済みのLoRAがあれば、SDXL系のモデルで顔や体を再現します（他のモデルでは言葉の説明だけです）。",
    style = ClawTheme.type.captionSmall,
    color = ClawTheme.colors.textMuted,
  )
}

@Composable
internal fun ProductionControlsPanel(
  state: ProductionControlsState,
  kind: String,
  characters: List<Triple<String, String, List<String>>>,
) {
  ClawPanel(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (kind in setOf("image", "video", "manga")) CharacterPicker(state, characters)
      ClawSecondaryButton(
        text = if (state.expanded) "制作設定を閉じる" else "制作設定（固定・引き継ぎ・品質・分岐）",
        onClick = { state.expanded = !state.expanded },
        modifier = Modifier.fillMaxWidth(),
      )
      if (!state.expanded) return@Column

      Text("GPUの使い方", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("fast" to "速く作る（GPU2枚）", "parallel" to "1枚で作る（RTX 3060）").forEach { (id, label) ->
          ProductionChip(label, state.gpuMode == id, { state.gpuMode = id }, Modifier.weight(1f))
        }
      }
      Text(
        if (state.gpuMode == "fast") "RTX 4060とRTX 3060の2枚で、複数の絵やカットを同時に作ります（動画カットは約2倍の速さ）。生成中は会話AIを止めます。完成から10分後か、チャットを送ると、自動で戻ります。"
        else "RTX 3060だけで作ります。1件ずつの速さは2枚のときと同程度です。会話AIは止まり、完成から10分後か、チャットを送ると、自動で戻ります。",
        style = ClawTheme.type.caption,
        color = ClawTheme.colors.textMuted,
      )

      Text("完成後の仕上げ", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("off" to "なし", "balanced" to "標準", "max" to "最高").forEach { (id, label) ->
          ProductionChip(label, state.qualityMode == id, { state.qualityMode = id }, Modifier.weight(1f))
        }
      }

      // 動画のモーションは、専用の主役選びがある。ここでは、選んだキャラの「何を保つか」だけを決める。
      if (kind == "motion") CharacterPicker(state, characters)
      if (kind in setOf("image", "video", "manga", "motion") && state.characterIds.isNotEmpty()) {
        Text("選んだキャラで同じに保つもの", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          ProductionChip("顔", state.keepFace, { state.keepFace = !state.keepFace }, Modifier.weight(1f))
          ProductionChip("体格", state.keepBody, { state.keepBody = !state.keepBody }, Modifier.weight(1f))
          ProductionChip("髪", state.keepHair, { state.keepHair = !state.keepHair }, Modifier.weight(1f))
          ProductionChip("衣装", state.keepOutfit, { state.keepOutfit = !state.keepOutfit }, Modifier.weight(1f))
        }
      }

      ProductionChip(
        if (state.sceneOn) "シーン状態を引き継ぐ：使用中" else "場所・姿勢・衣服を固定する",
        state.sceneOn,
        { state.sceneOn = !state.sceneOn },
        Modifier.fillMaxWidth(),
      )
      if (state.sceneOn) {
        ClawTextField(state.place, { state.place = it }, placeholder = "例：寝室、夜、暖色の照明", label = "場所", modifier = Modifier.fillMaxWidth())
        ClawTextField(state.camera, { state.camera = it }, placeholder = "例：腰の高さ、斜め前、寄り", label = "カメラ", modifier = Modifier.fillMaxWidth())
        ClawTextField(state.pose, { state.pose = it }, placeholder = "姿勢と人物の位置", label = "姿勢", modifier = Modifier.fillMaxWidth())
        ClawTextField(state.wardrobe, { state.wardrobe = it }, placeholder = "衣服の状態", label = "衣服", modifier = Modifier.fillMaxWidth())
        ClawTextField(state.continuity, { state.continuity = it }, placeholder = "濡れ方、小物、表情など次へ残す状態", label = "その他の引き継ぎ", minLines = 2, modifier = Modifier.fillMaxWidth())
      }

      if (state.repairSourceJobId.isNotBlank()) {
        Text("部分修正する場所", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          listOf("face" to "顔", "hands" to "手", "chest" to "胸", "groin" to "下半身", "full" to "全体").forEach { (id, label) ->
            ProductionChip(label, state.repairRegion == id, { state.repairRegion = id }, Modifier.weight(1f))
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          listOf("0.35" to "弱く", "0.55" to "標準", "0.75" to "強く").forEach { (id, label) ->
            ProductionChip(label, state.repairStrength == id, { state.repairStrength = id }, Modifier.weight(1f))
          }
        }
        Text("上の入力欄に、直したい内容を書いて作成します。選んだ範囲の外側は元画像のまま残ります。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
      }

      if (state.branchParentJobId.isNotBlank()) {
        Text("この作品は元作品を残したまま分岐として作ります。", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
        ClawTextField(state.branchName, { state.branchName = it }, placeholder = "例：別の展開", label = "分岐名", modifier = Modifier.fillMaxWidth())
      }

      if (kind == "voice") {
        ProductionChip(
          if (state.timelineOn) "音声タイムライン：使用中" else "音声を時間指定で並べる",
          state.timelineOn,
          { state.timelineOn = !state.timelineOn },
          Modifier.fillMaxWidth(),
        )
        if (state.timelineOn) {
          state.timeline.forEachIndexed { index, cue ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ProductionChip("セリフ", cue.kind == "line", { state.timeline[index] = cue.copy(kind = "line") }, Modifier.weight(1f))
                ProductionChip("効果音", cue.kind == "sfx", { state.timeline[index] = cue.copy(kind = "sfx") }, Modifier.weight(1f))
                ClawSecondaryButton(text = "削除", onClick = { if (state.timeline.size > 1) state.timeline.removeAt(index) })
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ClawTextField(cue.atSec, { state.timeline[index] = cue.copy(atSec = it) }, placeholder = "0", label = "開始秒", modifier = Modifier.weight(1f))
                ClawTextField(cue.volume, { state.timeline[index] = cue.copy(volume = it) }, placeholder = "1.0", label = "音量", modifier = Modifier.weight(1f))
              }
              if (cue.kind == "line") {
                ClawTextField(cue.text, { state.timeline[index] = cue.copy(text = it) }, placeholder = "[囁き] セリフ", label = "演技とセリフ", minLines = 2, modifier = Modifier.fillMaxWidth())
              } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                  listOf("wet" to "水音", "thrust" to "動き", "spank" to "打音").forEach { (id, label) ->
                    ProductionChip(label, cue.sfx == id, { state.timeline[index] = cue.copy(sfx = id) }, Modifier.weight(1f))
                  }
                }
              }
            }
          }
          ClawSecondaryButton(text = "時間指定を追加", onClick = { state.timeline.add(VoiceTimelineDraft()) }, modifier = Modifier.fillMaxWidth())
        }
      }
    }
  }
}
