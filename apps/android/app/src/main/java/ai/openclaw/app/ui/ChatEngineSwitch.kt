package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class ChatEngineState(
  val selected: String = "gemma",
  val running: String = "none",
  val switching: Boolean = false,
  val switchingTo: String = "",
  val elapsedSec: Int = 0,
  val error: String = "",
)

private fun engineLabel(id: String): String = if (id == "strata") "Strata" else "Gemma"

/**
 * 会話に使うモデルの切替（Gemma / Strata）。「オンライン」の表示の横に置く。
 * 押すと確認が出て、ゲートウェイが裏で切り替える（Strata は起動に約3分）。切替中は進み具合を出す。
 */
@Composable
internal fun ChatEngineSwitch(viewModel: MainViewModel, connected: Boolean) {
  val scope = rememberCoroutineScope()
  var state by remember { mutableStateOf(ChatEngineState()) }
  var ask by remember { mutableStateOf<String?>(null) }
  var message by remember { mutableStateOf("") }

  suspend fun refresh() {
    runCatching {
      val o = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ChatEngineStatus, "{}", timeoutMs = 10_000))
      state =
        ChatEngineState(
          selected = o.optString("selected", "gemma"),
          running = o.optString("running", "none"),
          switching = o.optBoolean("switching", false),
          switchingTo = o.optString("switchingTo", ""),
          elapsedSec = o.optInt("elapsedSec", 0),
          error = o.optString("error", ""),
        )
    }
  }

  LaunchedEffect(connected) {
    if (!connected) return@LaunchedEffect
    while (true) {
      refresh()
      delay(if (state.switching) 3_000 else 15_000)
    }
  }

  // 実際に動いているモデルを強調する（切替中は、行き先の側を強調）。
  val active = if (state.switching) state.switchingTo else if (state.running == "gemma" || state.running == "strata") state.running else state.selected
  val loading = state.switching || (state.running == "none" && connected)

  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
      for (id in listOf("gemma", "strata")) {
        val on = id == active
        Text(
          text = engineLabel(id),
          style = ClawTheme.type.caption,
          color = if (on) ClawTheme.colors.accent else ClawTheme.colors.textMuted,
          modifier =
            Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(if (on) ClawTheme.colors.accentSoft else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
              .clickable(enabled = connected && !state.switching && !on) { ask = id }
              .heightIn(min = 32.dp)
              .padding(horizontal = 10.dp, vertical = 7.dp),
        )
      }
    }
    val note =
      when {
        state.switching -> "切替中… ${state.elapsedSec}秒"
        state.error.isNotBlank() -> state.error
        message.isNotBlank() -> message
        loading -> "起動待ち"
        else -> ""
      }
    if (note.isNotBlank()) Text(note, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
  }

  val target = ask
  if (target != null) {
    AlertDialog(
      onDismissRequest = { ask = null },
      title = { Text("会話のモデルを ${engineLabel(target)} に切り替えますか？") },
      text = {
        Text(
          if (target == "strata") {
            "Strata（125B）は起動に約3分かかり、その間は会話が使えません。Gemma は止まります。画像や動画を作っている間は切り替えられません。"
          } else {
            "Gemma に戻します。Strata は止まります。起動に1〜2分かかり、その間は会話が使えません。"
          },
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            ask = null
            scope.launch {
              message = ""
              runCatching {
                val o = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ChatEngineSwitch, JSONObject().put("engine", target).toString(), timeoutMs = 30_000))
                if (!o.optBoolean("started", false)) message = o.optString("reason", "切り替えられませんでした")
              }.onFailure { message = "切り替えを送れませんでした（ゲートウェイが古いかもしれません）" }
              refresh()
            }
          },
        ) { Text("切り替える") }
      },
      dismissButton = { TextButton(onClick = { ask = null }) { Text("やめる") } },
    )
  }
}
