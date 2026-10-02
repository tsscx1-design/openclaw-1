package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSectionHeader
import ai.openclaw.app.ui.design.ClawTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

internal data class PcHealthUi(
  val level: String,
  val reasons: List<String>,
  val gpuName: String?,
  val gpuTempC: Int?,
  val gpuUtilPct: Int?,
  val vramUsedMb: Int?,
  val vramTotalMb: Int?,
  val gpuFanPct: Int?,
  val cpuModel: String,
  val cpuLoadPct: Int,
  val cpuTempC: Int?,
  val ramUsedGb: Double,
  val ramTotalGb: Double,
  val ramUsedPct: Int,
  val diskFreeGb: Int?,
  val services: List<Pair<String, Boolean>>,
)

internal fun parsePcHealth(json: String): PcHealthUi {
  val h = JSONObject(json).optJSONObject("health") ?: JSONObject()
  val gpu = h.optJSONObject("gpu")
  val cpu = h.optJSONObject("cpu") ?: JSONObject()
  val ram = h.optJSONObject("ram") ?: JSONObject()
  val reasons = h.optJSONArray("reasons")
  val services = h.optJSONArray("services")
  fun JSONObject.intOrNull(key: String) = if (has(key) && !isNull(key)) optDouble(key).toInt() else null
  return PcHealthUi(
    level = h.optString("level", "stopped"),
    reasons = (0 until (reasons?.length() ?: 0)).map { reasons!!.optString(it) },
    gpuName = gpu?.optString("name"),
    gpuTempC = gpu?.intOrNull("tempC"),
    gpuUtilPct = gpu?.intOrNull("utilPct"),
    vramUsedMb = gpu?.intOrNull("vramUsedMb"),
    vramTotalMb = gpu?.intOrNull("vramTotalMb"),
    gpuFanPct = gpu?.intOrNull("fanPct"),
    cpuModel = cpu.optString("model", "CPU"),
    cpuLoadPct = cpu.optInt("loadPct"),
    cpuTempC = cpu.intOrNull("tempC"),
    ramUsedGb = ram.optDouble("usedGb"),
    ramTotalGb = ram.optDouble("totalGb"),
    ramUsedPct = ram.optInt("usedPct"),
    diskFreeGb = h.optJSONObject("disk")?.intOrNull("freeGb"),
    services = (0 until (services?.length() ?: 0)).mapNotNull { services!!.optJSONObject(it) }.map { it.optString("name") to it.optBoolean("up") },
  )
}

/** Plain-language level with a traffic-light colour. */
@Composable
internal fun pcHealthLabel(level: String): Pair<String, Color> =
  when (level) {
    "stable" -> "🟢 安定" to ClawTheme.colors.success
    "caution" -> "🟡 注意" to ClawTheme.colors.warning
    "danger" -> "🔴 危険" to ClawTheme.colors.danger
    else -> "⛔ 停止中" to ClawTheme.colors.danger
  }

/** Polls the PC vitals every [intervalMs] while composed. */
@Composable
internal fun rememberPcHealth(viewModel: MainViewModel, intervalMs: Long): Pair<PcHealthUi?, String?> {
  var health by remember { mutableStateOf<PcHealthUi?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(intervalMs) {
    while (true) {
      try {
        health = parsePcHealth(viewModel.runGatewayRpc(GatewayMethod.PcHealthGet, "{}", timeoutMs = 20_000))
        error = null
      } catch (e: Exception) {
        error = "PCに接続できません"
      }
      delay(intervalMs)
    }
  }
  return health to error
}

/** Compact card for the home screen; tap for details. */
@Composable
internal fun PcHealthCard(viewModel: MainViewModel, onOpen: () -> Unit) {
  val (health, error) = rememberPcHealth(viewModel, intervalMs = 30_000)
  val (label, color) = if (health == null) ("…" to ClawTheme.colors.textMuted) else pcHealthLabel(health.level)
  ClawPanel(modifier = Modifier.clickable(onClick = onOpen)) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("PCの状態", style = ClawTheme.type.section.copy(fontSize = 15.sp), color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
        Text(error ?: label, style = ClawTheme.type.body.copy(fontSize = 15.sp), color = if (error != null) ClawTheme.colors.danger else color)
      }
      health?.let { h ->
        Text(
          text =
            listOfNotNull(
              h.gpuTempC?.let { "GPU ${it}℃" },
              "CPU ${h.cpuTempC?.let { "${it}℃" } ?: "${h.cpuLoadPct}%"}",
              "RAM ${h.ramUsedPct}%",
              h.vramUsedMb?.let { used -> h.vramTotalMb?.let { "VRAM ${used * 100 / maxOf(it, 1)}%" } },
            ).joinToString("　"),
          style = ClawTheme.type.caption.copy(fontSize = 12.5.sp),
          color = ClawTheme.colors.textMuted,
        )
        h.reasons.firstOrNull()?.let { Text(it, style = ClawTheme.type.caption.copy(fontSize = 12.sp), color = color) }
      }
    }
  }
}

