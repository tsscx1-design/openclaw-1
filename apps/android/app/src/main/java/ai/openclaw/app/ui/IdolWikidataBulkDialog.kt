package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 名簿の空の項目を、Wikidata（誰でも自由に使える公開データ）で補う。2段階：①「調べる」（何も書き込まない）
 * ②結果を見てから「反映する」。名前が合い、Wikidata の記録でも同じグループの人だけを使い、いまの値は上書きしない。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdolWikidataBulkDialog(
  viewModel: MainViewModel,
  groups: List<String>,
  onDismiss: () -> Unit,
  /** 反映できた（画面に出す一言）。 */
  onApplied: (String) -> Unit,
  /** 最初から選んでおくグループ（グループの画面から開いたとき）。 */
  initialGroup: String? = null,
) {
  val scope = rememberCoroutineScope()
  var groupChoice by remember { mutableStateOf<String?>(initialGroup) }
  var state by remember { mutableStateOf<JSONObject?>(null) }
  var running by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }

  suspend fun rpc(method: GatewayMethod, params: JSONObject = JSONObject()): JSONObject =
    JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = 120_000))

  suspend fun refresh() {
    runCatching {
      val s = rpc(GatewayMethod.IdolMasterWikidataBulkStatus).getJSONObject("state")
      state = s
      running = s.optString("status") == "running"
    }.onFailure { message = "状況を読めませんでした：${it.message}" }
  }

  LaunchedEffect(Unit) { refresh() }
  LaunchedEffect(running) {
    while (running) {
      delay(2000)
      refresh()
    }
  }

  val status = state?.optString("status").orEmpty()
  val applied = state?.optJSONObject("applied")
  val withNew = state?.optInt("withNew") ?: 0

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("名簿を Wikidata で補う") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "2段階です。①「調べる」（何も書き込みません）→ 結果を見る → ②「反映する」。" +
            "名前が合い、Wikidata の記録でも、同じグループの人だけを使います。いまの値は上書きせず、空の項目だけを埋めます。" +
            "反映の前に、名簿を自動でバックアップし、出典（Wikidata の番号）を残します。人数が多いと、10分以上かかります。",
          style = ClawTheme.type.captionSmall,
          color = ClawTheme.colors.textMuted,
        )
        if (status != "running") {
          Text("対象", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ClawSecondaryButton(text = if (groupChoice == null) "● 全部" else "全部", onClick = { groupChoice = null })
            groups.forEach { g -> ClawSecondaryButton(text = if (groupChoice == g) "● $g" else g, onClick = { groupChoice = g }) }
          }
        }
        if (status == "running" || status == "done" || status == "cancelled" || status == "error") {
          val total = state?.optInt("total") ?: 0
          Text(
            "${state?.optInt("done") ?: 0} / $total 人を調べました　Wikidata で見つかった人：${state?.optInt("found") ?: 0}人　補える人：${withNew}人",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.text,
          )
          val counts = state?.optJSONObject("fieldCounts")
          if (counts != null && counts.length() > 0) {
            val line = counts.keys().asSequence().toList().joinToString("　") { "$it ${counts.optInt(it)}人" }
            Text("補える項目：$line", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          }
          val unmatched = state?.optJSONArray("unmatched")?.length() ?: 0
          if (status != "running") Text("見つからなかった人：${unmatched}人（Wikidata に、まだ項目がない人は、そのままです）", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          val errors = state?.optInt("errors") ?: 0
          if (errors > 0) Text("読めなかった回数：$errors", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          if (status == "error") Text(state?.optString("error").orEmpty(), style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
          if (status == "cancelled") Text("途中で止めました。ここまでの結果を、反映することもできます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        }
        if (applied != null) {
          Text("反映しました：置き換え ${applied.optInt("updated")}人、追加 ${applied.optInt("added")}人。元の名簿は、自動でバックアップしました。", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
        }
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    },
    confirmButton = {
      Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
        if (status == "running") {
          TextButton(onClick = { scope.launch { runCatching { rpc(GatewayMethod.IdolMasterWikidataBulkCancel) }; refresh() } }) { Text("とめる") }
        } else {
          TextButton(enabled = !busy, onClick = {
            busy = true
            message = ""
            scope.launch {
              runCatching { rpc(GatewayMethod.IdolMasterWikidataBulkStart, JSONObject().apply { groupChoice?.let { put("group", it) } }) }
                .onSuccess { refresh() }
                .onFailure { message = "始められませんでした：${it.message}" }
              busy = false
            }
          }) { Text(if (status == "done" || status == "cancelled" || status == "error") "もう一度、調べる" else "調べる") }
        }
        if ((status == "done" || status == "cancelled") && withNew > 0 && applied == null) {
          TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
              runCatching { rpc(GatewayMethod.IdolMasterWikidataBulkApply) }
                .onSuccess { res ->
                  refresh()
                  onApplied("名簿を Wikidata で補いました（置き換え ${res.optInt("updated")}人）")
                }
                .onFailure { message = "反映できませんでした：${it.message}" }
              busy = false
            }
          }) { Text("反映する（${withNew}人）") }
        }
      }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("閉じる") } },
  )
}
