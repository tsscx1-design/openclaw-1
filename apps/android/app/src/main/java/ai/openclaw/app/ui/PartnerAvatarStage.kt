package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawTheme
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/** チャット画面のアバターの表情（ゲートウェイの avatar-frames.ts の名前と同じ）。 */
internal enum class AvatarEmotion(val key: String) {
  NORMAL("normal"),
  HAPPY("happy"),
  SAD("sad"),
  ANGRY("angry"),
  SURPRISED("surprised"),
  SHY("shy"),
  THINKING("thinking"),
  /** 触られたときの、成人向けの表情（絵がなければ「照れ」で代用）。 */
  AROUSED("aroused"),
}

/** 触った場所と、そのときの表情・ひとこと。adult = 成人向けの箇所。 */
internal data class AvatarTouch(
  val place: String,
  val emotion: AvatarEmotion,
  val words: List<String>,
  val adult: Boolean,
  /** 追加の表情・姿の絵の名前（blush / excited / ahegao / climax / disgust / collapse）。なければ null。 */
  val face: String? = null,
  /** 動き（wiggle=くねくね / sway=ゆらゆら / tremble=震え / recoil=嫌がって身を引く / wince=痛がって縮こまる）。なければ null。 */
  val motion: String? = null,
)

/** 追加の表情の絵（1枚ずつ）。ゲートウェイの avatar-frames.ts の extraFrames と同じ名前。 */
internal val AVATAR_EXTRA_KEYS: List<String> = listOf("blush", "excited", "ahegao", "climax", "disgust", "collapse", "squirt")

/** 絵がないときに、近い表情で代用する順番。 */
internal fun avatarFaceFallbacks(face: String): List<String> =
  when (face) {
    "squirt" -> listOf("squirt", "climax", "ahegao", "aroused_closed")
    "climax" -> listOf("climax", "ahegao", "aroused_closed", "shy_closed")
    "ahegao" -> listOf("ahegao", "aroused_closed", "shy_closed")
    "excited" -> listOf("excited", "aroused_closed", "happy_closed")
    "blush" -> listOf("blush", "shy_closed")
    "disgust" -> listOf("disgust", "angry_closed")
    "collapse" -> listOf("collapse", "climax", "ahegao", "aroused_closed")
    else -> listOf(face)
  }

/** 成人向けの箇所を続けて触ったときの、高まり（1〜5）に応じた反応。 */
internal fun arousalTouch(level: Int): AvatarTouch =
  when (level.coerceIn(1, 5)) {
    1 -> AvatarTouch("高まり1", AvatarEmotion.SHY, listOf("あっ…", "んっ…そこは…", "だ、だめ…"), true, "blush", "sway")
    2 -> AvatarTouch("高まり2", AvatarEmotion.AROUSED, listOf("はぁ…はぁ…", "んっ…あ…", "からだが熱い…"), true, "excited", "wiggle")
    3 -> AvatarTouch("高まり3", AvatarEmotion.AROUSED, listOf("もう…だめぇ…", "あっ…あぁ…", "おかしくなりそう…"), true, null, "wiggle")
    4 -> AvatarTouch("高まり4", AvatarEmotion.AROUSED, listOf("あひっ…あぁぁ…", "んあぁっ…", "きもちいい…"), true, "ahegao", "tremble")
    else -> AvatarTouch("高まり5", AvatarEmotion.AROUSED, listOf("いっ…いくぅぅっ！", "あぁぁぁっ！！", "だめっ…いっちゃう…！"), true, "climax", "tremble")
  }

