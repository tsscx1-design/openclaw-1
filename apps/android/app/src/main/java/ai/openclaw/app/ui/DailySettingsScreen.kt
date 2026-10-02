package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.material3.Switch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun DailySettingsScreen(
  viewModel: MainViewModel,
  partnerId: String? = null,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  // The server keeps these off until saved here; they are loaded from the Gateway below.
  var greetingEnabled by remember { mutableStateOf(false) }
  var fortuneEnabled by remember { mutableStateOf(true) }
  var greetingTime by remember { mutableStateOf("07:30") }
  var nightGreetingEnabled by remember { mutableStateOf(false) }
  var nightGreetingTime by remember { mutableStateOf("22:00") }
  var settingsStatus by remember { mutableStateOf("") }

  var partnerName by remember { mutableStateOf("AIパートナー") }
  var isGeneratingGreeting by remember { mutableStateOf(false) }
  var sampleGreeting by remember { mutableStateOf("") }

  var isGettingFortune by remember { mutableStateOf(false) }
  var fortuneResult by remember { mutableStateOf<JSONObject?>(null) }

  fun isValidTime(value: String): Boolean = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(value)

  // Saves the current switches/times on the PC; the PC sends the messages at those times.
  fun saveDailySettings() {
    val pid = partnerId ?: return
    if (!isValidTime(greetingTime) || !isValidTime(nightGreetingTime)) {
      settingsStatus = "時刻は 07:30 のように、24時間表記で入力してください。"
      return
    }
    coroutineScope.launch {
      try {
        val settings = JSONObject()
          .put("greetingEnabled", greetingEnabled)
          .put("greetingTime", greetingTime)
          .put("nightEnabled", nightGreetingEnabled)
          .put("nightTime", nightGreetingTime)
          .put("fortuneWithGreeting", fortuneEnabled)
        viewModel.runGatewayRpc(
          GatewayMethod.PartnerDailySet,
          JSONObject().put("partnerId", pid).put("settings", settings).toString(),
        )
        settingsStatus = "保存しました。PCが指定の時刻にスマホへ通知します。"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        settingsStatus = "保存できませんでした: ${e.message}"
      }
    }
  }

  fun fetchSampleGreeting() {
    val pid = partnerId ?: return
    coroutineScope.launch {
      isGeneratingGreeting = true
      try {
        val res = viewModel.generatePartnerGreeting(pid, "morning")
        val obj = JSONObject(res)
        partnerName = obj.optString("partnerName", "AIパートナー")
        sampleGreeting = obj.optString("greeting", "おはようございます！今日も一日、素敵な日になりますように！")
      } catch (e: Exception) {
        sampleGreeting = "挨拶を作れませんでした: ${e.message}"
      } finally {
        isGeneratingGreeting = false
      }
    }
  }

  fun fetchFortune() {
    val pid = partnerId ?: return
    coroutineScope.launch {
      isGettingFortune = true
      try {
        val res = viewModel.getPartnerFortune(pid)
        val obj = JSONObject(res)
        fortuneResult = obj.optJSONObject("fortune") ?: obj
      } catch (_: Exception) {
      } finally {
        isGettingFortune = false
      }
    }
  }

  LaunchedEffect(partnerId) {
    val pid = partnerId ?: return@LaunchedEffect
    try {
      val res = JSONObject(
        viewModel.runGatewayRpc(GatewayMethod.PartnerDailyGet, JSONObject().put("partnerId", pid).toString()),
      )
      res.optJSONObject("settings")?.let { saved ->
        greetingEnabled = saved.optBoolean("greetingEnabled", false)
        greetingTime = saved.optString("greetingTime", "07:30")
        nightGreetingEnabled = saved.optBoolean("nightEnabled", false)
        nightGreetingTime = saved.optString("nightTime", "22:00")
        fortuneEnabled = saved.optBoolean("fortuneWithGreeting", true)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      settingsStatus = "設定を読み込めませんでした: ${e.message}"
    }
    fetchSampleGreeting()
  }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
    Column(modifier = Modifier.fillMaxSize()) {
      // ヘッダー
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ClawIconButton(
          icon = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "戻る",
          onClick = onBack,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(text = "🌅 朝の挨拶 & 占い設定", style = ClawTheme.type.title, color = ClawTheme.colors.text)
          Text(text = "毎日の目覚まし挨拶・おみくじ・デイリーレポート", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        }
      }

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // 今日の運勢・おみくじセクション
        item {
          ClawPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(text = "🔮", fontSize = 24.sp)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(text = "今日の運勢・おみくじ", style = ClawTheme.type.section, color = ClawTheme.colors.text)
                }
                ClawPrimaryButton(
                  text = if (isGettingFortune) "占断中..." else "占う",
                  onClick = { fetchFortune() },
                )
              }
              Spacer(modifier = Modifier.height(10.dp))

              if (fortuneResult != null) {
                val f = fortuneResult!!
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClawTheme.colors.primary.copy(alpha = 0.1f))
                    .border(1.dp, ClawTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
                ) {
                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = "運勢: ${f.optString("luck", "大吉")}",
                        style = ClawTheme.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = ClawTheme.colors.primary,
                      )
                      Spacer(modifier = Modifier.width(12.dp))
                      Text(
                        text = "ラッキーカラー: ${f.optString("color", "ゴールド")}",
                        style = ClawTheme.type.caption,
                        color = ClawTheme.colors.text,
                      )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = "幸運のアイテム: ${f.optString("item", "温かい紅茶")}",
                      style = ClawTheme.type.caption,
                      color = ClawTheme.colors.textMuted,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = f.optString("message", "今日は新しい挑戦を始めるのに最適な日です！"),
                      style = ClawTheme.type.body,
                      color = ClawTheme.colors.text,
                    )
                  }
                }
              } else {
                Text(
                  text = "「占う」ボタンを押すと、AIが今日のあなたの運勢とラッキーアイテムを占います。",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )
              }
            }
          }
        }

        // 朝の挨拶プレビュー
        item {
          ClawPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(text = "☀️", fontSize = 22.sp)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(text = "朝の挨拶プレビュー", style = ClawTheme.type.section, color = ClawTheme.colors.text)
                }
                ClawIconButton(
                  icon = Icons.Default.Refresh,
                  contentDescription = "再生成",
                  onClick = { fetchSampleGreeting() },
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              if (isGeneratingGreeting) {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(color = ClawTheme.colors.primary)
                }
              } else {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClawTheme.colors.surfaceRaised)
                    .padding(12.dp)
                ) {
                  Text(
                    text = sampleGreeting.ifEmpty { "おはようございます！今日も一日、一緒に楽しく過ごしましょう！" },
                    style = ClawTheme.type.body,
                    color = ClawTheme.colors.text,
                  )
                }
              }
            }
          }
        }

        // 通知設定
        item {
          Text(text = "⚙️ 定期配信・通知設定", style = ClawTheme.type.section, color = ClawTheme.colors.text)
        }

        item {
          ClawPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column {
                  Text(text = "朝の目覚まし挨拶", style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.text)
                  Text(text = "毎朝指定時刻に挨拶メッセージを通知 ($greetingTime)", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                }
                Switch(
                  checked = greetingEnabled,
                  onCheckedChange = {
                    greetingEnabled = it
                    saveDailySettings()
                  },
                )
              }
              ClawTextField(
                value = greetingTime,
                onValueChange = {
                  greetingTime = it
                  if (isValidTime(it)) saveDailySettings()
                },
                placeholder = "朝の時刻 (例 07:30)",
                modifier = Modifier.fillMaxWidth(),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column {
                  Text(text = "夜のお疲れ様メッセージ", style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.text)
                  Text(text = "夜指定時刻に労いの言葉を通知 ($nightGreetingTime)", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                }
                Switch(
                  checked = nightGreetingEnabled,
                  onCheckedChange = {
                    nightGreetingEnabled = it
                    saveDailySettings()
                  },
                )
              }
              ClawTextField(
                value = nightGreetingTime,
                onValueChange = {
                  nightGreetingTime = it
                  if (isValidTime(it)) saveDailySettings()
                },
                placeholder = "夜の時刻 (例 22:00)",
                modifier = Modifier.fillMaxWidth(),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column {
                  Text(text = "挨拶におみくじ結果を同封", style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.text)
                  Text(text = "朝の通知と一緒に今日の運勢をお知らせ", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                }
                Switch(
                  checked = fortuneEnabled,
                  onCheckedChange = {
                    fortuneEnabled = it
                    saveDailySettings()
                  },
                )
              }
              if (settingsStatus.isNotBlank()) {
                Text(text = settingsStatus, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
              }
            }
          }
        }
      }
    }
  }
}
