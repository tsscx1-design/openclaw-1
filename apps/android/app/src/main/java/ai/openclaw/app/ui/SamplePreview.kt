package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** サンプル画像の保存の鍵。クリエの作例は「crie2_<モデル>」「crie2_pose_<体位>」（比較画面と同じ）。 */
internal fun modelSampleKey(modelId: String, adult: Boolean = false): String =
  if (adult) "crie2_adult_$modelId" else "crie2_$modelId"

internal fun poseSampleKey(poseId: String): String = "crie2_pose_$poseId"

/**
 * モデルや体位のサンプル。すでに作ってあれば、その場で見せる（比較画面と同じ保存場所）。
 * 無ければ「サンプルを作る」ボタンで、クリエの見た目で1枚作って保存する（約30秒〜2分）。
 */
@Composable
internal fun SamplePreview(
  viewModel: MainViewModel,
  key: String,
  title: String,
  prompt: String,
  modelId: String,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var bitmap by remember(key) { mutableStateOf<Bitmap?>(null) }
  var working by remember(key) { mutableStateOf(false) }
  var message by remember(key) { mutableStateOf("") }

  LaunchedEffect(key) { bitmap = withContext(Dispatchers.IO) { loadSampleBitmap(context, key) } }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    val shown = bitmap
    if (shown != null) {
      Image(
        bitmap = shown.asImageBitmap(),
        contentDescription = "$title のサンプル",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(12.dp)),
      )
      Text("$title のサンプル（クリエの見た目で作例）", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    } else {
      Text("$title：まだサンプルがありません", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    }
    ClawSecondaryButton(
      text = if (working) "⏳ サンプルを作っています…" else if (shown == null) "🖼 サンプルを作る" else "🔄 サンプルを作り直す",
      enabled = !working,
      onClick = {
        scope.launch {
          working = true
          message = ""
          runCatching { generateSampleBitmap(viewModel, prompt, modelId) }
            .onSuccess { made ->
              if (made != null) {
                saveSampleBitmap(context, key, made)
                bitmap = made
              } else {
                message = "サンプルができませんでした"
              }
            }
            .onFailure { if (it is CancellationException) throw it else message = "エラー: ${it.message}" }
          working = false
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )
    if (message.isNotBlank()) Text(message, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
  }
}

/** 1枚だけ作って待ち、できた絵を返す（最大8分待つ）。 */
internal suspend fun generateSampleBitmap(viewModel: MainViewModel, prompt: String, modelId: String): Bitmap? {
  val params = JSONObject().put("kind", "image").put("prompt", prompt).put("imageModel", modelId).put("aspect", "portrait").put("fast", true)
  val started = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaGenerate, params.toString(), timeoutMs = 30_000))
  val jobId = started.optString("jobId").takeIf { it.isNotBlank() } ?: return null
  repeat(120) {
    delay(4_000)
    val state = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobGet, JSONObject().put("jobId", jobId).toString(), timeoutMs = 10_000))
    when (state.optString("status")) {
      "done" -> {
        val result = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobResult, JSONObject().put("jobId", jobId).toString(), timeoutMs = 30_000))
        val bytes = Base64.decode(result.optString("base64"), Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      }
      "error" -> throw IllegalStateException(state.optString("error", "エラー"))
    }
  }
  return null
}
