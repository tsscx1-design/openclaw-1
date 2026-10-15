package ai.openclaw.app.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.convertUriToBase64DataUrl
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 漫画の作品（フォルダ）。巻は古い順。 */
private data class MangaWorkInfo(val id: String, val title: String, val volumes: List<MangaVolumeInfo>)

private data class MangaVolumeInfo(val jobId: String, val volume: Int, val title: String)

private fun parseMangaWorks(arr: JSONArray?): List<MangaWorkInfo> {
  val list = arr ?: return emptyList()
  return (0 until list.length()).map { i ->
    val o = list.getJSONObject(i)
    val vs = o.optJSONArray("volumes") ?: JSONArray()
    MangaWorkInfo(
      id = o.optString("id"),
      title = o.optString("title"),
      volumes = (0 until vs.length()).map { j ->
        val v = vs.getJSONObject(j)
        MangaVolumeInfo(v.optString("jobId"), v.optInt("volume", 1), v.optString("title"))
      },
    )
  }
}

private fun workOf2(works: List<MangaWorkInfo>, jobId: String): MangaWorkInfo? =
  works.firstOrNull { w -> w.volumes.any { it.jobId == jobId } }

private data class MediaItem(
  val jobId: String,
  val kind: String,
  val status: String,
  val request: String,
  val stage: String,
  val elapsedSec: Int,
  val estimateSec: Int = 0,
  val remainingSec: Int = 0,
  val durationSec: Int,
  val error: String,
  val mime: String,
  val lyrics: String,
  val qualityStatus: String = "",
  val qualityNote: String = "",
  val branchName: String = "",
  /** キャラクター名またはパートナー名（見分けやすいように、一覧と保存名に使う）。 */
  val owner: String = "",
  /** 複数ページの漫画のページ数（1枚だけなら 0）。 */
  val parts: Int = 0,
  /** 漫画の形（4koma / page / book）と、複数ページの漫画の巻数・題名。 */
  val mangaLayout: String = "",
  val mangaVolume: Int = 0,
  val mangaTitle: String = "",
  /** 先頭の 1 枚が表紙か。 */
  val mangaCover: Boolean = false,
)

private const val MEDIA_POLL_MS = 4_000L
private const val MEDIA_PREVIEW_MAX_PX = 900
private const val MEDIA_UPLOAD_MAX_PX = 1344
/** Results listed per library tab (the PC keeps them all until deleted). */
private const val MEDIA_LIBRARY_PAGE = 120
/** SDXL checkpoints that take the DMD2 "fast" mode (NoobAI is V-Pred, Z-Image runs elsewhere). */
private val FAST_IMAGE_MODELS = FAST_CAPABLE_IMAGE_MODELS
/** Video engines that make a fixed 2 s clip in sd.cpp (no length choice). */
private val SDCPP_VIDEO_ENGINES = setOf("standard", "remix")

/** "完成まで 約12分（順番待ち 約5分を含む）・目安" from a media.estimate reply. */
private fun formatEstimate(res: JSONObject): String {
  val total = res.optInt("totalSec")
  if (total <= 0) return ""
  val wait = res.optInt("waitSec")
  fun minutes(sec: Int) = if (sec < 60) "1分以内" else "約${(sec + 59) / 60}分"
  val waitPart = if (wait > 0) "（順番待ち ${minutes(wait)}を含む）" else ""
  val basis = if (res.optInt("samples") > 0) "これまでの実績から" else "目安"
  return "完成まで ${minutes(total)}$waitPart・$basis"
}

private val IMAGE_MODEL_LABELS = IMAGE_MODELS_FLAT.toMap()
private val VIDEO_ENGINE_LABELS =
  mapOf(
    "standard" to "標準",
    "remix" to "実写 Remix",
    "long" to "長い動画",
    "audio" to "音付き",
    "fast" to "高速",
    "h3" to "音付き実写 H3",
  )
