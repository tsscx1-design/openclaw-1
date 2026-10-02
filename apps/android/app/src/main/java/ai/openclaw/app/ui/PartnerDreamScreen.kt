package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class DreamItem(
  val id: String,
  val title: String,
  val story: String,
  val date: String,
  val mood: String = "不思議",
)

@Composable
internal fun PartnerDreamScreen(
  viewModel: MainViewModel,
  partnerId: String? = null,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var isLoading by remember { mutableStateOf(true) }
  var isGenerating by remember { mutableStateOf(false) }
  var partnerName by remember { mutableStateOf("AIパートナー") }
  var dreamList by remember { mutableStateOf<List<DreamItem>>(emptyList()) }

  fun loadDreams() {
    val pid = partnerId ?: return
    coroutineScope.launch {
      isLoading = true
      try {
        val res = viewModel.listPartnerDreams(pid)
        val obj = JSONObject(res)
        partnerName = obj.optString("partnerName", "AIパートナー")
        val arr = obj.optJSONArray("dreams") ?: JSONArray()
        val list = mutableListOf<DreamItem>()
        for (i in 0 until arr.length()) {
          val d = arr.getJSONObject(i)
          list.add(
            DreamItem(
              id = d.optString("id", "dream-$i"),
              title = d.optString("title", "星空の冒険"),
              story = d.optString("story", ""),
              date = d.optString("date", ""),
              mood = d.optString("mood", "不思議"),
            )
          )
        }
        dreamList = list
      } catch (_: Exception) {
      } finally {
        isLoading = false
      }
    }
  }

  fun generateNewDream() {
    val pid = partnerId ?: return
    coroutineScope.launch {
      isGenerating = true
      try {
        val res = viewModel.generatePartnerDream(pid)
        val obj = JSONObject(res)
        val newDream = obj.optJSONObject("dream")
        if (newDream != null) {
          val item = DreamItem(
            id = newDream.optString("id", "dream-${System.currentTimeMillis()}"),
            title = newDream.optString("title", "夜空を駆ける記憶"),
            story = newDream.optString("story", ""),
            date = newDream.optString("date", "今日"),
            mood = newDream.optString("mood", "温かい"),
          )
          dreamList = listOf(item) + dreamList
        }
      } catch (_: Exception) {
      } finally {
        isGenerating = false
      }
    }
  }

  LaunchedEffect(partnerId) {
    loadDreams()
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
            Text(text = "🌙 AIの夢日記", style = ClawTheme.type.title, color = ClawTheme.colors.text)
            Text(text = "${partnerName}が眠っている間に見た夢の記録", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
        }
        Row {
          ClawIconButton(icon = Icons.Default.Refresh, contentDescription = "更新", onClick = { loadDreams() })
          Spacer(modifier = Modifier.width(4.dp))
          ClawPrimaryButton(
            text = if (isGenerating) "紡ぎ中..." else "✨ 夢を見る",
            onClick = { generateNewDream() },
          )
        }
      }

      if (isLoading) {
        Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        ) {
          repeat(3) {
            SettingsRowSkeleton()
          }
        }
      } else if (dreamList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🌌 まだ夢日記がありません", style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "右上の「夢を見る」ボタンを押すと、あなたとの会話の思い出を元に、AIが幻想的な夢のストーリーを紡ぎます。",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted,
              modifier = Modifier.padding(horizontal = 24.dp),
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          items(dreamList) { item ->
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "✨ ${item.title}",
                    style = ClawTheme.type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    color = ClawTheme.colors.text,
                  )
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(6.dp))
                      .background(ClawTheme.colors.primary.copy(alpha = 0.15f))
                      .padding(horizontal = 8.dp, vertical = 2.dp)
                  ) {
                    Text(
                      text = item.mood,
                      style = ClawTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                      color = ClawTheme.colors.primary,
                    )
                  }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = item.date, style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  text = item.story,
                  style = ClawTheme.type.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                  color = ClawTheme.colors.text,
                )
              }
            }
          }
        }
      }
    }
  }
}
