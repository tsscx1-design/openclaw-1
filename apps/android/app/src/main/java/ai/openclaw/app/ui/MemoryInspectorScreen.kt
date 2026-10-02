package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawSectionHeader
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class DailyNoteItem(
  val fileName: String,
  val sizeBytes: Long,
  val updatedAt: Long,
)

@Composable
internal fun MemoryInspectorScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var selectedTab by remember { mutableIntStateOf(0) } // 0: 長期記憶(RAG), 1: ユーザー設定, 2: 短期記憶
  var statusMessage by remember { mutableStateOf("") }
  var isLoading by remember { mutableStateOf(false) }

  var partnerList by remember { mutableStateOf<List<Pair<String?, String>>>(listOf(null to "全体 (システム)")) }
  var selectedPartnerId by remember { mutableStateOf<String?>(null) }

  var longTermMemoryContent by remember { mutableStateOf("") }
  var userProfileContent by remember { mutableStateOf("") }
  var dailyNotes by remember { mutableStateOf<List<DailyNoteItem>>(emptyList()) }
  var estimatedTokens by remember { mutableIntStateOf(0) }
  var workspaceDir by remember { mutableStateOf("") }

  fun fetchPartners() {
    coroutineScope.launch {
      try {
        val resultJson = viewModel.runGatewayRpc(GatewayMethod.PartnerList, "{}")
        val obj = JSONObject(resultJson)
        val arr = obj.optJSONArray("partners") ?: JSONArray()
        val list = mutableListOf<Pair<String?, String>>(null to "全体 (システム)")
        for (i in 0 until arr.length()) {
          val p = arr.getJSONObject(i)
          val id = p.optString("id", "")
          val name = p.optString("name", "パートナー")
          list.add(id to name)
        }
        partnerList = list
      } catch (_: Exception) {}
    }
  }

  fun fetchMemories() {
    isLoading = true
    coroutineScope.launch {
      try {
        val params = JSONObject().apply {
          if (selectedPartnerId != null) put("partnerId", selectedPartnerId)
        }
        val resultJson = viewModel.runGatewayRpc(GatewayMethod.MemoryInspect, params.toString())
        val obj = JSONObject(resultJson)
        workspaceDir = obj.optString("workspaceDir", "")
        longTermMemoryContent = obj.optString("longTermMemory", "")
        userProfileContent = obj.optString("userProfile", "")

        val shortTerm = obj.optJSONObject("shortTerm")
        estimatedTokens = shortTerm?.optInt("estimatedTokens", 0) ?: 0

        val notesArr = obj.optJSONArray("dailyNotes") ?: JSONArray()
        val notes = mutableListOf<DailyNoteItem>()
        for (i in 0 until notesArr.length()) {
          val n = notesArr.getJSONObject(i)
          notes.add(
            DailyNoteItem(
              fileName = n.optString("fileName", ""),
              sizeBytes = n.optLong("sizeBytes", 0L),
              updatedAt = n.optLong("updatedAt", 0L)
            )
          )
        }
        dailyNotes = notes
      } catch (e: Exception) {
        statusMessage = "読み込みエラー: ${e.message}"
      } finally {
        isLoading = false
      }
    }
  }

  fun saveMemory(target: String, content: String) {
    statusMessage = "保存中..."
    coroutineScope.launch {
      try {
        val params = JSONObject().apply {
          put("target", target)
          put("content", content)
          if (selectedPartnerId != null) put("partnerId", selectedPartnerId)
        }
        viewModel.runGatewayRpc(GatewayMethod.MemoryUpdate, params.toString())
        statusMessage = "$target を正常に更新・保存しました！"
        fetchMemories()
      } catch (e: Exception) {
        statusMessage = "保存エラー: ${e.message}"
      }
    }
  }

  fun clearShortTerm() {
    statusMessage = "短期記憶をクリア中..."
    coroutineScope.launch {
      try {
        val params = JSONObject().apply { put("target", "short-term") }
        viewModel.runGatewayRpc(GatewayMethod.MemoryClear, params.toString())
        statusMessage = "短期セッション記憶をクリアしました。"
        fetchMemories()
      } catch (e: Exception) {
        statusMessage = "クリアエラー: ${e.message}"
      }
    }
  }

  LaunchedEffect(Unit) {
    fetchPartners()
    fetchMemories()
  }

  LaunchedEffect(selectedPartnerId) {
    fetchMemories()
  }

  ClawScaffold(
    contentPadding = PaddingValues(start = 20.dp, top = 13.dp, end = 20.dp, bottom = 6.dp),
    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
      ) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              ClawIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = nativeString("Back"),
                onClick = onBack
              )
              ClawIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "再読み込み",
                onClick = { fetchMemories() }
              )
            }
            Text(
              text = "記憶インスペクター (エージェント別RAG & メモリ)",
              style = ClawTheme.type.display.copy(fontSize = 18.sp, lineHeight = 22.sp),
              color = ClawTheme.colors.text
            )
            Text(
              text = "エージェントごとに独立して学習・保存された記憶や設定を確認・修正・削除できます。",
              style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
              color = ClawTheme.colors.textMuted
            )
          }
        }

        // 対象エージェント選択チップ
        item {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "対象エージェントの記憶:",
              style = ClawTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
              color = ClawTheme.colors.textMuted
            )
            androidx.compose.foundation.lazy.LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(partnerList) { p ->
                val isSelected = selectedPartnerId == p.first
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                      if (isSelected) ClawTheme.colors.primary.copy(alpha = 0.22f)
                      else ClawTheme.colors.surfaceRaised
                    )
                    .clickable {
                      selectedPartnerId = p.first
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text(
                    text = p.second,
                    style = ClawTheme.type.caption.copy(
                      fontSize = 12.sp,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.text
                  )
                }
              }
            }
          }
        }

        if (statusMessage.isNotBlank()) {
          item {
            ClawPanel {
              Text(
                text = statusMessage,
                style = ClawTheme.type.body,
                color = ClawTheme.colors.text
              )
            }
          }
        }

        // タブ切り替え
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(ClawTheme.colors.surfaceRaised)
              .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            val tabs = listOf("長期記憶 (RAG)", "ユーザー設定", "短期記憶")
            tabs.forEachIndexed { index, title ->
              val isSelected = selectedTab == index
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(6.dp))
                  .background(if (isSelected) ClawTheme.colors.primary else Color.Transparent)
                  .clickable { selectedTab = index }
                  .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = title,
                  style = ClawTheme.type.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                  color = if (isSelected) ClawTheme.colors.primaryText else ClawTheme.colors.textMuted
                )
              }
            }
          }
        }

        when (selectedTab) {
          0 -> {
            // --- 長期記憶 (MEMORY.md) ---
            item {
              ClawPanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text(
                    text = "長期記憶・学習知識 (MEMORY.md)",
                    style = ClawTheme.type.section,
                    color = ClawTheme.colors.text
                  )
                  Text(
                    text = "AIがWeb検索や会話を通じて永続的に学習・記憶したナレッジです。直接編集して保存することも可能です。",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted
                  )
                  ClawTextField(
                    value = longTermMemoryContent,
                    onValueChange = { longTermMemoryContent = it },
                    placeholder = "記憶内容...",
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                  )
                  ClawPrimaryButton(
                    text = "変更を保存する",
                    onClick = { saveMemory("MEMORY.md", longTermMemoryContent) },
                    modifier = Modifier.align(Alignment.End)
                  )
                }
              }
            }

            item {
              ClawSectionHeader(title = "日次ノート一覧 (${dailyNotes.size}件)")
            }

            if (dailyNotes.isEmpty()) {
              item {
                ClawPanel {
                  Text(
                    text = "日次ノートはありません。",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted
                  )
                }
              }
            } else {
              items(dailyNotes) { note ->
                ClawPanel {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column {
                      Text(
                        text = note.fileName,
                        style = ClawTheme.type.section.copy(fontSize = 13.5.sp),
                        color = ClawTheme.colors.text
                      )
                      Text(
                        text = "${note.sizeBytes} バイト",
                        style = ClawTheme.type.caption,
                        color = ClawTheme.colors.textMuted
                      )
                    }
                  }
                }
              }
            }
          }

          1 -> {
            // --- ユーザープロファイル (USER.md) ---
            item {
              ClawPanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text(
                    text = "ユーザープロファイル設定 (USER.md)",
                    style = ClawTheme.type.section,
                    color = ClawTheme.colors.text
                  )
                  Text(
                    text = "あなたの呼び名、趣味、会話のトーンなど、AIが配慮すべき基本設定です。",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted
                  )
                  ClawTextField(
                    value = userProfileContent,
                    onValueChange = { userProfileContent = it },
                    placeholder = "ユーザー情報...",
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                  )
                  ClawPrimaryButton(
                    text = "変更を保存する",
                    onClick = { saveMemory("USER.md", userProfileContent) },
                    modifier = Modifier.align(Alignment.End)
                  )
                }
              }
            }
          }

          2 -> {
            // --- 短期記憶 (Short-term session) ---
            item {
              ClawPanel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  Text(
                    text = "短期記憶ステータス",
                    style = ClawTheme.type.section,
                    color = ClawTheme.colors.text
                  )
                  Text(
                    text = "現在のチャットセッションで保持されているコンテキスト情報です。",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted
                  )

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = "推定コンテキストトークン消費:",
                      style = ClawTheme.type.body,
                      color = ClawTheme.colors.text
                    )
                    Text(
                      text = "~$estimatedTokens トークン",
                      style = ClawTheme.type.section,
                      color = ClawTheme.colors.primary
                    )
                  }

                  Spacer(modifier = Modifier.height(8.dp))

                  ClawSecondaryButton(
                    text = "短期記憶（会話ログ）をクリアしてリフレッシュ",
                    onClick = { clearShortTerm() }
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
