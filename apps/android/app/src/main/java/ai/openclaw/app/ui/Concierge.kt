package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject

/** 画面の移動の合図（コンシェルジュが「連れて行く」とき）。ShellScreen が受け取って開く。 */
internal object ConciergeBus {
  /** "route:<SettingsRoute>" / "deep:<id>" / "tab:<Tab>"。受け取ったら null に戻る。 */
  var pendingGo by mutableStateOf<String?>(null)
}

private const val PREFS = "concierge"

private class ConciergePrefs(context: Context) {
  private val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  var shown by mutableStateOf(p.getBoolean("shown", true))
  var walk by mutableStateOf(p.getBoolean("walk", true))
  var characterId by mutableStateOf(p.getString("characterId", "") ?: "")
  var x by mutableStateOf(p.getFloat("x", 0.78f))
  var y by mutableStateOf(p.getFloat("y", 0.70f))
  fun save() {
    p.edit().putBoolean("shown", shown).putBoolean("walk", walk).putString("characterId", characterId).putFloat("x", x).putFloat("y", y).apply()
  }
}

private val MINI_KEYS = listOf("mini_front", "mini_front34", "mini_side", "mini_back34", "mini_back", "mini_happy")

private fun miniCacheDir(context: Context, characterId: String): File = File(context.filesDir, "mini/$characterId").also { it.mkdirs() }

/** ミニキャラの絵（正面・斜め前・横・斜め後ろ・後ろ・手を振る）。なければ、アバターの通常の絵で代用する。 */
@Composable
private fun rememberMiniFrames(viewModel: MainViewModel, characterId: String): Pair<Map<String, Bitmap>, Boolean> {
  val context = LocalContext.current
  val frames = remember(characterId) { mutableStateMapOf<String, Bitmap>() }
  var isMini by remember(characterId) { mutableStateOf(false) }
  LaunchedEffect(characterId) {
    if (characterId.isBlank()) return@LaunchedEffect
    val dir = miniCacheDir(context, characterId)
    withContext(Dispatchers.IO) {
      for (key in MINI_KEYS) {
        val file = File(dir, "$key.png")
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.let { frames[key] = it }
      }
    }
    val versions =
      runCatching {
        val items = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ComicCharacterGallery, JSONObject().put("characterId", characterId).toString(), timeoutMs = 20_000)).optJSONArray("items")
        buildMap<String, Long> { if (items != null) for (i in 0 until items.length()) items.getJSONObject(i).let { put(it.optString("key"), it.optLong("version", 0L)) } }
      }.getOrNull()
    if (versions != null) {
      for (key in MINI_KEYS) {
        val version = versions[key] ?: continue
        val file = File(dir, "$key.png")
        val stamp = File(dir, "$key.version")
        val fresh = file.exists() && stamp.exists() && stamp.readText() == version.toString()
        if (fresh) continue
        runCatching {
          val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ComicCharacterImage, JSONObject().put("characterId", characterId).put("key", key).toString(), timeoutMs = 60_000))
          val bytes = Base64.decode(res.optString("imageBase64"), Base64.DEFAULT)
          withContext(Dispatchers.IO) {
            file.writeBytes(bytes)
            stamp.writeText(version.toString())
            BitmapFactory.decodeFile(file.absolutePath)?.let { frames[key] = it }
          }
        }
      }
      // ミニキャラがなければ、アバターの通常の絵（あれば）か設定画を、正面として使う
      if (frames["mini_front"] == null) {
        for (key in listOf("av_normal_closed", "sheet")) {
          if (versions[key] == null) continue
          runCatching {
            val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ComicCharacterImage, JSONObject().put("characterId", characterId).put("key", key).toString(), timeoutMs = 60_000))
            val bytes = Base64.decode(res.optString("imageBase64"), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { frames["mini_front"] = it }
          }
          if (frames["mini_front"] != null) break
        }
      }
    }
    isMini = frames["mini_side"] != null
  }
  return frames to isMini
}

