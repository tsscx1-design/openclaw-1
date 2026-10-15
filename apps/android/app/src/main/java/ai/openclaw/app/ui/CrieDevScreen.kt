package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class DevRow(val id: String, val title: String, val status: String, val stat: String, val needsBuild: Boolean, val error: String, val log: List<String>)

private fun statusLabel(status: String) =
  when (status) {
    "queued" -> "⏳ 待機中"
    "working" -> "🛠 作業中"
    "review" -> "👀 確認待ち"
    "nochange" -> "変更なし"
    "applied" -> "✅ 適用済み"
    "rejected" -> "取りやめ"
    "error" -> "⚠️ 失敗"
    else -> status
  }

/** 設定の「クリエ開発機能」：入・切、依頼、できた変更の確認・適用・取りやめ。 */
@Composable
internal fun CrieDevScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  var enabled by remember { mutableStateOf(false) }
  var rows by remember { mutableStateOf<List<DevRow>>(emptyList()) }
  var message by remember { mutableStateOf("") }
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var github by remember { mutableStateOf("") }
  var diffText by remember { mutableStateOf<String?>(null) }
  var confirmApply by remember { mutableStateOf<DevRow?>(null) }

  suspend fun reload() {
    runCatching {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.CrieDevGet, "{}", timeoutMs = 20_000))
      enabled = res.optBoolean("enabled")
      val arr = res.optJSONArray("requests")
      rows =
        (0 until (arr?.length() ?: 0)).map { i ->
          val o = arr!!.getJSONObject(i)
          val log = o.optJSONArray("log")
          DevRow(o.optString("id"), o.optString("title"), o.optString("status"), o.optString("diffStat"), o.optBoolean("needsBuild"), o.optString("error"), (0 until (log?.length() ?: 0)).map { log!!.optString(it) })
        }
    }
  }
  LaunchedEffect(Unit) {
    while (true) {
      reload()
      delay(5_000)
    }
  }

  ClawScaffold(contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 6.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
          Text(text = "🛠 クリエ開発機能", style = ClawTheme.type.title, color = ClawTheme.colors.text, modifier = Modifier.weight(1f).padding(start = 8.dp))
        }
      }
      item {
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "入にすると、クリエがこのプロジェクトの改良や新機能の追加を自分で作れます（インターネット検索OK。GitHubのプロジェクトを読み込んで、組み込み・改造もできます）。作業は本体のコピーで行い、できた変更を確認して「適用」を押すまで、本体は変わりません。適用後は、ビルドが必要です。",
              style = ClawTheme.type.captionSmall,
              color = ClawTheme.colors.textMuted,
            )
            ClawPrimaryButton(
              text = if (enabled) "✅ 入（タップで切る）" else "切（タップで入れる）",
              onClick = {
                scope.launch {
                  runCatching { viewModel.runGatewayRpc(GatewayMethod.CrieDevSet, JSONObject().put("enabled", !enabled).toString(), timeoutMs = 15_000) }
                  reload()
                }
              },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
      if (message.isNotBlank()) item { Text(text = message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary) }
      if (enabled) {
        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(text = "クリエに頼む", style = ClawTheme.type.section, color = ClawTheme.colors.text)
              ClawTextField(value = title, onValueChange = { title = it }, placeholder = "例: モーションの体位をもっと増やす", label = "題名", modifier = Modifier.fillMaxWidth())
              ClawTextField(value = description, onValueChange = { description = it }, placeholder = "何をどうしたいか。参考にしたいこと", label = "内容", minLines = 3, modifier = Modifier.fillMaxWidth())
              ClawTextField(value = github, onValueChange = { github = it }, placeholder = "https://github.com/ユーザー/リポジトリ（省略可）", label = "参考にするGitHub", modifier = Modifier.fillMaxWidth())
              ClawPrimaryButton(
                text = "頼む",
                enabled = title.isNotBlank(),
                onClick = {
                  scope.launch {
                    message = runCatching {
                      viewModel.runGatewayRpc(GatewayMethod.CrieDevRequest, JSONObject().put("title", title).put("description", description).put("githubUrl", github).toString(), timeoutMs = 30_000)
                    }.fold({ title = ""; description = ""; github = ""; "依頼しました。画面を離れても、アプリを閉じても続きます。" }, { "頼めませんでした：${it.message}" })
                    reload()
                  }
                },
                modifier = Modifier.fillMaxWidth(),
              )
              Text(text = "クリエとの会話からも頼めます（例:「この機能を足して」）。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            }
          }
        }
      }
      items(rows.size) { i ->
        val row = rows[i]
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "${statusLabel(row.status)}　${row.title}", style = ClawTheme.type.body, color = ClawTheme.colors.text)
            if (row.error.isNotBlank()) Text(text = row.error, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.danger)
            if (row.stat.isNotBlank()) Text(text = row.stat, style = ClawTheme.type.captionSmall.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted, maxLines = 8)
            if (row.log.isNotEmpty()) Text(text = row.log.takeLast(3).joinToString("\n"), style = ClawTheme.type.captionSmall.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted, maxLines = 6)
            if (row.needsBuild && row.status == "review") Text(text = "適用したあと、ゲートウェイのビルド（Kotlin があればアプリのビルドも）が必要です。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
            if (row.status == "review") {
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ClawSecondaryButton(
                  text = "変更を見る",
                  onClick = {
                    scope.launch {
                      diffText = runCatching { JSONObject(viewModel.runGatewayRpc(GatewayMethod.CrieDevDiff, JSONObject().put("id", row.id).toString(), timeoutMs = 30_000)).optString("patch") }.getOrElse { "読めませんでした：${it.message}" }
                    }
                  },
                  modifier = Modifier.weight(1f),
                )
                ClawPrimaryButton(text = "適用", onClick = { confirmApply = row }, modifier = Modifier.weight(1f))
                ClawSecondaryButton(
                  text = "取りやめ",
                  onClick = { scope.launch { message = runCatching { viewModel.runGatewayRpc(GatewayMethod.CrieDevReject, JSONObject().put("id", row.id).toString(), timeoutMs = 60_000) }.fold({ "取りやめました" }, { "できませんでした：${it.message}" }); reload() } },
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }
    }
  }

  diffText?.let { text ->
    AlertDialog(
      onDismissRequest = { diffText = null },
      title = { Text("変更の内容") },
      text = { androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth()) { item { Text(text = text, fontSize = 10.sp) } } },
      confirmButton = { TextButton(onClick = { diffText = null }) { Text("閉じる") } },
    )
  }
  confirmApply?.let { row ->
    AlertDialog(
      onDismissRequest = { confirmApply = null },
      title = { Text("「${row.title}」を本体に適用しますか？") },
      text = { Text("プロジェクトのファイルが書き換わります（コミットはされません）。あとでビルドが必要です。") },
      confirmButton = {
        TextButton(onClick = {
          confirmApply = null
          scope.launch {
            message = runCatching { viewModel.runGatewayRpc(GatewayMethod.CrieDevApply, JSONObject().put("id", row.id).toString(), timeoutMs = 120_000) }.fold({ "適用しました。ビルドをしてください。" }, { "適用できませんでした：${it.message}" })
            reload()
          }
        }) { Text("適用する") }
      },
      dismissButton = { TextButton(onClick = { confirmApply = null }) { Text("やめる") } },
    )
  }
}