/** 立ち絵の高さのうちの位置（0 = 頭の上、1 = 足もと）から、触った場所を決める。 */
internal fun avatarTouchAt(ratio: Float): AvatarTouch =
  when {
    ratio < 0.08f -> AvatarTouch("頭", AvatarEmotion.HAPPY, listOf("えへへ…", "なでなで、うれしい", "もっと…"), false)
    ratio < 0.20f -> AvatarTouch("顔", AvatarEmotion.SHY, listOf("ひゃっ…", "か、顔はだめ…", "もう…恥ずかしい"), false)
    ratio < 0.27f -> AvatarTouch("首・肩", AvatarEmotion.SURPRISED, listOf("ひゃん！", "くすぐったい…", "びっくりした"), false)
    ratio < 0.40f -> AvatarTouch("胸", AvatarEmotion.AROUSED, listOf("あっ…", "んっ…そこは…", "だ、だめ…"), true)
    ratio < 0.50f -> AvatarTouch("お腹・腰", AvatarEmotion.SURPRISED, listOf("くすぐったいよ", "ひゃう", "やめてよ〜"), false)
    ratio < 0.62f -> AvatarTouch("下腹部・お尻", AvatarEmotion.AROUSED, listOf("そ、そこは…", "あ…んっ", "だめ…ぞくぞくする"), true)
    ratio < 0.85f -> AvatarTouch("脚", AvatarEmotion.SHY, listOf("ちょ、ちょっと…", "くすぐったい", "もう、えっち"), false)
    else -> AvatarTouch("足", AvatarEmotion.SURPRISED, listOf("くすぐったい！", "きゃっ", "やだぁ"), false, "disgust", "recoil")
  }

/** アバターの絵の名前（口を閉じる／開く 7表情 と、まばたき）。 */
internal val AVATAR_FRAME_KEYS: List<String> =
  AvatarEmotion.entries.flatMap { listOf("${it.key}_closed", "${it.key}_open") } + "blink"

/** 服を脱がす段階の絵（un_1〜un_16。半脱ぎを含む）。ゲートウェイの avatar-frames.ts と同じ名前。 */
internal const val UNDRESS_LAST_STAGE = 16
internal val AVATAR_UNDRESS_KEYS: List<String> = (1..UNDRESS_LAST_STAGE).map { "un_$it" }

/**
 * なぞって脱がす（全16段階）。いまの段階と、なぞった向き・距離・始めた高さ（0 = 頭の上、1 = 足もと）から、次の段階を返す。
 * 上着：0→1 胸を下へ（ジッパーを下ろす）／1〜4 胸を横へ（左右に開く・片肩・両肩・脱ぐ）。
 * 下：5〜10 下半身を下へ（ゆるめる・腰・太もも・膝・足首・脱ぐ）。
 * ブラ：11〜13 胸を横へ（肩ひも・ずらす・外す）。下着：14・15 下半身を下へ（半分・脱ぐ）。当てはまらなければ null。
 */
internal fun undressNext(stage: Int, dx: Float, dy: Float, startRatio: Float, minDistance: Float): Int? {
  val down = dy >= minDistance && dy > kotlin.math.abs(dx)
  val sideways = kotlin.math.abs(dx) >= minDistance && kotlin.math.abs(dx) > kotlin.math.abs(dy)
  val chest = startRatio in 0.20f..0.50f
  val lower = startRatio in 0.45f..0.90f
  return when {
    stage == 0 && down && chest -> 1
    stage in UNDRESS_SIDEWAYS_STAGES && sideways && chest -> stage + 1
    stage in UNDRESS_DOWNWARD_STAGES && down && lower -> stage + 1
    else -> null
  }
}

private val UNDRESS_SIDEWAYS_STAGES: Set<Int> = setOf(1, 2, 3, 4, 11, 12, 13)
private val UNDRESS_DOWNWARD_STAGES: Set<Int> = setOf(5, 6, 7, 8, 9, 10, 14, 15)

/** 返事の文章から表情を決める。当てはまる言葉が多い表情を選び、なければ通常。 */
internal fun avatarEmotionOf(text: String): AvatarEmotion {
  if (text.isBlank()) return AvatarEmotion.NORMAL
  val rules =
    listOf(
      AvatarEmotion.ANGRY to listOf("怒", "ムカ", "許さ", "いい加減", "😡", "💢", "😠"),
      AvatarEmotion.SAD to listOf("悲し", "ごめん", "残念", "寂し", "泣", "つらい", "😢", "😭", "🥺"),
      AvatarEmotion.SURPRISED to listOf("えっ", "びっくり", "驚", "まさか", "本当に", "!?", "！？", "😲", "😮", "😳"),
      AvatarEmotion.SHY to listOf("照れ", "恥ずか", "えへへ", "てれ", "もう…", "///", "☺"),
      AvatarEmotion.THINKING to listOf("うーん", "考え", "ええと", "えーと", "どうしよう", "かな…", "🤔"),
      AvatarEmotion.HAPPY to listOf("嬉し", "うれし", "楽し", "やった", "ありがとう", "好き", "最高", "わーい", "😊", "😄", "😆", "✨", "♪", "笑", "！！"),
    )
  val scored = rules.map { (emotion, words) -> emotion to words.sumOf { word -> text.windowed(word.length).count { it == word } } }
  val best = scored.maxByOrNull { it.second }
  return if (best != null && best.second > 0) best.first else AvatarEmotion.NORMAL
}