/** Motion comic options (ids match MOTION_OPTIONS in the gateway), grouped as shown. */
internal val MOTION_OPTION_GROUPS: List<Pair<String, List<Pair<String, String>>>> =
  listOf(
    // ─ シチュエーション ─
    "シチュエーション" to listOf(
      "rape" to "レイプ・強制",
      "sleep" to "睡眠姦",
      "voyeur" to "盗撮・のぞき",
      "exhibition" to "露出・野外",
      "train_molestation" to "痴漢（電車）",
      "blackmail" to "脅迫・弱みを握る",
      "hypnosis" to "催眠",
      "drugged" to "薬・媚薬",
      "mind_break" to "理性崩壊",
      "ntr" to "寝取り・NTR",
      "cheating" to "浮気・不倫",
      "sugar_daddy" to "パパ活",
      "orgy" to "乱交・輪姦",
      "gangbang" to "ギャング姦",
      "harem" to "ハーレム（複数女性）",
      "femdom" to "女王様・主従逆転",
      "peeping" to "のぞき・隠し撮り",
      "prostitution" to "売春・援助交際",
      "office" to "オフィス・職場",
      "teacher_student" to "先生と生徒（成人）",
      "doctor" to "診察・医師",
      "shrine_maiden" to "巫女・神社",
      "nurse" to "ナース",
      "maid" to "メイド",
    ),
    // ─ 体験・初回 ─
    "体験" to listOf(
      "virgin" to "処女（血あり）",
      "first_anal" to "アナル初体験",
      "first_time" to "初体験・初エッチ",
    ),
    // ─ 調教・支配 ─
    "調教・支配" to listOf(
      "bdsm" to "BDSM総合",
      "pet_play" to "ペット調教",
      "ahegao_training" to "アヘ顔調教",
      "creampie_training" to "中出し調教",
      "orgasm_denial" to "絶頂禁止",
      "forced_orgasm" to "強制絶頂",
    ),
    // ─ ピアス ─
    "ピアス" to listOf("navel_piercing" to "へそピアス", "ear_piercing" to "耳ピアス", "nipple_piercing" to "乳首ピアス", "tongue_piercing" to "舌ピアス", "lip_piercing" to "口ピアス"),
    // ─ 体の印 ─
    "体の印" to listOf("womb_tattoo" to "淫紋", "hickey" to "痣・キスマーク", "tattoo" to "タトゥー", "body_writing" to "落書き", "tan_lines" to "日焼け跡"),
    // ─ 小物 ─
    "小物" to listOf("glasses" to "眼鏡", "collar" to "首輪", "leash" to "首輪とリード", "eyepatch" to "眼帯", "blindfold" to "目隠し", "ball_gag" to "ボールギャグ"),
    // ─ 拘束 ─
    "拘束" to listOf("bound_wrists" to "手首の拘束", "shibari" to "縄（緊縛）", "handcuffs" to "手錠", "spreader_bar" to "脚開き拘束", "suspension" to "宙吊り"),
    // ─ 状態・体液 ─
    "状態" to listOf("drool" to "ヨダレ", "foam" to "口から泡", "faint" to "失神", "tears" to "涙", "sweaty" to "汗だく", "oiled" to "ローション", "lactation" to "母乳", "cum_on_body" to "外出し・ぶっかけ", "multiple_creampie" to "中出し連続"),
    // ─ 耳・角など ─
    "獣耳など" to listOf("cat_ears" to "猫耳としっぽ", "bunny_ears" to "うさ耳", "demon" to "悪魔の角としっぽ"),
    // ─ 毛 ─
    "毛" to listOf("shaved" to "パイパン", "pubic_hair" to "陰毛あり"),
    // ─ シチュエーション（追加）─
    "シチュエーション（天気・時間）" to listOf("h6s_rain_walk" to "雨の中", "h6s_rain_downpour" to "土砂降りの雨", "h6s_rain_umbrella" to "ひとつの傘で", "h6s_rain_alley" to "雨の路地裏"),
    "シチュエーション（雰囲気）" to listOf("h6s_soaked" to "ずぶ濡れ", "h6s_soaked_shirt" to "濡れ透け（シャツ）", "h6s_soaked_dress" to "濡れたワンピース", "h6s_soaked_towel" to "ずぶ濡れのあとタオルで拭く", "h6s_outdoor_exposure" to "野外で服をゆるめる", "h6s_closet_hide" to "押入れに隠れる", "h6s_closet_futon" to "押入れから布団を出す", "h6s_bound_hogtie" to "手足をまとめて縛る（同意の上）", "h6s_bound_wrists" to "手首を縛られる（同意の上）", "h6s_bound_chair" to "椅子に縛られる（同意の上）", "h6s_bound_silk" to "シルクのスカーフで縛る（同意の上）", "h6s_mlegs_bed" to "M字に脚を開いて見せる", "h6s_mlegs_inspect" to "ベッドで脚を開いて待つ"),
    "シチュエーション（場所）" to listOf("h6s_soaked_shower" to "シャワーを浴びる", "h6s_car_night" to "夜の車の中", "h6s_car_back_seat" to "車の後部座席", "h6s_car_parking_lot" to "駐車場に停めた車", "h6s_car_mountain" to "山道の展望スポットの車内", "h6s_car_rain" to "雨の日の車内", "h6s_outdoor_night_park" to "夜の公園", "h6s_outdoor_forest" to "森の中", "h6s_outdoor_beach_night" to "夜の海辺", "h6s_outdoor_rooftop" to "夜の屋上", "h6s_outdoor_tent" to "テントの中", "h6s_outdoor_trail" to "ひと気のない遊歩道", "h6s_closet_dark" to "押入れの中"),
    // ─ シチュエーション（追加）─
    "シチュエーション（場所）" to listOf("h5s_001" to "レストランの個室", "h5s_002" to "夜景の見えるレストラン", "h5s_003" to "屋上のインフィニティプール", "h5s_004" to "ホテルのスカイラウンジ", "h5s_005" to "ホテルのバスローブの朝", "h5s_006" to "ホテルのバスタブ(窓付き)", "h5s_007" to "ジャグジー付きのテラス", "h5s_008" to "ダブルのコネクティングルーム", "h5s_009" to "メゾネットの寝室", "h5s_010" to "和モダンの寝室", "h5s_011" to "洞窟風呂", "h5s_012" to "岩風呂の混浴", "h5s_013" to "貸切の家族風呂風(大人二人)", "h5s_014" to "足湯のベンチ", "h5s_015" to "砂風呂", "h5s_016" to "岩盤浴", "h5s_017" to "ミストサウナ", "h5s_018" to "サウナ後の水風呂", "h5s_019" to "テントサウナと湖", "h5s_020" to "スパのトリートメントルーム", "h5s_021" to "スパのカップルルーム", "h5s_022" to "ヘアサロンの閉店後", "h5s_023" to "エステサロンのベッド", "h5s_024" to "ネイルサロンの帰り", "h5s_025" to "撮影スタジオのホリゾント", "h5s_026" to "ブライダル衣装の試着室", "h5s_027" to "ドレスの更衣室", "h5s_028" to "劇場のボックス席", "h5s_029" to "劇場のロビー", "h5s_030" to "オペラ座の廊下", "h5s_031" to "舞台の上の二人", "h5s_032" to "美術館のギャラリー", "h5s_033" to "彫刻の庭", "h5s_034" to "ジャズバー", "h5s_035" to "ピアノバー", "h5s_036" to "ワインセラー", "h5s_037" to "ルーフトップバー", "h5s_038" to "シガーバー", "h5s_039" to "隠れ家バー", "h5s_040" to "屋台のカウンター", "h5s_041" to "立ち飲み屋", "h5s_042" to "夜市", "h5s_043" to "ライブハウスの最前列", "h5s_044" to "クラブのバルコニー席", "h5s_045" to "カジノのテーブル", "h5s_046" to "ボウリング場の夜", "h5s_047" to "ダーツバー", "h5s_048" to "ビリヤード場", "h5s_049" to "卓球ができる温泉宿", "h5s_050" to "ゲームセンター", "h5s_051" to "プリクラの個室", "h5s_052" to "水族館のトンネル", "h5s_053" to "動物園の夕暮れ", "h5s_054" to "植物園の温室", "h5s_055" to "バラ園", "h5s_056" to "ラベンダー畑", "h5s_057" to "ひまわり畑", "h5s_059" to "コスモス畑", "h5s_060" to "棚田の夕景", "h5s_061" to "吊り橋の上", "h5s_062" to "展望台の夜景", "h5s_063" to "回転展望台", "h5s_064" to "観瀑台の水しぶき", "h5s_065" to "海中展望塔", "h5s_066" to "灯台の下の岬", "h5s_067" to "断崖の遊歩道", "h5s_068" to "朝の海辺の散歩", "h5s_069" to "夜の海岸", "h5s_070" to "入り江の秘密のビーチ", "h5s_071" to "南国のヴィラ", "h5s_072" to "水上コテージ", "h5s_073" to "ガラス張りのドームホテル", "h5s_074" to "ツリーハウス", "h5s_075" to "氷のホテル", "h5s_076" to "洞窟ホテル", "h5s_077" to "古民家の宿", "h5s_078" to "町家の離れ", "h5s_079" to "茶畑の見える宿", "h5s_080" to "寺の宿坊", "h5s_081" to "神社の石段", "h5s_082" to "竹林の露天", "h5s_083" to "雪見露天", "h5s_084" to "雪見の縁側", "h5s_085" to "スキー場のゴンドラ", "h5s_086" to "ゲレンデのカフェ", "h5s_087" to "スノーモービル", "h5s_088" to "ソリ遊び", "h5s_089" to "かまくらの中", "h5s_090" to "氷の祭りの会場", "h5s_091" to "イルミネーションの並木", "h5s_092" to "クリスマスマーケット", "h5s_093" to "ヨットのクルージング", "h5s_094" to "ヨットの船首", "h5s_095" to "カタマランのネット", "h5s_096" to "釣り船の朝", "h5s_097" to "遊覧船のデッキ", "h5s_098" to "屋形船", "h5s_099" to "ゴンドラの舟遊び", "h5s_100" to "カヌーで湖", "h5s_101" to "ボートハウス", "h5s_102" to "海辺のテラス", "h5s_103" to "キャンプ場の星空", "h5s_104" to "グランピングのドーム", "h5s_105" to "トレーラーハウス", "h5s_106" to "バンライフの車内", "h5s_107" to "湖畔のコテージ", "h5s_108" to "暖炉の前の毛皮のラグ", "h5s_109" to "山頂の朝日", "h5s_110" to "登山小屋の夜", "h5s_111" to "森の中の小川", "h5s_112" to "森の木漏れ日", "h5s_113" to "洞窟探検", "h5s_114" to "火山のふもとの温泉", "h5s_115" to "砂丘の夕暮れ", "h5s_116" to "草原の丘", "h5s_117" to "牧場のサイロ", "h5s_119" to "茶室の庭", "h5s_120" to "日本庭園の池", "h5s_121" to "和傘の小径", "h5s_122" to "着物の着付け部屋", "h5s_123" to "温泉街の射的場", "h5s_124" to "温泉卵のコーナー", "h5s_125" to "縁日の金魚すくい", "h5s_126" to "露店のりんご飴", "h5s_127" to "神楽殿", "h5s_128" to "神輿の通り", "h5s_129" to "盆踊りの輪", "h5s_130" to "灯篭流し"),
    "シチュエーション（旅・乗り物）" to listOf("h5s_131" to "新幹線の窓際", "h5s_132" to "ロマンスカーの展望席", "h5s_133" to "ローカル線の無人駅", "h5s_134" to "夜行バスの隣席", "h5s_135" to "フェリーのデッキ", "h5s_136" to "フェリーの個室", "h5s_137" to "豪華列車のラウンジ", "h5s_138" to "トロッコ列車", "h5s_139" to "ロープウェイ", "h5s_140" to "ビジネスクラスのシート", "h5s_141" to "プライベートジェット", "h5s_142" to "空港のラウンジ", "h5s_143" to "空港のロビーで待ち合わせ", "h5s_144" to "ホテルのロビー", "h5s_145" to "ベルボーイの去った後の部屋", "h5s_146" to "レンタカーのドライブ", "h5s_147" to "夜のドライブ", "h5s_148" to "高速道路のサービスエリア", "h5s_149" to "オープンカー", "h5s_150" to "サイドカー付きバイク", "h5s_151" to "バイクの二人乗り", "h5s_152" to "自転車の二人乗り風景", "h5s_153" to "馬車の旅", "h5s_154" to "気球の上", "h5s_155" to "ヘリコプターの遊覧", "h5s_156" to "海上のクルーザー", "h5s_157" to "寝台特急のラウンジカー", "h5s_158" to "キャラバンのテント", "h5s_159" to "登山鉄道の窓", "h5s_160" to "バスの最後列で", "h5s_161" to "地下鉄の終電", "h5s_162" to "駅のホーム別れ", "h5s_163" to "旅先の古い宿場町"),
    "シチュエーション（季節・行事）" to listOf("h5s_164" to "初日の出", "h5s_165" to "おせちの正月", "h5s_166" to "振袖の成人式(大人)", "h5s_167" to "節分の豆まき", "h5s_168" to "梅の花見", "h5s_169" to "ひな祭りの夜", "h5s_170" to "ホワイトデーのお返し", "h5s_171" to "桜並木のお花見", "h5s_172" to "お花見の宴会", "h5s_173" to "ゴールデンウィークの旅", "h5s_174" to "こいのぼりの川辺", "h5s_175" to "母の日(大人)の贈り物", "h5s_176" to "紫陽花の小径", "h5s_177" to "蛍の夜", "h5s_178" to "七夕の短冊", "h5s_179" to "夏の海開き", "h5s_180" to "海の家", "h5s_181" to "スイカ割り", "h5s_182" to "夏の花火(手持ち)", "h5s_183" to "大輪の打ち上げ花火", "h5s_184" to "花火大会の人混み", "h5s_185" to "夏の縁日", "h5s_186" to "夏の風鈴", "h5s_187" to "蚊帳の中", "h5s_188" to "お盆の帰省", "h5s_189" to "夏の入道雲", "h5s_190" to "秋の月見", "h5s_191" to "秋の紅葉狩り", "h5s_192" to "紅葉の露天風呂", "h5s_193" to "栗拾い", "h5s_194" to "芋煮会", "h5s_195" to "ハロウィンパーティー(大人)", "h5s_196" to "ハロウィンの小悪魔仮装", "h5s_197" to "ハロウィンの屋敷", "h5s_199" to "ボジョレーの解禁", "h5s_200" to "勤労感謝の温泉", "h5s_201" to "冬至のゆず湯", "h5s_202" to "クリスマスのイルミネーション", "h5s_203" to "クリスマスのディナー", "h5s_204" to "クリスマスのサンタ衣装(大人)", "h5s_205" to "クリスマスケーキ", "h5s_206" to "大晦日の年越しそば", "h5s_207" to "除夜の鐘", "h5s_208" to "年越しの花火", "h5s_209" to "お正月の羽子板", "h5s_210" to "バレンタインの手作りチョコ", "h5s_211" to "バレンタインのディナー", "h5s_212" to "誕生日のサプライズ", "h5s_213" to "誕生日のホテルステイ", "h5s_214" to "記念日のクルーズ", "h5s_215" to "結婚式の控え室", "h5s_216" to "結婚式のバージンロード", "h5s_217" to "結婚式のブーケトス", "h5s_218" to "二次会のダンスフロア", "h5s_219" to "ガーデンウェディング", "h5s_220" to "フォトウェディング", "h5s_221" to "新居の引っ越し", "h5s_222" to "新居の初夜"),
    "シチュエーション（天気・時間）" to listOf("h5s_223" to "夕立のあとの虹", "h5s_224" to "通り雨の軒下", "h5s_225" to "びしょ濡れの帰り道", "h5s_226" to "雨のカフェの窓際", "h5s_227" to "雨音の寝室", "h5s_228" to "雨のバスルーム", "h5s_229" to "秋の長雨", "h5s_230" to "吹雪の山荘", "h5s_231" to "粉雪の朝", "h5s_232" to "雪の降る駅", "h5s_233" to "霧の湖畔", "h5s_234" to "朝焼けのベランダ", "h5s_235" to "朝の寝起き", "h5s_236" to "朝のシャワー", "h5s_237" to "朝の洗面台", "h5s_238" to "朝食のテーブル", "h5s_239" to "昼下がりのカフェ", "h5s_240" to "昼下がりのソファ", "h5s_241" to "夕方のスーパーの帰り", "h5s_242" to "夕暮れの公園", "h5s_243" to "黄昏の橋の上", "h5s_244" to "薄暮の海", "h5s_245" to "月夜のテラス", "h5s_246" to "新月の星空", "h5s_247" to "真夜中のラーメン", "h5s_248" to "真夜中のベッドで読書", "h5s_249" to "深夜のドライブスルー", "h5s_250" to "未明の台所", "h5s_251" to "真昼のプール", "h5s_252" to "真夏の縁側", "h5s_253" to "猛暑の室内", "h5s_254" to "扇風機の前", "h5s_255" to "エアコンの効いた部屋", "h5s_256" to "梅雨明けの青空", "h5s_257" to "台風一過", "h5s_258" to "停電のろうそく", "h5s_259" to "雷雨の窓辺", "h5s_260" to "朝霧の高原"),
    "シチュエーション（関係・役割）" to listOf("h5s_261" to "同棲カップルの日曜", "h5s_262" to "結婚記念日の夫婦", "h5s_263" to "年上の恋人", "h5s_264" to "年下の恋人(成人)", "h5s_266" to "同窓会のあと(大人同士)", "h5s_267" to "隣の部屋の住人", "h5s_268" to "ルームシェア", "h5s_269" to "シェアハウスの夜", "h5s_270" to "同僚との出張", "h5s_271" to "同僚の飲み会帰り", "h5s_272" to "先輩と後輩(職場)", "h5s_273" to "社長と秘書", "h5s_274" to "編集者と作家", "h5s_276" to "写真家とモデル", "h5s_277" to "ダンサーとパートナー", "h5s_278" to "ピアニストと聴き手", "h5s_279" to "バーテンダーとの夜", "h5s_280" to "ソムリエとお客", "h5s_281" to "料理人と客", "h5s_282" to "旅先で出会った二人", "h5s_283" to "ビーチで知り合った二人", "h5s_284" to "スキー場の出会い", "h5s_285" to "登山の途中の出会い", "h5s_286" to "航海中の船客", "h5s_287" to "外国人の恋人", "h5s_288" to "元恋人との旅", "h5s_289" to "許嫁との再会", "h5s_290" to "長年仕えた執事", "h5s_291" to "お屋敷の令嬢と護衛", "h5s_292" to "女将と常連客", "h5s_293" to "仲居と宿泊客", "h5s_294" to "和服の師匠と弟子", "h5s_295" to "ヨガのパートナー", "h5s_297" to "ジムのトレーナー", "h5s_298" to "スタイリストと客", "h5s_299" to "ホテルのコンシェルジュ", "h5s_300" to "客室乗務員と機長", "h5s_301" to "バスガイドの休憩", "h5s_302" to "オフの日の女医", "h5s_303" to "弁護士と依頼人", "h5s_304" to "記者と取材相手", "h5s_305" to "夜の書店員", "h5s_306" to "花屋の店主", "h5s_307" to "パティシエと試食", "h5s_308" to "ライバル会社の二人", "h5s_309" to "ライバルのレーサー", "h5s_310" to "剣の師範と門下(大人)", "h5s_311" to "貴族と庶民", "h5s_312" to "王妃と近衛", "h5s_313" to "踊り子と旅人", "h5s_314" to "海賊と船長(大人)", "h5s_315" to "魔法使いと使い魔"),
    "シチュエーション（雰囲気）" to listOf("h5s_316" to "手を恋人つなぎ", "h5s_317" to "腕を組んで歩く", "h5s_318" to "肩にもたれる", "h5s_319" to "後ろからハグして耳に息", "h5s_320" to "前髪を直してもらう", "h5s_321" to "ボタンを留めてもらう", "h5s_322" to "ネクタイを結ぶ", "h5s_323" to "ファスナーを上げてもらう", "h5s_324" to "ペアルックの朝", "h5s_325" to "一つのマフラー", "h5s_326" to "一つの傘", "h5s_327" to "ひとつの毛布", "h5s_328" to "映画鑑賞の夜", "h5s_329" to "一緒に料理", "h5s_330" to "キッチンでのつまみ食い", "h5s_331" to "後ろから包丁を握る手", "h5s_332" to "お風呂上がりのタオルドライ", "h5s_333" to "お酒を口移し風に", "h5s_334" to "アイスの食べさせ合い", "h5s_335" to "ポッキーゲーム", "h5s_336" to "ダンスの手ほどき", "h5s_337" to "マッサージの交換", "h5s_338" to "肩もみのお返し", "h5s_339" to "首筋のマッサージ", "h5s_340" to "オイルマッサージ", "h5s_341" to "足裏マッサージ", "h5s_342" to "耳かきのお返し", "h5s_343" to "彼の膝に頭を乗せる", "h5s_344" to "抱きしめて眠る", "h5s_345" to "腕枕", "h5s_346" to "背中合わせ", "h5s_347" to "寄り添って星を見る", "h5s_348" to "ベランダで月を見る", "h5s_349" to "暖炉の前のダンス", "h5s_350" to "キャンドルライトのバス", "h5s_351" to "花びらを散らしたベッド", "h5s_352" to "風船の部屋", "h5s_353" to "紙吹雪のキス", "h5s_354" to "指輪の贈り物", "h5s_355" to "プロポーズの夜景", "h5s_356" to "手紙を読む", "h5s_357" to "照れ隠しの視線", "h5s_358" to "潤んだ瞳で見上げる", "h5s_359" to "いたずらな笑み", "h5s_360" to "耳たぶを甘噛み", "h5s_361" to "指先にキス", "h5s_362" to "手の甲へのキス", "h5s_363" to "うなじへのキス", "h5s_364" to "鎖骨のキス", "h5s_365" to "背中へのキス", "h5s_366" to "ひざへのキス", "h5s_367" to "足の指先へのキス", "h5s_368" to "濡れた髪をかきあげる", "h5s_369" to "濡れたシャツ越しの肌", "h5s_370" to "バスローブをはだける", "h5s_371" to "シーツにくるまる", "h5s_372" to "水着の紐を結んでもらう", "h5s_373" to "日焼け止めを塗り合う", "h5s_374" to "砂のついた肌", "h5s_375" to "ビーチタオルに寝そべる", "h5s_376" to "プールでじゃれ合い", "h5s_377" to "浮き輪で漂う", "h5s_378" to "水かけっこ", "h5s_379" to "雪合戦", "h5s_380" to "雪だるまづくり", "h5s_381" to "温かい飲み物を分ける", "h5s_382" to "酔いつぶれた肩を貸す", "h5s_383" to "二日酔いの朝の看病", "h5s_384" to "風邪の看病", "h5s_385" to "寝顔を見つめる", "h5s_386" to "寝ぼけて抱きつく", "h5s_387" to "いってきますのキス", "h5s_388" to "おかえりのハグ"),
    // ─ シチュエーション（追加）─
    "シチュエーション（場所）" to listOf("g4s_night_pool" to "ナイトプール", "g4s_onsen_open_air" to "露天風呂", "g4s_private_onsen_room" to "部屋付き露天風呂", "g4s_love_hotel" to "ラブホテルの部屋", "g4s_luxury_hotel_suite" to "高級ホテルのスイート", "g4s_car_interior_night" to "夜の車内", "g4s_car_rain_parking" to "雨の駐車場の車内", "g4s_elevator_late" to "深夜のエレベーター", "g4s_rooftop_night" to "屋上の夜景", "g4s_photo_studio" to "撮影スタジオ", "g4s_backstage_live" to "ライブ会場の楽屋", "g4s_live_house_crowd" to "ライブハウス", "g4s_library_closed" to "閉館後の図書館", "g4s_office_after_hours" to "残業のオフィス", "g4s_meeting_room" to "会議室", "g4s_clinic_roleplay" to "診察室ごっこ", "g4s_nurse_station_play" to "ナース服のロールプレイ", "g4s_ryokan_room" to "旅館の和室", "g4s_ryokan_corridor" to "旅館の廊下", "g4s_camp_tent" to "キャンプのテント", "g4s_campfire_night" to "焚き火の夜", "g4s_beach_sunset" to "夕暮れのビーチ", "g4s_private_beach" to "プライベートビーチ", "g4s_yacht_deck" to "ヨットのデッキ", "g4s_yacht_cabin" to "ヨットの船室", "g4s_night_club_vip" to "クラブのVIPルーム", "g4s_dance_floor" to "クラブのダンスフロア", "g4s_tatami_room" to "和室の畳", "g4s_castle_chamber" to "城の寝室", "g4s_amusement_park_night" to "夜の遊園地", "g4s_ferris_wheel" to "観覧車のゴンドラ", "g4s_movie_theater" to "映画館の最後列", "g4s_gym_after_hours" to "閉店後のジム", "g4s_yoga_studio" to "ヨガスタジオ", "g4s_sauna_room" to "サウナ室", "g4s_wedding_afterparty" to "結婚式の二次会", "g4s_home_party" to "ホームパーティー", "g4s_bar_counter" to "バーのカウンター", "g4s_izakaya_booth" to "居酒屋の個室", "g4s_karaoke_room" to "カラオケの個室", "g4s_bath_house" to "銭湯の貸切風呂", "g4s_bathroom_home" to "自宅の浴室", "g4s_bubble_bath" to "泡風呂", "g4s_shower_room" to "シャワールーム", "g4s_kitchen_morning" to "朝のキッチン", "g4s_living_sofa" to "リビングのソファ", "g4s_bedroom_dim" to "間接照明の寝室", "g4s_balcony_night" to "夜のバルコニー", "g4s_stairwell_emergency" to "非常階段", "g4s_parking_garage" to "地下駐車場", "g4s_fitting_room" to "試着室", "g4s_hot_spring_town" to "温泉街の夜", "g4s_festival_shrine" to "祭りの神社の裏", "g4s_garden_pavilion" to "庭園の東屋", "g4s_bamboo_forest" to "竹林の小径", "g4s_greenhouse" to "温室", "g4s_ice_skating_rink" to "スケートリンク", "g4s_ski_lodge" to "スキー場のロッジ", "g4s_mountain_cabin" to "山小屋", "g4s_space_station" to "宇宙ステーション", "g4s_cyberpunk_alley" to "ネオン街の路地", "g4s_fantasy_inn" to "ファンタジーの宿屋", "g4s_harem_tent" to "砂漠のテント", "g4s_train_sleeper" to "寝台列車の個室", "g4s_airplane_lounge" to "飛行機のファーストクラス", "g4s_cruise_cabin" to "クルーズ船のキャビン", "g4s_art_museum_night" to "夜の美術館", "g4s_aquarium_dark" to "暗い水族館", "g4s_planetarium" to "プラネタリウム", "g4s_laundromat_night" to "深夜のコインランドリー", "g4s_convenience_night" to "深夜のコンビニ前", "g4s_bookstore_cafe" to "夜のブックカフェ", "g4s_tea_ceremony_room" to "茶室", "g4s_dojo_empty" to "道場", "g4s_stage_theater" to "劇場の舞台袖", "g4s_swimming_pool_private" to "貸切の室内プール", "g4s_waterfall_pool" to "滝つぼ", "g4s_beach_house" to "海辺のコテージ", "g4s_farm_barn" to "牧場の納屋", "g4s_lighthouse" to "灯台"),
    "シチュエーション（関係・役割）" to listOf("g4s_boss_subordinate" to "上司と部下", "g4s_female_boss" to "女上司と部下", "g4s_secretary_role" to "秘書", "g4s_housewife_neighbor" to "人妻（隣人）", "g4s_older_sister_type" to "頼れるお姉さん", "g4s_maid_master" to "メイドと主人", "g4s_butler_lady" to "執事とお嬢様", "g4s_master_servant" to "主従の契約", "g4s_couple_lovers" to "恋人同士", "g4s_newlyweds" to "新婚夫婦", "g4s_rival_tension" to "ライバル同士", "g4s_ex_boyfriend" to "元カレとの再会", "g4s_ex_girlfriend" to "元カノとの再会", "g4s_best_friend_sibling" to "親友の兄", "g4s_coworker_secret" to "同僚との秘密", "g4s_business_trip" to "出張先の同室", "g4s_landlady" to "管理人のお姉さん", "g4s_bartender_guest" to "バーテンダーと客", "g4s_idol_manager" to "アイドルと担当者", "g4s_actress_director" to "女優と監督", "g4s_model_photographer" to "モデルとカメラマン", "g4s_fortune_teller" to "占い師と相談者", "g4s_princess_knight" to "姫と騎士", "g4s_witch_apprentice" to "魔女と弟子", "g4s_vampire_lover" to "吸血鬼と恋人", "g4s_succubus_encounter" to "サキュバスとの出会い", "g4s_angel_demon" to "天使と悪魔", "g4s_elf_adventurer" to "エルフと冒険者", "g4s_ninja_kunoichi" to "くノ一", "g4s_samurai_courtesan" to "侍と花魁", "g4s_queen_subject" to "女王様と下僕", "g4s_athlete_trainer" to "トレーナーとお客さん", "g4s_yoga_instructor" to "ヨガのインストラクター", "g4s_masseuse" to "マッサージ師", "g4s_hairdresser" to "美容師", "g4s_neighbor_widow" to "未亡人", "g4s_stepsister_adult" to "義姉（成人）", "g4s_cohabitation" to "同棲中の夜", "g4s_long_distance" to "遠距離恋愛の再会", "g4s_first_date_end" to "初デートの帰り道", "g4s_blind_date" to "お見合い", "g4s_honeymoon" to "ハネムーン", "g4s_anniversary_dinner" to "記念日のディナー"),
    "シチュエーション（天気・時間）" to listOf("g4s_rain_shelter" to "雨宿り", "g4s_rainy_window" to "雨の窓辺", "g4s_snow_night" to "雪の夜", "g4s_snow_cabin" to "雪に閉ざされた小屋", "g4s_dusk_balcony" to "夕暮れのベランダ", "g4s_sunset_beach_walk" to "夕日の浜辺の散歩", "g4s_midnight_city" to "真夜中の街", "g4s_fireworks_night" to "花火の夜", "g4s_summer_festival" to "夏祭り", "g4s_tanabata_night" to "七夕の夜", "g4s_cherry_blossom_night" to "夜桜", "g4s_autumn_leaves" to "紅葉の道", "g4s_new_year_shrine" to "初詣", "g4s_christmas_night" to "クリスマスの夜", "g4s_valentine" to "バレンタイン", "g4s_summer_heat" to "うだるような夏の日", "g4s_thunderstorm" to "雷雨の夜", "g4s_typhoon_inside" to "台風の夜の室内", "g4s_power_outage" to "停電", "g4s_morning_after" to "朝チュン", "g4s_early_morning_dawn" to "明け方の空", "g4s_noon_siesta" to "昼寝の時間", "g4s_late_night_kitchen" to "真夜中の台所", "g4s_moonlit_room" to "月明かりの寝室", "g4s_full_moon_garden" to "満月の庭", "g4s_starry_sky" to "満天の星空", "g4s_foggy_morning" to "霧の朝", "g4s_spring_breeze" to "春の風", "g4s_rainy_season_damp" to "梅雨の湿った夜", "g4s_hot_night_tropical" to "熱帯夜", "g4s_winter_kotatsu" to "こたつの冬", "g4s_new_years_eve" to "大晦日のカウントダウン", "g4s_golden_hour_window" to "黄金色の午後"),
    "シチュエーション（雰囲気）" to listOf("g4s_sweet_mood" to "あまあまな雰囲気", "g4s_intense_passion" to "激しく求め合う", "g4s_shy_bashful" to "恥じらい", "g4s_taking_lead" to "主導権を握る", "g4s_teasing_slowly" to "焦らす", "g4s_eye_contact" to "見つめ合う", "g4s_whisper_ear" to "耳元で囁く", "g4s_princess_carry" to "お姫様抱っこ", "g4s_wall_slam" to "壁ドン", "g4s_lap_pillow" to "膝枕", "g4s_sleep_together" to "添い寝", "g4s_forehead_touch" to "おでこをくっつける", "g4s_back_hug" to "バックハグ", "g4s_chin_lift" to "あごクイ", "g4s_kiss_forehead" to "おでこにキス", "g4s_deep_kiss" to "濃厚なキス", "g4s_neck_kiss" to "首筋へのキス", "g4s_hair_play" to "髪をなでる", "g4s_dry_hair" to "ドライヤーで髪を乾かす", "g4s_towel_wrap" to "バスタオル姿", "g4s_oversized_shirt" to "彼シャツ", "g4s_apron_naked" to "裸エプロン", "g4s_lingerie_reveal" to "下着姿のお披露目", "g4s_stockings_garter" to "ストッキングとガーター", "g4s_swimsuit_oil" to "サンオイルを塗る", "g4s_feed_each_other" to "食べさせ合う", "g4s_drunk_cuddly" to "酔って甘えん坊", "g4s_jealous_mood" to "やきもち", "g4s_tsundere_blush" to "ツンデレ", "g4s_yandere_gaze" to "ヤンデレの瞳", "g4s_confession" to "告白", "g4s_first_time_nervous" to "初めての夜", "g4s_reunion_hug" to "再会の抱擁", "g4s_make_up" to "仲直り", "g4s_bedroom_eyes" to "誘うまなざし", "g4s_strip_tease_slow" to "ストリップティーズ", "g4s_lap_sitting" to "膝の上に座る", "g4s_play_fight" to "じゃれ合い", "g4s_tickle_fight" to "くすぐり合い", "g4s_massage_back" to "背中のマッサージ", "g4s_foot_massage" to "足のマッサージ", "g4s_bath_together" to "一緒に入浴", "g4s_wash_hair" to "髪を洗ってもらう", "g4s_stargazing_blanket" to "毛布で星を見る", "g4s_secret_glance" to "こっそり目配せ", "g4s_under_table_touch" to "テーブルの下で手をつなぐ", "g4s_marked_neck" to "首筋のキスマーク", "g4s_mirror_gaze" to "鏡越しに見つめる", "g4s_lost_in_pleasure" to "余韻にひたる", "g4s_pillow_talk" to "ピロートーク", "g4s_sleepy_morning_kiss" to "寝起きのキス", "g4s_dancing_slow" to "ゆっくりダンス", "g4s_piano_duet" to "ピアノの連弾", "g4s_stolen_kiss" to "不意打ちのキス", "g4s_breathless_chase" to "追いかけっこ", "g4s_domination_soft" to "優しく命令される", "g4s_submissive_wait" to "待て（お預け）", "g4s_affection_praise" to "褒められてとろける"),
    // ─ プレイ（追加4：SMグッズ）─
    "プレイ（追加4）" to listOf("nose_hook" to "鼻フック", "mouth_spreader" to "強制開口器（開口マスク）", "tongue_clamp" to "舌クリップ", "pony_play" to "ポニープレイ", "pig_play" to "豚プレイ（鼻フック・しっぽ）", "human_furniture" to "人間椅子・家具プレイ", "sensory_deprivation" to "感覚遮断（耳栓と目隠し）", "nipple_weights" to "乳首ウェイト（おもり）", "clothespins_body" to "洗濯ばさみ（体につける）", "posture_collar" to "ネックコルセット（首を固定）", "ballet_boots" to "バレエブーツ（つま先立ち）", "ball_chain" to "足かせの鉄球"),
    // ─ プレイ（追加3）─
    "プレイ（追加3）" to listOf("tease_play" to "焦らしプレイ", "blindfold_play" to "目隠しプレイ", "abandon_play" to "放置プレイ", "dirty_talk" to "言葉責め", "watched_play" to "視姦プレイ", "brush_play" to "筆責め", "tickle_play" to "くすぐりプレイ", "shame_play" to "羞恥プレイ", "remote_vibe_public" to "遠隔バイブ（外で）", "hold_back_climax" to "イキ我慢", "shaving_play" to "剃毛プレイ", "ejaculation_control" to "射精管理", "trampling" to "踏みつけプレイ", "wrap_bondage" to "ラップ拘束", "male_squirt" to "男の潮吹き", "mutual_masturbation" to "相互オナニー", "cleaning_fellatio" to "お掃除フェラ（バキューム）", "vacuum_fellatio" to "バキュームフェラ", "irrumatio" to "イラマチオ", "saliva_play" to "唾液プレイ", "ball_licking" to "玉舐め", "tongue_cum" to "舌上射精", "cum_bubble" to "ザーメンぶくぶく", "toilet_play" to "トイレでプレイ", "karaoke_play" to "カラオケボックス", "uterus_sex" to "ウテルスセックス（深い挿入）", "love_doll_play" to "ラブドール化", "nip_slip" to "ポロリ", "coat_flash" to "裸コート", "scream_pleasure" to "快感の絶叫", "milk_feeding" to "母乳飲食", "soap_play" to "ソープ（泡・マット）", "deep_kiss" to "ディープキス"),
    // ─ プレイ（追加2）─
    "プレイ（追加2）" to listOf("squirt" to "潮吹き", "pregnant" to "妊娠（おなかが大きい）", "self_nipple_play" to "自分で乳首をいじる", "self_breast_play" to "自分で胸をいじる", "fingering" to "手マン", "clit_piercing" to "クリトリスピアス", "double_peace" to "ダブルピース", "anal_training" to "アナル開発", "swallow" to "ごっくん", "breeding" to "孕ませ（中出し・種付け）", "breast_sucking" to "乳吸", "enema" to "浣腸（プレイ）", "watersports" to "聖水（おしっこ）", "invisible_man" to "男透明化（ペニスだけ見える）", "pov_view" to "主観（POV）"),
    // ─ SM・グッズ・表情（カスタムメイドの分類を参考に追加）─
    "拘束（追加）" to listOf("pillory" to "ギロチン拘束（首と手首を固定）", "x_frame" to "X字の磔台", "armbinder" to "アームバインダー", "restraint_chair" to "拘束椅子", "wooden_horse" to "三角木馬", "bondage_tape" to "ボンデージテープ・ラップ", "body_harness" to "ボディハーネス（革ベルト）", "ring_gag" to "口枷（開口器）", "chains" to "鎖・足枷"),
    "状態（追加）" to listOf("aphrodisiac" to "媚薬で発情", "exhausted" to "ぐったり（絶頂後）", "heart_eyes" to "ハートの目"),
    "SM・責め" to listOf("whip" to "鞭", "riding_crop" to "乗馬鞭", "paddle_spank" to "スパンキング", "wax_play" to "ろうそく（低温）", "feather_tickle" to "くすぐり・羽根", "nipple_clamps" to "乳首クリップ", "ice_play" to "氷責め"),
    "グッズ" to listOf("vibrator" to "バイブ", "wand_massager" to "電マ", "rotor" to "ローター", "dildo" to "ディルド", "butt_plug" to "アナルプラグ（しっぽ付き含む）", "chastity_belt" to "貞操帯", "aphrodisiac_cream" to "媚薬クリーム・ローション"),
    "表情" to listOf("face_ahegao" to "アヘ顔（舌出し・白目寄り）", "face_melted" to "とろけ顔（半開きの目）", "face_flushed" to "上気した頬・潤んだ瞳", "face_bite_lip" to "唇を噛んで耐える", "face_climax" to "絶頂の顔", "face_seductive" to "誘う目つき・挑発の笑み", "face_contempt" to "見下す目つき"),
  )

