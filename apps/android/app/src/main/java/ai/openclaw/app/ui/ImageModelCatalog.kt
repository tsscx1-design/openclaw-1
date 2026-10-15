package ai.openclaw.app.ui

/** 絵のモデル（ゲートウェイの media-studio.ts の SDXL_MODELS と同じ id）。系統ごとに並べる。 */
internal data class ImageModelDef(
  val id: String,
  val name: String,
  /** 画面の短い説明 */
  val note: String,
)

/** 絵を1枚作るおおよその秒数（ゲートウェイの BASE_IMAGE_SEC と同じ。初回のモデル読み込みは含まない）。 */
internal val IMAGE_MODEL_SECONDS: Map<String, Int> =
  mapOf(
    "wai" to 30, "noobai" to 60, "anima" to 75, "hassaku" to 30, "illustrious" to 30, "novaanime" to 30, "plantmilk" to 30,
    "zimage" to 90, "klein" to 60, "qwenimg" to 80, "qwenimgturbo" to 45, "asianreal" to 30, "lustify" to 30, "cyberreal" to 30,
    "juggernaut" to 30, "realvis" to 30, "realismil" to 30,
    "illust2" to 30, "animagine" to 30, "rouwei" to 30, "chenkin" to 30, "janku" to 30, "ntrmix" to 30, "desire" to 30, "cyberanime" to 30, "cybersemi" to 30, "pencil" to 30, "cyberpony" to 30,
  )

/** 秒数を「約30秒」「約1分20秒」にする。 */
internal fun approxSeconds(seconds: Int): String =
  if (seconds < 60) "約${seconds}秒" else "約${seconds / 60}分${if (seconds % 60 >= 5) "${seconds % 60}秒" else ""}"

/** モデル名の後ろに、おおよその時間を付ける（どの画面のモデル選択にも出る）。 */
private fun withTime(defs: List<ImageModelDef>): List<ImageModelDef> =
  defs.map { def -> IMAGE_MODEL_SECONDS[def.id]?.let { def.copy(name = "${def.name}（${approxSeconds(it)}）") } ?: def }

private val IMAGE_MODEL_GROUPS_RAW: List<Pair<String, List<ImageModelDef>>> =
  listOf(
    "🎌 アニメ" to
      listOf(
        ImageModelDef("wai", "WAI", "人気のアニメ絵・崩れにくい"),
        ImageModelDef("noobai", "NoobAI", "アニメ・成人向けに強い"),
        ImageModelDef("hassaku", "Hassaku XL", "癖のない明るいアニメ絵"),
        ImageModelDef("anima", "Anima 2B", "新しいアニメ専用・タグも文章もOK（fastあり）"),
        ImageModelDef("illustrious", "Illustrious XL 1.1", "本家の土台・LoRAと相性がよい"),
        ImageModelDef("novaanime", "Nova Anime XL", "アニメと2.5Dの中間"),
        ImageModelDef("plantmilk", "Plant Milk", "混ぜモデル・味つけが違う"),
        ImageModelDef("illust2", "Illustrious XL 2.0", "1.1の後継。構図とタグの理解がよい。"),
        ImageModelDef("animagine", "Animagine XL 4.0", "キャラ名タグの再現に強い定番。"),
        ImageModelDef("rouwei", "RouWei 0.8", "色と構図が良く、成人向けも出しやすい。"),
        ImageModelDef("chenkin", "ChenkinNoob XL", "AIらしさが少ない、NoobAI系の新しい絵。"),
        ImageModelDef("janku", "JANKU v7.77", "高精細で背景・衣装が細かい。成人向けも可。"),
        ImageModelDef("ntrmix", "NTR MIX XIII", "濃いめの塗りで、成人向けに強い。"),
        ImageModelDef("desire", "Anime Desire v3", "成人向けのアニメ・同人調に特化。"),
        ImageModelDef("cyberanime", "CyberIllustrious Anime", "光と肌の質感が自然なアニメ絵。"),
        ImageModelDef("pencil", "pencil XL", "日本のイラスト寄りの、やわらかい絵。"),
      ),
    "📷 実写・半実写" to
      listOf(
        ImageModelDef("zimage", "Z-Image", "構図が正確・日本人の実写も自然"),
        ImageModelDef("klein", "FLUX.2 Klein 9B", "文章の指示に強い・高速（初回はDL）"),
        ImageModelDef("qwenimg", "Qwen Image 2.1", "日本語と細かい描写に強い（初回はDL）"),
        ImageModelDef("qwenimgturbo", "Qwen Image 2.1 Turbo", "公式8ステップ・高速。差分・ポーズ・透明背景向け（初回はDL）"),
        ImageModelDef("asianreal", "Asian Realism", "日本人・アジア系の実写"),
        ImageModelDef("lustify", "Lustify", "実写・制限解除"),
        ImageModelDef("cyberreal", "CyberRealistic", "実写・肌や光が自然"),
        ImageModelDef("juggernaut", "Juggernaut XL", "実写の定番"),
        ImageModelDef("realvis", "RealVisXL", "人物写真向け"),
        ImageModelDef("realismil", "Realism Illustrious", "アニメ寄りの実写（半実写）"),
        ImageModelDef("cybersemi", "CyberIllustrious 半実写", "アニメ寄りの顔で、質感は実写寄り。"),
        ImageModelDef("cyberpony", "CyberRealistic Pony", "Pony 系の実写。成人向けに強く、ポーズが多彩。"),
      ),
  )

internal val IMAGE_MODEL_GROUPS: List<Pair<String, List<ImageModelDef>>> =
  IMAGE_MODEL_GROUPS_RAW.map { (family, defs) -> family to withTime(defs) }

/** 全モデルを1列に並べたもの（id, 画面の名前）。 */
internal val IMAGE_MODELS_FLAT: List<Pair<String, String>> =
  IMAGE_MODEL_GROUPS.flatMap { (_, models) -> models.map { it.id to it.name } }

/** DMD2 の「速い」モードに対応するモデル（NoobAI は V-Pred、Z-Image は別の仕組みなので対応しない）。 */
internal val FAST_CAPABLE_IMAGE_MODELS: Set<String> =
  IMAGE_MODEL_GROUPS.flatMap { (_, models) -> models.map { it.id } }.toSet() - setOf("noobai", "zimage")