/** 返事の文章から、追加の表情（blush / excited / ahegao / climax / disgust / collapse）を決める。当てはまらなければ null。 */
internal fun avatarExtraFaceOf(text: String): String? {
  if (text.isBlank()) return null
  val rules =
    listOf(
      "collapse" to listOf("崩れ落", "立てな", "腰が抜け", "力が入らな", "へたり込"),
      "climax" to listOf("いくっ", "イク", "いっちゃ", "イッ", "絶頂", "いくぅ"),
      "ahegao" to listOf("あへ", "アヘ", "とろけ", "とろとろ", "頭が真っ白", "おかしくなりそう"),
      "excited" to listOf("興奮", "ドキドキ", "どきどき", "はぁはぁ", "はぁ…", "ゾクゾク", "ぞくぞく", "火照"),
      "blush" to listOf("赤面", "顔が赤", "顔が熱", "真っ赤", "照れちゃ", "ぽっ"),
      "disgust" to listOf("気持ち悪", "きもち悪", "うわぁ", "うげ", "いやだ", "嫌だ", "最低", "無理！", "やめて"),
    )
  return rules.firstOrNull { (_, words) -> words.any { it in text } }?.first
}

/** いま見せる絵の名前。まばたき中は blink、それ以外は表情と口の開き。 */
internal fun avatarFrameKey(emotion: AvatarEmotion, mouthOpen: Boolean, blinking: Boolean): String =
  if (blinking) "blink" else "${emotion.key}_${if (mouthOpen) "open" else "closed"}"

private fun avatarCacheDir(context: android.content.Context, characterId: String): File =
  File(context.filesDir, "avatar/$characterId").also { it.mkdirs() }

/**
 * チャット画面のアバター。キャラ図鑑のキャラに作った表情の絵を、読み上げに合わせて口を動かし、
 * 返事の内容で表情を替え、ときどきまばたきする。絵がまだなければ、何も出さない。
 */
@Composable
internal fun PartnerAvatarStage(
  viewModel: MainViewModel,
  characterId: String,
  speaking: Boolean,
  lastReply: String,
  height: Dp = 240.dp,
  modifier: Modifier = Modifier,
) {
  // Live2D モデルを使うことにしているキャラは、絵ではなく Live2D で出す
  val live2d = rememberActiveLive2d(viewModel, characterId)
  if (live2d != null) {
    Live2dAvatar(viewModel, characterId, live2d, speaking, lastReply, height, modifier)
  } else {
    FramesAvatarStage(viewModel, characterId, speaking, lastReply, height, modifier)
  }
}

