package ai.openclaw.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** 成人向け（裸・性的な絵）への向き不向き。 */
internal enum class AdultFit(val label: String) {
  GOOD("◎ 得意"),
  OK("○ 描ける（保証はしません）"),
  WEAK("△ 不得意（服を着た絵向き）"),
  UNKNOWN("？ まだ試していません"),
}

internal data class ModelInfo(
  /** 系統（アニメ／実写など）。 */
  val family: String,
  /** 特徴の説明（サンプルを作る前に、どんなモデルか分かるように）。 */
  val detail: String,
  val adult: AdultFit,
  /** 補足（初回のダウンロード、ライセンス、速いモード）。 */
  val extra: String = "",
)

/** モデルごとの情報。id はゲートウェイの画像モデルと同じ。 */
internal val IMAGE_MODEL_INFO: Map<String, ModelInfo> =
  mapOf(
    "wai" to ModelInfo("アニメ", "アニメ絵で一番人気の定番（WAI-Illustrious）。タグの理解が正確で、色がきれい。顔や手が崩れにくい標準モデル。", AdultFit.WEAK, "「速い」モードあり"),
    "noobai" to ModelInfo("アニメ", "アニメの成人向けで評価が最も高い（NoobAI-XL V-Pred）。体の描写と構図が正確。裸や性的な絵を描くなら、まずこれ。", AdultFit.GOOD, "「速い」モードは使えません"),
    "anima" to ModelInfo("アニメ", "アニメ・イラスト専用の新しい軽量モデル（Anima 2B）。タグでも文章でも指示できる。", AdultFit.UNKNOWN, "起動に少し時間がかかります。「速い」で蒸留版（約45秒）"),
    "hassaku" to ModelInfo("アニメ", "癖のない明るいアニメ絵（Hassaku XL v3.4）。LoRA と相性がよく、絵が崩れにくい。", AdultFit.OK, "「速い」モードあり"),
    "illustrious" to ModelInfo("アニメ", "アニメ系の本家の土台（Illustrious XL 1.1）。各種LoRAを合わせやすい。", AdultFit.OK, "「速い」モードあり"),
    "novaanime" to ModelInfo("アニメ", "アニメと2.5Dの中間（Nova Anime XL）。NoobAI系と Illustrious の混ぜモデル。", AdultFit.OK, "「速い」モードあり"),
    "plantmilk" to ModelInfo("アニメ", "Illustrious と NoobAI の混ぜモデル（Plant Milk）。味つけの違う絵になる。", AdultFit.OK, "「速い」モードあり"),
    "zimage" to ModelInfo("アニメ・実写", "普通の文章で指示できる（Z-Image Turbo 6B）。構図や小物が正確で、日本人の実写も自然。", AdultFit.UNKNOWN, "「速い」モードは使えません"),
    "klein" to ModelInfo("アニメ・実写", "文章の指示に強い高速モデル（FLUX.2 Klein 9B・4ステップ）。", AdultFit.UNKNOWN, "初回はモデルのダウンロード（大きい）。非商用ライセンス。まだ試していません"),
    "qwenimg" to ModelInfo("アニメ・実写", "日本語の指示と細かい描写に強い（Qwen Image 2.1）。同じ顔のまま編集する用途（立ち絵からのサンプル・成人向け立ち絵）にも使っている。", AdultFit.UNKNOWN, "初回はモデルのダウンロード。まだ試していません"),
    "qwenimgturbo" to ModelInfo("アニメ・実写", "Qwen Image 2.1の公式Turbo版。8ステップで、サンプル、表情・衣装差分、ポーズ・アングル、モーションコミック素材、透明背景を高速に作る。", AdultFit.UNKNOWN, "初回はTurboチェックポイントのダウンロード。通常版と切替可能"),
    "asianreal" to ModelInfo("実写", "日本人・アジア系の実写人物（Asian Realism v2）。", AdultFit.OK, "「速い」モードあり"),
    "lustify" to ModelInfo("実写", "写真のような実写の人物（LUSTIFY V9 Zenith）。成人向けの描写に強い最新版。", AdultFit.GOOD, "「速い」モードあり"),
    "cyberreal" to ModelInfo("実写", "人気の実写モデルの最新版（CyberRealistic XL v10）。肌や光の質感が自然。", AdultFit.OK, "「速い」モードあり"),
    "juggernaut" to ModelInfo("実写", "実写系の定番（Juggernaut XL Ragnarok）。手や全身の描写がよい。", AdultFit.OK, "「速い」モードあり"),
    "realvis" to ModelInfo("実写", "人物写真向け（RealVisXL V5.0）。自然なフィルム調の質感。", AdultFit.OK, "「速い」モードあり"),
    "realismil" to ModelInfo("半実写", "アニメ寄りの実写（Realism Illustrious v5.5）。booru タグで指示する。", AdultFit.OK, "「速い」モードあり"),
    "illust2" to ModelInfo("アニメ", "アニメ系の本家の土台の後継（Illustrious XL 2.0）。1.1より構図とタグの理解が良い。", AdultFit.OK, "「速い」モードあり"),
    "animagine" to ModelInfo("アニメ", "アニメ・ゲームキャラに強い定番（Animagine XL 4.0 opt）。キャラ名のタグで再現しやすく、色が安定。", AdultFit.OK, "「速い」モードあり"),
    "rouwei" to ModelInfo("アニメ", "Illustrious を大規模に再学習したアニメ絵（RouWei 0.8）。色と構図が良く、成人向けの描写も出しやすいと説明されている。", AdultFit.GOOD, "「速い」モードあり"),
    "chenkin" to ModelInfo("アニメ", "NoobAI-XL 1.1 から追加学習した最新のアニメ絵（ChenkinNoob V0.5）。いかにもAIらしい絵になりにくい。", AdultFit.OK, "「速い」モードあり"),
    "janku" to ModelInfo("アニメ", "高精細なアニメ絵で人気の混ぜモデル（JANKU v7.77）。背景や衣装が細かく、成人向けにも対応。", AdultFit.GOOD, "「速い」モードあり"),
    "ntrmix" to ModelInfo("アニメ", "濃いめの塗りのアニメ絵（NTR MIX XIII）。成人向けの描写に強い。", AdultFit.GOOD, "「速い」モードあり"),
    "desire" to ModelInfo("アニメ", "成人向けのアニメ・同人調に作られた Illustrious 系（PornWorks Anime Desire v3）。", AdultFit.GOOD, "「速い」モードあり"),
    "cyberanime" to ModelInfo("アニメ", "実写モデルの作者が作ったアニメ絵（CyberIllustrious Anime V4）。光と肌の質感が自然。", AdultFit.OK, "「速い」モードあり"),
    "cybersemi" to ModelInfo("半実写", "アニメと実写の間の絵（CyberIllustrious Semi-Real V3）。顔はアニメ寄り、質感は実写寄り。", AdultFit.OK, "「速い」モードあり"),
    "pencil" to ModelInfo("アニメ", "日本のイラスト寄りの、やわらかい絵（illustrious_pencil-XL v5）。blue_pencil の作者の新しい系統。", AdultFit.OK, "「速い」モードあり"),
    "cyberpony" to ModelInfo("実写", "Pony 系の実写モデル（CyberRealistic Pony V18）。成人向けの描写に強く、体のポーズが多彩。", AdultFit.GOOD, "「速い」モードあり。先頭に score_9 などの品質タグが入ります"),
  )

