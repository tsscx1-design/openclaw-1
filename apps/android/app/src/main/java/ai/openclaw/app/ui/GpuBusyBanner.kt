package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

/** PC が画像・動画・顔入れ替えなどの作業中で、チャットAIを止めているときの知らせ。 */
internal data class GpuBusyNotice(val kindLabel: String, val stage: String, val elapsedSec: Int, val chatPaused: Boolean)

/** 作業の種類の日本語名。 */
internal fun mediaKindLabel(kind: String): String =
  when (kind) {
    "image" -> "画像生成"
    "video" -> "動画生成"
    "music" -> "音楽生成"
    "manga" -> "漫画生成"
    "comic" -> "キャラ・コミックの生成"
    "voice" -> "声の生成"
    "motion" -> "モーションコミック"
    "faceswap" -> "顔入れ替え"
    else -> "生成"
  }

/** サーバーの状態（media.status）から、知らせを作る。作業中でなく、チャットAIも止まっていなければ null。 */
internal fun gpuNoticeOf(status: JSONObject): GpuBusyNotice? {
  val current = status.optJSONObject("current")
  return buildGpuNotice(
    paused = status.optBoolean("chatPaused"),
    running = current != null,
    kind = current?.optString("kind").orEmpty(),
    stage = current?.optString("stage").orEmpty(),
    elapsedSec = current?.optInt("elapsedSec") ?: 0,
  )
}

/** 知らせの中身を作る（作業中でなく、チャットAIも止まっていなければ null）。 */
internal fun buildGpuNotice(paused: Boolean, running: Boolean, kind: String, stage: String, elapsedSec: Int): GpuBusyNotice? {
  if (!paused && !running) return null
  return GpuBusyNotice(kindLabel = mediaKindLabel(kind), stage = stage, elapsedSec = elapsedSec, chatPaused = paused)
}

/** 知らせの文章。 */
internal fun gpuNoticeText(notice: GpuBusyNotice): String {
  val minutes = notice.elapsedSec / 60
  val elapsed = if (notice.elapsedSec >= 60) "（${minutes}分経過）" else ""
  return if (notice.chatPaused) {
    "⚠ ${notice.kindLabel}の作業中のため、チャットAIを一時停止しています$elapsed。" +
      (if (notice.stage.isNotBlank()) "\n${notice.stage}" else "") +
      "\n終わると、自動で戻ります。それまで返事は遅れるか、返りません。"
  } else {
    "⏳ ${notice.kindLabel}を作っています$elapsed。" + (if (notice.stage.isNotBlank()) "\n${notice.stage}" else "")
  }
}

/** 作業中の知らせ。8秒ごとに PC に聞き、作業中だけ出る（つながらないときは何も出さない）。 */
@Composable
internal fun GpuBusyBanner(viewModel: MainViewModel, modifier: Modifier = Modifier) {
  var notice by remember { mutableStateOf<GpuBusyNotice?>(null) }
  LaunchedEffect(viewModel) {
    while (true) {
      notice =
        try {
          gpuNoticeOf(JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaStatus, "{}", timeoutMs = 8_000)))
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          null
        }
      delay(8_000)
    }
  }
  notice?.let {
    Column(
      modifier =
        modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(ClawTheme.colors.primary.copy(alpha = 0.14f))
          .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      Text(text = gpuNoticeText(it), style = ClawTheme.type.caption, color = ClawTheme.colors.text)
    }
  }
}
