package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 生成物の絵柄の切り替え（アニメ／実写）。全部の生成（画像・設定画・見本・漫画・モーション）に、効く。
 * 自動では判別しない。「アニメで作る」にすると、実写用のモデルを、アニメ用に替える。「実写で作る」はその逆。
 * 「指定なし」は、画面で選んだモデルをそのまま使う。選んだ値は、PC（ゲートウェイ）に、覚えておく。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GenStyleSwitch(viewModel: MainViewModel, modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  var style by remember { mutableStateOf("auto") }
  var note by remember { mutableStateOf("") }

  LaunchedEffect(Unit) {
    runCatching { style = JSONObject(viewModel.runGatewayRpc(GatewayMethod.StudioStyleGet, "{}", timeoutMs = 20_000)).optString("style", "auto").ifBlank { "auto" } }
  }

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("生成物の絵柄", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf("auto" to "指定なし", "anime" to "アニメで作る", "photo" to "実写で作る").forEach { (id, label) ->
        ClawSecondaryButton(
          text = if (style == id) "● $label" else label,
          onClick = {
            val before = style
            style = id
            scope.launch {
              runCatching { viewModel.runGatewayRpc(GatewayMethod.StudioStyleSet, JSONObject().put("style", id).toString(), timeoutMs = 20_000) }
                .onSuccess { note = "" }
                .onFailure { style = before; note = "切り替えられませんでした：${it.message}" }
            }
          },
        )
      }
    }
    Text(
      when (style) {
        "anime" -> "すべての生成を、アニメの絵にします（実写用のモデルは、アニメ用に替えます）。"
        "photo" -> "すべての生成を、実写の写真にします（アニメ用のモデルは、実写用に替えます）。"
        else -> "画面で選んだモデルのまま、作ります。"
      },
      style = ClawTheme.type.captionSmall,
      color = ClawTheme.colors.textMuted,
    )
    if (note.isNotBlank()) Text(note, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
  }
}
