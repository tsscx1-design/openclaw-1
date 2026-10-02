package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.PartnerAvatar
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class PartnerSingleChatMessage(
  val id: String,
  val role: String, // "user" or "assistant"
  val content: String,
  val timestamp: Long,
)

/**
 * Scrolls to [index], ignoring interruption by another scroll (auto-scroll or the user's finger).
 * An interrupted scroll throws, and before this guard it aborted message sending entirely.
 */
internal suspend fun androidx.compose.foundation.lazy.LazyListState.scrollToBottomSafely(index: Int, animate: Boolean = true) {
  if (index < 0) return
  try {
    if (animate) animateScrollToItem(index) else scrollToItem(index)
  } catch (e: kotlinx.coroutines.CancellationException) {
    // Another scroll took over (MutationInterruptedException); only a real cancellation of the
    // caller propagates, so the message flow continues.
    kotlinx.coroutines.currentCoroutineContext().ensureActive()
  }
}

// C-3 修正: ユーザーフレンドリーなエラーメッセージ生成ヘルパー
private fun friendlyError(action: String, e: Exception): String {
  android.util.Log.w("OpenClawPartnerChat", "$action failed", e)
  val msg = e.message?.lowercase() ?: ""
  // Keep the real cause visible: the generic hints alone made failures impossible to diagnose.
  val detail = "\n（詳細: ${e.javaClass.simpleName}: ${e.message?.take(160) ?: "なし"}）"
  return detail.let { suffix -> friendlyErrorHint(action, msg) + suffix }
}

private fun friendlyErrorHint(action: String, msg: String): String {
  return when {
    msg.contains("timeout") || msg.contains("timed out") ->
      "⏰ ${action}がタイムアウトしました。PCとの接続を確認して再試行してください。"
    msg.contains("refused") || msg.contains("connect") || msg.contains("unreachable") ->
      "📡 PCに接続できません。OpenClawが起動しているか確認してください。"
    msg.contains("abort") || msg.contains("cancel") ->
      "🔄 ${action}が中断されました。もう一度お試しください。"
    else ->
      "❌ ${action}に失敗しました。PCとの接続を確認して再試行してください。"
  }
}