/** Irodori-TTS delivery emojis offered as buttons for voice lines. */
private val VOICE_EMOJIS =
  listOf("🥵" to "喘ぎ", "😮‍💨" to "吐息", "🌬️" to "息切れ", "👂" to "囁き", "😮" to "息をのむ", "😱" to "絶叫", "😖" to "苦しげ", "💋" to "リップ", "👅" to "水音", "🤐" to "口塞ぎ", "😏" to "甘え", "🫣" to "照れ", "🙏" to "懇願", "🫶" to "優しく", "🥺" to "震え声", "😭" to "泣き", "🤭" to "笑い", "⏸️" to "間", "🐢" to "ゆっくり", "⏩" to "早口")

private val ASPECT_LABELS = mapOf("portrait" to "縦長", "square" to "正方形", "landscape" to "横長")

/** One line naming what will be made, for the confirmation before "作る". */
private fun generationSummary(
  kind: String,
  imageModel: String,
  fastImage: Boolean,
  aspect: String,
  videoEngine: String,
  videoSeconds: Int,
  smoothVideo: Boolean,
  musicSeconds: Int,
  instrumental: Boolean,
  mangaLayout: String,
  motionMinutes: Int = 1,
  motionFellatio: Boolean = false,
): String =
  when (kind) {
    "voice" -> "声・Irodori-TTS"
    "motion" -> "モーションコミック・約${motionMinutes}分" + (if (motionFellatio) "・最後にフェラ" else "")
    "manga" ->
      "漫画・" + (when (mangaLayout) { "page" -> "1ページ（5コマ）"; "book" -> "本格・複数ページ"; else -> "4コマ" }) +
        "・${IMAGE_MODEL_LABELS[imageModel] ?: imageModel}" +
        (if (fastImage && imageModel in FAST_IMAGE_MODELS) "（速い）" else "")
    "image" ->
      "画像・${IMAGE_MODEL_LABELS[imageModel] ?: imageModel}" +
        (if (fastImage && imageModel in FAST_IMAGE_MODELS) "（速い）" else "") +
        "・${ASPECT_LABELS[aspect] ?: aspect}"
    "video" ->
      "動画・${VIDEO_ENGINE_LABELS[videoEngine] ?: videoEngine}・" +
        (if (videoEngine in SDCPP_VIDEO_ENGINES) "2秒" else "${videoSeconds}秒") +
        (if (smoothVideo) "・なめらか" else "") +
        "・${ASPECT_LABELS[aspect] ?: aspect}"
    else -> "音楽・${musicSeconds}秒・" + (if (instrumental) "曲だけ" else "歌あり")
  }

private fun parseMediaItems(arr: JSONArray?): List<MediaItem> {
  val items = arr ?: return emptyList()
  return (0 until items.length()).map { i ->
    val o = items.getJSONObject(i)
    MediaItem(
      jobId = o.optString("jobId"),
      kind = o.optString("kind"),
      status = o.optString("status"),
      request = o.optString("request"),
      stage = o.optString("stage"),
      elapsedSec = o.optInt("elapsedSec", 0),
      estimateSec = o.optInt("estimateSec", 0),
      remainingSec = o.optInt("remainingSec", 0),
      durationSec = o.optInt("durationSec", 0),
      error = o.optString("error"),
      mime = o.optString("mime"),
      lyrics = o.optString("lyrics"),
      qualityStatus = o.optJSONObject("quality")?.optString("status").orEmpty(),
      qualityNote = o.optJSONObject("quality")?.optString("note").orEmpty(),
      branchName = o.optJSONObject("branch")?.optString("name").orEmpty(),
      owner = o.optString("owner"),
      parts = o.optInt("parts", 0),
      mangaLayout = o.optString("mangaLayout"),
      mangaVolume = o.optInt("mangaVolume", 0),
      mangaTitle = o.optString("mangaTitle"),
      mangaCover = o.optBoolean("mangaCover", false),
    )
  }
}

private fun kindLabel(kind: String) =
  when (kind) {
    "image" -> "🎨 画像"
    "video" -> "🎬 動画"
    "manga" -> "📖 漫画"
    "voice" -> "🎤 声"
    "motion" -> "🎞 モーション"
    else -> "🎵 音楽"
  }

private fun extensionFor(mime: String) =
  when (mime) {
    "image/png" -> "png"
    "video/webm" -> "webm"
    "video/mp4" -> "mp4"
    "video/x-matroska" -> "mkv"
    "audio/mpeg" -> "mp3"
    "audio/wav" -> "wav"
    else -> "bin"
  }

private fun decodePreview(bytes: ByteArray): Bitmap? =
  try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MEDIA_PREVIEW_MAX_PX) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
  } catch (_: Exception) {
    null
  }

/** Copies a finished file into Pictures / Movies / Music under an "OpenClaw" folder. */
private fun saveToDevice(context: Context, file: File, mime: String, owner: String = ""): Boolean {
  // キャラ名・パートナー名があれば、その名前のフォルダと、名前入りのファイル名で保存する。
  val safeOwner = owner.replace(Regex("[\\/:*?\"<>|]"), "_").trim()
  val (collection, folder) =
    when {
      mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
      mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
      else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
    }
  val values =
    ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, if (safeOwner.isNotEmpty()) "${safeOwner}_${file.name}" else file.name)
      put(MediaStore.MediaColumns.MIME_TYPE, mime)
      put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/OpenClaw" + if (safeOwner.isNotEmpty()) "/$safeOwner" else "")
    }
  val uri = context.contentResolver.insert(collection, values) ?: return false
  context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
  return true
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .heightIn(min = 44.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(
          if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f),
        )
        .border(
          width = if (selected) 1.5.dp else 1.dp,
          color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f),
          shape = RoundedCornerShape(10.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
      color = if (selected) ClawTheme.colors.text else ClawTheme.colors.textMuted,
    )
  }
}