@Composable
private fun FramesAvatarStage(
  viewModel: MainViewModel,
  characterId: String,
  speaking: Boolean,
  lastReply: String,
  height: Dp,
  modifier: Modifier,
) {
  val context = LocalContext.current
  val frames = remember(characterId) { mutableStateMapOf<String, Bitmap>() }
  var failed by remember(characterId) { mutableStateOf(false) }

  // 保存してある絵を読み、足りない分や古い分だけ、ゲートウェイから取る。
  LaunchedEffect(characterId) {
    val dir = avatarCacheDir(context, characterId)
    val versions =
      runCatching {
        val items = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ComicCharacterGallery, JSONObject().put("characterId", characterId).toString(), timeoutMs = 20_000))
          .optJSONArray("items")
        buildMap<String, Long> {
          if (items != null) for (i in 0 until items.length()) items.getJSONObject(i).let { put(it.optString("key"), it.optLong("version", 0L)) }
        }
      }.getOrNull()
    if (versions == null || versions["av_normal_closed"] == null) {
      // 通信できない・まだ作っていない。保存してある絵があれば、それを使う。
      withContext(Dispatchers.IO) {
        for (key in AVATAR_FRAME_KEYS + AVATAR_EXTRA_KEYS + AVATAR_UNDRESS_KEYS) {
          val file = File(dir, "$key.png")
          if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.let { frames[key] = it }
        }
      }
      failed = frames["normal_closed"] == null
      return@LaunchedEffect
    }
    for (key in AVATAR_FRAME_KEYS + AVATAR_EXTRA_KEYS + AVATAR_UNDRESS_KEYS) {
      val file = File(dir, "$key.png")
      val stamp = File(dir, "$key.version")
      val version = versions["av_$key"] ?: continue
      val fresh = file.exists() && stamp.exists() && stamp.readText() == version.toString()
      if (!fresh) {
        runCatching {
          val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ComicCharacterImage, JSONObject().put("characterId", characterId).put("key", "av_$key").toString(), timeoutMs = 60_000))
          val bytes = Base64.decode(res.optString("imageBase64"), Base64.DEFAULT)
          withContext(Dispatchers.IO) {
            file.writeBytes(bytes)
            stamp.writeText(version.toString())
          }
        }
      }
      withContext(Dispatchers.IO) {
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.let { frames[key] = it }
      }
    }
    failed = frames["normal_closed"] == null
  }

  var mouthOpen by remember { mutableStateOf(false) }
  var blinking by remember { mutableStateOf(false) }
  // 読み上げ中は、口を開け閉めする（間隔は少しばらつかせる）
  LaunchedEffect(speaking) {
    if (!speaking) {
      mouthOpen = false
      return@LaunchedEffect
    }
    while (true) {
      mouthOpen = !mouthOpen
      delay(Random.nextLong(110, 190))
    }
  }
  // ときどきまばたきする
  LaunchedEffect(characterId) {
    while (true) {
      delay(Random.nextLong(2_500, 6_000))
      blinking = true
      delay(140)
      blinking = false
    }
  }

  if (failed || frames["normal_closed"] == null) return

  // 触られたときの反応（少しのあいだだけ、表情を変える）
  var touched by remember { mutableStateOf<Pair<AvatarTouch, String>?>(null) }
  val touchScope = androidx.compose.runtime.rememberCoroutineScope()
  // 服を脱がした段階（0 = 着ている）。キャラを変えると戻る
  var undress by remember(characterId) { mutableStateOf(0) }
  // 成人向けの箇所を続けて触ると高まる（1〜5）。間があくと戻る
  var arousal by remember(characterId) { mutableStateOf(0) }
  var lastAdultTouchAt by remember(characterId) { mutableStateOf(0L) }
  // 触れた場所を気持ちの状態に伝え、弱い場所なら高まりを上乗せする
  LaunchedEffect(AffectBus.pendingBoost) {
    val boost = AffectBus.pendingBoost
    if (boost > 0) {
      arousal = minOf(5, arousal + boost)
      AffectBus.pendingBoost = 0
    }
  }
  LaunchedEffect(touched) {
    val current = touched ?: return@LaunchedEffect
    if (current.first.face == "climax") {
      delay(2_000)
      // 潮吹きの絵があれば続けて見せる（脱いでいる途中は、顔だけの絶頂のまま）
      if (undress == 0 && frames["squirt"] != null) {
        touched = AvatarTouch("潮吹き", AvatarEmotion.AROUSED, listOf("あっ…あぁぁっ！", "でちゃう…っ！", "とまらない…っ"), true, "squirt", "tremble").let { it to it.words.random() }
        return@LaunchedEffect
      }
      delay(600)
      if (undress == 0 && frames["collapse"] != null) {
        touched = AvatarTouch("崩れ落ちる", AvatarEmotion.AROUSED, listOf("はぁ…はぁ…", "もう…立てない…"), true, "collapse", "sway").let { it to it.words.random() }
        return@LaunchedEffect
      }
    }
    if (current.first.face == "squirt") {
      delay(2_400)
      if (undress == 0 && frames["collapse"] != null) {
        touched = AvatarTouch("崩れ落ちる", AvatarEmotion.AROUSED, listOf("はぁ…はぁ…", "もう…立てない…"), true, "collapse", "sway").let { it to it.words.random() }
        return@LaunchedEffect
      }
    }
    delay(if (current.first.face == "collapse") 3_500 else 2_600)
    if (current.first.face == "collapse" || current.first.face == "climax" || current.first.face == "squirt") arousal = 0
    touched = null
  }
  // 動き（くねくね・震え・身を引く・縮こまる）。反応の間だけ、時計を進める
  val motion = touched?.first?.motion
  var clockNanos by remember { mutableStateOf(0L) }
  LaunchedEffect(motion) {
    if (motion == null) {
      clockNanos = 0L
      return@LaunchedEffect
    }
    while (true) androidx.compose.runtime.withFrameNanos { clockNanos = it }
  }
  val emotion = touched?.first?.emotion ?: remember(lastReply) { avatarEmotionOf(lastReply) }
  // 触られた反応が優先。なければ、返事の文章に合わせる（セリフと表情をそろえる）
  val extraFace = touched?.first?.face ?: remember(lastReply) { avatarExtraFaceOf(lastReply) }
  val key = avatarFrameKey(emotion, mouthOpen, blinking && !mouthOpen)
  val stageFrame = if (undress > 0) frames["un_$undress"] else null
  // 顔（または姿）の絵：追加の表情があればそれ、なければ表情と口の開き
  val faceBitmap =
    (if (extraFace != null && !((extraFace == "collapse" || extraFace == "squirt") && stageFrame != null)) avatarFaceFallbacks(extraFace).firstNotNullOfOrNull { frames[it] } else null)
      ?: frames[key] ?: frames["${emotion.key}_closed"]
      ?: (if (emotion == AvatarEmotion.AROUSED) frames["shy_closed"] else null)
      ?: frames["normal_closed"] ?: return
  // 服を脱いだ段階では、その段階の絵の上に、顔だけを重ねる（半脱ぎのままでも表情が変わる）
  val bitmap = stageFrame ?: faceBitmap

  // 呼吸のような、ごくゆっくりした上下と拡大
  val breath = rememberInfiniteTransition(label = "avatar-breath")
  val lift by breath.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing), RepeatMode.Reverse),
    label = "avatar-lift",
  )
  Box(
    // 背景は透明（絵そのものが透明なので、画面の背景がそのまま見える）
    modifier =
      modifier.fillMaxWidth().height(height).pointerInput(undress, bitmap) {
        // なぞって脱がす：向きと場所で、次の段階の絵に替わる
        var startRatio = 0f
        var dx = 0f
        var dy = 0f
        var slideZone = "chest"
        detectDragGestures(
          onDragStart = { start ->
            val boxHeight = size.height.toFloat()
            val shownHeight = minOf(boxHeight, size.width.toFloat() * bitmap.height / bitmap.width)
            startRatio = ((start.y - (boxHeight - shownHeight)) / shownHeight).coerceIn(0f, 1f)
            slideZone = touchZoneAt(startRatio)
            dx = 0f
            dy = 0f
          },
          onDrag = { change, amount ->
            change.consume()
            dx += amount.x
            dy += amount.y
          },
          onDragEnd = {
            // スライドすると、絶頂する（そのあと、潮吹き → 崩れ落ちる、へ続く）
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            if (distance >= 48.dp.toPx()) {
              arousal = 5
              touchScope.launch { AffectBus.touch(viewModel, slideZone, "slide") }
              lastAdultTouchAt = System.currentTimeMillis()
              val climax = AvatarTouch("絶頂", AvatarEmotion.AROUSED, listOf("いっ…いくぅぅっ！", "あぁぁぁっ！！", "だめっ…いっちゃう…！"), true, "climax", "tremble")
              touched = climax to climax.words.random()
            }
          },
        )
      }.pointerInput(bitmap) {
        detectTapGestures(
          onLongPress = { at ->
            val shown = minOf(size.height.toFloat(), size.width.toFloat() * bitmap.height / bitmap.width)
            touchScope.launch { AffectBus.touch(viewModel, touchZoneAt(((at.y - (size.height - shown)) / shown).coerceIn(0f, 1f)), "hold") }
            // 長押しすると、感じる（続けるほど高まる。12秒あくと最初から）
            val now = System.currentTimeMillis()
            arousal = if (now - lastAdultTouchAt < 12_000) minOf(arousal + 1, 5) else 1
            lastAdultTouchAt = now
            val step = arousalTouch(arousal)
            touched = step to step.words.random()
          },
        ) { at ->
          val shown = minOf(size.height.toFloat(), size.width.toFloat() * bitmap.height / bitmap.width)
          touchScope.launch { AffectBus.touch(viewModel, touchZoneAt(((at.y - (size.height - shown)) / shown).coerceIn(0f, 1f)), "tap") }
          // タップすると、服を1段階ずつ脱がす
          val next = undress + 1
          if (next > UNDRESS_LAST_STAGE) {
            val done = AvatarTouch("もう脱げない", AvatarEmotion.SHY, listOf("もう…なにも着てない…", "これ以上は…", "ぜんぶ見られちゃってる…"), true, "blush")
            touched = done to done.words.random()
          } else if (frames["un_$next"] != null) {
            undress = next
            val reaction = AvatarTouch("服", AvatarEmotion.SHY, listOf("あっ…", "ま、待って…", "んっ…", "は、恥ずかしい…"), true, "blush")
            touched = reaction to reaction.words.random()
          } else {
            touched = AvatarTouch("お知らせ", AvatarEmotion.NORMAL, listOf("脱衣の絵がまだありません。アバターを作り直してください"), false) to "脱衣の絵がまだありません。アバターを作り直してください"
          }
        }
      },
    contentAlignment = Alignment.BottomCenter,
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(height)
          .graphicsLayer {
            translationY = -3f * lift * density
            val grow = 1f + 0.012f * lift
            scaleX = grow
            scaleY = grow
            if (motion != null) {
              val sec = clockNanos / 1_000_000_000f
              fun wave(rate: Float, phase: Float = 0f) = kotlin.math.sin(sec * rate + phase)
              when (motion) {
                "wiggle" -> { // くねくね
                  rotationZ = 4f * wave(7f)
                  translationX = 7f * density * wave(5f, 1f)
                  scaleX = grow * (1f + 0.025f * wave(9f))
                }
                "sway" -> { // ゆらゆら
                  rotationZ = 2f * wave(4f)
                  translationX = 3f * density * wave(3f)
                }
                "tremble" -> { // 震え
                  translationX = 3.5f * density * wave(58f)
                  rotationZ = 1.8f * wave(43f)
                  translationY = translationY + 2f * density * kotlin.math.abs(wave(37f))
                }
                "recoil" -> { // 嫌がって身を引く
                  translationX = -14f * density * kotlin.math.abs(wave(8f))
                  rotationZ = -3.5f * kotlin.math.abs(wave(8f))
                }
                "wince" -> { // 痛がって縮こまる
                  scaleY = grow * (1f - 0.035f * kotlin.math.abs(wave(10f)))
                  translationY = translationY + 5f * density * kotlin.math.abs(wave(10f))
                  rotationZ = 2.5f * wave(12f)
                }
              }
            }
          },
    ) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "アバター",
        // 全身が枠に収まる大きさで出す。枠を大きくすれば、キャラも大きくなる
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomCenter,
        modifier = Modifier.fillMaxWidth().height(height),
      )
      if (stageFrame != null) {
        // 顔の部分（頭の上から約2割）だけを、表情の絵に替える。境目はぼかす
        val headFraction = 0.21f
        Image(
          bitmap = faceBitmap.asImageBitmap(),
          contentDescription = null,
          contentScale = ContentScale.Fit,
          alignment = Alignment.BottomCenter,
          modifier =
            Modifier
              .fillMaxWidth()
              .height(height)
              .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
              .drawWithContent {
                drawContent()
                val shownHeight = minOf(size.height, size.width * faceBitmap.height / faceBitmap.width)
                val top = size.height - shownHeight
                val end = top + shownHeight * headFraction
                drawRect(
                  brush =
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                      colorStops =
                        arrayOf(
                          0f to androidx.compose.ui.graphics.Color.Black,
                          (((end - shownHeight * 0.04f)) / size.height).coerceIn(0f, 1f) to androidx.compose.ui.graphics.Color.Black,
                          (end / size.height).coerceIn(0f, 1f) to androidx.compose.ui.graphics.Color.Transparent,
                          1f to androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    ),
                  blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                )
              },
        )
      }
    }
    if (speaking) {
      Text(text = "🔊", style = ClawTheme.type.caption, modifier = Modifier.align(Alignment.BottomEnd))
    }
    if (undress > 0) {
      Text(
        text = "👕 着せ直す",
        style = ClawTheme.type.caption,
        color = ClawTheme.colors.primary,
        modifier = Modifier.align(Alignment.TopEnd).clickable { undress = 0 }.padding(8.dp),
      )
    }
    touched?.let { (touch, words) ->
      Text(
        text = "「$words」",
        style = ClawTheme.type.body,
        color = if (touch.adult) ClawTheme.colors.danger else ClawTheme.colors.text,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
      )
    }
  }
}