@Composable
private fun Meter(title: String, value: String, fraction: Float?, warn: Boolean) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row {
      Text(title, style = ClawTheme.type.body, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
      Text(value, style = ClawTheme.type.body, color = if (warn) ClawTheme.colors.warning else ClawTheme.colors.text)
    }
    if (fraction != null) {
      LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = if (warn) ClawTheme.colors.warning else ClawTheme.colors.primary,
      )
    }
  }
}

@Composable
internal fun PcHealthScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  val (health, error) = rememberPcHealth(viewModel, intervalMs = 5_000)
  ClawScaffold(contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 6.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nativeString("Back"), onClick = onBack)
          Text("PCの状態", style = ClawTheme.type.display.copy(fontSize = 20.sp), color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
        }
        Text("5秒ごとに更新。危険・停止が続くとスマホに通知します（1時間に1回まで）。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
      }
      if (error != null) {
        item { Text(error, style = ClawTheme.type.body, color = ClawTheme.colors.danger) }
      }
      health?.let { h ->
        item {
          val (label, color) = pcHealthLabel(h.level)
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(label, style = ClawTheme.type.display.copy(fontSize = 22.sp), color = color)
              if (h.reasons.isEmpty()) {
                Text("問題はありません", style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
              }
              h.reasons.forEach { Text("・$it", style = ClawTheme.type.body, color = color) }
            }
          }
        }
        item { ClawSectionHeader(title = "温度・使用率") }
        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              h.gpuTempC?.let { t ->
                Meter("GPU ${h.gpuName.orEmpty()}", "${t}℃・使用率${h.gpuUtilPct ?: 0}%${h.gpuFanPct?.let { "・ファン$it%" } ?: ""}", t / 100f, t >= 80)
              }
              if (h.vramUsedMb != null && h.vramTotalMb != null) {
                Meter("VRAM（GPUメモリ）", "${h.vramUsedMb / 1024f}".take(4) + " / ${h.vramTotalMb / 1024}GB", h.vramUsedMb.toFloat() / maxOf(h.vramTotalMb, 1), false)
              }
              Meter(
                "CPU",
                (h.cpuTempC?.let { "${it}℃・" } ?: "") + "使用率${h.cpuLoadPct}%",
                h.cpuLoadPct / 100f,
                (h.cpuTempC ?: 0) >= 85,
              )
              if (h.cpuTempC == null) {
                Text(
                  "CPU温度は、PCで「OpenClaw_CPU温度センサー設定.bat」を右クリック→管理者として実行すると表示されます。",
                  style = ClawTheme.type.caption.copy(fontSize = 11.5.sp),
                  color = ClawTheme.colors.textMuted,
                )
              }
              Meter("メモリ（RAM）", "${h.ramUsedGb} / ${h.ramTotalGb}GB（${h.ramUsedPct}%）", h.ramUsedPct / 100f, h.ramUsedPct >= 92)
              h.diskFreeGb?.let { Meter("ディスク空き", "${it}GB", null, it < 15) }
            }
          }
        }
        item { ClawSectionHeader(title = "サービス") }
        items(h.services) { (name, up) ->
          Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (up) ClawTheme.colors.success else ClawTheme.colors.textSubtle))
            Text(name, style = ClawTheme.type.body, color = ClawTheme.colors.text, modifier = Modifier.weight(1f).padding(start = 10.dp))
            Text(if (up) "稼働中" else "停止中", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
        }
      }
    }
  }
}