/** 成人向けの絵に、おすすめのモデル。 */
internal val ADULT_RECOMMENDED_MODELS: List<Pair<String, String>> = listOf("noobai" to "NoobAI（アニメ）", "lustify" to "Lustify（実写）")

/** そのモデルで裸などの絵を描くのは、おすすめしない（不得意）か。 */
internal fun isWeakForAdult(modelId: String): Boolean = IMAGE_MODEL_INFO[modelId]?.adult == AdultFit.WEAK

/**
 * 選んだモデルの情報。サンプルをまだ作っていなくても、どんなモデルか分かるようにする。
 * [adultWanted] が true で、成人向けが不得意なモデルのときは、おすすめのモデルへ切り替えるボタンも出す。
 */
@Composable
internal fun ModelInfoCard(modelId: String, adultWanted: Boolean = false, onPick: ((String) -> Unit)? = null, modifier: Modifier = Modifier) {
  val info = IMAGE_MODEL_INFO[modelId] ?: return
  val name = IMAGE_MODEL_GROUPS.flatMap { it.second }.firstOrNull { it.id == modelId }?.name ?: modelId
  Column(
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text("📋 $name　［${info.family}］", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
    Text(info.detail, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    Text("成人向け：${info.adult.label}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.text)
    if (info.extra.isNotBlank()) Text(info.extra, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    if (adultWanted && isWeakForAdult(modelId)) {
      Text("🔞 裸などの絵は、このモデルでは描けないことがあります。次のどれかに切り替えてください。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
      if (onPick != null) {
        ADULT_RECOMMENDED_MODELS.forEach { (id, label) ->
          ClawSecondaryButton(text = "$label に切り替える", onClick = { onPick(id) }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        }
      }
    }
  }
}

/** モデルの短い説明（選択欄の名前の下に出す）。 */
internal fun modelNote(modelId: String): String =
  IMAGE_MODEL_GROUPS.flatMap { it.second }.firstOrNull { it.id == modelId }?.note.orEmpty()

/** モデルを選ぶ欄。名前の下に、説明を書く。 */
@Composable
internal fun ModelChip(name: String, note: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, modelId: String? = null) {
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(10.dp))
        .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
        .border(
          width = if (selected) 1.5.dp else 1.dp,
          color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f),
          shape = RoundedCornerShape(10.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // クリエのサンプル絵（モデル別サンプル比較で作ったもの）を、名前の上に小さく出す。タップで全画面
    if (modelId != null) {
      val context = androidx.compose.ui.platform.LocalContext.current
      val sample = remember(modelId) { loadSampleBitmap(context, modelSampleKey(modelId)) }
      var zoom by remember { mutableStateOf(false) }
      if (sample != null) {
        androidx.compose.foundation.Image(
          bitmap = sample.asImageBitmap(),
          contentDescription = "$name のクリエのサンプル",
          contentScale = androidx.compose.ui.layout.ContentScale.Crop,
          modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).clickable { zoom = true },
        )
        if (zoom) ZoomableImageDialog(bitmap = sample.asImageBitmap(), title = name, onDismiss = { zoom = false })
      }
    }
    // モデルの札は数が多いので、「項目の表示の大きさ」の倍率には左右されない、決まった大きさにする
    Text(name, style = ClawTheme.type.body.copy(fontSize = 13.sp), color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    if (note.isNotBlank()) {
      Text(note, style = ClawTheme.type.captionSmall.copy(fontSize = 10.sp), color = ClawTheme.colors.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
  }
}

/** 成人向けの絵が「得意」と分かっているモデルは「成人向け」、それ以外は「一般」に入れる。 */
internal fun isAdultModel(modelId: String): Boolean =
  if (modelId.startsWith("st_")) ModelStoreCache.find(modelId)?.audience == "adult" else IMAGE_MODEL_INFO[modelId]?.adult == AdultFit.GOOD

/**
 * 絵のモデルを選ぶ欄：「一般」「成人向け」に分け、その中をジャンル（アニメ・実写…）ごとに折りたたんで見せる。
 * 選んでいるモデルのある組だけ、最初から開いています。
 */
@Composable
internal fun GroupedModelPicker(viewModel: ai.openclaw.app.MainViewModel, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, columns: Int = 2) {
  LaunchedEffect(Unit) { ModelStoreCache.refresh(viewModel) }
  var storeOpen by remember { mutableStateOf(false) }
  if (storeOpen) ModelStoreDialog(viewModel = viewModel, onClose = { storeOpen = false })
  // ダウンロードして使えるようになったモデルも、アニメ／実写の組に入れる
  val installedStore = ModelStoreCache.models.filter { it.state == "installed" }
  val groups = IMAGE_MODEL_GROUPS.mapIndexed { index, (family, defs) ->
    family to (defs + installedStore.filter { (it.genre == "real") == (index == 1) }.map { ImageModelDef(it.id, it.name, it.desc.take(24)) })
  }
  val sections: List<Triple<String, String, List<ImageModelDef>>> =
    listOf("一般" to false, "成人向け" to true).flatMap { (label, adult) ->
      groups.mapNotNull { (family, defs) ->
        val inSection = defs.filter { isAdultModel(it.id) == adult }
        if (inSection.isEmpty()) null else Triple(label, family, inSection)
      }
    }
  var opened by remember { mutableStateOf<Set<String>>(emptySet()) }
  var closed by remember { mutableStateOf<Set<String>>(emptySet()) }
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    var lastLabel = ""
    sections.forEach { (label, family, defs) ->
      if (label != lastLabel) {
        lastLabel = label
        Text(
          text = if (label == "一般") "🌿 一般" else "🔞 成人向け（制限が少ない・成人表現が得意）",
          style = ClawTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
          color = ClawTheme.colors.text,
          modifier = Modifier.padding(top = 4.dp),
        )
      }
      val key = "$label/$family"
      val holdsSelected = defs.any { it.id == selected }
      val isOpen = if (key in closed) false else (key in opened || holdsSelected)
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.4f))
            .clickable {
              if (isOpen) { closed = closed + key; opened = opened - key } else { opened = opened + key; closed = closed - key }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(text = "${if (isOpen) "▾" else "▸"} $family（${defs.size}）", style = ClawTheme.type.caption, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
        if (!isOpen && holdsSelected) Text(text = "選択中", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
      }
      if (isOpen) {
        defs.chunked(columns).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { def ->
              ModelChip(name = def.name, note = def.note, selected = selected == def.id, onClick = { onSelect(def.id) }, modifier = Modifier.weight(1f), modelId = def.id)
            }
            repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
          }
        }
      }
    }
    ClawSecondaryButton(text = "📦 モデルを追加で導入する（ダウンロード）", onClick = { storeOpen = true }, modifier = Modifier.fillMaxWidth())
  }
}