/** 向き（画面の動きの角度）から、使う絵と、左右を反転するかを決める。8方向。 */
private fun frameFor(dx: Float, dy: Float, frames: Map<String, Bitmap>): Pair<Bitmap?, Boolean> {
  val deg = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0
  val sector = (((deg + 22.5) % 360.0) / 45.0).toInt() // 0 右, 1 右下, 2 下, 3 左下, 4 左, 5 左上, 6 上, 7 右上
  val (key, flip) =
    when (sector) {
      0 -> "mini_side" to false
      1 -> "mini_front34" to false
      2 -> "mini_front" to false
      3 -> "mini_front34" to true
      4 -> "mini_side" to true
      5 -> "mini_back34" to true
      6 -> "mini_back" to false
      else -> "mini_back34" to false
    }
  val bitmap = frames[key] ?: frames["mini_front"] ?: frames.values.firstOrNull()
  return bitmap to (flip && frames[key] != null)
}

private data class ChatLine(val fromUser: Boolean, val text: String, val go: String = "", val goLabel: String = "")

/**
 * 画面の中を歩いているミニキャラ（コンシェルジュ）。タップで質問、長押しで好きな場所に置ける。
 * 上へ歩くと後ろ姿、下へ歩くと前姿、斜めは斜めの姿になる。歩くのは入・切できる。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConciergeOverlay(viewModel: MainViewModel) {
  val context = LocalContext.current
  val prefs = remember { ConciergePrefs(context) }
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  // ミニキャラにするキャラ：選んだもの、なければ「クリエ」
  var libraryChars by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  LaunchedEffect(Unit) {
    runCatching {
      val arr = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ComicCharacterList, "{}", timeoutMs = 20_000)).optJSONArray("characters") ?: JSONArray()
      libraryChars = (0 until arr.length()).map { arr.getJSONObject(it).let { c -> c.optString("id") to c.optString("name") } }
    }
  }
  val characterId = prefs.characterId.ifBlank { libraryChars.firstOrNull { it.second == "クリエ" }?.first.orEmpty() }
  val (frames, hasMini) = rememberMiniFrames(viewModel, characterId)

  if (!prefs.shown) {
    // 隠している間は、小さな呼び出しボタンだけ
    Box(modifier = Modifier.fillMaxSize()) {
      Box(
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 92.dp).size(28.dp).clip(RoundedCornerShape(14.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.8f)).clickable { prefs.shown = true; prefs.save() },
        contentAlignment = Alignment.Center,
      ) { Text("🧚", fontSize = 14.sp) }
    }
    return
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val sprite = 76.dp
    val areaW = maxWidth
    val areaH = maxHeight
    val minX = 4f
    val maxX = (areaW - sprite).value - 4f
    val minY = 56f
    val maxY = (areaH - sprite).value - 76f
    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(areaW, areaH) {
      if (!placed && maxX > minX && maxY > minY) {
        animX.snapTo((minX + (maxX - minX) * prefs.x).coerceIn(minX, maxX))
        animY.snapTo((minY + (maxY - minY) * prefs.y).coerceIn(minY, maxY))
        placed = true
      }
    }
    var heading by remember { mutableStateOf(0f to 1f) }
    var walking by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }

    // 歩き回る：ときどき行き先を決めて、まっすぐ歩く（斜めも）。話しているとき・持っているときは止まる
    LaunchedEffect(prefs.walk, open, dragging, placed, maxX, maxY) {
      if (!prefs.walk || open || dragging || !placed || maxX <= minX || maxY <= minY) {
        walking = false
        return@LaunchedEffect
      }
      while (true) {
        delay(Random.nextLong(2_500, 7_000))
        val tx = Random.nextFloat() * (maxX - minX) + minX
        val ty = Random.nextFloat() * (maxY - minY) + minY
        val dx = tx - animX.value
        val dy = ty - animY.value
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < 30f) continue
        heading = dx to dy
        walking = true
        val millis = (distance / 70f * 1000f).toInt().coerceAtLeast(400)
        coroutineScope {
          launch { animX.animateTo(tx, tween(millis, easing = LinearEasing)) }
          launch { animY.animateTo(ty, tween(millis, easing = LinearEasing)) }
        }
        walking = false
        prefs.x = ((animX.value - minX) / (maxX - minX)).coerceIn(0f, 1f)
        prefs.y = ((animY.value - minY) / (maxY - minY)).coerceIn(0f, 1f)
        prefs.save()
      }
    }

    // 歩くときのゆらゆら（足を運んでいるように見せる）
    val transition = rememberInfiniteTransition(label = "concierge-walk")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(460, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val swing = if (walking) sin(phase * 2f * PI.toFloat()) * 7f else 0f
    val hop = if (walking) -abs(sin(phase * 2f * PI.toFloat())) * 5f else sin(phase * 2f * PI.toFloat()) * 0.8f

    val (picture, flip) = if (open && frames["mini_happy"] != null) frames["mini_happy"] to false else frameFor(heading.first, heading.second, frames)
    Box(
      modifier =
        Modifier
          .offset(x = animX.value.dp, y = animY.value.dp)
          .size(sprite)
          .graphicsLayer {
            rotationZ = swing
            translationY = hop * density.density
            scaleX = if (flip) -1f else 1f
          }
          .pointerInput(Unit) { detectTapGestures(onTap = { open = !open }) }
          .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
              onDragStart = { dragging = true; open = false },
              onDrag = { change, amount ->
                change.consume()
                scope.launch {
                  animX.snapTo((animX.value + amount.x / density.density).coerceIn(minX, maxX))
                  animY.snapTo((animY.value + amount.y / density.density).coerceIn(minY, maxY))
                }
              },
              onDragEnd = {
                dragging = false
                prefs.x = ((animX.value - minX) / (maxX - minX)).coerceIn(0f, 1f)
                prefs.y = ((animY.value - minY) / (maxY - minY)).coerceIn(0f, 1f)
                prefs.save()
              },
              onDragCancel = { dragging = false },
            )
          },
      contentAlignment = Alignment.BottomCenter,
    ) {
      if (picture != null) {
        Image(bitmap = picture.asImageBitmap(), contentDescription = "コンシェルジュ", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
      } else {
        Text("🧚", fontSize = 40.sp)
      }
      if (dragging) Text("ここ？", fontSize = 11.sp, color = ClawTheme.colors.primary, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (open) {
      val panelWidth = minOf(areaW - 16.dp, 340.dp)
      val below = animY.value < (areaH.value / 2f)
      val panelX = (animX.value.dp + sprite / 2 - panelWidth / 2).coerceIn(8.dp, areaW - panelWidth - 8.dp)
      ConciergePanel(
        viewModel = viewModel,
        prefs = prefs,
        hasMini = hasMini,
        characterId = characterId,
        libraryChars = libraryChars,
        width = panelWidth,
        modifier = Modifier.offset(x = panelX, y = if (below) (animY.value.dp + sprite + 4.dp) else (animY.value.dp - 330.dp).coerceAtLeast(8.dp)),
        onClose = { open = false },
      )
    }
  }
}

private operator fun Dp.div(value: Int): Dp = this / value.toFloat()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConciergePanel(
  viewModel: MainViewModel,
  prefs: ConciergePrefs,
  hasMini: Boolean,
  characterId: String,
  libraryChars: List<Pair<String, String>>,
  width: Dp,
  modifier: Modifier,
  onClose: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val lines = remember { mutableStateListOf(ChatLine(false, "こんにちは！アプリのことなら何でも聞いてね。使い方、設定の場所、困ったこと…そこまで案内もするよ。")) }
  var input by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var picking by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  LaunchedEffect(lines.size) { listState.animateScrollToItem(maxOf(0, lines.size - 1)) }

  fun ask(text: String) {
    if (text.isBlank() || busy) return
    lines.add(ChatLine(true, text))
    input = ""
    busy = true
    scope.launch {
      val history = JSONArray(lines.takeLast(7).dropLast(1).map { JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("text", it.text) })
      val answer =
        runCatching { JSONObject(viewModel.runGatewayRpc(GatewayMethod.ConciergeAsk, JSONObject().put("message", text).put("history", history).toString(), timeoutMs = 240_000)) }
          .fold(
            { ChatLine(false, it.optString("reply").ifBlank { "うまく答えられなかったよ。" }, it.optString("go"), it.optString("goLabel")) },
            { ChatLine(false, "ごめんね、いま答えられないみたい（${it.message}）。PCとつながっているか確かめてね。") },
          )
      lines.add(answer)
      busy = false
    }
  }

  Column(
    modifier = modifier.width(width).clip(RoundedCornerShape(16.dp)).background(ClawTheme.colors.surface).border(1.dp, ClawTheme.colors.border, RoundedCornerShape(16.dp)).padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(text = "🧚 コンシェルジュ", style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
      Text(text = if (prefs.walk) "🚶 歩く：入" else "🧍 歩く：切", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary, modifier = Modifier.clickable { prefs.walk = !prefs.walk; prefs.save() }.padding(4.dp))
      Text(text = "✕", fontSize = 14.sp, color = ClawTheme.colors.textMuted, modifier = Modifier.clickable { onClose() }.padding(4.dp))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      items(lines) { line ->
        Column(horizontalAlignment = if (line.fromUser) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
          Text(
            text = line.text,
            style = ClawTheme.type.caption.copy(fontSize = 13.sp),
            color = ClawTheme.colors.text,
            modifier = Modifier.widthIn(max = width - 40.dp).clip(RoundedCornerShape(10.dp)).background(if (line.fromUser) ClawTheme.colors.primary.copy(alpha = 0.18f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.7f)).padding(horizontal = 9.dp, vertical = 6.dp),
          )
          if (line.go.isNotBlank()) {
            Text(
              text = "▶ ${line.goLabel.ifBlank { "そこへ行く" }}",
              style = ClawTheme.type.caption.copy(fontSize = 13.sp),
              color = ClawTheme.colors.primary,
              modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.primary.copy(alpha = 0.12f)).clickable { ConciergeBus.pendingGo = line.go; onClose() }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }
      if (busy) item { Text("考えているよ…", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      listOf("使い方を教えて", "設定はどこ？", "生成のやり方", "困っている").forEach { q ->
        Text(text = q, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.text, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.7f)).clickable { ask(q) }.padding(horizontal = 8.dp, vertical = 5.dp))
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      ClawTextField(value = input, onValueChange = { input = it }, placeholder = "聞きたいことを入力", modifier = Modifier.weight(1f), maxLines = 3)
      ClawPrimaryButton(text = "送る", onClick = { ask(input) }, enabled = !busy && input.isNotBlank())
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Text(text = "キャラを変える", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { picking = !picking }.padding(horizontal = 6.dp, vertical = 4.dp))
      Text(text = "隠す", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { prefs.shown = false; prefs.save(); onClose() }.padding(horizontal = 6.dp, vertical = 4.dp))
      if (!hasMini && characterId.isNotBlank()) {
        Text(
          text = "ミニキャラの絵を作る（約3分）",
          style = ClawTheme.type.captionSmall,
          color = ClawTheme.colors.primary,
          modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
            scope.launch {
              message = runCatching {
                viewModel.runGatewayRpc(GatewayMethod.ComicCharacterMini, JSONObject().put("characterId", characterId).toString(), timeoutMs = 30_000)
              }.fold({ "作り始めたよ。通知バーで進み具合が見られるよ。" }, { "作れなかった：${it.message}（先に設定画が要るよ）" })
            }
          }.padding(horizontal = 6.dp, vertical = 4.dp),
        )
      }
    }
    if (message.isNotBlank()) Text(text = message, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
    if (picking) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(text = "クリエ（標準）", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.text, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.7f)).clickable { prefs.characterId = ""; prefs.save(); picking = false }.padding(horizontal = 8.dp, vertical = 5.dp))
        libraryChars.forEach { (id, name) ->
          Text(text = name, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.text, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.7f)).clickable { prefs.characterId = id; prefs.save(); picking = false }.padding(horizontal = 8.dp, vertical = 5.dp))
        }
      }
    }
  }
}
