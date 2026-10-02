package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTextField
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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

data class DiaryEntry(
  val id: String,
  val date: String,
  val title: String,
  val content: String,
  val aiComment: String,
)

@Composable
internal fun DiaryScreen(
  viewModel: MainViewModel,
  partnerId: String? = null,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var isLoading by remember { mutableStateOf(true) }
  var partnerName by remember { mutableStateOf("AIパートナー") }
  var diaryList by remember { mutableStateOf<List<DiaryEntry>>(emptyList()) }

  var showCreateDialog by remember { mutableStateOf(false) }
  var userInputNotes by remember { mutableStateOf("") }
  var isWritingDiary by remember { mutableStateOf(false) }

  fun loadDiaries() {
    val pid = partnerId ?: return
    coroutineScope.launch {
      isLoading = true
      try {
        val res = viewModel.listPartnerDiaries(pid)
        val obj = JSONObject(res)
        partnerName = obj.optString("partnerName", "AIパートナー")
        val arr = obj.optJSONArray("diaries") ?: JSONArray()
        val list = mutableListOf<DiaryEntry>()
        for (i in 0 until arr.length()) {
          val d = arr.getJSONObject(i)
          list.add(
            DiaryEntry(
              id = d.optString("id", "diary-$i"),
              date = d.optString("date", ""),
              title = d.optString("title", "今日の日記"),
              content = d.optString("content", ""),
              aiComment = d.optString("aiComment", ""),
            )
          )
        }
        diaryList = list
      } catch (_: Exception) {
      } finally {
        isLoading = false
      }
    }
  }

  fun createDiary() {
    val pid = partnerId ?: return
    val notes = userInputNotes.trim()
    if (notes.isEmpty()) return

    coroutineScope.launch {
      isWritingDiary = true
      try {
        val res = viewModel.createPartnerDiary(pid, notes)
        val obj = JSONObject(res)
        val d = obj.optJSONObject("diary")
        if (d != null) {
          val entry = DiaryEntry(
            id = d.optString("id", "diary-${System.currentTimeMillis()}"),
            date = d.optString("date", "今日"),
            title = d.optString("title", "今日の一日"),
            content = d.optString("content", notes),
            aiComment = d.optString("aiComment", "今日もお疲れ様でした！"),
          )
          diaryList = listOf(entry) + diaryList
          showCreateDialog = false
          userInputNotes = ""
        }
      } catch (_: Exception) {
      } finally {
        isWritingDiary = false
      }
    }
  }

  LaunchedEffect(partnerId) {
    loadDiaries()
  }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
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
            Text(text = "📖 日記アシスタント", style = ClawTheme.type.title, color = ClawTheme.colors.text)
            Text(text = "${partnerName}が代筆・整理する毎日の記録", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
        }
        Row {
          ClawIconButton(icon = Icons.Default.Refresh, contentDescription = "更新", onClick = { loadDiaries() })
          Spacer(modifier = Modifier.width(4.dp))
          ClawPrimaryButton(
            text = "＋ 日記を書く",
            onClick = { showCreateDialog = true },
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
      } else if (diaryList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "📝 まだ日記がありません", style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "「日記を書く」ボタンから、今日あった出来事や感情を箇条書きで話しかけてみてください。\nAIが温かい文章の日記にして保存します。",
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
          items(diaryList) { entry ->
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(text = entry.title, style = ClawTheme.type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = ClawTheme.colors.text)
                  Text(text = entry.date, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = entry.content, style = ClawTheme.type.body.copy(fontSize = 14.sp, lineHeight = 22.sp), color = ClawTheme.colors.text)
                if (entry.aiComment.isNotBlank()) {
                  Spacer(modifier = Modifier.height(10.dp))
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(ClawTheme.colors.surfaceRaised)
                      .padding(10.dp)
                  ) {
                    Text(
                      text = "💬 ${partnerName}: ${entry.aiComment}",
                      style = ClawTheme.type.caption.copy(fontSize = 12.sp),
                      color = ClawTheme.colors.textMuted,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    if (showCreateDialog) {
      AlertDialog(
        onDismissRequest = { if (!isWritingDiary) showCreateDialog = false },
        title = { Text(text = "今日の日記を作成", color = ClawTheme.colors.text) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "今日あった出来事や考えたことを自由に教えてください。\nAIが素敵な日記に仕上げます：",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted,
            )
            ClawTextField(
              value = userInputNotes,
              onValueChange = { userInputNotes = it },
              placeholder = "例: カフェで新しいプロジェクトの構想を練った。コーヒーが美味しかった。",
              modifier = Modifier.fillMaxWidth().height(140.dp),
            )
          }
        },
        confirmButton = {
          ClawPrimaryButton(
            text = if (isWritingDiary) "執筆中..." else "AIに書いてもらう",
            onClick = { createDiary() },
          )
        },
        dismissButton = {
          ClawPrimaryButton(
            text = "キャンセル",
            onClick = { showCreateDialog = false },
          )
        },
      )
    }
  }
}
