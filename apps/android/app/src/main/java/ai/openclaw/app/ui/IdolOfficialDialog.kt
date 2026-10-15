package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * グループの公式サイト（いまは AKB48）から、名簿の空の項目と、写真のない人の写真を取り込む。
 * ①注意書きに同意 → ②「調べる」（何も書き込まない）→ ③結果を見て「反映する」。
 * 公式サイトのプロフィールや写真は運営の著作物なので、この端末の中で、個人で使う範囲に限る。
 */
@Composable
internal fun IdolOfficialDialog(
  viewModel: MainViewModel,
  group: String = "AKB48",
  onDismiss: () -> Unit,
  /** 反映できた（画面に出す一言）。 */
  onApplied: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  var agreed by remember { mutableStateOf(false) }
  var state by remember { mutableStateOf<JSONObject?>(null) }
  var running by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }

  suspend fun rpc(method: GatewayMethod, params: JSONObject = JSONObject()): JSONObject =
    JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = 300_000))

  suspend fun refresh() {
    runCatching {
      val s = rpc(GatewayMethod.IdolMasterOfficialStatus).getJSONObject("state")
      state = s
      running = s.optString("status") == "running"
    }.onFailure { message = "状況を読めませんでした：${it.message}" }
  }

  LaunchedEffect(running) {
    while (running) {
      delay(2000)
      refresh()
    }
  }

  if (!agreed) {
    AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("公式サイトから取り込む前に") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "$group の公式サイトのプロフィール（誕生日・期・愛称・出身地・事務所）と写真は、運営の著作物です。",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.text,
          )
          Text(
            "・取り込んだ内容は、この PC とあなたの端末の中だけで、個人で使う範囲にとどめてください。\n" +
              "・他の人への配布や、ネットへの公開は、しないでください。\n" +
              "・公開されているページを、1件ずつ、間を空けて読みます（約50ページ）。ログインや制限の回避は、していません。\n" +
              "・すでにある値と、あなたが入れた写真は、上書きしません。\n" +
              "・サイトの利用規約は、あなた自身で、確認してください。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textMuted,
          )
          Text("同意すると、まず「調べる」だけを行います（何も書き込みません）。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        }
      },
      confirmButton = {
        TextButton(onClick = {
          agreed = true
          scope.launch {
            runCatching { rpc(GatewayMethod.IdolMasterOfficialStart, JSONObject().put("group", group)) }
              .onSuccess { refresh() }
              .onFailure { message = "始められませんでした：${it.message}" }
          }
        }) { Text("同意して調べる") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
    return
  }

  val status = state?.optString("status").orEmpty()
  val applied = state?.optJSONObject("applied")
  val withNew = state?.optInt("withNew") ?: 0
  val photos = state?.optInt("photos") ?: 0

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("$group を公式サイトから取り込む") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status == "running" || status == "done" || status == "cancelled" || status == "error") {
          Text(
            "${state?.optInt("done") ?: 0} / ${state?.optInt("total") ?: 0} 人を調べました　公式サイトで見つかった人：${state?.optInt("found") ?: 0}人　補える人：${withNew}人",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.text,
          )
          val counts = state?.optJSONObject("fieldCounts")
          if (counts != null && counts.length() > 0) {
            val line = counts.keys().asSequence().toList().joinToString("　") { "$it ${counts.optInt(it)}人" }
            Text("補える項目：$line", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          }
          if (status != "running") {
            Text("写真を保存できる人：${photos}人（写真のない人だけ）", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            val unmatched = state?.optJSONArray("unmatched")?.length() ?: 0
            Text("公式の一覧に見つからなかった人：${unmatched}人（卒業・名前の表記違いなど。そのままです）", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          }
          val errors = state?.optInt("errors") ?: 0
          if (errors > 0) Text("読めなかった回数：$errors", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          if (status == "error") Text(state?.optString("error").orEmpty(), style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
          if (status == "cancelled") Text("途中で止めました。ここまでの結果を、反映することもできます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        } else {
          Text("準備中…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        }
        if (applied != null) {
          Text(
            "反映しました：名簿 ${applied.optInt("updated")}人、写真 ${applied.optInt("photosSaved")}枚。元の名簿は、自動でバックアップしました。",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.primary,
          )
        }
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    },
    confirmButton = {
      Column(horizontalAlignment = Alignment.End) {
        if (status == "running") {
          TextButton(onClick = { scope.launch { runCatching { rpc(GatewayMethod.IdolMasterOfficialCancel) }; refresh() } }) { Text("とめる") }
        } else if ((status == "done" || status == "cancelled") && withNew > 0 && applied == null) {
          TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
              runCatching { rpc(GatewayMethod.IdolMasterOfficialApply, JSONObject().put("savePhotos", true)) }
                .onSuccess { res ->
                  refresh()
                  onApplied("$group の名簿を公式サイトで補いました（名簿 ${res.optInt("updated")}人、写真 ${res.optInt("photosSaved")}枚）")
                }
                .onFailure { message = "反映できませんでした：${it.message}" }
              busy = false
            }
          }) { Text("反映する（${withNew}人${if (photos > 0) "・写真${photos}枚" else ""}）") }
          TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
              runCatching { rpc(GatewayMethod.IdolMasterOfficialApply, JSONObject().put("savePhotos", false)) }
                .onSuccess { res ->
                  refresh()
                  onApplied("$group の名簿を公式サイトで補いました（名簿 ${res.optInt("updated")}人、写真なし）")
                }
                .onFailure { message = "反映できませんでした：${it.message}" }
              busy = false
            }
          }) { Text("写真は保存せず、名簿だけ反映する") }
        }
      }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("閉じる") } },
  )
}
