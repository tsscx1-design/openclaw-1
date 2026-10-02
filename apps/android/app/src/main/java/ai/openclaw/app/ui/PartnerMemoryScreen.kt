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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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

data class MemoryFactItem(
  val fact: String,
  val category: String = "general",
  val date: String = "",
)

@Composable
internal fun PartnerMemoryScreen(
  viewModel: MainViewModel,
  partnerId: String? = null,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var isLoading by remember { mutableStateOf(true) }
  var factsList by remember { mutableStateOf<List<MemoryFactItem>>(emptyList()) }
  var partnerName by remember { mutableStateOf("AIパートナー") }

  var showAddDialog by remember { mutableStateOf(false) }
  var newFactText by remember { mutableStateOf("") }
  var newFactCategory by remember { mutableStateOf("好み") }

  fun loadFacts() {
    val pid = partnerId ?: return
    coroutineScope.launch {
      isLoading = true
      try {
        val res = viewModel.getPartnerFacts(pid)
        val obj = JSONObject(res)
        partnerName = obj.optString("partnerName", "AIパートナー")
        val arr = obj.optJSONArray("facts") ?: JSONArray()
        val list = mutableListOf<MemoryFactItem>()
        for (i in 0 until arr.length()) {
          val item = arr.optJSONObject(i)
          if (item != null) {
            list.add(
              MemoryFactItem(
                fact = item.optString("fact", ""),
                category = item.optString("category", "general"),
                date = item.optString("date", ""),
              )
            )
          } else {
            val str = arr.optString(i)
            if (str.isNotBlank()) {
              list.add(MemoryFactItem(fact = str))
            }
          }
        }
        factsList = list
      } catch (_: Exception) {
      } finally {
        isLoading = false
      }
    }
  }

  fun saveFacts(updated: List<MemoryFactItem>) {
    val pid = partnerId ?: return
    coroutineScope.launch {
      val arr = JSONArray()
      for (item in updated) {
        val o = JSONObject().apply {
          put("fact", item.fact)
          put("category", item.category)
          put("date", item.date)
        }
        arr.put(o)
      }
      viewModel.updatePartnerFacts(pid, arr)
      factsList = updated
    }
  }

  LaunchedEffect(partnerId) {
    loadFacts()
  }

  ClawScaffold(
    contentPadding = PaddingValues(16.dp),
  ) {
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
            Text(
              text = "🧠 AIメモリー管理",
              style = ClawTheme.type.title,
              color = ClawTheme.colors.text,
            )
            Text(
              text = "${partnerName}が記憶した事実リスト",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted,
            )
          }
        }
        Row {
          ClawIconButton(
            icon = Icons.Default.Refresh,
            contentDescription = "更新",
            onClick = { loadFacts() },
          )
          Spacer(modifier = Modifier.width(4.dp))
          ClawIconButton(
            icon = Icons.Default.Add,
            contentDescription = "記憶を追加",
            onClick = {
              newFactText = ""
              showAddDialog = true
            },
          )
        }
      }

      if (isLoading) {
        Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        ) {
          repeat(5) {
            SettingsRowSkeleton()
          }
        }
      } else if (factsList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "📭 まだ記憶された事実がありません",
              style = ClawTheme.type.body,
              color = ClawTheme.colors.textMuted,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "会話を重ねると、AIが自動的にあなたの好みや大事な事実を抽出して覚えます。\n右上の「＋」ボタンから手動で追加することもできます。",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted,
              modifier = Modifier.padding(horizontal = 24.dp),
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(factsList) { item ->
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClawTheme.colors.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                      Text(
                        text = item.category,
                        style = ClawTheme.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = ClawTheme.colors.primary,
                      )
                    }
                    if (item.date.isNotBlank()) {
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = item.date,
                        style = ClawTheme.type.caption.copy(fontSize = 10.sp),
                        color = ClawTheme.colors.textMuted,
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = item.fact,
                    style = ClawTheme.type.body,
                    color = ClawTheme.colors.text,
                  )
                }
                ClawIconButton(
                  icon = Icons.Default.Delete,
                  contentDescription = "削除",
                  onClick = {
                    val updated = factsList.filter { it != item }
                    saveFacts(updated)
                  },
                )
              }
            }
          }
        }
      }
    }

    // 手動追加ダイアログ
    if (showAddDialog) {
      AlertDialog(
        onDismissRequest = { showAddDialog = false },
        title = { Text(text = "新しい記憶を追加", color = ClawTheme.colors.text) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "カテゴリー:", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            ClawTextField(
              value = newFactCategory,
              onValueChange = { newFactCategory = it },
              placeholder = "好み、仕事、趣味、性格など",
              modifier = Modifier.fillMaxWidth(),
            )
            Text(text = "記憶内容 (事実):", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            ClawTextField(
              value = newFactText,
              onValueChange = { newFactText = it },
              placeholder = "例: 珈琲はブラックが好き、週末はプログラミングをする",
              modifier = Modifier.fillMaxWidth(),
            )
          }
        },
        confirmButton = {
          ClawPrimaryButton(
            text = "追加",
            onClick = {
              if (newFactText.isNotBlank()) {
                val newItem = MemoryFactItem(
                  fact = newFactText.trim(),
                  category = newFactCategory.trim().ifEmpty { "general" },
                  date = "手動登録",
                )
                val updated = factsList + newItem
                saveFacts(updated)
                showAddDialog = false
              }
            },
          )
        },
        dismissButton = {
          ClawPrimaryButton(
            text = "キャンセル",
            onClick = { showAddDialog = false },
          )
        },
      )
    }
  }
}