@Composable
internal fun MediaStudioScreen(viewModel: MainViewModel, onBack: () -> Unit, onOpenComics: () -> Unit = {}, onOpenModelSamples: () -> Unit = {}, onOpenPoseSamples: () -> Unit = {}) {
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  var kind by remember { mutableStateOf("image") }
  // 作品の保存庫は、はじめはたたんである
  var libraryOpen by remember { mutableStateOf(false) }
  // 画像・動画・漫画の生成にも足す項目（キャラ図鑑・モーション・体位。"opt:…" "motion:…" "pose:…" "custom:…"）
  var extraItems by remember { mutableStateOf(setOf<String>()) }
  var extraOpen by remember { mutableStateOf(false) }
  if (extraOpen) ExtraItemsDialog(viewModel = viewModel, selected = extraItems, onChange = { extraItems = it }, onClose = { extraOpen = false })
  // ナビゲーションから「生成：動画」などで直接開かれたとき
  val deepTarget = ShellDeepLink.pending
  androidx.compose.runtime.LaunchedEffect(deepTarget) {
    val target = deepTarget ?: return@LaunchedEffect
    if (target.startsWith("media:")) {
      when (val what = target.removePrefix("media:")) {
        "modelSamples" -> onOpenModelSamples()
        "poseSamples" -> onOpenPoseSamples()
        "image", "video", "manga", "motion", "music", "voice" -> kind = what
      }
      ShellDeepLink.pending = null
    }
  }
  var prompt by remember { mutableStateOf("") }
  var aspect by remember { mutableStateOf("portrait") }
  var lyrics by remember { mutableStateOf("") }
  var instrumental by remember { mutableStateOf(false) }
  var musicSeconds by remember { mutableStateOf(90) }
  var mangaLayout by remember { mutableStateOf("4koma") }
  var mangaPages by remember { mutableStateOf(8) }
  var mangaTone by remember { mutableStateOf("color") }
  /** 続編を作るとき、続きもとの巻のジョブID（と表示用の題名）。 */
  var mangaSequelOf by remember { mutableStateOf<String?>(null) }
  var mangaSequelLabel by remember { mutableStateOf("") }
  /** false = キャラ図鑑で選んだ人だけで作る（AI が新しい人物を足さない）。 */
  var mangaAutoCast by remember { mutableStateOf(true) }
  var mangaCover by remember { mutableStateOf(true) }
  // Voice lines (Irodori-TTS): a studio voice or a described one, and the model.
  var voiceOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  var voiceId by remember { mutableStateOf("") }
  var voiceCaption by remember { mutableStateOf("") }
  var irodoriModel by remember { mutableStateOf("large") }
  // 音声ドラマ：お題から長い台本を書いて声にし、背景の音を重ねる
  var dramaOn by remember { mutableStateOf(false) }
  var dramaMinutes by remember { mutableStateOf(5) }
  var dramaAmbience by remember { mutableStateOf("rain") }
  // Motion comic: about how long, and whether it ends with fellatio.
  var motionMinutes by remember { mutableStateOf(1) }
  var motionFellatio by remember { mutableStateOf(true) }
  // Draw the key pictures first and look at them before the long video part.
  var motionDraft by remember { mutableStateOf(true) }
  var motionTemplate by remember { mutableStateOf("missionary") }
  val motionExtras = remember { MotionExtrasState() }
  var motionOptions by remember { mutableStateOf<Set<String>>(emptySet()) }
  // A heroine from the character library (id, name, outfit names) and the outfit she wears.
  var motionCharacters by remember { mutableStateOf<List<Triple<String, String, List<String>>>>(emptyList()) }
  // キャラアイコン画像（characterId → Bitmap）
  var motionCharacterIcons by remember { mutableStateOf<Map<String, android.graphics.Bitmap>>(emptyMap()) }
  var motionCharacterId by remember { mutableStateOf("") }
  var motionOutfit by remember { mutableStateOf("") }
  // ② 選択中キャラの声情報をUIに表示するための状態
  var motionCharaVoiceCaption by remember { mutableStateOf("") }
  var motionCharaVoiceId by remember { mutableStateOf("") }
  // ④ 多人数シーン対応（2〜4人目）
  var motionSceneCount by remember { mutableStateOf(1) }  // 何人でのシーン（1〜4）
  var motionCharacterId2 by remember { mutableStateOf("") }
  var motionOutfit2 by remember { mutableStateOf("") }
  var motionCharacterId3 by remember { mutableStateOf("") }
  var motionOutfit3 by remember { mutableStateOf("") }
  var motionCharacterId4 by remember { mutableStateOf("") }
  var motionOutfit4 by remember { mutableStateOf("") }
  val productionControls = remember { ProductionControlsState() }
  var videoEngine by remember { mutableStateOf("standard") }
  var imageModel by remember { mutableStateOf("wai") }
  var fastImage by remember { mutableStateOf(false) }
  var smoothVideo by remember { mutableStateOf(false) }
  // ⑤ FlashVSR 高画質化（モーションコミック用）
  var motionFlashVsr by remember { mutableStateOf(false) }
  // 前段シーン（着衣→脱衣→挡入）カスタムステップ（トップレベル state → RPC から参照可能）
  var customApproachSteps by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
  var videoSeconds by remember { mutableStateOf(5) }
  var sourceImageJobId by remember { mutableStateOf<String?>(null) }
  var sourceImageBase64 by remember { mutableStateOf<String?>(null) }
  var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
  // Library tab: "" = everything, or one kind; counts come from the PC for each tab label.
  var libraryKind by remember { mutableStateOf("") }
  var libraryCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
  var statusMessage by remember { mutableStateOf("") }
  var isSending by remember { mutableStateOf(false) }
  var deleteTarget by remember { mutableStateOf<MediaItem?>(null) }
  var works by remember { mutableStateOf<List<MangaWorkInfo>>(emptyList()) }
  var renameWorkTarget by remember { mutableStateOf<MangaWorkInfo?>(null) }
  var renameWorkText by remember { mutableStateOf("") }
  var moveTarget by remember { mutableStateOf<MediaItem?>(null) }
  var moveWorkId by remember { mutableStateOf("") }
  val bookInfo = remember { mutableStateMapOf<String, JSONObject>() }
  val castOpen = remember { mutableStateMapOf<String, Boolean>() }
  var revoiceTarget by remember { mutableStateOf<MediaItem?>(null) }
  var revoiceId by remember { mutableStateOf("") }
  var revoiceCaption by remember { mutableStateOf("") }
  // "完成まで約○分" for the settings on screen, queue included; refreshed as they change.
  var estimateText by remember { mutableStateOf("") }
  var showConfirm by remember { mutableStateOf(false) }
  var playingVideo by remember { mutableStateOf<String?>(null) }
  var playingAudio by remember { mutableStateOf<String?>(null) }
  val previews = remember { mutableStateMapOf<String, Bitmap>() }
  /** 複数ページの漫画の 2 ページ目以降（キーは「ジョブID#ページ番号」）と、開いているページ。 */
  val pagePreviews = remember { mutableStateMapOf<String, Bitmap>() }
  val viewPage = remember { mutableStateMapOf<String, Int>() }
  val files = remember { mutableStateMapOf<String, File>() }
  val player = remember { mutableStateOf<MediaPlayer?>(null) }

  LaunchedEffect(Unit) {
    if (motionCharacters.isEmpty()) {
      motionCharacters =
        runCatching {
          val arr = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ComicCharacterList, "{}")).optJSONArray("characters") ?: JSONArray()
          (0 until arr.length()).map { i ->
            val character = arr.getJSONObject(i)
            val outfits = character.optJSONArray("outfits") ?: JSONArray()
            Triple(
              character.optString("id"),
              character.optString("name"),
              (0 until outfits.length()).map { outfits.getJSONObject(it).optString("name") },
            )
          }
        }.getOrDefault(emptyList())
    }
  }

  DisposableEffect(Unit) {
    onDispose { player.value?.release() }
  }

  suspend fun loadList() {
    try {
      val params = JSONObject().put("limit", MEDIA_LIBRARY_PAGE)
      if (libraryKind.isNotEmpty()) params.put("kind", libraryKind)
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobList, params.toString()))
      items = parseMediaItems(res.optJSONArray("jobs"))
      res.optJSONObject("counts")?.let { c -> libraryCounts = listOf("image", "video", "music", "manga", "voice", "motion").associateWith { c.optInt(it) } }
      if (libraryKind == "manga" || libraryKind.isEmpty()) {
        runCatching { works = parseMangaWorks(JSONObject(viewModel.runGatewayRpc(GatewayMethod.MangaWorkList, "{}")).optJSONArray("works")) }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      statusMessage = "読み込みエラー: ${e.message}"
    }
  }

  suspend fun loadBookInfo(jobId: String) {
    runCatching {
      bookInfo[jobId] = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MangaBookGet, JSONObject().put("jobId", jobId).toString()))
    }.onFailure { statusMessage = "登場人物を読み込めませんでした: ${it.message}" }
  }

  fun registerCastMember(jobId: String, name: String) {
    coroutineScope.launch {
      runCatching {
        viewModel.runGatewayRpc(GatewayMethod.MangaBookRegister, JSONObject().put("jobId", jobId).put("name", name).toString())
        statusMessage = "「$name」をキャラ図鑑に登録しました。図鑑で立ち絵を作ったり学習すると、次の巻でさらに同じ顔に近づきます。"
        loadBookInfo(jobId)
      }.onFailure { statusMessage = "登録できませんでした: ${it.message}" }
    }
  }

  /** Downloads a finished result into the app cache once; previews images on the way. */
  suspend fun fetchFile(item: MediaItem): File? {
    files[item.jobId]?.let { return it }
    return try {
      val cached = File(context.cacheDir, "media-${item.jobId}.${extensionFor(item.mime)}")
      if (withContext(Dispatchers.IO) { cached.isFile && cached.length() > 0L }) {
        if (item.mime.startsWith("image/")) {
          withContext(Dispatchers.IO) { cached.readBytes() }.let { decodePreview(it) }?.let { previews[item.jobId] = it }
        }
        files[item.jobId] = cached
        return cached
      }
      val res =
        JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobResult, JSONObject().put("jobId", item.jobId).toString()))
      val mime = res.optString("mime", item.mime)
      val bytes = withContext(Dispatchers.Default) { Base64.decode(res.optString("base64"), Base64.DEFAULT) }
      val file = File(context.cacheDir, "media-${item.jobId}.${extensionFor(mime)}")
      withContext(Dispatchers.IO) {
        val pending = File(context.cacheDir, "media-${item.jobId}.part")
        pending.writeBytes(bytes)
        if (file.exists()) file.delete()
        if (!pending.renameTo(file)) {
          pending.delete()
          error("端末のキャッシュに保存できませんでした")
        }
      }
      if (mime.startsWith("image/")) decodePreview(bytes)?.let { previews[item.jobId] = it }
      files[item.jobId] = file
      file
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      statusMessage = "取得エラー: ${e.message}"
      null
    }
  }

  /** The settings chosen on screen; an estimate only says whether a source image is given. */
  fun generationParams(forEstimate: Boolean): JSONObject {
    val params = JSONObject().put("kind", kind)
    val sequelOf = if (kind == "manga" && mangaLayout == "book") mangaSequelOf else null
    if (!forEstimate) params.put("prompt", prompt.trim().ifBlank { if (sequelOf != null) "前の巻の続きをおまかせで" else "" })
    if (kind == "manga") {
      params.put("mangaLayout", mangaLayout).put("mangaTone", mangaTone)
      if (mangaLayout == "book") params.put("mangaPages", mangaPages)
      if (sequelOf != null) params.put("mangaSequelOf", sequelOf)
      if (mangaLayout == "book" && !mangaAutoCast) params.put("mangaAutoCast", false)
      if (mangaLayout == "book" && !mangaCover) params.put("mangaCover", false)
    }
    if (extraItems.isNotEmpty() && (kind == "image" || kind == "video" || kind == "manga" || kind == "motion")) params.put("extraItems", org.json.JSONArray(extraItems.toList()))
    if (kind == "voice") {
      params.put("irodoriModel", irodoriModel)
      if (dramaOn) params.put("voiceDrama", JSONObject().put("minutes", dramaMinutes).put("ambience", dramaAmbience))
      if (voiceId.isNotBlank()) params.put("voiceId", voiceId) else if (voiceCaption.isNotBlank()) params.put("voiceCaption", voiceCaption.trim())
    }
    if (kind == "motion") {
      params.put("motionMinutes", motionMinutes).put("motionFellatio", motionFellatio)
      if (motionDraft) params.put("motionDraft", true)
      motionExtras.writeTo(params)
      if (imageModel.isNotBlank()) params.put("motionImageModel", imageModel)
      params.put("motionTemplate", motionTemplate)
      if (motionOptions.isNotEmpty()) params.put("motionOptions", org.json.JSONArray(motionOptions.toList()))
      if (motionCharacterId.isNotBlank()) params.put("motionCharacterId", motionCharacterId)
      if (motionCharacterId.isNotBlank() && motionOutfit.isNotBlank()) params.put("motionOutfit", motionOutfit)
      if (voiceId.isNotBlank()) params.put("voiceId", voiceId) else if (voiceCaption.isNotBlank()) params.put("voiceCaption", voiceCaption.trim())
      // ⑤ FlashVSR 高画質化フラグ
      if (motionFlashVsr) params.put("flashVsr", true)
      // ④ 多人数シーン
      params.put("motionSceneCount", motionSceneCount)
      if (motionCharacterId2.isNotBlank()) params.put("motionCharacterId2", motionCharacterId2)
      if (motionCharacterId2.isNotBlank() && motionOutfit2.isNotBlank()) params.put("motionOutfit2", motionOutfit2)
      if (motionSceneCount >= 3 && motionCharacterId3.isNotBlank()) params.put("motionCharacterId3", motionCharacterId3)
      if (motionSceneCount >= 3 && motionCharacterId3.isNotBlank() && motionOutfit3.isNotBlank()) params.put("motionOutfit3", motionOutfit3)
      if (motionSceneCount >= 4 && motionCharacterId4.isNotBlank()) params.put("motionCharacterId4", motionCharacterId4)
      if (motionSceneCount >= 4 && motionCharacterId4.isNotBlank() && motionOutfit4.isNotBlank()) params.put("motionOutfit4", motionOutfit4)
      // ⑥ 前段シーン カスタムステップ（着衣→脱衣→挿入の追加ステップ）
      if (customApproachSteps.isNotEmpty()) {
        val stepsJson = org.json.JSONArray()
        customApproachSteps.forEach { (label, tags, fontStyle) ->
          stepsJson.put(org.json.JSONObject().put("label", label).put("tags", tags).put("fontStyle", fontStyle))
        }
        params.put("motionApproachSteps", stepsJson)
      }
    }
    if (kind != "music" && kind != "voice" && kind != "motion") {
      if (kind != "manga") params.put("aspect", aspect)
      params.put("imageModel", imageModel)
      if (fastImage && imageModel in FAST_IMAGE_MODELS) params.put("fast", true)
    }
    if (kind == "video") {
      params.put("engine", videoEngine)
      if (videoEngine !in SDCPP_VIDEO_ENGINES) params.put("durationSec", videoSeconds)
      if (smoothVideo) params.put("smooth", true)
      sourceImageJobId?.let { params.put("sourceImageJobId", it) }
      if (forEstimate) {
        if (sourceImageBase64 != null) params.put("hasSourceImage", true)
      } else {
        sourceImageBase64?.let { params.put("sourceImageBase64", it) }
      }
    }
    if (kind == "music") {
      params.put("instrumental", instrumental).put("durationSec", musicSeconds)
      if (!forEstimate && !instrumental && lyrics.isNotBlank()) params.put("lyrics", lyrics.trim())
    }
    productionControls.writeTo(params, kind, prompt)
    return params
  }

  fun generate() {
    if (isSending) return
    coroutineScope.launch {
      isSending = true
      try {
        val params = generationParams(forEstimate = false)
        val method =
          if (productionControls.branchParentJobId.isNotBlank()) {
            params.put("parentJobId", productionControls.branchParentJobId)
            params.put("branchName", productionControls.branchName)
            GatewayMethod.MediaBranch
          } else {
            GatewayMethod.MediaGenerate
          }
        viewModel.runGatewayRpc(method, params.toString())
        val what =
          when (kind) {
            "image" -> "画像"
            "video" -> "動画"
            "manga" -> "漫画"
            "voice" -> "声"
            "motion" -> "モーションコミック"
            else -> "曲"
          }
        statusMessage =
          "${what}を作り始めました。$estimateText\n作っている間、チャットは一時的に止まります。"
        productionControls.clearSourceActions()
        loadList()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        statusMessage = "開始できませんでした: ${e.message}"
      } finally {
        isSending = false
      }
    }
  }

  fun toggleAudio(item: MediaItem) {
    coroutineScope.launch {
      if (playingAudio == item.jobId) {
        player.value?.release()
        player.value = null
        playingAudio = null
        return@launch
      }
      val file = fetchFile(item) ?: return@launch
      player.value?.release()
      player.value =
        MediaPlayer().apply {
          setDataSource(file.absolutePath)
          setOnCompletionListener { playingAudio = null }
          prepare()
          start()
        }
      playingAudio = item.jobId
    }
  }

  /** 複数ページの漫画の 1 ページ分（0 始まり）をゲートウェイから取る。 */
  suspend fun fetchPage(item: MediaItem, page: Int): ByteArray? =
    try {
      val res =
        JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobResult, JSONObject().put("jobId", item.jobId).put("part", page).toString(), timeoutMs = 60_000))
      withContext(Dispatchers.Default) { Base64.decode(res.optString("base64"), Base64.DEFAULT) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      statusMessage = "取得エラー: ${e.message}"
      null
    }

  suspend fun loadPagePreview(item: MediaItem, page: Int) {
    val key = "${item.jobId}#$page"
    if (pagePreviews.containsKey(key)) return
    val bytes = fetchPage(item, page) ?: return
    decodePreview(bytes)?.let { pagePreviews[key] = it }
  }

  fun savePages(item: MediaItem) {
    coroutineScope.launch {
      var saved = 0
      for (page in 0 until item.parts) {
        val bytes = fetchPage(item, page) ?: break
        val file = File(context.cacheDir, "media-${item.jobId}-p${page + 1}.png")
        val ok =
          withContext(Dispatchers.IO) {
            file.writeBytes(bytes)
            runCatching { saveToDevice(context, file, "image/png", item.owner) }.getOrDefault(false)
          }
        if (!ok) break
        saved += 1
      }
      statusMessage = if (saved == item.parts) "全${item.parts}ページを端末の「OpenClaw」フォルダに保存しました。" else "保存できませんでした（${saved}/${item.parts}ページ）。"
    }
  }

  fun save(item: MediaItem) {
    coroutineScope.launch {
      val file = fetchFile(item) ?: return@launch
      val ok = withContext(Dispatchers.IO) { runCatching { saveToDevice(context, file, item.mime, item.owner) }.getOrDefault(false) }
      statusMessage = if (ok) "端末の「OpenClaw」フォルダに保存しました。" else "保存できませんでした。"
    }
  }

  fun delete(item: MediaItem) {
    coroutineScope.launch {
      try {
        viewModel.runGatewayRpc(GatewayMethod.MediaJobDelete, JSONObject().put("jobId", item.jobId).toString())
        previews.remove(item.jobId)
        files.remove(item.jobId)?.delete()
        loadList()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        statusMessage = "削除エラー: ${e.message}"
      }
    }
  }

  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      if (uri == null) return@rememberLauncherForActivityResult
      coroutineScope.launch {
        val dataUri = withContext(Dispatchers.IO) { convertUriToBase64DataUrl(context, uri, MEDIA_UPLOAD_MAX_PX) }
        if (dataUri == null) {
          statusMessage = "この画像は読み込めませんでした。"
        } else {
          sourceImageBase64 = dataUri.substringAfter(",")
          sourceImageJobId = null
          statusMessage = "選んだ画像を動かします。動きの指示を書いて「作る」を押してください。"
        }
      }
    }

  LaunchedEffect(libraryKind) { loadList() }
  val hasActive = items.any { it.status == "queued" || it.status == "running" }
  val activeCount = items.count { it.status == "queued" || it.status == "running" }
  LaunchedEffect(
    kind, imageModel, fastImage, aspect, videoEngine, videoSeconds, smoothVideo,
    musicSeconds, instrumental, mangaLayout, mangaPages, irodoriModel, motionMinutes, motionFellatio, sourceImageJobId, sourceImageBase64 != null, activeCount,
    productionControls.gpuMode, productionControls.qualityMode, productionControls.repairSourceJobId, productionControls.branchParentJobId,
  ) {
    delay(400)
    estimateText =
      try {
        val res =
          JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaEstimate, generationParams(forEstimate = true).toString()))
        formatEstimate(res)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        ""
      }
  }
  LaunchedEffect(hasActive) {
    while (hasActive) {
      delay(MEDIA_POLL_MS)
      loadList()
    }
  }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(text = "🎨 生成スタジオ", style = ClawTheme.type.title, color = ClawTheme.colors.text)
              Text(
                text = "画像・動画・音楽をこのPCで作ります（制限解除モデル）",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
            }
          }
          ClawIconButton(icon = Icons.Default.Refresh, contentDescription = "更新", onClick = { coroutineScope.launch { loadList() } })
        }
      }

      item {
        ClawSecondaryButton(
          text = "📚 連載コミック・キャラ図鑑を開く",
          onClick = onOpenComics,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      item {
        ClawSecondaryButton(
          text = "🖼️ モデル別サンプルを比べる（WAI / NoobAI / Lustify …）",
          onClick = onOpenModelSamples,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      item {
        ClawSecondaryButton(
          text = "🕹️ 体位サンプルを見る（正常位 / 騎乗位 / 後背位 …）",
          onClick = onOpenPoseSamples,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      item {
        ClawPanel(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              listOf("image" to "🎨 画像", "video" to "🎬 動画", "music" to "🎵 音楽").forEach { (id, label) ->
                ChoiceChip(label = label, selected = kind == id, onClick = { kind = id }, modifier = Modifier.weight(1f))
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              listOf("manga" to "📖 漫画", "voice" to "🎤 声", "motion" to "🎞 モーション").forEach { (id, label) ->
                ChoiceChip(label = label, selected = kind == id, onClick = { kind = id }, modifier = Modifier.weight(1f))
              }
            }

            ClawTextField(
              value = prompt,
              onValueChange = { prompt = it },
              placeholder =
                when (kind) {
                  "image" -> "例: 銀髪で猫耳のメイドがカフェでコーヒーを運んでいる、笑顔"
                  "video" -> "例: 髪が風になびいて、こちらに微笑みかける（空欄なら自然に動かします）"
                  "manga" -> "例: 寝坊したルナ（黒髪ボブの女の子）が全力で駅まで走るけど、今日は日曜日だった"
                  "motion" -> "例: クロエ、25歳、身長168cm、黒髪ロングの姫カット、赤い目、ジト目、巨乳、ゴスロリ。ダウナーで淡々と話す恋人。夜のゴシックな寝室で正常位、最後は中出し"
                  "voice" -> "1行に1セリフ。下の絵文字で演技を入れられます。\n例: ん…😮‍💨 そこ、だめぇ…🥵\n例: 👂ねぇ…大好きだよ。🫶"
                  else -> "例: 明るいJ-POP、女性ボーカル、夏の海をドライブする気分"
                },
              minLines = 3,
              label =
                when (kind) {
                  "image" -> "どんな絵？（日本語でOK）"
                  "video" -> "どんな動き？（日本語でOK）"
                  "manga" -> "どんなお話？（登場人物・オチなど。AIが脚本を書きます）"
                  "voice" -> "読ませるセリフ（1行ずつ・1行30秒まで）"
                  "motion" -> "キャラと場面（名前・年齢・体型・髪・目・衣装・性格、場所、体位、終わり方）"
                  else -> "どんな曲？（日本語でOK）"
                },
              modifier = Modifier.fillMaxWidth(),
            )

            ProductionControlsPanel(productionControls, kind, motionCharacters)

            if (kind == "motion") {
              Text(text = "長さ", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(1 to "約1分", 3 to "約3分", 5 to "約5分").forEach { (minutes, label) ->
                  ChoiceChip(label = label, selected = motionMinutes == minutes, onClick = { motionMinutes = minutes }, modifier = Modifier.weight(1f))
                }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceChip(label = "最後にフェラあり", selected = motionFellatio, onClick = { motionFellatio = true }, modifier = Modifier.weight(1f))
                ChoiceChip(label = "フェラなし", selected = !motionFellatio, onClick = { motionFellatio = false }, modifier = Modifier.weight(1f))
              }
              val finishedFilms = items.filter { it.status == "done" && it.mime.startsWith("video/") }.map { FinishedFilm(it.jobId, it.request.ifBlank { "（指示なし）" }, it.kind == "motion") }
              MotionExtrasPanel(motionExtras, finishedFilms)
              MotionJoinPanel(viewModel, finishedFilms) { coroutineScope.launch { loadList() } }
              MotionBranchPanel(viewModel, finishedFilms) { coroutineScope.launch { loadList() } }
              // ── 前段シーン（着衣→脱衣→本番）カスタムステップ ──
              var showApproachEditor by remember { mutableStateOf(false) }
              // 前段シーンはトップレベル state変数を使用（customApproachSteps）
              val defaultApproachLabels = listOf("① 着衣", "② 脱がす/ずらす/破く", "③ 半裸", "④ 前戯", "⑤ 本番(挿入)")
              ClawSecondaryButton(
                text = if (showApproachEditor) "▲ 前段シーン設定を閉じる" else "▼ 前段シーン設定（${defaultApproachLabels.size + customApproachSteps.size}ステップ）",
                onClick = { showApproachEditor = !showApproachEditor },
                modifier = Modifier.fillMaxWidth(),
              )
              if (showApproachEditor) {
                Column(
                  modifier = Modifier.fillMaxWidth()
                    .background(ClawTheme.colors.canvas, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text("標準ステップ（固定）:", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                  defaultApproachLabels.forEach { label ->
                    Text(
                      text = "  🔒 $label",
                      style = ClawTheme.type.captionSmall,
                      color = ClawTheme.colors.text,
                      modifier = Modifier.fillMaxWidth().background(ClawTheme.colors.surface, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                  }
                  if (customApproachSteps.isNotEmpty()) {
                    Text("追加ステップ:", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                    customApproachSteps.forEachIndexed { i, (label, tags, fontStyle) ->
                      Row(
                        modifier = Modifier.fillMaxWidth().background(ClawTheme.colors.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        Column(modifier = Modifier.weight(1f)) {
                          Text("⑥+ $label", style = ClawTheme.type.captionSmall.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.primary)
                          if (tags.isNotBlank()) Text(tags.take(60), style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
                        }
                        ClawSecondaryButton(text = "×", onClick = { customApproachSteps = customApproachSteps.toMutableList().also { it.removeAt(i) } })
                      }
                    }
                  }
                  var newStepLabel by remember { mutableStateOf("") }
                  var newStepTags by remember { mutableStateOf("") }
                  var newStepFont by remember { mutableStateOf("whisper") }
                  Text("＋ ステップを追加:", style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.text)
                  ClawTextField(value = newStepLabel, onValueChange = { newStepLabel = it }, placeholder = "例: 縛り・ローター責め・騎乗位フィニッシュ", label = "ステップ名", modifier = Modifier.fillMaxWidth())
                  ClawTextField(value = newStepTags, onValueChange = { newStepTags = it }, placeholder = "例: bondage, tied wrists, vibrator", label = "英語タグ（省略可）", modifier = Modifier.fillMaxWidth())
                  Text("フォントスタイル:", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("normal", "whisper", "shout", "sfx").forEach { style ->
                      ChoiceChip(label = style, selected = newStepFont == style, onClick = { newStepFont = style }, modifier = Modifier.weight(1f))
                    }
                  }
                  ClawPrimaryButton(text = "＋ 追加", enabled = newStepLabel.isNotBlank(), onClick = { customApproachSteps = customApproachSteps + Triple(newStepLabel, newStepTags, newStepFont); newStepLabel = ""; newStepTags = "" }, modifier = Modifier.fillMaxWidth())
                }
              }
              Text(text = "体位・場面", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
              // ④ 体位・場面フィルター
              var templateSearch by remember { mutableStateOf("") }
              ClawTextField(
                value = templateSearch,
                onValueChange = { templateSearch = it },
                placeholder = "🔍 体位・場面を検索",
                label = "検索",
                modifier = Modifier.fillMaxWidth(),
              )
              val allPoseTemplates = listOf(
                "missionary" to "正常位", "cowgirl" to "騎乗位", "reverse_cowgirl" to "背面騎乗位",
                "doggystyle" to "後背位", "prone" to "寝バック", "standing" to "立ちバック",
                "mating_press" to "種付けプレス", "spooning" to "側位", "lotus" to "対面座位",
                "bath" to "お風呂", "paizuri" to "パイズリ", "suspended_congress" to "駅弁",
                "full_nelson" to "フルネルソン", "piledriver" to "まんぐり返し", "amazon" to "アマゾン",
                "bent_over_desk" to "机で立ちバック", "knees_to_chest" to "屈曲位", "matsubakuzushi" to "松葉崩し",
                "squatting_cowgirl" to "しゃがみ騎乗位", "standing_missionary" to "立ち鼎", "desk_missionary" to "机の上で正常位",
                "sixtynine" to "シックスナイン", "anal" to "アナル", "pegging" to "逆アナル",
                "tribadism" to "擦り合わせ", "fisting" to "フィスト", "footjob" to "足コキ",
                "thighjob" to "太ももコキ", "handjob" to "手コキ", "blowjob" to "フェラチオ",
                "deepthroat" to "ディープスロート", "facesitting" to "顔面騎乗",
                // 世界の体位・場所別（追加）
                // 世界の体位・場所別（追加）
                "h6p_m_legs_lying" to "M字開脚（寝て）", "h6p_m_legs_sitting" to "M字開脚（座って）", "h6p_m_legs_wall" to "M字開脚（壁にもたれて）",
                "h6p_m_legs_bed" to "M字開脚（ベッドの上）", "h6p_m_legs_hands_knees" to "M字開脚（自分で膝を持つ）", "h6p_m_legs_hands_thigh" to "M字開脚（太ももを持つ）",
                "h6p_m_legs_chair" to "M字開脚（椅子に座って）", "h6p_m_legs_sofa" to "M字開脚（ソファの上）", "h6p_m_legs_mirror" to "M字開脚（鏡の前）",
                "h6p_m_legs_panties_aside" to "M字開脚（下着をずらす）", "h6p_m_legs_tiptoe" to "M字開脚（つま先立ち・しゃがむ）", "h6p_m_legs_bridge" to "M字開脚（腰を浮かせる）",
                "h6p_kupaa_two_fingers" to "くぱぁ（片手の人差し指と中指で広げる）", "h6p_kupaa_both_index" to "くぱぁ（両手の人差し指で広げる）", "h6p_kupaa_both_hands" to "くぱぁ（両手で大きく広げる）",
                "h6p_kupaa_sitting" to "くぱぁ（座って片手で広げる）", "h6p_kupaa_standing_aside" to "くぱぁ（立って下着をずらして広げる）", "h6p_kupaa_from_behind" to "くぱぁ（四つん這いで後ろから）",
                "h6p_kupaa_bed_blush" to "くぱぁ（顔を赤くしながら）", "h6p_kupaa_wet" to "くぱぁ（濡れているところを見せる）", "h6p_hogtie_floor" to "手足をまとめて縛る（床・同意の上）",
                "h6p_hogtie_bed" to "手足をまとめて縛る（ベッドの上）", "h6p_hogtie_kneeling" to "手足をまとめて縛る（ひざ立ち）", "h6p_frogtie" to "かえる縛り（足を折りたたんで縛る）",
                "h6p_box_tie" to "後ろ手に縛る（箱縛り）", "h6p_car_back_seat" to "車の後部座席で", "h6p_car_hood" to "車のボンネットに手をついて",
                "h6p_car_driver_seat" to "運転席にまたがって", "h6p_car_open_door" to "ドアを開けた車のそばで", "h6p_outdoor_tree" to "野外・木に手をついて",
                "h6p_outdoor_grass" to "野外・草の上で", "h6p_outdoor_rain" to "雨の中で", "h6p_outdoor_bench" to "公園のベンチで（夜）",
                "h6p_outdoor_beach" to "砂浜の岩かげで", "h6p_closet_standing" to "押入れの中で（立って）", "h6p_closet_lying" to "押入れの中で（重なって）",
                "h6p_closet_hiding" to "押入れで息をひそめて",
                // 世界の体位・場所別（追加）
                // 世界の体位・場所別（追加）
                "h6p_m_legs_lying" to "M字開脚（寝て）", "h6p_m_legs_sitting" to "M字開脚（座って）", "h6p_m_legs_wall" to "M字開脚（壁にもたれて）",
                "h6p_m_legs_bed" to "M字開脚（ベッドの上）", "h6p_m_legs_hands_knees" to "M字開脚（自分で膝を持つ）", "h6p_m_legs_hands_thigh" to "M字開脚（太ももを持つ）",
                "h6p_m_legs_chair" to "M字開脚（椅子に座って）", "h6p_m_legs_sofa" to "M字開脚（ソファの上）", "h6p_m_legs_mirror" to "M字開脚（鏡の前）",
                "h6p_m_legs_panties_aside" to "M字開脚（下着をずらす）", "h6p_m_legs_tiptoe" to "M字開脚（つま先立ち・しゃがむ）", "h6p_m_legs_bridge" to "M字開脚（腰を浮かせる）",
                "h6p_kupaa_two_fingers" to "くぱぁ（片手の人差し指と中指で広げる）", "h6p_kupaa_both_index" to "くぱぁ（両手の人差し指で広げる）", "h6p_kupaa_both_hands" to "くぱぁ（両手で大きく広げる）",
                "h6p_kupaa_sitting" to "くぱぁ（座って片手で広げる）", "h6p_kupaa_standing_aside" to "くぱぁ（立って下着をずらして広げる）", "h6p_kupaa_from_behind" to "くぱぁ（四つん這いで後ろから）",
                "h6p_kupaa_bed_blush" to "くぱぁ（顔を赤くしながら）", "h6p_kupaa_wet" to "くぱぁ（濡れているところを見せる）", "h6p_hogtie_floor" to "手足をまとめて縛る（床・同意の上）",
                "h6p_hogtie_bed" to "手足をまとめて縛る（ベッドの上）", "h6p_hogtie_kneeling" to "手足をまとめて縛る（ひざ立ち）", "h6p_frogtie" to "かえる縛り（足を折りたたんで縛る）",
                "h6p_box_tie" to "後ろ手に縛る（箱縛り）", "h6p_car_back_seat" to "車の後部座席で", "h6p_car_hood" to "車のボンネットに手をついて",
                "h6p_car_driver_seat" to "運転席にまたがって", "h6p_car_open_door" to "ドアを開けた車のそばで", "h6p_outdoor_tree" to "野外・木に手をついて",
                "h6p_outdoor_grass" to "野外・草の上で", "h6p_outdoor_rain" to "雨の中で", "h6p_outdoor_bench" to "公園のベンチで（夜）",
                "h6p_outdoor_beach" to "砂浜の岩かげで", "h6p_closet_standing" to "押入れの中で（立って）", "h6p_closet_lying" to "押入れの中で（重なって）",
                "h6p_closet_hiding" to "押入れで息をひそめて",
                "h5p_001" to "燕返し", "h5p_002" to "鴨の入首", "h5p_003" to "帆かけ茶臼",
                "h5p_004" to "窓の月", "h5p_005" to "本駒掛け", "h5p_006" to "千鳥",
                "h5p_007" to "仏壇返し", "h5p_008" to "浮橋", "h5p_009" to "御所車",
                "h5p_010" to "立ち松葉", "h5p_011" to "鵯越え", "h5p_012" to "抱き上げ",
                "h5p_013" to "撞木反り", "h5p_014" to "筏茶臼", "h5p_015" to "手懸け",
                "h5p_016" to "提灯ぶら下がり", "h5p_017" to "菊一文字", "h5p_018" to "二つ巴",
                "h5p_019" to "絞り芙蓉", "h5p_020" to "後櫓", "h5p_021" to "碁盤攻め",
                "h5p_022" to "錦の帯", "h5p_023" to "洞入り", "h5p_024" to "唐草居茶臼",
                "h5p_025" to "窓越しの立ち", "h5p_026" to "とんぼ返り", "h5p_027" to "つる首",
                "h5p_028" to "菊座重ね", "h5p_029" to "座り鼎", "h5p_030" to "櫓立ち前抱え",
                "h5p_031" to "壁際の片脚上げ立位", "h5p_032" to "カーマの蓮華座", "h5p_033" to "蓮の花",
                "h5p_034" to "ヨーニの開き", "h5p_035" to "かさね枕の位", "h5p_036" to "蛇の抱擁",
                "h5p_037" to "つる草の抱擁", "h5p_038" to "木登りの抱擁", "h5p_039" to "胡麻と米の抱擁",
                "h5p_040" to "牛乳と水の抱擁", "h5p_041" to "ブランコ座位", "h5p_042" to "ハンモック位",
                "h5p_043" to "ベッドの端から", "h5p_044" to "ベッドの端で膝立ち", "h5p_045" to "ソファの背もたれ越し",
                "h5p_046" to "ソファの肘掛け位", "h5p_047" to "窓辺の対面立位", "h5p_048" to "鏡の前の立位",
                "h5p_049" to "鏡の前の騎乗", "h5p_050" to "椅子で後ろ向き座位", "h5p_051" to "椅子の背抱き",
                "h5p_052" to "階段の途中で", "h5p_053" to "バルコニーの手すり立位", "h5p_054" to "シャワー壁立位",
                "h5p_055" to "湯船の縁に座って", "h5p_056" to "湯船の対面座位", "h5p_057" to "湯船の背面座位",
                "h5p_058" to "洗い場の四つん這い", "h5p_059" to "プールサイドの縁", "h5p_060" to "プールの中の抱え上げ",
                "h5p_061" to "波打ち際の騎乗", "h5p_062" to "砂浜の側位", "h5p_063" to "ハンモックのスプーン",
                "h5p_064" to "テントの中の対面座位", "h5p_065" to "寝袋の中のスプーン", "h5p_066" to "車の後部座席の騎乗",
                "h5p_067" to "ボンネットに手をつく立位", "h5p_068" to "運転席での座位", "h5p_069" to "テーブルの端に座る対面",
                "h5p_070" to "キッチンカウンター座位", "h5p_071" to "洗面台の上で", "h5p_072" to "冷蔵庫に背中をつけて",
                "h5p_073" to "本棚の前の立位", "h5p_074" to "ピアノの上で", "h5p_075" to "舞台袖の壁際",
                "h5p_076" to "楽屋の鏡台前", "h5p_077" to "撮影スタジオの四つん這い", "h5p_078" to "サウナベンチの上段",
                "h5p_079" to "マッサージベッドでうつ伏せ", "h5p_080" to "温泉の岩に寄りかかって", "h5p_081" to "畳の上の膝抱え位",
                "h5p_082" to "布団の中のスプーン", "h5p_083" to "布団の上の対面座位", "h5p_084" to "縁側の四つん這い",
                "h5p_085" to "障子の影の側位", "h5p_086" to "天蓋ベッドの騎乗", "h5p_087" to "ベッドの足元で膝立ち対面",
                "h5p_088" to "肩に乗せた騎乗", "h5p_089" to "腰を浮かせた座位", "h5p_090" to "ひざ抱え位",
                "h5p_091" to "足首を揃えた横倒し位", "h5p_092" to "足首を肩に乗せる両手つなぎ位", "h5p_093" to "片脚を抱える位",
                "h5p_094" to "枕を腰に敷く位", "h5p_095" to "重なり位", "h5p_096" to "マットレスの縁の四つん這い",
                "h5p_097" to "両腕を後ろに引く位", "h5p_098" to "上体を起こして背中を預ける位", "h5p_099" to "頬を寄せる低い四つん這い",
                "h5p_100" to "前かがみ騎乗", "h5p_101" to "指を絡める騎乗", "h5p_102" to "のけぞり騎乗",
                "h5p_103" to "反り騎乗", "h5p_104" to "横向き騎乗", "h5p_105" to "膝立ち対面座位",
                "h5p_106" to "ダンスのような立位", "h5p_107" to "壁際の抱きしめ立位", "h5p_108" to "抱き合うスプーン",
                "h5p_109" to "脚を後ろにかけるスプーン", "h5p_110" to "長座位の抱擁", "h5p_111" to "あぐら座位",
                "h5p_112" to "鏡に向かう後ろ抱え座位", "h5p_113" to "胸の上の背面位", "h5p_114" to "膝の間に座る背面位",
                "h5p_115" to "舟漕ぎ位", "h5p_116" to "ヨガ風バランス座位", "h5p_117" to "肩にもたれる立位",
                "h5p_118" to "首にぶら下がる立位", "h5p_119" to "窓ガラスに手をつく立位",
                "g4p_lotus" to "蓮華座（あぐら対面）", "g4p_spoon" to "スプーン（添い寝後ろから）", "g4p_cross_legs_front" to "交差位（脚を交差）",
                "g4p_standing_front_lift" to "立位・片脚持ち上げ", "g4p_standing_wall_front" to "壁際の立位（向かい合い）", "g4p_wall_back_press" to "壁に押し付け後背立位",
                "g4p_kitchen_counter" to "キッチンカウンターで対面", "g4p_bed_edge_missionary" to "ベッドの縁で正常位", "g4p_legs_on_shoulders" to "肩掛け正常位",
                "g4p_pretzel" to "プレッツェル（交差横位）", "g4p_scissors" to "シザース（貝合わせ）", "g4p_butterfly" to "バタフライ（机の端で）",
                "g4p_crab" to "クラブ（後ろ手座位）", "g4p_reverse_lotus" to "背面座位（膝の上）", "g4p_reverse_lotus_mirror" to "鏡の前の背面座位",
                "g4p_chair_straddle" to "椅子での対面座位", "g4p_chair_reverse" to "椅子での背面座位", "g4p_sofa_arm_doggy" to "ソファの肘掛けにもたれる後背位",
                "g4p_sofa_cowgirl" to "ソファで騎乗位", "g4p_sofa_leg_up" to "ソファで片脚を背もたれに", "g4p_stair_doggy" to "階段の途中で後背位",
                "g4p_stair_sitting" to "階段に座って対面", "g4p_car_back_seat" to "車の後部座席で対面座位", "g4p_car_front_seat_reverse" to "助手席で背面座位",
                "g4p_pool_edge" to "プールの縁にもたれて", "g4p_pool_float" to "浮き輪につかまって後ろから", "g4p_bath_cowgirl" to "湯船の中で騎乗位",
                "g4p_bath_edge_sit" to "浴槽の縁に座って対面", "g4p_shower_wall" to "シャワー室の壁に手をついて", "g4p_shower_lift" to "シャワーの下で抱え上げ",
                "g4p_onsen_rock" to "露天風呂の岩にもたれて", "g4p_table_edge_front" to "テーブルの端に座って対面", "g4p_desk_bend_over" to "デスクに上半身を伏せて",
                "g4p_desk_chair_lap" to "オフィスチェアの膝上", "g4p_bed_prone_bone" to "うつ伏せの寝バック（脚を閉じて）", "g4p_prone_arch" to "腰を反らせる伏せ位",
                "g4p_pillow_hips" to "腰枕の正常位", "g4p_legs_up_v" to "V字開脚正常位", "g4p_ankle_hold" to "足首を持って開く正常位",
                "g4p_hug_missionary" to "抱きしめ正常位", "g4p_kiss_missionary" to "キスしながらの正常位", "g4p_hands_clasped_missionary" to "両手を握り合う正常位",
                "g4p_face_sit_cunni" to "顔面騎乗（口での愛撫）", "g4p_cunnilingus_lying" to "寝て脚を開き口で愛撫される", "g4p_cunnilingus_standing" to "立って片脚を肩に乗せられる愛撫",
                "g4p_cunnilingus_sitting" to "椅子に座って脚を開く愛撫", "g4p_fellatio_kneeling" to "ひざまずく口奉仕", "g4p_fellatio_sitting_edge" to "ベッドの縁に座らせて口奉仕",
                "g4p_fellatio_lying" to "寝かせて覆いかぶさる口奉仕", "g4p_fellatio_shower" to "シャワーでの口奉仕", "g4p_deep_throat_edge" to "ベッドの縁で頭を垂らす口奉仕",
                "g4p_handjob_behind" to "後ろから抱きついて手コキ", "g4p_handjob_lap" to "膝に座って手で愛撫", "g4p_fingering_back_hug" to "後ろから抱いて指で愛撫",
                "g4p_fingering_lying" to "寝かせて指で愛撫", "g4p_fingering_kiss" to "キスしながら指で愛撫", "g4p_titfuck_kneel" to "ひざまずいてパイズリ（谷間に挟む）",
                "g4p_titfuck_lying" to "仰向けの男にまたがるパイズリ", "g4p_thigh_job" to "太もも挟み（素股）", "g4p_thigh_job_front" to "向かい合って素股",
                "g4p_footjob_sitting" to "足で責める（座位）", "g4p_footjob_lying" to "寝て足で愛撫", "g4p_buttjob" to "尻で擦る（お尻コキ）",
                "g4p_armpit_hold" to "腋に挟む", "g4p_mutual_oral_side" to "横向きシックスナイン", "g4p_mutual_oral_standing" to "立ちシックスナイン（逆さ抱え）",
                "g4p_yab_yum" to "ヤブユム（神聖な抱擁）", "g4p_indian_cross" to "インディアン・クロス", "g4p_twining" to "ツイスト位（絡み合い）",
                "g4p_splitting_bamboo" to "竹割り（片脚を上げる）", "g4p_supported_kneel" to "持ち上げ座位（ひざ立ち）", "g4p_fan_position" to "扇の位（脚を広げて後ろから）",
                "g4p_elephant" to "エレファント（低い後背位）", "g4p_cat_position" to "キャット（密着して擦る正常位）", "g4p_bridge" to "ブリッジ位",
                "g4p_wheelbarrow" to "手押し車（四つん這いで脚を持つ）", "g4p_standing_arms_back" to "立って両腕を引かれる後背位", "g4p_bent_forward_wall" to "壁に額をつけた後背立位",
                "g4p_tied_knot" to "結び目（座位の絡み）", "g4p_rocking_chair" to "揺り椅子の座位", "g4p_hammock" to "ハンモックの上で",
                "g4p_balcony_rail" to "バルコニーの手すりに手をついて", "g4p_rooftop_wall" to "屋上の壁際で", "g4p_beach_lying" to "砂浜で寝て正常位",
                "g4p_beach_cowgirl" to "砂浜の騎乗位", "g4p_boat_deck" to "ヨットのデッキで後背位", "g4p_tent_sleeping_bag" to "テントの寝袋の中で",
                "g4p_elevator_wall" to "エレベーターの壁で抱え上げ", "g4p_changing_room_stand" to "フィッティングルームの立位", "g4p_car_hood" to "ボンネットに手をついて",
                "g4p_piano_top" to "ピアノの上で正常位", "g4p_dining_table_lying" to "ダイニングテーブルに寝て", "g4p_kitchen_behind" to "キッチンで後ろから抱いて",
                "g4p_laundry_machine" to "洗濯機の上で", "g4p_window_press" to "窓ガラスに押し付けて後背位", "g4p_mirror_standing_back" to "鏡に手をついた立ちバック",
                "g4p_mirror_cowgirl" to "鏡の前の騎乗位", "g4p_slow_grind_cowgirl" to "ゆっくり腰を回す騎乗位", "g4p_lean_forward_cowgirl" to "前に倒れて抱きつく騎乗位",
                "g4p_squat_reverse" to "しゃがむ背面騎乗位（足裏をつく）", "g4p_reverse_cowgirl_lean" to "背面騎乗位で仰け反る", "g4p_kneeling_lap" to "ひざ立ちで向かい合う",
                "g4p_sideways_straddle" to "横向きにまたがる座位", "g4p_solo_pillow" to "枕を抱えての一人遊び", "g4p_solo_mirror" to "鏡を見ながら一人遊び",
                "g4p_solo_bath" to "お風呂での一人遊び", "g4p_solo_bed_toy" to "ベッドで道具を使う一人遊び", "g4p_solo_phone" to "スマホを見ながら一人遊び",
                "g4p_solo_kneel_pillow" to "ひざ立ちで枕にまたがる", "g4p_solo_wall" to "壁にもたれて立ったまま", "g4p_solo_chair_edge" to "椅子の角に擦りつける",
                "g4p_self_breast_lying" to "仰向けで胸に触れる", "g4p_tease_stripping" to "ゆっくり脱いで見せる", "g4p_lap_dance" to "ラップダンス（膝の上で踊る）",
                "g4p_cuddle_after" to "事後の添い寝", "g4p_morning_wake" to "朝の寝起き抱きつき", "g4p_knee_pillow_kiss" to "膝枕でキス",
                // 四十八手や、よくある変わった体位・特殊プレイ（追加分）
                "prone_bone" to "寝バック（うつ伏せ）", "backward_sitting" to "背面座位", "wheelbarrow" to "手押し車",
                "scissors" to "松葉（貝合わせ）", "pretzel" to "プレッツェル", "naruto" to "鳴門（側位）",
                "chidori" to "千鳥（側位・背後）", "cowgirl_leaning" to "前傾騎乗位", "bridge_missionary" to "ブリッジ正常位",
                "standing_carry_wall" to "壁立ち抱きかかえ", "car_sex" to "車の中", "shower_sex" to "シャワー",
                "mirror_sex" to "鏡の前", "kitchen_counter" to "キッチンカウンター", "bondage_missionary" to "拘束正常位",
                "threesome_mmf" to "3P（男2・女1）", "threesome_ffm" to "3P（女2・男1）", "cunnilingus" to "クンニ",
                "titfuck_lying" to "寝パイズリ", "double_blowjob" to "ダブルフェラ",
                // 体位の一覧記事・海外の呼び名より（追加2）
                "extended_missionary" to "伸長位（しがらみ）", "daishuki_hold" to "だいしゅきホールド", "cat_position" to "CAT体位", "stake_driver" to "杭打ち騎乗位", "spider_cowgirl" to "スパイダー騎乗位", "lying_cowgirl" to "密着騎乗位", "rolls_royce" to "ロールスロイス体位", "honkoma" to "本駒駆け", "wine_opener" to "ワインオープナー", "face_off" to "フェイスオフ", "pretzel_dip" to "レッツェル・ディップ", "straight_iron" to "ストレートアイロン", "g_spot_master" to "Gスポットの達人", "cowgirl_helper" to "騎乗位ヘルパー", "leapfrog" to "馬跳び", "ballet_dancer" to "バレエダンサー", "cross_booty" to "クロス・ブーティ", "caboose" to "カブース", "reverse_spoon" to "リバース・スプーン", "golden_arch" to "ゴールデン・アーチ", "clam" to "貝殻", "blender" to "かくはん機", "pinball_wizard" to "ピンボール名人", "split_spoon" to "先割れスプーン", "seated_wheelbarrow" to "座位での手押し車", "tabletop" to "テーブルトップ", "champagne_room" to "シャンパンルーム", "om_position" to "オーム", "good_x" to "グッド・エックス", "lazy_man" to "怠け者", "snow_angel" to "雪の天使", "primitive_lotus" to "未開の蓮", "snake" to "ヘビ", "bow_cowgirl" to "反り騎乗位", "panther" to "女豹のポーズ", "all_cunnilingus" to "全員クンニ（3P）", "all_fellatio" to "全員フェラ（3P）", "cowgirl_caress" to "騎乗位愛撫（3P）", "back_caress" to "バック愛撫（3P）", "missionary_caress" to "正常位愛撫（3P）", "double_cowgirl" to "W騎乗位（騎乗位＋顔面騎乗）", "double_paizuri" to "Wパイズリ", "double_fingering" to "W手マン", "nipple_lick_handjob" to "W乳首舐め手コキ", "insertion_fellatio" to "挿入とフェラの2点責め", "double_handjob" to "W手コキ", "handjob_fellatio" to "手コキフェラ", "breast_play_cunni" to "おっぱい責めクンニ", "edo_ichifu_nijo" to "一夫二女（江戸の3P体位）", "edo_kagetsugi" to "花月擬（江戸の3P体位）", "fisting_pose" to "フィスト（上級者）",
                "togawa_gatame" to "戸川固め", "valedictorian" to "卒業生総代", "leg_hold" to "足抱え", "spit_roast" to "串刺し（前後から）", "solo_prone" to "うつぶせオナニー", "solo_bubble_bath" to "泡風呂オナニー", "solo_mirror" to "鏡を見ながら", "solo_circles" to "円を描く（指）", "solo_sofa_grind" to "ソファでグラインド", "solo_g_spot" to "Gスポット責め（指）", "solo_tap" to "タップダンス（指先）",
                "rape_scene" to "レイプ", "sleep_sex" to "睡眠姦", "train_molest" to "痴漢（電車）",
                "outdoor_sex" to "露出・野外", "sugar_daddy_scene" to "パパ活", "orgy_scene" to "乱交・輪姦",
                "ntr_scene" to "寝取り・NTR", "blackmail_scene" to "脅迫", "hypno_scene" to "催眠",
                "drug_scene" to "媚薬", "office_sex" to "オフィスで", "ryona" to "リョナ",
                "femdom_scene" to "女王様・M男", "prostitution_scene" to "売春・援交",
                "voyeur_scene" to "盗撮", "pet_play_scene" to "ペット調教",
              )
              // PC（ゲートウェイ）の一覧を使う（なければ、アプリに入っている一覧）
              androidx.compose.runtime.LaunchedEffect(Unit) {
                MotionCatalog.load(viewModel)
                SampleStore.refresh(viewModel)
              }
              val poseSource = if (MotionCatalog.poses.isNotEmpty()) MotionCatalog.poses.map { Triple(it.first, it.second, it.third) } else allPoseTemplates.map { Triple(it.first, it.second, "体位") }
              val filteredPoseTemplates = if (templateSearch.isBlank()) poseSource
                else poseSource.filter { (_, label, _) -> label.contains(templateSearch) || templateSearch in label }
              // 体位
              ItemSizePicker(Modifier.fillMaxWidth())
              Text(text = "▸ 体位", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
              // 体位は、遊びの種類ごとに折りたたむ（検索中は開く）
              var openPoseGroups by remember { mutableStateOf(setOf<String>()) }
              filteredPoseTemplates.groupBy { it.third }.forEach { (group, rows) ->
                val open = templateSearch.isNotBlank() || group in openPoseGroups || rows.any { it.first == motionTemplate }
                Row(
                  modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.4f))
                    .clickable { openPoseGroups = if (group in openPoseGroups) openPoseGroups - group else openPoseGroups + group }.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                  Text(text = "${if (open) "▼" else "▶"} $group（${rows.size}）", style = ClawTheme.type.caption, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
                  if (rows.any { it.first == motionTemplate }) Text(text = "選択中", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
                }
                if (open) {
                  ItemChoiceGrid(
                    viewModel = viewModel,
                    items = rows.map { (id, label, _) -> ChoiceItem(id, label, "pose:$id") },
                    isSelected = { motionTemplate == it },
                    onClick = { motionTemplate = it },
                  )
                }
              }
              Text(text = "オプション（いくつでも）", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
              // オプションも、グループごとに折りたたむ（選んでいるものがあるグループは開いておく）
              var openOptionGroups by remember { mutableStateOf(setOf<String>()) }
              val optionSource = if (MotionCatalog.options.isNotEmpty()) MotionCatalog.options else MOTION_OPTION_GROUPS
              optionSource.forEach { (group, items) ->
                val chosenCount = items.count { it.first in motionOptions }
                val open = group in openOptionGroups
                Row(
                  modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.4f))
                    .clickable { openOptionGroups = if (open) openOptionGroups - group else openOptionGroups + group }.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                  Text(text = "${if (open) "▼" else "▶"} $group（${items.size}）", style = ClawTheme.type.caption, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
                  if (chosenCount > 0) Text(text = "${chosenCount}件を選択中", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
                }
                if (open) {
                  ItemChoiceGrid(
                    viewModel = viewModel,
                    items = items.map { (id, label) -> ChoiceItem(id, label, "motion:$id") },
                    isSelected = { it in motionOptions },
                    onClick = { id -> motionOptions = if (id in motionOptions) motionOptions - id else motionOptions + id },
                  )
                }
              }
              // ✨ クリエ専用：クリエに頼んで足した項目（アプリを作り直さなくても、PCにつないでいれば、ここに出る）
              if (SampleStore.customItems.isNotEmpty()) {
                var crieOpen by remember { mutableStateOf(false) }
                val crieChosen = SampleStore.customItems.count { ("custom:" + it.first) in extraItems }
                Row(
                  modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.primary.copy(alpha = 0.10f))
                    .clickable { crieOpen = !crieOpen }.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                  Text(text = "${if (crieOpen) "▼" else "▶"} ✨ クリエ専用（追加した項目 ${SampleStore.customItems.size}件）", style = ClawTheme.type.caption, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
                  if (crieChosen > 0) Text(text = "${crieChosen}件を選択中", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
                }
                if (crieOpen) {
                  ItemChoiceGrid(
                    viewModel = viewModel,
                    items = SampleStore.customItems.map { (id, label, _) -> ChoiceItem(id, label, "custom:$id") },
                    isSelected = { ("custom:$it") in extraItems },
                    onClick = { id -> val key = "custom:$id"; extraItems = if (key in extraItems) extraItems - key else extraItems + key },
                  )
                }
              }
              LaunchedEffect(Unit) {
                motionCharacters =
                  runCatching {
                    val arr = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ComicCharacterList, "{}")).optJSONArray("characters") ?: JSONArray()
                    val icons = mutableMapOf<String, android.graphics.Bitmap>()
                    val list = (0 until arr.length()).map { i ->
                      val c = arr.getJSONObject(i)
                      val outfits = c.optJSONArray("outfits") ?: JSONArray()
                      // アイコン画像をデコード
                      val iconB64 = c.optString("iconBase64", "")
                      if (iconB64.isNotBlank()) {
                        runCatching {
                          val bytes = android.util.Base64.decode(iconB64, android.util.Base64.DEFAULT)
                          android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }.getOrNull()?.let { icons[c.optString("id")] = it }
                      }
                      Triple(c.optString("id"), c.optString("name"), (0 until outfits.length()).map { outfits.getJSONObject(it).optString("name") })
                    }
                    motionCharacterIcons = icons
                    list
                  }.getOrDefault(emptyList())
                // ② キャラクター選択時の声情報を反映
                if (motionCharacterId.isBlank() && motionCharacters.isNotEmpty()) {
                  // 何も選んでいなければデフォルト選択しない（ユーザーが選ぶ）
                }
              }
              Text(text = "キャラ（キャラ図鑑のキャラで作ると、漫画と同じ顔・設定シート・声になる）", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
              // キャラ選択: アイコン + 名前表示
              val allCharOptions = listOf(Triple("", "おまかせ", emptyList<String>())) + motionCharacters
              allCharOptions.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  row.forEach { (id, name, _) ->
                    val isSelected = motionCharacterId == id
                    Box(
                      modifier = Modifier.weight(1f)
                        .background(
                          if (isSelected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surface,
                          RoundedCornerShape(10.dp),
                        )
                        .border(
                          width = if (isSelected) 2.dp else 1.dp,
                          color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.border,
                          shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { motionCharacterId = id; motionOutfit = "" }
                        .padding(8.dp),
                      contentAlignment = Alignment.Center,
                    ) {
                      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // アイコン: キャラ図鑑のキャラはアイコン表示、おまかせは絵文字
                        if (id.isBlank()) {
                          Text("🎲", fontSize = 24.sp)
                        } else {
                          // キャラ図鑑のアイコン画像を表示
                          val iconBmp = motionCharacterIcons[id]
                          if (iconBmp != null) {
                            Image(
                              bitmap = iconBmp.asImageBitmap(),
                              contentDescription = name,
                              contentScale = ContentScale.Crop,
                              modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            )
                          } else {
                            Text("👤", fontSize = 22.sp)
                          }
                        }
                        Text(
                          text = if (name.length > 8) name.take(7) + "…" else name,
                          style = ClawTheme.type.captionSmall.copy(fontSize = 11.sp),
                          color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.text,
                          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                          maxLines = 1,
                        )
                      }
                    }
                  }
                  repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
              }
              // 選択中キャラのバナー表示
              if (motionCharacterId.isNotBlank()) {
                val selectedChara = motionCharacters.firstOrNull { it.first == motionCharacterId }
                if (selectedChara != null) {
                  Row(
                    modifier = Modifier.fillMaxWidth()
                      .background(ClawTheme.colors.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                      .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Text("✅", fontSize = 20.sp)
                    Column {
                      Text(
                        text = "選択中: ${selectedChara.second}",
                        style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        color = ClawTheme.colors.primary,
                      )
                      if (selectedChara.third.isNotEmpty()) {
                        Text(
                          text = "衣装: ${selectedChara.third.joinToString(" / ")}",
                          style = ClawTheme.type.captionSmall,
                          color = ClawTheme.colors.textMuted,
                        )
                      }
                    }
                  }
                }
              }
              // ② 選択中キャラの声確認
              if (motionCharacterId.isNotBlank()) {
                LaunchedEffect(motionCharacterId) {
                  runCatching {
                    val arr2 = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ComicCharacterList, "{}")).optJSONArray("characters") ?: JSONArray()
                    for (i2 in 0 until arr2.length()) {
                      val c2 = arr2.getJSONObject(i2)
                      if (c2.optString("id") == motionCharacterId) {
                        motionCharaVoiceId = c2.optString("voiceId")
                        motionCharaVoiceCaption = c2.optString("voiceCaption")
                        break
                      }
                    }
                  }
                }
                val voiceLabel = when {
                  motionCharaVoiceId.isNotBlank() -> "🎙️ スタジオの声を使用"
                  motionCharaVoiceCaption.isNotBlank() -> "🎙️ 声の説明: $motionCharaVoiceCaption"
                  else -> "🎙️ 声未設定（デフォルト）"
                }
                Text(
                  text = voiceLabel,
                  style = ClawTheme.type.captionSmall,
                  color = ClawTheme.colors.textMuted,
                )
              }
              val outfits = motionCharacters.firstOrNull { it.first == motionCharacterId }?.third.orEmpty()
              if (outfits.isNotEmpty()) {
                Text(text = "衣装", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
                (listOf("") + outfits).chunked(3).forEach { row ->
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { name ->
                      ChoiceChip(label = name.ifBlank { "いつもの服" }, selected = motionOutfit == name, onClick = { motionOutfit = name }, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                  }
                }
              }
              // ④ 2人目ヒロイン（ハーレム・百合・NTR・乱交シーン用）
              CharacterSlotPicker(
                label = "2人目のキャラ（ハーレム・乱交・百合・NTR用）",
                noneLabel = "なし（1人）",
                characters = motionCharacters,
                icons = motionCharacterIcons,
                selectedId = motionCharacterId2,
                onPick = { id -> motionCharacterId2 = id; motionOutfit2 = "" },
              )
              val outfits2 = motionCharacters.firstOrNull { it.first == motionCharacterId2 }?.third.orEmpty()
              if (outfits2.isNotEmpty() && motionCharacterId2.isNotBlank()) {
                Text(text = "2人目の衣装", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
                (listOf("") + outfits2).chunked(3).forEach { row ->
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { name ->
                      ChoiceChip(
                        label = name.ifBlank { "いつもの服" },
                        selected = motionOutfit2 == name,
                        onClick = { motionOutfit2 = name },
                        modifier = Modifier.weight(1f),
                      )
                    }
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                  }
                }
              }
              // ── 多人数シーン：人数選択 ──────────────────────
              Text(text = "👥 シーン人数（何人で登場させるか）", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(1 to "1人", 2 to "2人", 3 to "3人", 4 to "4人").forEach { (n, label) ->
                  ChoiceChip(
                    label = label,
                    selected = motionSceneCount == n,
                    onClick = {
                      motionSceneCount = n
                      if (n < 3) { motionCharacterId3 = ""; motionOutfit3 = "" }
                      if (n < 4) { motionCharacterId4 = ""; motionOutfit4 = "" }
                    },
                    modifier = Modifier.weight(1f),
                  )
                }
              }
              if (motionSceneCount >= 3) {
                CharacterSlotPicker(
                  label = "3人目のキャラ（サブ2・NTR・グループ等）",
                  noneLabel = "なし（AI任せ）",
                  characters = motionCharacters,
                  icons = motionCharacterIcons,
                  selectedId = motionCharacterId3,
                  onPick = { id -> motionCharacterId3 = id; motionOutfit3 = "" },
                )
                val outfits3 = motionCharacters.firstOrNull { it.first == motionCharacterId3 }?.third.orEmpty()
                if (outfits3.isNotEmpty() && motionCharacterId3.isNotBlank()) {
                  Text(text = "3人目の衣装", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
                  (listOf("") + outfits3).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                      row.forEach { name -> ChoiceChip(label = name.ifBlank { "いつもの服" }, selected = motionOutfit3 == name, onClick = { motionOutfit3 = name }, modifier = Modifier.weight(1f)) }
                      repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                  }
                }
              }
              if (motionSceneCount >= 4) {
                CharacterSlotPicker(
                  label = "4人目のキャラ（乱交・グループ用）",
                  noneLabel = "なし（AI任せ）",
                  characters = motionCharacters,
                  icons = motionCharacterIcons,
                  selectedId = motionCharacterId4,
                  onPick = { id -> motionCharacterId4 = id; motionOutfit4 = "" },
                )
                val outfits4 = motionCharacters.firstOrNull { it.first == motionCharacterId4 }?.third.orEmpty()
                if (outfits4.isNotEmpty() && motionCharacterId4.isNotBlank()) {
                  Text(text = "4人目の衣装", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
                  (listOf("") + outfits4).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                      row.forEach { name -> ChoiceChip(label = name.ifBlank { "いつもの服" }, selected = motionOutfit4 == name, onClick = { motionOutfit4 = name }, modifier = Modifier.weight(1f)) }
                      repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                  }
                }
              }
              // ── ⑤ FlashVSR 高画質化スイッチ ──

              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                ChoiceChip(
                  label = "📺 標準画質",
                  selected = !motionFlashVsr,
                  onClick = { motionFlashVsr = false },
                  modifier = Modifier.weight(1f),
                )
                ChoiceChip(
                  label = "✨ FlashVSR 高画質",
                  selected = motionFlashVsr,
                  onClick = { motionFlashVsr = true },
                  modifier = Modifier.weight(1f),
                )
              }
              if (motionFlashVsr) {
                Text(
                  text = "✨ 動画生成後にFlashVSRで高画質化します（+10〜15分）。画面がシャープになります。",
                  style = ClawTheme.type.captionSmall,
                  color = ClawTheme.colors.textMuted,
                )
              }
              // ── ①下書き確認バナー ──
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(
                    if (motionDraft) ClawTheme.colors.primary.copy(alpha = 0.13f)
                    else ClawTheme.colors.danger.copy(alpha = 0.10f)
                  )
                  .padding(12.dp),
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(
                    text = if (motionDraft) "✅ 下書き確認モード（推奨）" else "⚠️ 一気に作成モード",
                    style = ClawTheme.type.label.copy(fontWeight = FontWeight.Bold),
                    color = if (motionDraft) ClawTheme.colors.primary else ClawTheme.colors.danger,
                  )
                  Text(
                    text = if (motionDraft)
                      "キーとなる絵（4〜5枚）だけ先に生成して確認。「顔が違う」「場面が違う」をここで修正してから動画に進めます。（確認まで約15〜20分）"
                    else
                      "台本・絵・動画まで一気に作ります。1分もので約1.5〜2時間。途中でキャンセルできません。",
                    style = ClawTheme.type.captionSmall,
                    color = ClawTheme.colors.textMuted,
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ChoiceChip(
                      label = "✅ まず絵を確認（推奨）",
                      selected = motionDraft,
                      onClick = { motionDraft = true },
                      modifier = Modifier.weight(1f),
                    )
                    ChoiceChip(
                      label = "⚡ 最後まで一気に",
                      selected = !motionDraft,
                      onClick = { motionDraft = false },
                      modifier = Modifier.weight(1f),
                    )
                  }
                }
              }
              Text(
                text = "AIが台本を書き、声・絵・動画のカット（ゆっくり→激しく→山場→絶頂→射精→余韻）を作ってつなぎます。1カット約11分かかるので、1分ものでも1.5〜2時間ほどかかります。登場人物は20歳以上の大人だけです。",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
            }

            if (kind == "voice" || kind == "motion") {
              LaunchedEffect(Unit) {
                voiceOptions =
                  runCatching {
                    val arr = JSONObject(viewModel.runGatewayRpc(GatewayMethod.VoiceList, "{}")).optJSONArray("voices") ?: JSONArray()
                    (0 until arr.length()).mapNotNull { i ->
                      val v = arr.getJSONObject(i)
                      if ((v.optJSONArray("files")?.length() ?: 0) > 0) v.optString("id") to v.optString("name") else null
                    }
                  }.getOrDefault(emptyList())
              }
              if (kind == "voice") {
                Text(text = "演技の絵文字（押すとセリフの最後に入ります）", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
                VOICE_EMOJIS.chunked(5).forEach { row ->
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (emoji, label) ->
                      ChoiceChip(label = "$emoji$label", selected = false, onClick = { prompt += emoji }, modifier = Modifier.weight(1f))
                    }
                    repeat(5 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                  }
                }
              }
              Text(text = "声", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
              (listOf("" to "✏️ 説明で作る") + voiceOptions).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  row.forEach { (id, name) ->
                    ChoiceChip(label = name, selected = voiceId == id, onClick = { voiceId = id }, modifier = Modifier.weight(1f))
                  }
                  repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
              }
              if (voiceId.isBlank()) {
                ClawTextField(
                  value = voiceCaption,
                  onValueChange = { voiceCaption = it },
                  placeholder = "例: 大人の女性の、甘く柔らかい色っぽい声。耳元で囁くように話す。",
                  label = "声の説明",
                  minLines = 2,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
              if (kind == "voice") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  ChoiceChip(label = "高品質（1行40秒〜）", selected = irodoriModel == "large", onClick = { irodoriModel = "large" }, modifier = Modifier.weight(1f))
                  ChoiceChip(label = "速い（1行数秒）", selected = irodoriModel == "small", onClick = { irodoriModel = "small" }, modifier = Modifier.weight(1f))
                  ChoiceChip(label = "とても速い（1行1秒以下）", selected = irodoriModel == "fast", onClick = { irodoriModel = "fast" }, modifier = Modifier.weight(1f))
                }
                Text(
                  text = "行ごとの演技は [囁き] [喘ぎ:強] [吐息:弱] のように角括弧で書けます（弱・中・強で強さ）。",
                  style = ClawTheme.type.captionSmall,
                  color = ClawTheme.colors.textMuted,
                )
                ChoiceChip(
                  label = if (dramaOn) "✅ 音声ドラマにする（お題から長い台本を書く）" else "音声ドラマにする（お題から長い台本を書く）",
                  selected = dramaOn,
                  onClick = { dramaOn = !dramaOn },
                  modifier = Modifier.fillMaxWidth(),
                )
                if (dramaOn) {
                  Text(text = "長さ", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(3, 5, 10, 20, 30).forEach { m -> ChoiceChip(label = "${m}分", selected = dramaMinutes == m, onClick = { dramaMinutes = m }, modifier = Modifier.weight(1f)) }
                  }
                  Text(text = "背景の音", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("none" to "なし", "rain" to "雨", "night" to "夜", "room" to "部屋", "bath" to "お風呂", "wind" to "風").forEach { (id, label) ->
                      ChoiceChip(label = label, selected = dramaAmbience == id, onClick = { dramaAmbience = id }, modifier = Modifier.weight(1f))
                    }
                  }
                  Text(
                    text = "上の入力欄には、物語のお題（どんな場面・誰が・どう進むか）を書きます。台本の行ごとに演技が付き、約7行で1分です。",
                    style = ClawTheme.type.captionSmall,
                    color = ClawTheme.colors.textMuted,
                  )
                }
              }
            }

            if (kind == "manga") {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceChip(label = "4コマ", selected = mangaLayout == "4koma", onClick = { mangaLayout = "4koma" }, modifier = Modifier.weight(1f))
                ChoiceChip(label = "1ページ（5コマ）", selected = mangaLayout == "page", onClick = { mangaLayout = "page" }, modifier = Modifier.weight(1f))
                ChoiceChip(label = "本格・複数ページ", selected = mangaLayout == "book", onClick = { mangaLayout = "book" }, modifier = Modifier.weight(1f))
              }
              if (mangaLayout == "book" && mangaSequelOf != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                  Text(
                    text = "📚 続編：$mangaSequelLabel の続き（同じ登場人物・これまでの話を引き継ぎます。入力欄は、次の巻でやりたいことだけでよく、空でも作れます）",
                    style = ClawTheme.type.caption.copy(fontSize = 13.sp),
                    color = ClawTheme.colors.primary,
                    modifier = Modifier.weight(1f),
                  )
                  ClawSecondaryButton(text = "やめる", onClick = { mangaSequelOf = null; mangaSequelLabel = "" })
                }
              }
              if (mangaLayout == "book") {
                Text(text = "ページ数", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  listOf(4, 8, 16, 24, 32).forEach { n -> ChoiceChip(label = "${n}", selected = mangaPages == n, onClick = { mangaPages = n }, modifier = Modifier.weight(1f)) }
                }
                Text(text = "表紙", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  ChoiceChip(label = "つける", selected = mangaCover, onClick = { mangaCover = true }, modifier = Modifier.weight(1f))
                  ChoiceChip(label = "なし", selected = !mangaCover, onClick = { mangaCover = false }, modifier = Modifier.weight(1f))
                }
              }
              if (mangaLayout == "book") {
                Text(text = "登場人物", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  ChoiceChip(label = "足りない役はAIが作る", selected = mangaAutoCast, onClick = { mangaAutoCast = true }, modifier = Modifier.weight(1f))
                  ChoiceChip(label = "選んだ人だけ", selected = !mangaAutoCast, onClick = { mangaAutoCast = false }, modifier = Modifier.weight(1f))
                }
                Text(
                  text = "下の「キャラ図鑑から選ぶ」で選んだ人（複数可）はそのまま登場します。選ばなければ、AIが登場人物を作ります。気に入った人は、できあがってから「登場人物」でキャラ図鑑に登録できます。",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )
              }
              Text(text = "絵の色", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceChip(label = "カラー", selected = mangaTone == "color", onClick = { mangaTone = "color" }, modifier = Modifier.weight(1f))
                ChoiceChip(label = "白黒（スクリーントーン）", selected = mangaTone == "mono", onClick = { mangaTone = "mono" }, modifier = Modifier.weight(1f))
              }
              Text(
                text =
                  if (mangaLayout == "book") {
                    "AIが全体の流れを考え、1ページずつコマ割り（大ゴマ・斜めのコマ）、カメラの構図、台詞、叫び・心の声・ささやきの吹き出し、擬音、集中線まで書き、表紙も描きます。GPU 2枚で絵を分担します。ページ数が多いほど時間がかかります（確認画面に目安が出ます）。人物の顔はコマごとに少し変わることがあります。"
                  } else {
                    "AIが脚本（コマ割り・台詞）を考え、コマを絵にして縦書きの吹き出しを入れます。人物の顔はコマごとに少し変わることがあります。"
                  },
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
            }

            if (kind == "image" || kind == "manga" || (kind == "video" && sourceImageJobId == null && sourceImageBase64 == null)) {
              Text(
                text = if (kind == "video") "最初の1枚のモデル" else "絵のモデル",
                style = ClawTheme.type.caption.copy(fontSize = 13.sp),
                color = ClawTheme.colors.text,
              )
              // Times measured on the RTX 4060 (2026-10-03), 2nd image onward; the first one also loads the model.
              val models = IMAGE_MODEL_GROUPS.flatMap { (_, defs) -> defs.map { Triple(it.id, it.name, it.note) } }
              GenStyleSwitch(viewModel = viewModel)
              GroupedModelPicker(viewModel = viewModel, selected = imageModel, onSelect = { imageModel = it }, columns = 3)
              Text(
                text = (models.firstOrNull { it.first == imageModel }?.third ?: ModelStoreCache.find(imageModel)?.desc.orEmpty()) +
                  if (imageModel == "zimage") "（初回はモデルのダウンロードがあります）" else "",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
              // 選んだモデルの情報（サンプルを作っていなくても、どんなモデルか分かる）
              ModelInfoCard(modelId = imageModel, adultWanted = kind == "motion", onPick = { imageModel = it })
              // モデル別サンプル比較へのショートカット
              ClawSecondaryButton(
                text = "🖼️ 全モデルで今すぐサンプル生成して比べる →",
                onClick = onOpenModelSamples,
                modifier = Modifier.fillMaxWidth(),
              )
              // ── 選択中モデルのサンプル画像インライン表示 ──────────────
              val selectedModelSampleFile = remember(imageModel) { sampleFile(context, imageModel) }
              var selectedModelSampleBitmap by remember(imageModel) { mutableStateOf<android.graphics.Bitmap?>(null) }
              LaunchedEffect(imageModel) {
                selectedModelSampleBitmap = null
                if (selectedModelSampleFile.exists()) {
                  selectedModelSampleBitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    android.graphics.BitmapFactory.decodeFile(selectedModelSampleFile.absolutePath)
                  }
                }
              }
              if (selectedModelSampleBitmap != null) {
                Text(
                  text = "📷 ${models.first { it.first == imageModel }.second} のサンプル",
                  style = ClawTheme.type.captionSmall,
                  color = ClawTheme.colors.textMuted,
                )
                Image(
                  bitmap = selectedModelSampleBitmap!!.asImageBitmap(),
                  contentDescription = "モデルサンプル画像",
                  contentScale = ContentScale.Fit,
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                )
              } else {
                // サンプルがない場合は生成ボタンを目立たせる
                Text(
                  text = "💡 モデルを選んで「全モデルでサンプル生成」するとここに表示されます",
                  style = ClawTheme.type.captionSmall,
                  color = ClawTheme.colors.textSubtle,
                )
              }
              if (imageModel in FAST_IMAGE_MODELS) {

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  ChoiceChip(label = "きれい", selected = !fastImage, onClick = { fastImage = false }, modifier = Modifier.weight(1f))
                  ChoiceChip(label = "速い（DMD2）", selected = fastImage, onClick = { fastImage = true }, modifier = Modifier.weight(1f))
                }
              }
            }

            if (kind != "music" && kind != "manga" && kind != "voice" && kind != "motion") {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("portrait" to "縦長", "square" to "正方形", "landscape" to "横長").forEach { (id, label) ->
                  ChoiceChip(label = label, selected = aspect == id, onClick = { aspect = id }, modifier = Modifier.weight(1f))
                }
              }
            }

            if (kind == "video") {
              Text(text = "作り方", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
              val engines =
                listOf(
                  Triple("standard", "標準", "制限解除版。2秒・約5分"),
                  Triple("remix", "実写 Remix", "実写向けの制限解除版。動きが大きい。2秒・約7分"),
                  Triple("long", "長い動画", "Wan2.2 SVI。5〜30秒、崩れにくい。動画1秒あたり約2分半"),
                  Triple("audio", "音付き LTX-2.3", "LTX-2.3。映像と音を同時に、〜10秒。動画1秒あたり約1分半"),
                  Triple("fast", "高速", "FastWan 5B。〜5秒を速く。動画1秒あたり約40秒"),
                  Triple("h3", "MiniMax H3 高速8step", "MiniMax H3 PDD 8-step。実写の映像と声・環境音を同時に。5秒で約9分"),
                )
              engines.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  row.forEach { (id, label, _) ->
                    ChoiceChip(label = label, selected = videoEngine == id, onClick = { videoEngine = id }, modifier = Modifier.weight(1f))
                  }
                }
              }
              Text(
                text = engines.first { it.first == videoEngine }.third +
                  if (videoEngine !in SDCPP_VIDEO_ENGINES) "（初回はモデルのダウンロードで時間がかかります）" else "",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceChip(label = "ふつう", selected = !smoothVideo, onClick = { smoothVideo = false }, modifier = Modifier.weight(1f))
                ChoiceChip(label = "なめらか（2倍）", selected = smoothVideo, onClick = { smoothVideo = true }, modifier = Modifier.weight(1f))
              }
              if (videoEngine !in SDCPP_VIDEO_ENGINES) {
                val choices =
                  when (videoEngine) {
                    "long" -> listOf(5, 10, 20, 30)
                    "audio" -> listOf(4, 6, 8, 10)
                    "h3" -> listOf(5, 8, 10)
                    else -> listOf(3, 5)
                  }
                if (videoSeconds !in choices) videoSeconds = choices.first()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  choices.forEach { sec ->
                    ChoiceChip(label = "${sec}秒", selected = videoSeconds == sec, onClick = { videoSeconds = sec }, modifier = Modifier.weight(1f))
                  }
                }
              }
              val source =
                when {
                  sourceImageJobId != null -> "作った画像を動かします"
                  sourceImageBase64 != null -> "選んだ画像を動かします"
                  else -> "文章から最初の1枚を描いて、それを動かします"
                }
              Text(text = "元にする絵: $source", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawSecondaryButton(text = "端末の画像を選ぶ", onClick = { photoPicker.launch("image/*") })
                if (sourceImageJobId != null || sourceImageBase64 != null) {
                  ClawSecondaryButton(
                    text = "文章から作るに戻す",
                    onClick = {
                      sourceImageJobId = null
                      sourceImageBase64 = null
                    },
                  )
                }
              }
              Text(
                text = "作った画像の下にある「この絵を動かす」からも選べます。",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
            }

            if (kind == "music") {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceChip(label = "🎤 歌あり", selected = !instrumental, onClick = { instrumental = false }, modifier = Modifier.weight(1f))
                ChoiceChip(label = "🎹 曲だけ", selected = instrumental, onClick = { instrumental = true }, modifier = Modifier.weight(1f))
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(30, 60, 90, 120).forEach { sec ->
                  ChoiceChip(label = "${sec}秒", selected = musicSeconds == sec, onClick = { musicSeconds = sec }, modifier = Modifier.weight(1f))
                }
              }
              if (!instrumental) {
                ClawTextField(
                  value = lyrics,
                  onValueChange = { lyrics = it },
                  placeholder = "空欄なら AI が日本語で作詞します。\n自分で書くなら [Verse] [Chorus] などで区切ってください。",
                  minLines = 4,
                  label = "歌詞（任意）",
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }

            if (estimateText.isNotBlank()) {
              Text(
                text = "⏱ $estimateText",
                style = ClawTheme.type.caption.copy(fontSize = 13.sp),
                color = ClawTheme.colors.text,
              )
            }
            if (kind == "image" || kind == "video" || kind == "manga") {
              ClawSecondaryButton(
                text = if (extraItems.isEmpty()) "🧩 項目を足す（キャラ図鑑・モーション・体位）" else "🧩 足した項目：${extraItems.size}件（タップで変更）",
                onClick = { extraOpen = true },
                modifier = Modifier.fillMaxWidth(),
              )
            }
            ClawPrimaryButton(
              text = if (isSending) "送信中..." else "✨ 作る",
              onClick = { showConfirm = true },
              enabled = !isSending && (prompt.isNotBlank() || kind == "video" || (kind == "manga" && mangaLayout == "book" && mangaSequelOf != null)),
              modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            )
            if (statusMessage.isNotBlank()) {
              Text(text = statusMessage, style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.textMuted)
            }
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "${if (libraryOpen) "▼" else "▶"} 📁 作品の保存庫（消すまでPCに残ります）　${libraryCounts.values.sum()}件",
            style = ClawTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = ClawTheme.colors.text,
            modifier = Modifier.fillMaxWidth().clickable { libraryOpen = !libraryOpen }.padding(vertical = 6.dp),
          )
          if (libraryOpen) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            val total = libraryCounts.values.sum()
            listOf("" to "すべて $total", "image" to "画像 ${libraryCounts["image"] ?: 0}", "video" to "動画 ${libraryCounts["video"] ?: 0}", "music" to "音楽 ${libraryCounts["music"] ?: 0}", "manga" to "漫画 ${libraryCounts["manga"] ?: 0}", "voice" to "声 ${libraryCounts["voice"] ?: 0}", "motion" to "モーション ${libraryCounts["motion"] ?: 0}")
              .forEach { (id, label) ->
                ChoiceChip(label = label, selected = libraryKind == id, onClick = { libraryKind = id }, modifier = Modifier.weight(1f))
              }
          }
        }
      }

      if (libraryOpen && items.isEmpty()) {
        item {
          Text(
            text = "まだ作品がありません。上で内容を書いて「作る」を押してください。\n作品はPCに保存され、ここから見たり端末に保存したりできます。",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
          )
        }
      }

      val workOf = works.flatMap { w -> w.volumes.map { it.jobId to w } }.toMap()
      val shown =
        if (libraryKind == "manga" && workOf.isNotEmpty()) {
          val running = items.filter { !workOf.containsKey(it.jobId) && (it.status == "queued" || it.status == "running") }
          val grouped = items.filter { workOf.containsKey(it.jobId) }
            .sortedWith(compareBy({ i -> works.indexOfFirst { it.id == workOf[i.jobId]?.id } }, { it.mangaVolume }))
          running + grouped + items.filter { !workOf.containsKey(it.jobId) && it !in running }
        } else items
      val firstOfWork = run {
        val seen = mutableSetOf<String>()
        shown.filter { i -> workOf[i.jobId]?.let { seen.add(it.id) } == true }.map { it.jobId }.toSet()
      }
      items(if (libraryOpen) shown else emptyList(), key = { it.jobId }) { item ->
        val isMotionDraft = item.kind == "motion" && item.mime.startsWith("image/")
        if (item.status == "done" && (item.kind == "image" || item.kind == "manga" || isMotionDraft)) {
          LaunchedEffect(item.jobId) { if (!previews.containsKey(item.jobId)) fetchFile(item) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val work = workOf[item.jobId]
        if (libraryKind == "manga" && work != null && item.jobId in firstOfWork) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(
              text = "📁 ${work.title}（${work.volumes.size}巻）",
              style = ClawTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
              color = ClawTheme.colors.primary,
              modifier = Modifier.weight(1f),
            )
            ClawSecondaryButton(text = "名前を変える", onClick = { renameWorkTarget = work; renameWorkText = work.title })
          }
        }
        ClawPanel(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "${kindLabel(item.kind)}${if (item.mangaVolume > 0 && item.kind == "manga") "　第${item.mangaVolume}巻" else ""}　${if (item.owner.isNotBlank()) "【${item.owner}】" else ""}${if (item.branchName.isNotBlank()) "【分岐: ${item.branchName}】" else ""}${item.request.ifBlank { "（指示なし）" }}",
              style = ClawTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
              color = ClawTheme.colors.text,
              maxLines = 3,
            )
            when (item.status) {
              "queued", "running" -> {
                val elapsed = if (item.elapsedSec > 0) "（経過 ${item.elapsedSec / 60}分${item.elapsedSec % 60}秒）" else ""
                val left =
                  if (item.status == "queued" && item.estimateSec > 0) "　順番待ち・目安 約${(item.estimateSec + 59) / 60}分"
                  else if (item.estimateSec > 0) "　残り約${(item.remainingSec + 59) / 60}分（全体の目安 約${(item.estimateSec + 59) / 60}分）"
                  else ""
                Text(
                  text = "⏳ ${item.stage.ifBlank { "準備中" }} $elapsed$left",
                  style = ClawTheme.type.caption.copy(fontSize = 13.sp),
                  color = ClawTheme.colors.primary,
                )
              }
              "error" -> {
                Text(
                  text = "作れませんでした: ${item.error.ifBlank { "原因不明" }}",
                  style = ClawTheme.type.caption.copy(fontSize = 13.sp),
                  color = ClawTheme.colors.textMuted,
                )
              }
              else -> {
                when (if (isMotionDraft) "image" else item.kind) {
                  "image", "manga" -> {
                    val page = if (item.parts > 1) (viewPage[item.jobId] ?: 0).coerceIn(0, item.parts - 1) else 0
                    if (item.parts > 1 && page > 0) {
                      LaunchedEffect(item.jobId, page) { loadPagePreview(item, page) }
                    }
                    val bitmap = if (page == 0) previews[item.jobId] else pagePreviews["${item.jobId}#$page"]
                    if (item.parts > 1) {
                      Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                      ) {
                        ClawSecondaryButton(text = "◀ 前", onClick = { viewPage[item.jobId] = (page - 1).coerceAtLeast(0) }, modifier = Modifier.weight(1f))
                        Text(
                          text = if (item.mangaCover && page == 0) "表紙（全${item.parts - 1}ページ）" else "${if (item.mangaCover) page else page + 1} / ${if (item.mangaCover) item.parts - 1 else item.parts} ページ",
                          style = ClawTheme.type.caption.copy(fontSize = 13.sp),
                          color = ClawTheme.colors.text,
                        )
                        ClawSecondaryButton(text = "次 ▶", onClick = { viewPage[item.jobId] = (page + 1).coerceAtMost(item.parts - 1) }, modifier = Modifier.weight(1f))
                      }
                    }
                    if (bitmap != null) {
                      Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = item.request,
                        contentScale = ContentScale.Fit,
                        modifier =
                          Modifier.fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                            .clip(RoundedCornerShape(10.dp)),
                      )
                    } else {
                      Text(text = "読み込み中...", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                    }
                  }
                  "video" -> {
                    val file = files[item.jobId]
                    val isMotionComic = item.kind == "motion"
                    if (playingVideo == item.jobId && file != null) {
                      // モーションコミック専用プレイヤー: セリフ吹き出しオーバーレイ付き
                      Box(modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(10.dp))) {
                        AndroidView(
                          factory = { ctx ->
                            VideoView(ctx).apply {
                              setVideoPath(file.absolutePath)
                              setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                              }
                            }
                          },
                          modifier = Modifier.fillMaxWidth().height(420.dp),
                        )
                        // セリフ表示：キャラ右横に吹き出し
                        if (isMotionComic) {
                          MotionDialogueBubble(
                            request = item.request,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                          )
                        }
                      }
                    }
                    ClawSecondaryButton(
                      text = if (playingVideo == item.jobId) "■ 閉じる" else "▶ 再生",
                      onClick = {
                        if (playingVideo == item.jobId) {
                          playingVideo = null
                        } else {
                          coroutineScope.launch { if (fetchFile(item) != null) playingVideo = item.jobId }
                        }
                      },
                    )
                  }
                  else -> {
                    ClawSecondaryButton(
                      text = if (playingAudio == item.jobId) "■ 止める" else "▶ 再生",
                      onClick = { toggleAudio(item) },
                    )
                    if (item.lyrics.isNotBlank() && item.lyrics != "[Instrumental]") {
                      Text(
                        text = item.lyrics,
                        style = ClawTheme.type.caption.copy(fontSize = 12.sp, lineHeight = 18.sp),
                        color = ClawTheme.colors.textMuted,
                        maxLines = 12,
                      )
                    }
                  }
                }
                if (item.durationSec > 0) {
                  Text(
                    text = "作成時間 ${item.durationSec / 60}分${item.durationSec % 60}秒",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted,
                  )
                }
                if (item.qualityNote.isNotBlank()) {
                  Text(
                    text = if (item.qualityStatus == "done") "✨ ${item.qualityNote}" else "⚠️ ${item.qualityNote}",
                    style = ClawTheme.type.caption,
                    color = if (item.qualityStatus == "done") ClawTheme.colors.primary else ClawTheme.colors.textMuted,
                  )
                }
              }
            }
            if (item.status == "done" && item.kind == "manga" && item.mangaLayout == "book") {
              val open = castOpen[item.jobId] == true
              ClawSecondaryButton(
                text = if (open) "▲ 登場人物・あらすじを閉じる" else "👥 登場人物・あらすじ",
                onClick = {
                  castOpen[item.jobId] = !open
                  if (!open) coroutineScope.launch { loadBookInfo(item.jobId) }
                },
                modifier = Modifier.fillMaxWidth(),
              )
              if (open) {
                val info = bookInfo[item.jobId]
                if (info == null) {
                  Text(text = "読み込み中...", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                } else {
                  if (info.optString("summary").isNotBlank()) {
                    Text(text = "あらすじ: ${info.optString("summary")}", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
                  }
                  if (info.optString("hook").isNotBlank()) {
                    Text(text = "終わり方: ${info.optString("hook")}", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.textMuted)
                  }
                  val cast = info.optJSONArray("cast") ?: JSONArray()
                  for (i in 0 until cast.length()) {
                    val member = cast.getJSONObject(i)
                    val name = member.optString("name")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                      Text(
                        text = "$name　${member.optString("profile").take(60)}",
                        style = ClawTheme.type.caption.copy(fontSize = 13.sp),
                        color = ClawTheme.colors.text,
                        modifier = Modifier.weight(1f),
                      )
                      if (member.optString("characterId").isBlank()) {
                        ClawSecondaryButton(text = "図鑑に登録", onClick = { registerCastMember(item.jobId, name) })
                      } else {
                        Text(text = "図鑑にいます ✓", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
                      }
                    }
                  }
                }
              }
            }
            if (item.status == "done" && item.kind in setOf("image", "video", "music", "manga", "voice", "motion")) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ClawSecondaryButton(
                  text = "この作品から分岐",
                  onClick = {
                    kind = item.kind
                    prompt = item.request
                    sourceImageJobId = null
                    sourceImageBase64 = null
                    productionControls.beginBranch(item.jobId)
                    libraryOpen = false
                    statusMessage = "元作品を残したまま別の展開を作ります。内容と制作設定を変更してください。"
                  },
                  modifier = Modifier.weight(1f),
                )
                if (item.kind == "manga" && item.mangaLayout == "book") {
                  ClawSecondaryButton(
                    text = "📚 続き（次の巻）を作る",
                    onClick = {
                      kind = "manga"
                      mangaLayout = "book"
                      mangaSequelOf = item.jobId
                      mangaSequelLabel = "第${item.mangaVolume.coerceAtLeast(1)}巻「${item.mangaTitle}」"
                      prompt = ""
                      libraryOpen = false
                      statusMessage = "この巻の続きを作ります。登場人物と話の流れは引き継がれます。やりたい展開があれば入力欄に書き、「作る」を押してください。"
                    },
                    modifier = Modifier.weight(1f),
                  )
                  ClawSecondaryButton(
                    text = "📁 作品を移す",
                    onClick = { moveTarget = item; moveWorkId = "" },
                    modifier = Modifier.weight(1f),
                  )
                }
                if (item.mime.startsWith("image/")) {
                  ClawSecondaryButton(
                    text = "一部分だけ直す",
                    onClick = {
                      kind = "image"
                      prompt = ""
                      sourceImageJobId = null
                      sourceImageBase64 = null
                      productionControls.beginRepair(item.jobId)
                      libraryOpen = false
                      statusMessage = "修正する場所を制作設定で選び、上の入力欄に直したい内容を書いてください。"
                    },
                    modifier = Modifier.weight(1f),
                  )
                }
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (item.status == "done") {
                if (item.kind == "motion" && item.mime.startsWith("video/")) {
                  ClawSecondaryButton(
                    text = "映像を活かして声を変更",
                    onClick = { revoiceTarget = item; revoiceId = ""; revoiceCaption = "" },
                  )
                }
                ClawSecondaryButton(
                  text = if (item.parts > 1) "全${item.parts}ページを端末に保存" else "端末に保存",
                  onClick = { if (item.parts > 1) savePages(item) else save(item) },
                )
                if (isMotionDraft) {
                  ClawPrimaryButton(
                    text = "この絵で動画を作る",
                    onClick = {
                      coroutineScope.launch {
                        runCatching {
                          viewModel.runGatewayRpc(
                            GatewayMethod.MediaGenerate,
                            JSONObject().put("kind", "motion").put("prompt", "（下書きの続き）").put("motionContinueJobId", item.jobId).toString(),
                          )
                          statusMessage = "下書きの絵で動画を作り始めました（1.5時間ほど）。"
                          loadList()
                        }.onFailure { statusMessage = "開始できませんでした: ${it.message}" }
                      }
                    },
                  )
                  ClawSecondaryButton(
                    text = "全部描き直す",
                    onClick = {
                      coroutineScope.launch {
                        runCatching {
                          viewModel.runGatewayRpc(
                            GatewayMethod.MediaGenerate,
                            JSONObject()
                              .put("kind", "motion")
                              .put("prompt", "（下書きの描き直し）")
                              .put("motionContinueJobId", item.jobId)
                              .put("motionDraft", true)
                              .put("motionRedrawKeys", org.json.JSONArray(listOf("k1", "f1")))
                              .toString(),
                          )
                          statusMessage = "下書きの絵を描き直しています（数分）。"
                          loadList()
                        }.onFailure { statusMessage = "開始できませんでした: ${it.message}" }
                      }
                    },
                  )
                }
                if (item.kind == "image") {
                  ClawSecondaryButton(
                    text = "✨ 高画質化（SeedVR2）",
                    onClick = {
                      coroutineScope.launch {
                        runCatching {
                          viewModel.runGatewayRpc(
                            GatewayMethod.MediaGenerate,
                            JSONObject().put("kind", "image").put("prompt", "（高画質化）").put("upscaleSourceJobId", item.jobId).put("upscaleResolution", 1536).put("upscaleModel", "3b").toString(),
                            timeoutMs = 30_000,
                          )
                          statusMessage = "高画質化を始めました（約1分）。できたら一覧に出ます。"
                          loadList()
                        }.onFailure { statusMessage = "始められませんでした: ${it.message}" }
                      }
                    },
                  )
                  ClawSecondaryButton(
                    text = "この絵を動かす",
                    onClick = {
                      kind = "video"
                      sourceImageJobId = item.jobId
                      sourceImageBase64 = null
                      statusMessage = "この絵を動かします。上で動きの指示を書いて「作る」を押してください。"
                    },
                  )
                }
              }
              when (item.status) {
                "done", "error" -> ClawSecondaryButton(text = "削除", onClick = { deleteTarget = item })
                "running" -> ClawSecondaryButton(text = "中止", onClick = { deleteTarget = item })
                "queued" -> ClawSecondaryButton(text = "取り消す", onClick = { deleteTarget = item })
              }
            }
          }
        }
        }
      }
    }

    renameWorkTarget?.let { target ->
      AlertDialog(
        onDismissRequest = { renameWorkTarget = null },
        title = { Text("作品の名前を変える") },
        text = { ClawTextField(value = renameWorkText, onValueChange = { renameWorkText = it }, label = "作品名", placeholder = target.title, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
          ClawPrimaryButton(
            text = "変える",
            enabled = renameWorkText.isNotBlank(),
            onClick = {
              renameWorkTarget = null
              coroutineScope.launch {
                runCatching {
                  viewModel.runGatewayRpc(GatewayMethod.MangaWorkRename, JSONObject().put("id", target.id).put("title", renameWorkText.trim()).toString())
                  loadList()
                }.onFailure { statusMessage = "名前を変えられませんでした: ${it.message}" }
              }
            },
          )
        },
        dismissButton = { ClawSecondaryButton(text = "やめる", onClick = { renameWorkTarget = null }) },
      )
    }

    moveTarget?.let { target ->
      val current = workOf2(works, target.jobId)
      AlertDialog(
        onDismissRequest = { moveTarget = null },
        title = { Text("第${target.mangaVolume.coerceAtLeast(1)}巻「${target.mangaTitle}」を移す") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
            works.filter { it.id != current?.id }.forEach { w ->
              ChoiceChip(label = "📁 ${w.title}（${w.volumes.size}巻）", selected = moveWorkId == w.id, onClick = { moveWorkId = w.id }, modifier = Modifier.fillMaxWidth())
            }
            ChoiceChip(label = "新しい作品にする", selected = moveWorkId.isBlank(), onClick = { moveWorkId = "" }, modifier = Modifier.fillMaxWidth())
          }
        },
        confirmButton = {
          ClawPrimaryButton(
            text = "移す",
            onClick = {
              moveTarget = null
              coroutineScope.launch {
                runCatching {
                  val params = JSONObject().put("jobId", target.jobId)
                  if (moveWorkId.isNotBlank()) params.put("workId", moveWorkId) else params.put("title", target.mangaTitle)
                  viewModel.runGatewayRpc(GatewayMethod.MangaWorkMove, params.toString())
                  loadList()
                }.onFailure { statusMessage = "移せませんでした: ${it.message}" }
              }
            },
          )
        },
        dismissButton = { ClawSecondaryButton(text = "やめる", onClick = { moveTarget = null }) },
      )
    }

    revoiceTarget?.let { target ->
      LaunchedEffect(target.jobId) {
        runCatching {
          val response = JSONObject(viewModel.runGatewayRpc(GatewayMethod.VoiceList, "{}"))
          val voices = response.optJSONArray("voices") ?: org.json.JSONArray()
          voiceOptions = (0 until voices.length()).mapNotNull { i ->
            val voice = voices.getJSONObject(i)
            if ((voice.optJSONArray("files")?.length() ?: 0) > 0) voice.optString("id") to voice.optString("name") else null
          }
        }.onFailure { statusMessage = "声の一覧を読み込めませんでした: ${it.message}" }
      }
      AlertDialog(
        onDismissRequest = { revoiceTarget = null },
        title = { Text("映像を活かして声を変更") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text("元作品を残して、同じ映像素材とセリフで新しい声の作品を作ります。画像・動画AIでの描き直しを省きます。")
            voiceOptions.forEach { (id, name) ->
              ChoiceChip(label = name, selected = revoiceId == id, onClick = { revoiceId = id }, modifier = Modifier.fillMaxWidth())
            }
            ChoiceChip(label = "声を文章で指定する", selected = revoiceId.isBlank(), onClick = { revoiceId = "" }, modifier = Modifier.fillMaxWidth())
            if (revoiceId.isBlank()) {
              ClawTextField(value = revoiceCaption, onValueChange = { revoiceCaption = it }, label = "声の説明", placeholder = "例: 落ち着いた低めの声", modifier = Modifier.fillMaxWidth())
            }
          }
        },
        confirmButton = {
          ClawPrimaryButton(
            text = "この声で作る",
            enabled = revoiceId.isNotBlank() || revoiceCaption.isNotBlank(),
            onClick = {
              val params = JSONObject().put("kind", "motion").put("prompt", "（映像を活かして声を変更）").put("motionRevoiceJobId", target.jobId)
              if (revoiceId.isNotBlank()) params.put("voiceId", revoiceId) else params.put("voiceCaption", revoiceCaption.trim())
              revoiceTarget = null
              coroutineScope.launch {
                runCatching {
                  viewModel.runGatewayRpc(GatewayMethod.MediaGenerate, params.toString())
                  statusMessage = "映像を再利用して、新しい声の作品を作っています。"
                  loadList()
                }.onFailure { statusMessage = "声を変更できませんでした: ${it.message}" }
              }
            },
          )
        },
        dismissButton = { ClawSecondaryButton(text = "閉じる", onClick = { revoiceTarget = null }) },
      )
    }
    deleteTarget?.let { target ->
      val (title, body, action) =
        when (target.status) {
          "queued" -> Triple("順番待ちから取り消しますか？", "まだ作り始めていないので、すぐに消えます。", "取り消す")
          "running" ->
            Triple(
              "作るのを中止しますか？",
              "途中まで作った分は残りません。順番待ちがあれば、次を始めます。",
              "中止する",
            )
          else -> Triple("この作品を削除しますか？", "PC内のファイルが消えます。端末に保存した分は残ります。", "削除する")
        }
      AlertDialog(
        onDismissRequest = { deleteTarget = null },
        title = { Text(text = title, color = ClawTheme.colors.text) },
        text = { Text(text = body, color = ClawTheme.colors.textMuted) },
        confirmButton = {
          ClawPrimaryButton(
            text = action,
            onClick = {
              delete(target)
              deleteTarget = null
            },
          )
        },
        dismissButton = { ClawSecondaryButton(text = "やめる", onClick = { deleteTarget = null }) },
      )
    }

    if (showConfirm) {
      AlertDialog(
        onDismissRequest = { showConfirm = false },
        title = { Text(text = "この内容で作りますか？", color = ClawTheme.colors.text) },
        text = {
          Text(
            text =
              listOf(
                generationSummary(kind, imageModel, fastImage, aspect, videoEngine, videoSeconds, smoothVideo, musicSeconds, instrumental, mangaLayout, motionMinutes, motionFellatio),
                estimateText.ifBlank { "かかる時間: 計算できませんでした" },
                "作っている間、チャットは一時的に止まります。",
              ).joinToString("\n"),
            color = ClawTheme.colors.textMuted,
          )
        },
        confirmButton = {
          ClawPrimaryButton(
            text = "作る",
            onClick = {
              showConfirm = false
              generate()
            },
          )
        },
        dismissButton = { ClawSecondaryButton(text = "やめる", onClick = { showConfirm = false }) },
      )
    }
  }
}

// ──────────────────────────────────────────────────────────────
// モデル別サンプルギャラリー
// 同じプロンプトを全モデルで生成してサムネを2列グリッドで並べる
// ──────────────────────────────────────────────────────────────

private val MODEL_DEFS = IMAGE_MODEL_GROUPS.flatMap { (_, defs) -> defs.map { Triple(it.id, it.name, it.note) } }

// クリエ（スタジオパートナー）の既定ルック
internal const val KURIE_LOOK =
  "1girl, solo, mature female, adult woman, 23 years old, black hair, bob cut, blunt bangs, neon blue streaked hair, blue inner hair, brown eyes, cheerful smile, open mouth, medium breasts, " +
    "oversized white hoodie, blue drawstrings, headphones around neck, black leggings, white sneakers, holding stylus, holding tablet, indoors"

// ─────────────────────────────────────────────────────────────
// サンプルキャッシュ管理ユーティリティ
// ─────────────────────────────────────────────────────────────
private fun sampleCacheDir(context: android.content.Context) =
  java.io.File(context.filesDir, "model_samples").also { it.mkdirs() }

private fun sampleFile(context: android.content.Context, key: String): java.io.File =
  java.io.File(sampleCacheDir(context), "${key.replace("/", "_")}.png")

internal fun saveSampleBitmap(context: android.content.Context, key: String, bitmap: Bitmap) {
  runCatching {
    java.io.FileOutputStream(sampleFile(context, key)).use { out ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
    }
  }
}

internal fun loadSampleBitmap(context: android.content.Context, key: String): Bitmap? {
  val f = sampleFile(context, key)
  return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
}

// ─────────────────────────────────────────────────────────────
// モデル別サンプルギャラリー（キャッシュ保存対応）
// ─────────────────────────────────────────────────────────────
@Composable
fun ModelSampleGallery(viewModel: MainViewModel, onBack: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = rememberCoroutineScope()

  // タブ: 0=一般, 1=成人向け
  var selectedTab by remember { mutableStateOf(0) }
  val TABS = listOf("🌸 一般向け", "👤 クリエ")
  val GENERAL_PROMPT = "1girl, solo, beautiful, portrait, natural lighting, smile, casual clothes, photorealistic"
  val CRIE_PROMPT   = KURIE_LOOK

  val currentPromptDefault = if (selectedTab == 0) GENERAL_PROMPT else CRIE_PROMPT
  // プロンプトは、はじめからクリエの文が入っていて、自由に書き換えられる
  var promptText by remember(selectedTab) { mutableStateOf(currentPromptDefault) }
  val effectivePrompt = promptText.ifBlank { currentPromptDefault }
  var zoomed by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
  val busyOne = remember { mutableStateMapOf<String, Boolean>() }

  var isGenerating by remember { mutableStateOf(false) }
  var statusMsg    by remember { mutableStateOf("") }
  val jobIds  = remember { mutableStateMapOf<String, String>() }
  val bitmaps = remember { mutableStateMapOf<String, Bitmap?>() }
  val errors  = remember { mutableStateMapOf<String, String>() }

  // キャッシュキーのプレフィックス: "gen" or "adu"
  val tabPrefix = if (selectedTab == 0) "gen" else "crie2"
  fun cacheKey(modelId: String) = "${tabPrefix}_${modelId}"

  // 起動時・タブ切替時にキャッシュを読み込む
  LaunchedEffect(selectedTab) {
    bitmaps.clear(); errors.clear(); jobIds.clear()
    for ((modelId, _, _) in MODEL_DEFS) {
      val bmp = loadSampleBitmap(context, cacheKey(modelId))
      bitmaps[modelId] = bmp // null でも登録（「未生成」表示のため）
    }
    statusMsg = if (bitmaps.values.any { it != null }) "💾 キャッシュから読み込みました" else "まだサンプルが生成されていません。「サンプル生成」ボタンで生成してください。"
  }

  suspend fun pollJobs() {
    val remaining = jobIds.keys.toMutableSet()
    while (remaining.isNotEmpty()) {
      delay(4_000)
      for (model in remaining.toList()) {
        val jid = jobIds[model] ?: continue
        try {
          val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobGet, JSONObject().put("jobId", jid).toString(), timeoutMs = 10_000))
          when (res.optString("status")) {
            "done" -> {
              val imgRes = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobResult, JSONObject().put("jobId", jid).toString(), timeoutMs = 30_000))
              val b64 = imgRes.optString("base64", "")
              if (b64.isNotBlank()) {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
                  bitmaps[model] = bmp
                  saveSampleBitmap(context, cacheKey(model), bmp) // ← キャッシュ保存
                }
              }
              remaining.remove(model)
            }
            "error" -> { errors[model] = res.optString("error", "エラー"); remaining.remove(model) }
          }
        } catch (e: Exception) {
          if (e is kotlinx.coroutines.CancellationException) throw e
          // Gateway が再起動などで忘れたジョブは、いくら待っても出てこない。
          if (e.message?.contains("見つかりません") == true) {
            errors[model] = "Gateway の再起動などで、この作業の記録が消えました。もう一度作ってください"
            remaining.remove(model)
          }
        }
      }
    }
    isGenerating = false
    statusMsg = "✅ 生成完了！次回から即表示されます（キャッシュ保存済み）"
  }

  /** 1つのモデルで1枚作る（ジョブを出して、できるまで待つ）。 */
  suspend fun generateOne(modelId: String, name: String) {
    errors.remove(modelId)
    try {
      val p = JSONObject().put("kind", "image").put("prompt", effectivePrompt)
        .put("imageModel", modelId).put("aspect", "portrait").put("fast", true)
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaGenerate, p.toString(), timeoutMs = 30_000))
      val jid = res.optString("jobId", "").takeIf { it.isNotBlank() } ?: run {
        errors[modelId] = "ジョブID取得失敗"
        return
      }
      jobIds[modelId] = jid
      var done = false
      repeat(120) { // 最大120 * 4秒 = 8分待ち
        if (done) return@repeat
        delay(4_000)
        runCatching {
          val st = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobGet, JSONObject().put("jobId", jid).toString(), timeoutMs = 10_000))
          when (st.optString("status")) {
            "done" -> {
              val imgRes = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobResult, JSONObject().put("jobId", jid).toString(), timeoutMs = 30_000))
              val b64 = imgRes.optString("base64", "")
              if (b64.isNotBlank()) {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
                  bitmaps[modelId] = bmp
                  saveSampleBitmap(context, cacheKey(modelId), bmp)
                  statusMsg = "✅ $name 完了！"
                }
              }
              done = true
            }
            "error" -> {
              errors[modelId] = st.optString("error", "エラー")
              done = true
            }
          }
        }
      }
      jobIds.remove(modelId)
      if (!done) errors[modelId] = "タイムアウト"
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
      jobIds.remove(modelId)
      errors[modelId] = e.message ?: "失敗"
    }
  }

  /** 1つのモデルだけ、タップで作る（もう絵があれば作り直す）。 */
  fun generateSingle(modelId: String, name: String) {
    if (effectivePrompt.isBlank() || busyOne[modelId] == true) return
    scope.launch {
      busyOne[modelId] = true
      statusMsg = "⏳ $name を生成中…"
      generateOne(modelId, name)
      busyOne.remove(modelId)
    }
  }

  /** まだ絵のないモデルを、1つずつ順番に生成してリアルタイムで表示する。 */
  fun generateAll() {
    if (effectivePrompt.isBlank()) return
    scope.launch {
      isGenerating = true; jobIds.clear(); errors.clear()
      val modelsToGen = MODEL_DEFS.filter { (id, _, _) -> bitmaps[id] == null }
      if (modelsToGen.isEmpty()) {
        statusMsg = "✅ 全モデルのサンプルがキャッシュ済みです。「🗑 キャッシュ削除」後に再生成できます。"
        isGenerating = false
        return@launch
      }
      statusMsg = "📸 ${modelsToGen.size} モデルを1枚ずつ順番に生成します…"
      modelsToGen.forEachIndexed { idx, (modelId, name, _) ->
        if (!isGenerating) return@forEachIndexed  // キャンセル時
        statusMsg = "⏳ [${idx + 1}/${modelsToGen.size}] $name を生成中…"
        generateOne(modelId, name)
      }
      isGenerating = false
      val ok = MODEL_DEFS.count { (id, _, _) -> bitmaps[id] != null }
      statusMsg = "🎉 完了！ $ok/${MODEL_DEFS.size} モデルのサンプルをキャッシュ保存しました"
    }
  }

  fun stopGeneration() { isGenerating = false }

  zoomed?.let { (title, bmp) -> ZoomableImageDialog(bitmap = bmp.asImageBitmap(), title = title, onDismiss = { zoomed = null }) }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      // ── ヘッダー ──
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
          androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(text = "🖼️ モデル別サンプル比較", style = ClawTheme.type.title, color = ClawTheme.colors.text)
            Text(text = "生成したサンプルはキャッシュ保存されます", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
        }
      }

      // ── タブ切替 ──
      item {
        androidx.compose.material3.PrimaryTabRow(
          selectedTabIndex = selectedTab,
          containerColor = ClawTheme.colors.surface,
          contentColor = ClawTheme.colors.primary,
        ) {
          TABS.forEachIndexed { i, label ->
            androidx.compose.material3.Tab(
              selected = selectedTab == i,
              onClick = { selectedTab = i },
              text = { Text(label, style = ClawTheme.type.body.copy(fontSize = 13.sp)) },
            )
          }
        }
      }

      // ── プロンプト・生成パネル ──
      item {
        ClawPanel(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ClawTextField(
              value = promptText,
              onValueChange = { promptText = it },
              placeholder = "使うプロンプト（英語のタグ）",
              label = if (selectedTab == 0) "使うプロンプト（自由に変えられます）" else "クリエの使うプロンプト（自由に変えられます）",
              minLines = 3,
              modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ClawSecondaryButton(text = "↺ はじめの文に戻す", onClick = { promptText = currentPromptDefault }, modifier = Modifier.weight(1f))
            }
            Text(
              text = "各モデルの「▶ このモデルで作る」で、1つずつ作れます。上の「サンプル生成」は、絵のないモデルを順番に作ります。",
              style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ClawPrimaryButton(
                text = if (isGenerating) "⏳ 生成中… (タップで停止)" else "▶ サンプル生成 ${MODEL_DEFS.size}モデル順番に",
                onClick = { if (isGenerating) stopGeneration() else generateAll() },
                modifier = Modifier.weight(1f),
              )
              if (bitmaps.values.any { it != null }) {
                ClawSecondaryButton(
                  text = "🗑 キャッシュ削除",
                  onClick = {
                    for ((modelId, _, _) in MODEL_DEFS) {
                      sampleFile(context, cacheKey(modelId)).delete()
                      bitmaps[modelId] = null
                    }
                    statusMsg = "キャッシュを削除しました"
                  },
                  modifier = Modifier.wrapContentWidth(),
                )
              }
            }
            if (statusMsg.isNotBlank()) Text(text = statusMsg, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
          }
        }
      }

      // ── 2列グリッド表示 ──
      val rows = MODEL_DEFS.chunked(2)
      items(rows) { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          for ((modelId, name, desc) in row) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
              ClawPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(text = name, style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), color = ClawTheme.colors.text)
                  Text(text = desc, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
                  val bmp = bitmaps[modelId]; val err = errors[modelId]; val jid = jobIds[modelId]
                  when {
                    bmp != null -> androidx.compose.foundation.Image(
                      bitmap = bmp.asImageBitmap(), contentDescription = "$name サンプル",
                      contentScale = ContentScale.Fit,
                      modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { zoomed = name to bmp },
                    )
                    err != null -> Text(text = "❌ $err", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.danger)
                    jid != null -> Text(text = "⏳ 生成中…", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
                    else -> Box(
                      modifier = Modifier.fillMaxWidth().height(120.dp)
                        .background(ClawTheme.colors.surface, RoundedCornerShape(8.dp)),
                      contentAlignment = Alignment.Center,
                    ) {
                      Text(text = "未生成", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textSubtle, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                  }
                  ClawSecondaryButton(
                    text = if (busyOne[modelId] == true) "⏳ 生成中…" else if (bmp != null) "▶ 作り直す" else "▶ このモデルで作る",
                    enabled = busyOne[modelId] != true,
                    onClick = { generateSingle(modelId, name) },
                    modifier = Modifier.fillMaxWidth(),
                  )
                }
              }
            }
          }
          if (row.size == 1) androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}


