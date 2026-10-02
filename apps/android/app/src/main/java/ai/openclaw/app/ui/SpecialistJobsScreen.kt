package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawSectionHeader
import ai.openclaw.app.ui.design.ClawTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class SpecialistWorkerRow(
  val key: String,
  val name: String,
  val role: String,
  val online: Boolean,
  val fileReady: Boolean,
)

internal data class SpecialistJobRow(
  val title: String,
  val workerName: String,
  val status: String,
  val startedAt: Long,
  val finishedAt: Long?,
  val detail: String,
)

internal fun parseSpecialistWorkers(json: String): List<SpecialistWorkerRow> {
  val arr = JSONObject(json).optJSONArray("squad") ?: JSONArray()
  return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
    SpecialistWorkerRow(
      key = it.optString("key"),
      name = it.optString("name"),
      role = it.optString("role"),
      online = it.optBoolean("online"),
      fileReady = it.optBoolean("fileReady"),
    )
  }
}

internal fun parseSpecialistJobs(json: String): List<SpecialistJobRow> {
  val arr = JSONObject(json).optJSONArray("jobs") ?: JSONArray()
  return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
    SpecialistJobRow(
      title = it.optString("title"),
      workerName = it.optString("workerName"),
      status = it.optString("status"),
      startedAt = it.optLong("startedAt"),
      finishedAt = it.optLong("finishedAt").takeIf { t -> t > 0 },
      detail = it.optString("error").ifBlank { it.optString("resultPreview") },
    )
  }
}

/** Background worker squad: which CPU workers are up and what they are doing. */
@Composable
internal fun SpecialistJobsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val scope = rememberCoroutineScope()
  var workers by remember { mutableStateOf<List<SpecialistWorkerRow>>(emptyList()) }
  var jobs by remember { mutableStateOf<List<SpecialistJobRow>>(emptyList()) }
  var status by remember { mutableStateOf("") }
  var busyKey by remember { mutableStateOf<String?>(null) }

  suspend fun refresh() {
    try {
      workers = parseSpecialistWorkers(viewModel.runGatewayRpc(GatewayMethod.SpecialistStatus, "{}"))
      jobs = parseSpecialistJobs(viewModel.runGatewayRpc(GatewayMethod.SpecialistJobsList, "{}"))
      status = ""
    } catch (e: Exception) {
      status = "読み込みエラー: ${e.message}"
    }
  }

  // Jobs run for minutes; poll while the screen is open.
  LaunchedEffect(Unit) {
    while (true) {
      refresh()
      delay(5_000)
    }
  }

  ClawScaffold(contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 6.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nativeString("Back"), onClick = onBack)
          Text(
            text = "裏作業員",
            style = ClawTheme.type.display.copy(fontSize = 20.sp),
            color = ClawTheme.colors.text,
            modifier = Modifier.weight(1f),
          )
          ClawIconButton(icon = Icons.Default.Refresh, contentDescription = "再読み込み", onClick = { scope.launch { refresh() } })
        }
        Text(
          text = "メインモデルから任された調べ物や作業を、CPU上の小型モデルが裏で処理します。完了するとチャットに報告されます。",
          style = ClawTheme.type.caption.copy(fontSize = 12.5.sp),
          color = ClawTheme.colors.textMuted,
        )
        if (status.isNotBlank()) {
          Text(text = status, style = ClawTheme.type.caption, color = ClawTheme.colors.danger)
        }
      }

      item { ClawSectionHeader(title = "ワーカー") }
      items(workers, key = { it.key }) { worker ->
        ClawPanel {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(if (worker.online) ClawTheme.colors.success else ClawTheme.colors.textSubtle),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
              Text(text = worker.name, style = ClawTheme.type.body, color = ClawTheme.colors.text)
              Text(
                text = when {
                  !worker.fileReady -> "モデル未ダウンロード（汎用ワーカーが代行）"
                  worker.online -> "待機中・${worker.role}"
                  else -> "停止中（依頼が来ると自動で起動）"
                },
                style = ClawTheme.type.caption.copy(fontSize = 12.sp),
                color = ClawTheme.colors.textMuted,
              )
            }
            if (worker.fileReady) {
              ClawSecondaryButton(
                text = if (busyKey == worker.key) "…" else if (worker.online) "停止" else "起動",
                onClick = {
                  if (busyKey != null) return@ClawSecondaryButton
                  busyKey = worker.key
                  scope.launch {
                    try {
                      val method = if (worker.online) GatewayMethod.SpecialistStop else GatewayMethod.SpecialistStart
                      viewModel.runGatewayRpc(method, JSONObject().put("worker", worker.key).toString(), timeoutMs = 120_000)
                    } catch (e: Exception) {
                      status = "操作エラー: ${e.message}"
                    } finally {
                      busyKey = null
                      refresh()
                    }
                  }
                },
              )
            }
          }
        }
      }

      item { ClawSectionHeader(title = "最近の作業") }
      if (jobs.isEmpty()) {
        item {
          Text(
            text = "まだ作業はありません。チャットで「〇〇を調べて」などと頼むと、ここに表示されます。",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }
      }
      items(jobs) { job ->
        val time = SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(job.startedAt))
        val (badge, color) =
          when (job.status) {
            "running" -> "作業中" to ClawTheme.colors.warning
            "failed" -> "失敗" to ClawTheme.colors.danger
            else -> "完了" to ClawTheme.colors.success
          }
        ClawPanel {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(text = badge, style = ClawTheme.type.caption.copy(fontSize = 12.sp), color = color)
              Text(
                text = "  $time ・ ${job.workerName}",
                style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                color = ClawTheme.colors.textMuted,
              )
            }
            Text(text = job.title, style = ClawTheme.type.body, color = ClawTheme.colors.text)
            if (job.detail.isNotBlank()) {
              Text(
                text = job.detail,
                style = ClawTheme.type.caption.copy(fontSize = 12.sp),
                color = ClawTheme.colors.textMuted,
                maxLines = 4,
              )
            }
          }
        }
      }
    }
  }
}
