package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

data class GameMessage(
  val sender: String,
  val text: String,
  val isAi: Boolean,
)

@Composable
internal fun MiniGameScreen(
  viewModel: MainViewModel,
  partnerId: String? = null,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var selectedGame by remember { mutableStateOf<String?>(null) }
  var isPlaying by remember { mutableStateOf(false) }
  var isAiThinking by remember { mutableStateOf(false) }
  var gameLogs by remember { mutableStateOf<List<GameMessage>>(emptyList()) }
  var userInputText by remember { mutableStateOf("") }
  var partnerName by remember { mutableStateOf("AIパートナー") }

  val listState = rememberLazyListState()

  val gameTypes = listOf(
    Triple("shiritori", "🍎 しりとり対決", "AIと語尾を繋げて勝負！語彙力が試されます。"),
    Triple("riddle", "❓ なぞなぞクイズ", "AIが面白いなぞなぞを出題。ヒントをもらいながら正解を目指そう！"),
    Triple("twenty_questions", "🤔 20の質問", "AIが思い浮かべたものを20回以内のYes/No質問で当てるゲーム！"),
    Triple("story_relay", "📖 ストーリーリレー", "一文ずつ交代で物語を紡ぎ、即興小説を作る創作ゲーム！"),
  )

  fun startGame(type: String) {
    val pid = partnerId ?: return
    coroutineScope.launch {
      selectedGame = type
      isPlaying = true
      isAiThinking = true
      gameLogs = emptyList()
      try {
        val res = viewModel.startPartnerGame(pid, type)
        val obj = JSONObject(res)
        partnerName = obj.optString("partnerName", "AIパートナー")
        val initialMessage = obj.optString("message", "ゲームを始めましょう！あなたの番です。")
        gameLogs = listOf(GameMessage(sender = partnerName, text = initialMessage, isAi = true))
      } catch (e: Exception) {
        gameLogs = listOf(GameMessage(sender = partnerName, text = "ゲームを開始しました！何から始めますか？", isAi = true))
      } finally {
        isAiThinking = false
      }
    }
  }

  fun sendTurn() {
    val pid = partnerId ?: return
    val text = userInputText.trim()
    if (text.isEmpty() || isAiThinking) return

    userInputText = ""
    val userMsg = GameMessage(sender = "あなた", text = text, isAi = false)
    val updated = gameLogs + userMsg
    gameLogs = updated

    coroutineScope.launch {
      isAiThinking = true
      try {
        val historyArr = JSONArray()
        for (m in updated) {
          historyArr.put(JSONObject().apply {
            put("role", if (m.isAi) "assistant" else "user")
            put("content", m.text)
          })
        }
        val res = viewModel.turnPartnerGame(pid, text, historyArr)
        val obj = JSONObject(res)
        val reply = obj.optString("reply", "ふふ、面白いですね！")
        gameLogs = gameLogs + GameMessage(sender = partnerName, text = reply, isAi = true)
      } catch (e: Exception) {
        gameLogs = gameLogs + GameMessage(sender = partnerName, text = "なるほど！続けますよ！", isAi = true)
      } finally {
        isAiThinking = false
      }
    }
  }

  LaunchedEffect(gameLogs.size) {
    if (gameLogs.isNotEmpty()) {
      listState.animateScrollToItem(gameLogs.size - 1)
    }
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
            onClick = {
              if (isPlaying) {
                isPlaying = false
                selectedGame = null
              } else {
                onBack()
              }
            },
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(text = "🎮 AIミニゲーム", style = ClawTheme.type.title, color = ClawTheme.colors.text)
            Text(
              text = if (isPlaying) "${partnerName}とプレイ中" else "遊びたいゲームを選んでください",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted,
            )
          }
        }
        if (isPlaying) {
          ClawIconButton(
            icon = Icons.Default.Refresh,
            contentDescription = "リセット",
            onClick = { selectedGame?.let { startGame(it) } },
          )
        }
      }

      if (!isPlaying) {
        // ゲーム選択メニュー
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(gameTypes) { (type, title, desc) ->
            ClawPanel(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { startGame(type) }
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text(text = title, style = ClawTheme.type.title.copy(fontSize = 17.sp), color = ClawTheme.colors.text)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = desc, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End,
                ) {
                  Text(
                    text = "スタート ▶",
                    style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = ClawTheme.colors.primary,
                  )
                }
              }
            }
          }
        }
      } else {
        // ゲーム進行チャットUI
        LazyColumn(
          state = listState,
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(gameLogs) { msg ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = if (msg.isAi) Arrangement.Start else Arrangement.End,
            ) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(if (msg.isAi) ClawTheme.colors.surfaceRaised else ClawTheme.colors.primary)
                  .padding(12.dp)
                  .fillMaxWidth(0.85f)
              ) {
                Column {
                  Text(
                    text = msg.sender,
                    style = ClawTheme.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = if (msg.isAi) ClawTheme.colors.primary else ClawTheme.colors.text.copy(alpha = 0.8f),
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = msg.text,
                    style = ClawTheme.type.body,
                    color = ClawTheme.colors.text,
                  )
                }
              }
            }
          }
          if (isAiThinking) {
            item {
              Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ClawTheme.colors.primary, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "${partnerName}が考え中...", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
              }
            }
          }
        }

        // 入力バー
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ClawTextField(
            value = userInputText,
            onValueChange = { userInputText = it },
            placeholder = "あなたの回答やメッセージを入力...",
            modifier = Modifier.weight(1f),
          )
          ClawPrimaryButton(
            text = "送信",
            onClick = { sendTurn() },
          )
        }
      }
    }
  }
}