// ─────────────────────────────────────────────────────────────
// 体位定義
// ─────────────────────────────────────────────────────────────
internal data class PoseDef(val id: String, val label: String, val prompt: String)

internal const val KURIE_NUDE =
  "1girl, solo, mature female, adult woman, 23 years old, black hair, bob cut, blunt bangs, neon blue streaked hair, blue inner hair, brown eyes, medium breasts, nude, naked, headphones around neck, " +
    "masterpiece, best quality, ultra detailed, anime style illustration"

internal val POSE_DEFS = listOf(
  PoseDef("missionary",      "正常位",        "$KURIE_NUDE, missionary position sex, lying on back on bed, legs spread wide, man on top, moaning, bedroom, soft lighting"),
  PoseDef("cowgirl",         "騎乗位",         "$KURIE_NUDE, cowgirl position, woman on top riding, straddling man lying down, hips moving, ahegao expression, warm bedroom lighting"),
  PoseDef("reverse_cowgirl", "逆騎乗位",       "$KURIE_NUDE, reverse cowgirl position, facing away from man, riding, looking back with pleasure, bed"),
  PoseDef("doggystyle",      "後背位",         "$KURIE_NUDE, doggy style position, on all fours, from behind sex, looking back with pleasure, bedroom"),
  PoseDef("standing_back",   "立ちバック",     "$KURIE_NUDE, standing sex from behind, against wall, one leg lifted, moaning face, indoor night"),
  PoseDef("mating_press",    "種付けプレス",   "$KURIE_NUDE, mating press position, legs folded back over head, deep missionary, pinned down, ahegao, bed"),
  PoseDef("sixtynine",       "69",             "$KURIE_NUDE, 69 position, mutual oral sex, lying side by side, nude couple, intimate bedroom"),
  PoseDef("blowjob",         "フェラチオ",     "$KURIE_NUDE, oral sex, blowjob, kneeling position, looking up, man standing, tongue out, saliva, intimate"),
  PoseDef("spooning",        "後ろから抱き",   "$KURIE_NUDE, spooning sex position, side lying, sex from behind while spooning, intimate bedroom night"),
  PoseDef("sitting",         "座位",           "$KURIE_NUDE, sitting sex position, face to face, woman sitting on mans lap, straddling sitting, intimate"),
  PoseDef("standing_front",  "正面立ち",       "$KURIE_NUDE, face to face standing sex, lifting leg, passionate kiss, intimate, indoor"),
  PoseDef("anal",            "アナル",         "$KURIE_NUDE, anal sex, doggystyle anal position, from behind, looking back, bed, intimate"),
  PoseDef("titjob",          "パイズリ",       "$KURIE_NUDE, paizuri, titjob, breasts pressed together around penis, kneeling, looking up with smile"),
  PoseDef("handjob",         "手コキ",         "$KURIE_NUDE, handjob, stroking penis with hand, kneeling beside, bedroom, intimate"),
  PoseDef("rape",            "レイプ",         "$KURIE_NUDE, forced sex scene, pinned down, tears, struggling, held down, bedroom, dramatic lighting"),
)

