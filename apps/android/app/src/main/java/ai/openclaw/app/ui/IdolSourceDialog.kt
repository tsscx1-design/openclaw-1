package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * グループの情報元（メンバー一覧のページの URL）から、名簿の空の項目・写真・名簿にいない人を取り込む。
 * ①URL を貼る＋注意書きを読んで同意する → ②「取り込みを待機に入れる」を押す（ここで終わり。次の作業に移れる）。
 * 取り込みは、サーバーで1件ずつ順番に、自動で行う（調べる → 反映する）。進み具合と結果は「取り込みの状況」で見られる。
 * ページの作り（同じ形の枠の繰り返し）で読む。読めないときだけ、会話のモデル（Gemma）が文章から読む（確認が必要）。
 * ボット対策のあるページは、読まない。
 */
@Composable
internal fun IdolSourceDialog(
  viewModel: MainViewModel,
  group: String,
  /** 公式サイトのボタンから開いたときの URL（空なら、前に入れた URL を出す）。 */
  initialUrl: String = "",
  onDismiss: () -> Unit,
  /** 待機に入れた（画面に出す一言）。 */
  onApplied: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var url by remember { mutableStateOf(initialUrl) }
  var message by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var addMissing by remember { mutableStateOf(true) }
  var savePhotos by remember { mutableStateOf(true) }

  suspend fun rpc(method: GatewayMethod, params: JSONObject = JSONObject()): JSONObject =
    JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = 60_000))

  // 前に入れた URL があれば出す
  LaunchedEffect(group) {
    runCatching {
      val list = rpc(GatewayMethod.IdolMasterSourceList).optJSONArray("sources")
      val saved = (0 until (list?.length() ?: 0)).map { list!!.getJSONObject(it) }.filter { it.optString("group") == group }.map { it.optString("url") }
      if (saved.isNotEmpty() && initialUrl.isBlank()) url = saved.joinToString("\n")
    }
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("情報元から取り込む（$group）") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("「$group」のメンバー一覧のページの URL を、貼ってください（ブラウザでそのページを開いて、アドレスをコピーします）。ページが複数あるときは、1行に1つ、何個でも入れられます。", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        ClawTextField(value = url, onValueChange = { url = it }, placeholder = "https://…", label = "メンバー一覧のページの URL（複数は、1行に1つ）", modifier = Modifier.fillMaxWidth())
        ClawSecondaryButton(text = "📋 コピーした URL を貼り付ける", onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()?.let { url = it } }, modifier = Modifier.fillMaxWidth())
        Text(
          "【確認と注意】\n" +
            "・ページの文章・プロフィール・写真は、そのサイトの運営の著作物です。取り込んだ内容は、個人で使う範囲にとどめてください。他の人への配布や、ネットへの公開は、しないでください。\n" +
            "・公開されているページを、1件ずつ、間を空けて読みます。ボット対策のあるページは、読めません（避けることは、しません）。\n" +
            "・すでにある値と、あなたが入れた写真は、上書きしません。反映の前に、名簿を自動でバックアップします。\n" +
            "・ページの作りから読めないときは、会話のモデル（AI）が文章から読みます。その場合は、名前や日付を、あとで確認してください。\n" +
            "・サイトの利用規約は、あなた自身で、確認してください。",
          style = ClawTheme.type.captionSmall,
          color = ClawTheme.colors.textMuted,
        )
        ClawSecondaryButton(text = if (addMissing) "● 名簿にいない人も、追加する" else "○ 名簿にいない人も、追加する", onClick = { addMissing = !addMissing }, modifier = Modifier.fillMaxWidth())
        ClawSecondaryButton(text = if (savePhotos) "● 写真も保存する（個人利用）" else "○ 写真も保存する（個人利用）", onClick = { savePhotos = !savePhotos }, modifier = Modifier.fillMaxWidth())
        Text("「取り込みを待機に入れる」を押すと、ここで終わりです。順番に、自動で取り込みます（調べて、そのまま反映します）。進み具合は、ファンブックの「取り込みの状況」で見られます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    },
    confirmButton = {
      TextButton(enabled = url.isNotBlank() && !busy, onClick = {
        busy = true
        message = ""
        scope.launch {
          runCatching { rpc(GatewayMethod.IdolMasterSourceQueueAdd, JSONObject().put("group", group).put("url", url.trim()).put("savePhotos", savePhotos).put("addMissing", addMissing)) }
            .onSuccess { res ->
              val count = res.optJSONArray("jobs")?.length() ?: 1
              val waiting = (res.optJSONArray("queue")?.let { arr -> (0 until arr.length()).count { arr.getJSONObject(it).optString("status") in setOf("waiting", "running") } } ?: 1)
              onApplied("「$group」の取り込みを${count}件、待機に入れました（待機・実行中：${waiting}件）。進み具合は「取り込みの状況」で見られます")
              onDismiss()
            }
            .onFailure { message = "待機に入れられませんでした：${it.message}" }
          busy = false
        }
      }) { Text("同意して、取り込みを待機に入れる") }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("閉じる") } },
  )
}

