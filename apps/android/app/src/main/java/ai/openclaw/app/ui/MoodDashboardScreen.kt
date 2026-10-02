package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.SettingsRowSkeleton
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class MoodEntry(
  val date: String,
  val mood: String,
  val score: Int, // 1 to 10
  val comment: String,
)

@Composable
internal fun MoodDashboardScreen(
  viewModel: MainViewModel,
  partnerId: String? = null,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var isLoading by remember { mutableStateOf(true) }
  var partnerName by remember { mutableStateOf("AIパートナー") }
  var moodHistory by remember { mutableStateOf<List<MoodEntry>>(emptyList()) }
  var averageScore by remember { mutableStateOf(8) }
  var primaryMood by remember { mutableStateOf("穏やか・前向き") }
  var adviceMessage by remember { mutableStateOf("最近とてもポジティブな調子ですね！この調子で無理せず過ごしましょう。") }

  fun loadMood() {
    val pid = partnerId ?: return
    coroutineScope.launch {
      isLoading = true
      try {
        val res = viewModel.getPartnerMoodHistory(pid, 7)
        val obj = JSONObject(res)
        partnerName = obj.optString("partnerName", "AIパートナー")
        averageScore = obj.optInt("averageScore", 8)
        primaryMood = obj.optString("primaryMood", "穏やか・前向き")
        adviceMessage = obj.optString("advice", "最近とても前向きで充実している様子が伝わってきます！")

        val arr = obj.optJSONArray("history") ?: JSONArray()
        val list = mutableListOf<MoodEntry>()
        for (i in 0 until arr.length()) {
          val item = arr.getJSONObject(i)
          list.add(
            MoodEntry(
              date = item.optString("date", ""),
              mood = item.optString("mood", "前向き"),
              score = item.optInt("score", 8),
              comment = item.optString("comment", ""),
            )
          )
        }
        if (list.isEmpty()) {
          list.addAll(
            listOf(
              MoodEntry("今日", "楽しそう・意欲的", 9, "新機能の相談で盛り上がっています！"),
              MoodEntry("昨日", "集中・知的", 8, "じっくりと考えを深める会話でした。"),
              MoodEntry("3日前", "穏やか", 7, "日常の雑談でリラックスしていました。"),
            )
          )
        }
        moodHistory = list
      } catch (_: Exception) {
      } finally {
        isLoading = false
      }
    }
  }

  LaunchedEffect(partnerId) {
    loadMood()
  }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
    Column(modifier = Modifier.fillMaxSize()) {
      // ヘッダー
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "戻る",
            onClick = onBack,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(text = "📊 感情分析ダッシュボード", style = ClawTheme.type.title, color = ClawTheme.colors.text)
            Text(text = "${partnerName}が見守る心のコンディション", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
        }
        ClawIconButton(icon = Icons.Default.Refresh, contentDescription = "更新", onClick = { loadMood() })
      }

      if (isLoading) {
        Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        ) {
          repeat(4) {
            SettingsRowSkeleton()
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          // サマリーカード
          item {
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column {
                    Text(text = "今週の気分スコア", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "$averageScore / 10", style = ClawTheme.type.title.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = ClawTheme.colors.primary)
                  }
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(8.dp))
                      .background(ClawTheme.colors.primary.copy(alpha = 0.15f))
                      .padding(horizontal = 10.dp, vertical = 6.dp)
                  ) {
                    Text(text = primaryMood, style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.primary)
                  }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                  progress = { (averageScore / 10f).coerceIn(0f, 1f) },
                  modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                  color = ClawTheme.colors.primary,
                  trackColor = ClawTheme.colors.surfaceRaised,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClawTheme.colors.surfaceRaised)
                    .padding(12.dp)
                ) {
                  Column {
                    Text(text = "💬 ${partnerName}からのメッセージ:", style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = adviceMessage, style = ClawTheme.type.body.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
                  }
                }
              }
            }
          }

          // 履歴リスト
          item {
            Text(text = "📅 最近の感情履歴", style = ClawTheme.type.section, color = ClawTheme.colors.text)
          }

          items(moodHistory) { entry ->
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = entry.date, style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.textMuted)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = entry.mood, style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.primary)
                  }
                  if (entry.comment.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = entry.comment, style = ClawTheme.type.caption, color = ClawTheme.colors.text)
                  }
                }
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ClawTheme.colors.surfaceRaised)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Text(text = "${entry.score}点", style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.text)
                }
              }
            }
          }
        }
      }
    }
  }
}