@Composable
internal fun PartnerChatScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
  onOpenMemoryInspector: () -> Unit = {},
  onOpenCharacterSheet: () -> Unit = {},
  onOpenMemory: () -> Unit = {},
  onOpenBond: () -> Unit = {},
  onOpenDailySettings: () -> Unit = {},
  onOpenMiniGame: () -> Unit = {},
  onOpenDream: () -> Unit = {},
  onOpenMood: () -> Unit = {},
  onOpenDiary: () -> Unit = {},
) {
  BackHandler(onBack = onBack)

  val coroutineScope = rememberCoroutineScope()
  val partnerId by viewModel.activePartnerId.collectAsState()

  // H-4 修正: 送信ジョブを保持し、Composable破棄時にキャンセル
  var sendJob by remember { mutableStateOf<Job?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      sendJob?.cancel()
    }
  }

  var partnerName by remember { mutableStateOf("AIパートナー") }
  var partnerAvatarUrl by remember { mutableStateOf("") }
  var partnerPersona by remember { mutableStateOf("") }
  var partnerModel by remember { mutableStateOf("") }
  var messages by remember { mutableStateOf<List<PartnerSingleChatMessage>>(emptyList()) }
  // C-2 修正: 入力テキストはrememberSaveableで画面回転時に保持
  var inputText by rememberSaveable { mutableStateOf("") }
  var isThinking by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf("") }
  // 「キャラっぽくない」フィードバック対象の返答
  var feedbackTarget by remember { mutableStateOf<PartnerSingleChatMessage?>(null) }
  var feedbackNote by remember { mutableStateOf("") }

  // パートナーごとの声（partner.settings の voicePitch / voiceRate）で読み上げる
  val ttsContext = androidx.compose.ui.platform.LocalContext.current
  var voicePitch by remember { mutableStateOf(1f) }
  var voiceRate by remember { mutableStateOf(1f) }
  val tts = remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
  DisposableEffect(Unit) {
    lateinit var engine: android.speech.tts.TextToSpeech
    engine =
      android.speech.tts.TextToSpeech(ttsContext) { status ->
        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
          engine.language = java.util.Locale.JAPANESE
          tts.value = engine
        }
      }
    onDispose {
      engine.stop()
      engine.shutdown()
      tts.value = null
    }
  }
  LaunchedEffect(partnerId) {
    val pid = partnerId ?: return@LaunchedEffect
    runCatching {
      val settings =
        org.json.JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerSettingsGet, org.json.JSONObject().put("partnerId", pid).toString()))
          .optJSONObject("settings")
      voicePitch = settings?.optDouble("voicePitch", 1.0)?.toFloat() ?: 1f
      voiceRate = settings?.optDouble("voiceRate", 1.0)?.toFloat() ?: 1f
    }
  }

  fun speakWithPhoneTts(text: String) {
    val engine = tts.value ?: return
    engine.setPitch(voicePitch)
    engine.setSpeechRate(voiceRate)
    // Drop action notes like （笑顔で頷く） that should not be read aloud.
    engine.speak(text.replace(Regex("（[^）]*）|\\([^)]*\\)"), ""), android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "partner-${System.currentTimeMillis()}")
  }

  fun speak(text: String) {
    val pid = partnerId
    if (pid != null) {
      coroutineScope.launch {
        val played =
          try {
            PartnerVoicePlayer.speak(ttsContext, viewModel, pid, text)
          } catch (e: Exception) {
            statusMessage = friendlyError("読み上げ", e)
            true
          }
        if (!played) speakWithPhoneTts(text)
      }
    } else {
      speakWithPhoneTts(text)
    }
  }
  // "auto" (✨自動判定 - デフォルト), "deep" (🧠熟考 - Bonsai2フルパワー), "instant" (⚡即答 0.5s - 挨拶・緊急用)
  var thinkingMode by rememberSaveable { mutableStateOf("auto") }

  // 記憶表示・編集モーダル
  var showMemoryDialog by remember { mutableStateOf(false) }
  var memoryContent by remember { mutableStateOf("") }
  var isSavingMemory by remember { mutableStateOf(false) }

  // モデル変更ダイアログ
  var showModelDialog by remember { mutableStateOf(false) }
  var availableModels by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  var isLoadingModels by remember { mutableStateOf(false) }
  var isSwitchingModel by remember { mutableStateOf(false) }

  val listState = rememberLazyListState()
  var autoScrollEnabled by remember { mutableStateOf(true) }

  // C-4 修正: ユーザーが手動で上にスクロールしたら自動スクロールを無効化
  LaunchedEffect(listState.isScrollInProgress) {
    if (listState.isScrollInProgress && messages.isNotEmpty()) {
      // ユーザーが最下部から5件以上離れたら自動スクロール無効
      val lastVisibleIdx = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      if (lastVisibleIdx < messages.size - 5) {
        autoScrollEnabled = false
      }
    }
  }

  LaunchedEffect(messages.size, isThinking) {
    if (autoScrollEnabled && messages.isNotEmpty()) {
      listState.scrollToBottomSafely(messages.size - 1)
    }
  }

  // ステータスメッセージの自動消去タイマー
  LaunchedEffect(statusMessage) {
    // Errors stay until dismissed so they can be read and copied; info messages fade out.
    if (statusMessage.isNotBlank() && !statusMessage.contains("（詳細:")) {
      delay(4000L)
      statusMessage = ""
    }
  }

  fun loadChat() {
    val currentId = partnerId ?: return
    coroutineScope.launch {
      try {
        val resStr = viewModel.getPartnerChat(currentId)
        val obj = JSONObject(resStr)
        val pObj = obj.optJSONObject("partner")
        if (pObj != null) {
          partnerName = pObj.optString("name", "AIパートナー")
          partnerAvatarUrl = pObj.optString("avatarUrl", "")
          partnerPersona = pObj.optString("persona", "")
          partnerModel = pObj.optString("modelId", "")
        }
        val arr = obj.optJSONArray("history") ?: JSONArray()
        val list = mutableListOf<PartnerSingleChatMessage>()
        for (i in 0 until arr.length()) {
          val item = arr.getJSONObject(i)
          list.add(
            PartnerSingleChatMessage(
              id = item.optString("id", "msg-$i"),
              role = item.optString("role", "assistant"),
              content = item.optString("content", ""),
              timestamp = item.optLong("timestamp", 0L),
            )
          )
        }
        messages = list
        if (list.isNotEmpty()) {
          listState.scrollToBottomSafely(list.size - 1)
        }
      } catch (e: Exception) {
        // C-3 修正: エラーメッセージ改善
        statusMessage = friendlyError("履歴の読み込み", e)
      }
    }
  }

  fun sendMessage() {
    val currentId = partnerId ?: return
    val text = inputText.trim()
    if (text.isBlank() || isThinking) return

    val tempUserMsg = PartnerSingleChatMessage(
      id = "temp-user-${System.currentTimeMillis()}",
      role = "user",
      content = text,
      timestamp = System.currentTimeMillis(),
    )
    messages = messages + tempUserMsg
    inputText = ""
    isThinking = true

    sendJob = coroutineScope.launch {
      try {
        listState.scrollToBottomSafely(messages.size - 1)
        val resStr = viewModel.sendPartnerMessage(currentId, text, thinkingMode)
        val obj = JSONObject(resStr)
        val arr = obj.optJSONArray("history") ?: JSONArray()
        val list = mutableListOf<PartnerSingleChatMessage>()
        for (i in 0 until arr.length()) {
          val item = arr.getJSONObject(i)
          list.add(
            PartnerSingleChatMessage(
              id = item.optString("id", "msg-$i"),
              role = item.optString("role", "assistant"),
              content = item.optString("content", ""),
              timestamp = item.optLong("timestamp", 0L),
            )
          )
        }
        // --- 🌟 スラスラ応答（リアルタイム・タイピングストリーミング演出） ---
        val lastAssistant = list.lastOrNull { it.role == "assistant" }
        if (lastAssistant != null && lastAssistant.content.isNotBlank()) {
          val fullText = lastAssistant.content
          val historyBase = list.dropLast(1)
          isThinking = false // 思考中ドットを閉じる

          // 1〜3文字ずつスラスラと流れるようにリアルタイム描画
          val step = if (fullText.length > 200) 3 else if (fullText.length > 80) 2 else 1
          val delayMs = 12L
          var currentLen = 0
          while (currentLen < fullText.length) {
            currentLen = (currentLen + step).coerceAtMost(fullText.length)
            val partialMsg = lastAssistant.copy(content = fullText.substring(0, currentLen))
            messages = historyBase + partialMsg
            listState.scrollToBottomSafely(messages.size - 1, animate = false)
            delay(delayMs)
          }
        }
        messages = list
        if (list.isNotEmpty()) {
          listState.scrollToBottomSafely(list.size - 1)
        }

        // H-2 修正: extractPartnerFactsを削除（PC側autoLearnPartnerMemoryで自動実行されるため二重呼出しだった）
        launch {
          try {
            viewModel.updatePartnerBond(currentId, text.length)
          } catch (_: Exception) {}
        }
      } catch (e: Exception) {
        // C-3 修正: エラーメッセージ改善
        statusMessage = friendlyError("メッセージ送信", e)
      } finally {
        isThinking = false
      }
    }
  }

  fun clearChat() {
    val currentId = partnerId ?: return
    coroutineScope.launch {
      try {
        viewModel.clearPartnerChat(currentId)
        messages = emptyList()
        statusMessage = "会話履歴をリセットしました（記憶は保持されます）"
      } catch (e: Exception) {
        statusMessage = friendlyError("履歴リセット", e)
      }
    }
  }

  fun openMemory() {
    val currentId = partnerId ?: return
    coroutineScope.launch {
      try {
        val resStr = viewModel.getPartnerMemory(currentId)
        val obj = JSONObject(resStr)
        memoryContent = obj.optString("longTermMemory", "")
        showMemoryDialog = true
      } catch (e: Exception) {
        statusMessage = friendlyError("記憶の読み込み", e)
      }
    }
  }

  fun saveMemory() {
    val currentId = partnerId ?: return
    isSavingMemory = true
    coroutineScope.launch {
      try {
        viewModel.updatePartnerMemory(currentId, memoryContent, null)
        showMemoryDialog = false
        statusMessage = "${partnerName}の記憶を更新しました！"
      } catch (e: Exception) {
        statusMessage = friendlyError("記憶の保存", e)
      } finally {
        isSavingMemory = false
      }
    }
  }

  fun openModelDialog() {
    showModelDialog = true
    isLoadingModels = true
    coroutineScope.launch {
      try {
        val resultJson = viewModel.runGatewayRpc(GatewayMethod.OllamaModelsList, "{}", timeoutMs = 30_000L)
        val rawObj = JSONObject(resultJson)
        val obj = rawObj.optJSONObject("payload") ?: rawObj
        val modelsArr = obj.optJSONArray("models")
        val list = mutableListOf<Pair<String, String>>()
        if (modelsArr != null) {
          for (i in 0 until modelsArr.length()) {
            val m = modelsArr.getJSONObject(i)
            val name = m.optString("name", "")
            val size = m.optLong("size", 0L)
            val sizeStr = if (size > 0) String.format("%.1f GB", size.toDouble() / (1024 * 1024 * 1024)) else ""
            if (name.isNotBlank()) {
              list.add(name to sizeStr)
            }
          }
        }
        availableModels = list
      } catch (e: Exception) {
        statusMessage = "モデル一覧の取得に失敗: ${e.message}"
      } finally {
        isLoadingModels = false
      }
    }
  }

  fun switchModelTo(modelName: String) {
    val currentId = partnerId ?: return
    val cleanName = modelName.replace("ollama/", "")
    isSwitchingModel = true
    statusMessage = "${partnerName}のモデルを切り替え中: $cleanName..."
    coroutineScope.launch {
      try {
        val params = JSONObject().apply {
          put("model", modelName)
          put("partnerId", currentId)
        }
        val resultJson = viewModel.runGatewayRpc(GatewayMethod.OllamaModelSwitch, params.toString(), timeoutMs = 60_000L)
        val rawObj = JSONObject(resultJson)
        val obj = rawObj.optJSONObject("payload") ?: rawObj
        val isSuccess = obj.optBoolean("success", false) || obj.optBoolean("ok", false)
        if (isSuccess) {
          partnerModel = modelName
          showModelDialog = false
          statusMessage = "${partnerName}のモデルを「$cleanName」に切り替えました！"
        } else {
          val errMsg = obj.optString("error", "切替結果が不正です")
          statusMessage = "モデル切替失敗: $errMsg"
        }
      } catch (e: Exception) {
        statusMessage = "モデル切替失敗: ${e.message}"
      } finally {
        isSwitchingModel = false
      }
    }
  }

  LaunchedEffect(partnerId) {
    loadChat()
  }

  ClawScaffold(
    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 6.dp),
    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
  ) {
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
      // --- ヘッダー（最大高さ56dpでチャット欄のスペースを完全死守） ---
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 56.dp)
          .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f, fill = false),
        ) {
          ClawIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = nativeString("Back"),
            onClick = onBack,
          )
          Spacer(modifier = Modifier.width(6.dp))
          PartnerAvatar(
            avatarUrl = partnerAvatarUrl,
            name = partnerName,
            size = 38.dp,
          )
          Spacer(modifier = Modifier.width(8.dp))
          val shortModelName = remember(partnerModel) {
            if (partnerModel.isBlank()) ""
            else partnerModel.removePrefix("ollama/").substringAfterLast("/")
          }
          Column(
            modifier = Modifier.weight(1f, fill = false)
          ) {
            Text(
              text = partnerName,
              style = ClawTheme.type.section.copy(fontSize = 15.sp),
              color = ClawTheme.colors.text,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = if (shortModelName.isNotBlank()) "専用チャット · $shortModelName ▾" else "個別トークルーム ▾",
              style = ClawTheme.type.caption.copy(fontSize = 11.sp),
              color = ClawTheme.colors.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { openModelDialog() }
            )
          }
        }

        var headerMenuExpanded by remember { mutableStateOf(false) }

        // 右側のアクションメニュー（省スペース設計：縦三点リーダーに集約）
        Box {
          ClawIconButton(
            icon = Icons.Default.MoreVert,
            contentDescription = "メニュー",
            onClick = { headerMenuExpanded = true },
          )
          DropdownMenu(
            expanded = headerMenuExpanded,
            onDismissRequest = { headerMenuExpanded = false },
          ) {
            DropdownMenuItem(
              text = { Text("🤖 AIモデル変更・切替") },
              onClick = {
                headerMenuExpanded = false
                openModelDialog()
              },
            )
            DropdownMenuItem(
              text = { Text(if (autoScrollEnabled) "📜 スクロールON（追従）" else "⏸️ スクロールOFF（固定）") },
              onClick = {
                autoScrollEnabled = !autoScrollEnabled
                headerMenuExpanded = false
              },
            )
            DropdownMenuItem(
              text = { Text("⚙️ 9大属性キャラクター設定") },
              onClick = {
                headerMenuExpanded = false
                onOpenCharacterSheet()
              },
            )
            DropdownMenuItem(
              text = { Text("🧠 この子の記憶 (MEMORY.md)") },
              onClick = {
                headerMenuExpanded = false
                openMemory()
              },
            )
            DropdownMenuItem(
              text = { Text("🔄 会話を再読み込み") },
              onClick = {
                headerMenuExpanded = false
                loadChat()
              },
            )
            DropdownMenuItem(
              text = { Text("🗑️ 会話履歴をクリア", color = ClawTheme.colors.danger) },
              onClick = {
                headerMenuExpanded = false
                clearChat()
              },
            )
          }
        }
      }

      // ステータスメッセージ
      if (statusMessage.isNotBlank()) {
        val clipContext = androidx.compose.ui.platform.LocalContext.current
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ClawTheme.colors.surfaceRaised)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Long-press selects the text; the button copies the whole message.
          androidx.compose.foundation.text.selection.SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
              text = statusMessage,
              style = ClawTheme.type.caption.copy(fontSize = 12.sp),
              color = ClawTheme.colors.primary,
            )
          }
          TextButton(onClick = {
            val clipboard = clipContext.getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("OpenClaw error", statusMessage))
          }) {
            Text("コピー", style = ClawTheme.type.caption.copy(fontSize = 12.sp))
          }
          TextButton(onClick = { statusMessage = "" }) {
            Text("✕", style = ClawTheme.type.caption.copy(fontSize = 12.sp))
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
      }

      // --- チャットタイムライン（Boxラップで最新ジャンプボタンをフロート表示） ---
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(vertical = 8.dp),
        ) {
          if (messages.isEmpty() && !isThinking) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(
                    text = "👋 ${partnerName}との個別トーク",
                    style = ClawTheme.type.section.copy(fontSize = 15.sp),
                    color = ClawTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "このチャットの内容と学習記憶は、${partnerName}専用に保存されます。",
                    style = ClawTheme.type.caption.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = ClawTheme.colors.textMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }

          items(messages) { msg ->
            val isUser = msg.role == "user"
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            ) {
              if (!isUser) {
                PartnerAvatar(
                  avatarUrl = partnerAvatarUrl,
                  name = partnerName,
                  size = 32.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
              }

              Box(
                modifier = Modifier
                  .fillMaxWidth(0.82f)
                  .clip(
                    RoundedCornerShape(
                      topStart = 14.dp,
                      topEnd = 14.dp,
                      bottomStart = if (isUser) 14.dp else 2.dp,
                      bottomEnd = if (isUser) 2.dp else 14.dp,
                    )
                  )
                  .background(
                    if (isUser) ClawTheme.colors.primary.copy(alpha = 0.22f)
                    else ClawTheme.colors.surfaceRaised
                  )
                  .padding(horizontal = 14.dp, vertical = 10.dp)
              ) {
                Column {
                  if (!isUser) {
                    Text(
                      text = partnerName,
                      style = ClawTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                      color = ClawTheme.colors.primary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                  }
                  Text(
                    text = msg.content,
                    style = ClawTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = ClawTheme.colors.text,
                  )
                  if (!isUser) {
                    Text(
                      text = "🔊 読み上げ",
                      style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                      color = ClawTheme.colors.textMuted,
                      modifier =
                        Modifier
                          .padding(top = 4.dp)
                          .clip(RoundedCornerShape(6.dp))
                          .clickable { speak(msg.content) }
                          .padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                    Text(
                      text = "🙅 キャラっぽくない",
                      style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                      color = ClawTheme.colors.textMuted,
                      modifier =
                        Modifier
                          .padding(top = 4.dp)
                          .clip(RoundedCornerShape(6.dp))
                          .clickable {
                            feedbackNote = ""
                            feedbackTarget = msg
                          }.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                  }
                  // タイムスタンプ表示
                  if (msg.timestamp > 0L) {
                    val timeStr = remember(msg.timestamp) {
                      SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                    }
                    Text(
                      text = timeStr,
                      style = ClawTheme.type.caption.copy(fontSize = 10.sp),
                      color = ClawTheme.colors.textMuted.copy(alpha = 0.6f),
                      modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                      textAlign = TextAlign.End,
                    )
                  }
                }
              }
            }
          }

          if (isThinking) {
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                PartnerAvatar(
                  avatarUrl = partnerAvatarUrl,
                  name = partnerName,
                  size = 32.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ClawTheme.colors.surfaceRaised)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                  ThinkingDotsAnimation()
                }
              }
            }
          }
        }

        // スクロールOFF中または上部を閲覧中に表示される「↓ 最新へ」ボタン
        if (!autoScrollEnabled || listState.canScrollForward) {
          Box(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(end = 8.dp, bottom = 8.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(ClawTheme.colors.surfaceRaised)
              .border(1.dp, ClawTheme.colors.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
              .clickable {
                autoScrollEnabled = true // C-4 修正: 再有効化
                coroutineScope.launch {
                  if (messages.isNotEmpty()) {
                    listState.scrollToBottomSafely(messages.size - 1)
                  }
                }
              }
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "最新へ",
                tint = ClawTheme.colors.primary,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "最新へ",
                style = ClawTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = ClawTheme.colors.primary,
              )
            }
          }
        }
      }

      // --- 思考モード切替セレクター（自動 / 即答 / 熟考） ---
      val thinkingModes = remember {
        listOf(
          "auto" to "✨ 自動 (おすすめ)",
          "instant" to "⚡ 即答 (0.5s)",
          "deep" to "🧠 熟考",
        )
      }
      LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        items(thinkingModes.size) { index ->
          val (mode, label) = thinkingModes[index]
          val isSelected = thinkingMode == mode
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) ClawTheme.colors.primary.copy(alpha = 0.2f) else ClawTheme.colors.surfaceRaised,
            border = BorderStroke(
              1.dp,
              if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.primary.copy(alpha = 0.15f),
            ),
            onClick = { thinkingMode = mode },
          ) {
            Text(
              text = label,
              style = ClawTheme.type.caption.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              ),
              color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }
        }
      }

      // --- クイックアクションバー ---
      val quickActions = remember {
        listOf(
          "🧠" to "記憶",
          "💖" to "絆",
          "🔮" to "占い",
          "🎮" to "ゲーム",
          "🌙" to "夢日記",
          "📊" to "感情",
          "📖" to "日記",
          "🌐" to "Web検索",
        )
      }
      val quickActionHandlers: List<() -> Unit> = listOf(
        onOpenMemory,
        onOpenBond,
        onOpenDailySettings,
        onOpenMiniGame,
        onOpenDream,
        onOpenMood,
        onOpenDiary,
        {
          if (!inputText.startsWith("【Web検索】")) {
            inputText = "【Web検索】 $inputText"
          }
        },
      )
      LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        items(quickActions.size) { index ->
          QuickActionChip(
            emoji = quickActions[index].first,
            label = quickActions[index].second,
            onClick = quickActionHandlers[index],
          )
        }
      }

      // --- 入力バー（アイコンFAB送信ボタン） ---
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ClawTextField(
          value = inputText,
          onValueChange = { inputText = it },
          placeholder = "${partnerName}にメッセージを送信...",
          modifier = Modifier.weight(1f),
        )
        val canSend = inputText.isNotBlank() && !isThinking
        FloatingActionButton(
          onClick = { if (canSend) sendMessage() },
          modifier = Modifier.size(42.dp),
          containerColor = if (canSend) ClawTheme.colors.primary else ClawTheme.colors.surfaceRaised,
          elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
          shape = CircleShape,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "送信",
            tint = if (canSend) ClawTheme.colors.canvas else ClawTheme.colors.textMuted,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }

    // --- この子の記憶ダイアログ ---
    if (showMemoryDialog) {
      AlertDialog(
        onDismissRequest = { showMemoryDialog = false },
        title = {
          Text(
            text = "🧠 ${partnerName}の記憶 (MEMORY.md)",
            style = ClawTheme.type.section,
            color = ClawTheme.colors.text,
          )
        },
        text = {
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "このパートナーが会話から学習した長期記憶です。直接修正・追記することもできます。",
              style = ClawTheme.type.caption.copy(fontSize = 12.sp),
              color = ClawTheme.colors.textMuted,
            )
            ClawTextField(
              value = memoryContent,
              onValueChange = { memoryContent = it },
              placeholder = "記憶内容...",
              modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            )
          }
        },
        confirmButton = {
          ClawPrimaryButton(
            text = if (isSavingMemory) "保存中..." else "記憶を更新",
            onClick = { saveMemory() },
          )
        },
        dismissButton = {
          ClawSecondaryButton(
            text = "閉じる",
            onClick = { showMemoryDialog = false },
          )
        },
      )
    }

    // --- 「キャラっぽくない」フィードバック ---
    feedbackTarget?.let { target ->
      AlertDialog(
        onDismissRequest = { feedbackTarget = null },
        title = { Text("キャラっぽくない返答", style = ClawTheme.type.section, color = ClawTheme.colors.text) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "どこが違うか書くと、次からの会話に反映されます（空欄でも送れます）。",
              style = ClawTheme.type.caption.copy(fontSize = 12.sp),
              color = ClawTheme.colors.textMuted,
            )
            ClawTextField(
              value = feedbackNote,
              onValueChange = { feedbackNote = it },
              placeholder = "例: 一人称は「俺」。もっとぶっきらぼうに話す",
              minLines = 3,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        },
        confirmButton = {
          TextButton(onClick = {
            val pid = partnerId
            feedbackTarget = null
            if (pid != null) {
              coroutineScope.launch {
                statusMessage =
                  try {
                    val params = org.json.JSONObject().apply {
                      put("partnerId", pid)
                      put("message", target.content)
                      if (feedbackNote.isNotBlank()) put("note", feedbackNote.trim())
                    }
                    viewModel.runGatewayRpc(GatewayMethod.PartnerFeedbackAdd, params.toString())
                    "修正メモを${partnerName}のキャラクターシートに追加しました。"
                  } catch (e: Exception) {
                    friendlyError("フィードバックの保存", e)
                  }
              }
            }
          }) { Text("送る") }
        },
        dismissButton = { TextButton(onClick = { feedbackTarget = null }) { Text("やめる") } },
      )
    }

    // --- モデル変更・切替ダイアログ ---
    if (showModelDialog) {
      AlertDialog(
        onDismissRequest = {
          if (!isSwitchingModel) showModelDialog = false
        },
        title = {
          Text(
            text = "🤖 ${partnerName}のAIモデル変更",
            style = ClawTheme.type.section,
            color = ClawTheme.colors.text,
          )
        },
        text = {
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            val curClean = partnerModel.replace("ollama/", "")
            Text(
              text = if (curClean.isNotBlank()) "現在設定中: $curClean" else "モデル未設定",
              style = ClawTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
              color = ClawTheme.colors.primary,
            )

            if (isSwitchingModel) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "⏳ モデルを切り替えてVRAMに常駐させています...",
                  style = ClawTheme.type.caption.copy(fontSize = 13.sp),
                  color = ClawTheme.colors.text,
                )
              }
            } else if (isLoadingModels) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "📡 インストール済みモデルを取得中...",
                  style = ClawTheme.type.caption.copy(fontSize = 13.sp),
                  color = ClawTheme.colors.textMuted,
                )
              }
            } else if (availableModels.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "利用可能なモデルが見つかりません。Ollamaカタログ画面でモデルをダウンロードしてください。",
                  style = ClawTheme.type.caption.copy(fontSize = 12.sp),
                  color = ClawTheme.colors.textMuted,
                )
              }
            } else {
              Text(
                text = "切り替えたいモデルをタップしてください:",
                style = ClawTheme.type.caption.copy(fontSize = 12.sp),
                color = ClawTheme.colors.textMuted,
              )
              LazyColumn(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                items(availableModels) { (name, size) ->
                  val isCurrent = (name == curClean || name == partnerModel || "ollama/$name" == partnerModel)
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(if (isCurrent) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised)
                      .border(
                        width = if (isCurrent) 1.5.dp else 0.5.dp,
                        color = if (isCurrent) ClawTheme.colors.primary else ClawTheme.colors.border,
                        shape = RoundedCornerShape(8.dp),
                      )
                      .clickable(enabled = !isSwitchingModel) {
                        switchModelTo(name)
                      }
                      .padding(horizontal = 12.dp, vertical = 10.dp)
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                          text = name,
                          style = ClawTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                          color = ClawTheme.colors.text,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        if (size.isNotBlank()) {
                          Text(
                            text = "サイズ: $size",
                            style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                            color = ClawTheme.colors.textMuted,
                          )
                        }
                      }
                      if (isCurrent) {
                        Text(
                          text = "✓ 使用中",
                          style = ClawTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                          color = ClawTheme.colors.primary,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          ClawSecondaryButton(
            text = "閉じる",
            onClick = { if (!isSwitchingModel) showModelDialog = false },
          )
        },
      )
    }
  }
}

/**
 * クイックアクションチップ。Material3 Surface でリップルエフェクト付き。
 */
@Composable
private fun QuickActionChip(
  emoji: String,
  label: String,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = ClawTheme.colors.surfaceRaised,
    border = BorderStroke(1.dp, ClawTheme.colors.primary.copy(alpha = 0.25f)),
    onClick = onClick,
  ) {
    Text(
      text = "$emoji $label",
      style = ClawTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
      color = ClawTheme.colors.primary,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
  }
}

/**
 * 3ドット波打ちアニメーション（ChatGPT風の考え中インジケーター）。
 */
@Composable
private fun ThinkingDotsAnimation() {
  val transition = rememberInfiniteTransition(label = "thinking")
  Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(3) { index ->
      val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
          animation = tween(durationMillis = 400, delayMillis = index * 120),
          repeatMode = RepeatMode.Reverse,
        ),
        label = "dot$index",
      )
      Box(
        modifier = Modifier
          .size(8.dp)
          .offset(y = offsetY.dp)
          .clip(CircleShape)
          .background(ClawTheme.colors.primary.copy(alpha = 0.6f)),
      )
    }
  }
}