/** 取り込みの待機の状況（待機・実行中・終わったもの）。2秒ごとに更新。終わったものがあれば、画面を読み直してもらう。 */
@Composable
internal fun IdolSourceQueueDialog(
  viewModel: MainViewModel,
  onDismiss: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var jobs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
  var message by remember { mutableStateOf("") }

  suspend fun call(method: GatewayMethod, params: JSONObject = JSONObject()) {
    runCatching {
      val res = JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = 30_000))
      val arr = res.optJSONArray("queue")
      jobs = (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.sortedByDescending { it.optLong("queuedAt") }
    }.onFailure { message = "状況を読めませんでした：${it.message}" }
  }

  LaunchedEffect(Unit) {
    while (true) {
      call(GatewayMethod.IdolMasterSourceQueueList)
      delay(2000)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("取り込みの状況") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (jobs.isEmpty()) Text("待機中・実行中の取り込みは、ありません。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        jobs.forEach { job ->
          val status = job.optString("status")
          val result = job.optJSONObject("result")
          val label =
            when (status) {
              "waiting" -> "⏳ 待機中"
              "running" -> "🔄 実行中（${job.optInt("done")} / ${job.optInt("total")}人）"
              "done" -> "✅ 終わりました"
              "cancelled" -> "⏹ とめました"
              else -> "⚠ 失敗しました"
            }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${job.optString("group")}　$label", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
            Text(job.optString("url"), style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted, maxLines = 1)
            if (result != null) {
              Text("ページで見つかった人：${result.optInt("found")}人　名簿を更新：${result.optInt("updated")}人　追加：${result.optInt("added")}人　写真：${result.optInt("photosSaved")}枚　X のリンク：${result.optInt("xLinks")}人（画像が取れた人：${result.optInt("xPhotos")}人）" + (if (result.optInt("mixchLinks") > 0) "　MIXCH のリンク：${result.optInt("mixchLinks")}人（画像：${result.optInt("mixchPhotos")}枚）" else "") + if (result.optString("method") == "ai") "（AI が読みました。内容を確認してください）" else "", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            }
            if (result != null) {
              val skipped = result.optJSONObject("xSkipped")
              if (skipped != null && skipped.length() > 0) {
                val names = mapOf("robots" to "robots.txt で、X が自動取得を断っている", "botcheck" to "ボット確認で止まった", "noimage" to "ページに画像がなかった", "error" to "読めなかった（その他）")
                Text("X の画像が取れなかった理由：" + skipped.keys().asSequence().toList().joinToString("、") { "${names[it] ?: it} ${skipped.optInt(it)}人" }, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
              }
            }
            if (status == "error") Text(job.optString("error"), style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
            if (status == "waiting" || status == "running") {
              TextButton(onClick = { scope.launch { call(GatewayMethod.IdolMasterSourceQueueCancel, JSONObject().put("id", job.optString("id"))) } }) { Text(if (status == "running") "とめる" else "待機から外す") }
            }
          }
        }
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    dismissButton = {
      Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
        TextButton(onClick = {
          scope.launch {
            call(GatewayMethod.IdolMasterSourceQueueAddAll)
            message = "保存した情報元を、すべて待機に入れました（すでに待機中のものは、除きました）"
          }
        }) { Text("🔄 保存した情報元を、すべて更新") }
        TextButton(onClick = { scope.launch { call(GatewayMethod.IdolMasterSourceQueueClear) } }) { Text("終わったものを消す") }
      }
    },
  )
}

/**
 * 「URL から追加」：グループの名前（新しい名前でも、いまある名前でも）を決めて、続けて、メンバー（キャラ）一覧のページの URL を入れる。
 * 取り込みは、待機に入れて、順番に自動で行う。グループが分類の中にまだ無ければ、この分類に作る。
 */
@Composable
internal fun IdolUrlAddDialog(
  viewModel: MainViewModel,
  category: String,
  defaultGroup: String,
  onDismiss: () -> Unit,
  onQueued: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  var group by remember { mutableStateOf(defaultGroup) }
  var chosen by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }
  val target = chosen
  if (target != null) {
    IdolSourceDialog(viewModel = viewModel, group = target, onDismiss = onDismiss, onApplied = onQueued)
    return
  }
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("URL から追加（$category）") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("取り込んだ人（キャラ）を入れるグループの名前を決めてください。新しい名前なら、「$category」の中に、新しく作ります。次の画面で、ページの URL を入れます（複数は、1行に1つ）。", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        ClawTextField(value = group, onValueChange = { group = it }, placeholder = "グループの名前", label = "グループの名前", modifier = Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    },
    confirmButton = {
      TextButton(enabled = group.isNotBlank() && !busy, onClick = {
        busy = true
        message = ""
        scope.launch {
          // 分類の中に、グループが無ければ作る（すでにあるときのエラーは、そのまま進める）
          runCatching { viewModel.runGatewayRpc(GatewayMethod.IdolMasterGroupAdd, JSONObject().put("category", category).put("group", group.trim()).toString(), timeoutMs = 30_000) }
          chosen = group.trim()
          busy = false
        }
      }) { Text("次へ（URL を入れる）") }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("閉じる") } },
  )
}