private const val POSE_NEG =
  "clothes, clothed, dressed, text, watermark, signature, lowres, bad anatomy, worst quality, blurry, deformed"

// ─────────────────────────────────────────────────────────────
// 体位サンプルギャラリー（キャッシュ保存対応）
// ─────────────────────────────────────────────────────────────



// ─────────────────────────────────────────────────────────────────────────────
// モーションコミック専用 UI コンポーネント
// ─────────────────────────────────────────────────────────────────────────────

/**
 * モーションコミック用セリフ吹き出し。
 * 動画上にオーバーレイして、キャラクターの横にセリフを表示する。
 * request 文字列からセリフをパースして表示する（プロトタイプ実装）。
 *
 * fontStyle 対応:
 *  normal  → Noto Serif JP 風（明朝・標準）
 *  whisper → 小さい・細い（ひそひそ・色っぽい）
 *  shout   → 大きい・太ゴシック（叫び・驚き）
 *  thought → 斜体・丸み（心の声・モノローグ）
 *  sfx     → 超大きい太字（擬音）
 *  caption → 枠付き小字（状況説明）
 */
@Composable
private fun MotionDialogueBubble(
    request: String,
    modifier: Modifier = Modifier,
) {
    // request からセリフを抽出（「」で囲まれた文字列、なければ先頭60字）
    val dialogueRegex = Regex("[「『]([^」』]{1,40})[」』]")
    val lines = dialogueRegex.findAll(request).map { it.groupValues[1] }.take(3).toList()
    val displayText = lines.firstOrNull() ?: request.take(40).ifBlank { null }

    if (displayText != null) {
        // 吹き出し本体（右下に配置）
        Box(
            modifier = modifier
                .background(
                    color = androidx.compose.ui.graphics.Color(0xCC000000),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = displayText,
                style = ClawTheme.type.body.copy(
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    letterSpacing = 0.5.sp,
                ),
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

/**
 * モーションコミック前段シーン（着衣→脱衣→前戯）表示用カード。
 * approach ステップを横スクロールで表示し、タップで詳細確認できる。
 */
@Composable
fun MotionApproachSequence(
    steps: List<org.json.JSONObject>,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "前段シーン（ステップ）",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
        )
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(steps, key = { it.optString("tags").take(20) }) { step ->
                val line = step.optString("line", "")
                val fontStyle = step.optString("fontStyle", "normal")
                val fontSize = when (fontStyle) {
                    "shout"   -> 20.sp
                    "sfx"     -> 24.sp
                    "whisper" -> 13.sp
                    "caption" -> 12.sp
                    else      -> 16.sp
                }
                val fontWeight = when (fontStyle) {
                    "shout", "sfx" -> FontWeight.ExtraBold
                    "whisper"      -> FontWeight.Light
                    else           -> FontWeight.Normal
                }
                val bgAlpha = if (fontStyle == "caption") 0xDD else 0xCC

                Box(
                    modifier = Modifier
                        .heightIn(min = 60.dp)
                        .background(
                            color = androidx.compose.ui.graphics.Color(bgAlpha.toLong() shl 24 or 0x1A1A2EL),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (fontStyle == "sfx") "♪ " else line,
                        style = ClawTheme.type.body.copy(
                            fontSize = fontSize,
                            fontWeight = fontWeight,
                            lineHeight = (fontSize.value * 1.4f).sp,
                        ),
                        color = when (fontStyle) {
                            "shout"   -> androidx.compose.ui.graphics.Color(0xFFFF6B6B)
                            "sfx"     -> androidx.compose.ui.graphics.Color(0xFFFFD93D)
                            "whisper" -> androidx.compose.ui.graphics.Color(0xFFFFB3C6)
                            "caption" -> androidx.compose.ui.graphics.Color(0xFFAAAAAA)
                            else      -> androidx.compose.ui.graphics.Color.White
                        },
                    )
                }
            }
        }
    }
}
