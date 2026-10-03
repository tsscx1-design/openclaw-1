package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.PartnerAvatar
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class GroupMessage(
  val id: String,
  val sender: String,
  val content: String,
  val isUser: Boolean,
  val avatarUrl: String? = null,
  val timestamp: Long = System.currentTimeMillis(),
  /** Who spoke, so the reply can be read aloud in that partner's own voice. */
  val partnerId: String? = null,
)

@Composable
internal fun GroupChatScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var availablePartners by remember { mutableStateOf<List<PartnerItem>>(emptyList()) }
  var selectedPartnerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var messages by remember { mutableStateOf<List<GroupMessage>>(emptyList()) }
  var inputText by remember { mutableStateOf("") }

  val listState = rememberLazyListState()
  var autoScrollEnabled by remember { mutableStateOf(true) }

  // 自律対話ループ関連のステート
  var isLoopRunning by remember { mutableStateOf(false) }
  var thinkingPartnerName by remember { mutableStateOf<String?>(null) }
  var nextDesignatedPartnerId by remember { mutableStateOf<String?>(null) }
  var pendingInterruptionText by remember { mutableStateOf<String?>(null) }
  var loopJob by remember { mutableStateOf<Job?>(null) }

  // Reading aloud: each reply in its partner's own PC voice, one at a time (never overlapping).
  val context = LocalContext.current
  val voicePrefs = remember { context.getSharedPreferences("partner_voice", android.content.Context.MODE_PRIVATE) }
  var groupAutoRead by remember { mutableStateOf(voicePrefs.getBoolean("group_auto_read", false)) }
  val playingKey by PartnerVoicePlayer.playingKey.collectAsState()
  val voiceQueue = remember { Channel<GroupMessage>(Channel.UNLIMITED) }
  /** Replies queued or playing; the loop lets the next reply be written while one plays. */
  var voicePending by remember { mutableStateOf(0) }

  // The group chat runs its own model on the PC (Bonsai is swapped out while it is open).
  var groupModels by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  var groupModelId by remember { mutableStateOf("") }
  var modelReady by remember { mutableStateOf(false) }
  var modelStatus by remember { mutableStateOf("") }

  fun enterGroupModel() {
    coroutineScope.launch {
      modelReady = false
      modelStatus = "マルチチャット用のモデルを準備中…（30秒ほど）"
      try {
        val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.GroupchatEnter, "{}", timeoutMs = 300_000))
        modelStatus =
          when {
            res.optBoolean("busy") -> "画像・動画の生成中のため、いつものモデルで話します"
            res.optString("modelId") == "main" -> "いつものモデルで話します"
            else -> ""
          }
      } catch (e: Exception) {
        modelStatus = "モデルの準備に失敗しました: ${e.message}"
      } finally {
        modelReady = true
      }
    }
  }

  LaunchedEffect(Unit) {
    runCatching {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.GroupchatModels, "{}"))
      val arr = res.optJSONArray("models") ?: JSONArray()
      groupModels =
        listOf("main" to "いつものモデル") +
          (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { it.optString("id") to it.optString("label") }
      groupModelId = res.optString("current")
    }
    enterGroupModel()
  }

  LaunchedEffect(Unit) {
    for (msg in voiceQueue) {
      val pid = msg.partnerId
      if (pid != null && groupAutoRead) {
        try {
          if (PartnerVoicePlayer.speak(context, viewModel, pid, msg.content, key = msg.id)) {
            PartnerVoicePlayer.playingKey.first { it != msg.id }
          }
        } catch (e: kotlinx.coroutines.CancellationException) {
          throw e
        } catch (_: Exception) {
          // A partner without a PC voice is simply not read aloud.
        }
      }
      voicePending = (voicePending - 1).coerceAtLeast(0)
    }
  }

  fun stopVoice() {
    while (voiceQueue.tryReceive().isSuccess) {
      // Drop replies that were waiting to be read.
    }
    voicePending = 0
    PartnerVoicePlayer.stop()
  }

  // メッセージ追加またはAI思考時の自動スクロール
  LaunchedEffect(messages.size, thinkingPartnerName) {
    if (autoScrollEnabled && messages.isNotEmpty()) {
      listState.scrollToBottomSafely(messages.size - 1)
    }
  }

  // M-1 修正: 画面表示時にPC側から保存済みグループチャット履歴を復元
  LaunchedEffect(selectedPartnerIds) {
    if (selectedPartnerIds.isEmpty()) return@LaunchedEffect
    try {
      val partnerIdsJson = JSONArray()
      selectedPartnerIds.forEach { partnerIdsJson.put(it) }
      val params = JSONObject().apply { put("partnerIds", partnerIdsJson) }
      val resultJson = viewModel.runGatewayRpc(
        GatewayMethod.GroupchatHistoryGet,
        params.toString(),
        timeoutMs = 10_000
      )
      val obj = JSONObject(resultJson)
      val historyArr = obj.optJSONArray("history") ?: return@LaunchedEffect
      val restored = mutableListOf<GroupMessage>()
      for (i in 0 until historyArr.length()) {
        val item = historyArr.getJSONObject(i)
        restored.add(
          GroupMessage(
            id = "restored-$i-${item.optLong("timestamp", 0)}",
            sender = item.optString("sender", ""),
            content = item.optString("content", ""),
            isUser = item.optBoolean("isUser", false),
            timestamp = item.optLong("timestamp", System.currentTimeMillis()),
          )
        )
      }
      if (restored.isNotEmpty()) {
        messages = restored
      }
    } catch (_: Exception) {
      // 履歴復元失敗は無視（新規チャットとして開始）
    }
  }

  // M-3 修正: DisposableEffectでループジョブを確実にキャンセル
  // （画面回転、Activity破棄、タスクキル等でBackHandlerが発火しない場合の対策）
  DisposableEffect(Unit) {
    onDispose {
      isLoopRunning = false
      loopJob?.cancel()
      PartnerVoicePlayer.stop()
      // Bring the main model (Bonsai) back for the 1:1 and agent chats.
      viewModel.leaveGroupChatModel()
    }
  }

  // 端末の戻るボタンで確実に前画面に戻り、ループを終了
  BackHandler {
    isLoopRunning = false
    loopJob?.cancel()
    onBack()
  }

  fun stopLoop() {
    isLoopRunning = false
    loopJob?.cancel()
    thinkingPartnerName = null
    stopVoice()
  }

  fun startLoop() {
    if (isLoopRunning || selectedPartnerIds.isEmpty()) return
    isLoopRunning = true

    loopJob = coroutineScope.launch {
      while (isLoopRunning) {
        // While one reply is read aloud the next one is written; wait if more are queued.
        if (groupAutoRead) snapshotFlow { voicePending }.first { it <= 1 }
        try {
          // 割り込みメッセージの取得
          val interruption = pendingInterruptionText
          pendingInterruptionText = null

          val targetId = nextDesignatedPartnerId

          // M-2 修正: パートナー数に応じた動的スライス（N人 × 5ターン、最低10件/最大50件）
          // 3人以上のグループでも各パートナーに十分な文脈を渡す
          val historySliceCount = (selectedPartnerIds.size * 5).coerceIn(10, 50)
          val historyJson = JSONArray()
          for (m in messages.takeLast(historySliceCount)) {
            historyJson.put(
              JSONObject().apply {
                put("sender", m.sender)
                put("content", m.content)
                put("isUser", m.isUser)
              }
            )
          }

          val partnerIdsJson = JSONArray()
          selectedPartnerIds.forEach { partnerIdsJson.put(it) }

          val params = JSONObject().apply {
            put("partnerIds", partnerIdsJson)
            if (!targetId.isNullOrBlank()) {
              put("targetPartnerId", targetId)
            }
            if (!interruption.isNullOrBlank()) {
              put("interruptionMessage", interruption)
            }
            put("history", historyJson)
          }

          val targetPartner = availablePartners.find { it.id == targetId }
          thinkingPartnerName = targetPartner?.name ?: "パートナー"

          val resultJson = viewModel.runGatewayRpc(
            GatewayMethod.GroupchatTurn,
            params.toString(),
            timeoutMs = 60_000
          )
          val obj = JSONObject(resultJson)
          val replyObj = obj.optJSONObject("reply")
          val senderName = replyObj?.optString("sender")?.takeIf { it.isNotBlank() }
            ?: obj.optString("sender", "パートナー")
          val content = replyObj?.optString("content")?.takeIf { it.isNotBlank() }
            ?: obj.optString("content", "")
          val avatarUrl = replyObj?.optString("avatarUrl")?.takeIf { it.isNotBlank() }
            ?: obj.optString("avatarUrl", "")
          val nextId = obj.optString("nextDesignatedPartnerId", "").takeIf { it.isNotBlank() }
          val speakerId = replyObj?.optString("partnerId")?.takeIf { it.isNotBlank() }

          if (content.isNotBlank()) {
            val reply = GroupMessage(
              id = "partner-${System.currentTimeMillis()}",
              sender = senderName,
              content = content,
              isUser = false,
              avatarUrl = avatarUrl,
              partnerId = speakerId,
            )
            messages = messages + reply
            nextDesignatedPartnerId = nextId
            if (groupAutoRead && speakerId != null) {
              voicePending += 1
              voiceQueue.trySend(reply)
            }
          }
        } catch (e: Exception) {
          messages = messages + GroupMessage(
            id = "err-${System.currentTimeMillis()}",
            sender = "システム",
            content = "エラーが発生しました: ${e.message}",
            isUser = false
          )
          stopLoop()
          break
        } finally {
          thinkingPartnerName = null
        }

        // 会話の自然な間（1.5秒）
        if (isLoopRunning) {
          delay(1500)
        }
      }
    }
  }

  fun sendMessage() {
    val text = inputText.trim()
    if (text.isBlank() || selectedPartnerIds.isEmpty()) return

    val userMsg = GroupMessage(
      id = "user-${System.currentTimeMillis()}",
      sender = "あなた",
      content = text,
      isUser = true
    )
    messages = messages + userMsg
    inputText = ""

    // ユーザー発言を割り込みとしてセット
    pendingInterruptionText = text

    // ループが停止中なら開始、稼働中なら自動的に次のターンで話題を優先
    if (!isLoopRunning) {
      startLoop()
    }
  }

  var isSuggestingTopic by remember { mutableStateOf(false) }

  fun suggestTopic() {
    coroutineScope.launch {
      isSuggestingTopic = true
      try {
        val topic = viewModel.suggestGroupTopic(selectedPartnerIds.toList())
        inputText = topic
      } catch (_: Exception) {
      } finally {
        isSuggestingTopic = false
      }
    }
  }

  fun fetchPartners() {
    coroutineScope.launch {
      try {
        val resultJson = viewModel.runGatewayRpc(GatewayMethod.PartnerList, "{}")
        val obj = JSONObject(resultJson)
        val arr = obj.optJSONArray("partners") ?: JSONArray()
        val list = mutableListOf<PartnerItem>()
        for (i in 0 until arr.length()) {
          val p = arr.getJSONObject(i)
          list.add(
            PartnerItem(
              id = p.optString("id", ""),
              name = p.optString("name", "名称未設定"),
              avatarUrl = p.optString("avatarUrl", ""),
              persona = p.optString("persona", ""),
              modelId = p.optString("modelId", "ternary-bonsai-2-27b"),
              reasoningEffort = p.optString("reasoningEffort", "none")
            )
          )
        }
        availablePartners = list
        // デフォルトで全員選択
        if (selectedPartnerIds.isEmpty() && list.isNotEmpty()) {
          selectedPartnerIds = list.map { it.id }.toSet()
        }
      } catch (e: Exception) {
        // Handle error
      }
    }
  }

  LaunchedEffect(Unit) {
    fetchPartners()
  }

  ClawScaffold(
    contentPadding = PaddingValues(start = 16.dp, top = 13.dp, end = 16.dp, bottom = 6.dp),
    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .imePadding()
    ) {
      // ヘッダー
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f, fill = false)
        ) {
          ClawIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = nativeString("Back"),
            onClick = {
              stopLoop()
              onBack()
            }
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "自律ループ・グループチャット",
              style = ClawTheme.type.display.copy(fontSize = 15.sp, lineHeight = 19.sp),
              color = ClawTheme.colors.text
            )
            Text(
              text = if (isLoopRunning) "会話ループ稼働中（割り込み可能）" else "会話ループ停止中",
              style = ClawTheme.type.caption.copy(
                fontSize = 11.sp,
                color = if (isLoopRunning) ClawTheme.colors.primary else ClawTheme.colors.textMuted
              )
            )
          }
        }

        // 停止 / 再開ボタン & 自動スクロールトグル
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // 自動スクロール ON/OFF トグル
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(
                if (autoScrollEnabled) ClawTheme.colors.primary.copy(alpha = 0.16f)
                else ClawTheme.colors.surfaceRaised
              )
              .border(
                1.dp,
                if (autoScrollEnabled) ClawTheme.colors.primary.copy(alpha = 0.4f) else ClawTheme.colors.surfaceRaised,
                RoundedCornerShape(12.dp)
              )
              .clickable { autoScrollEnabled = !autoScrollEnabled }
              .padding(horizontal = 8.dp, vertical = 5.dp),
          ) {
            Text(
              text = if (autoScrollEnabled) "📜 スクロールON" else "⏸️ スクロールOFF",
              style = ClawTheme.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
              color = if (autoScrollEnabled) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
            )
          }

          if (isLoopRunning) {
            ClawSecondaryButton(
              text = "🛑 停止",
              onClick = { stopLoop() }
            )
          } else {
            ClawPrimaryButton(
              text = if (modelReady) "▶ 会話開始" else "準備中…",
              onClick = { startLoop() },
              enabled = modelReady,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // 参加パートナー選択バー
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
      ) {
        items(availablePartners) { partner ->
          val isSelected = selectedPartnerIds.contains(partner.id)
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(20.dp))
              .background(if (isSelected) ClawTheme.colors.primary.copy(alpha = 0.2f) else ClawTheme.colors.surfaceRaised)
              .clickable {
                selectedPartnerIds = if (isSelected) {
                  if (selectedPartnerIds.size > 1) selectedPartnerIds - partner.id else selectedPartnerIds
                } else {
                  selectedPartnerIds + partner.id
                }
              }
              .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (isSelected) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "選択中",
                  tint = ClawTheme.colors.primary,
                  modifier = Modifier.size(16.dp).padding(end = 4.dp)
                )
              }
              Text(
                text = partner.name,
                style = ClawTheme.type.caption.copy(fontSize = 12.sp),
                color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.text
              )
            }
          }
        }
      }

      // 使うモデル（マルチチャット専用）と読み上げ
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
      ) {
        item {
          Text("🧠 モデル", style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted)
        }
        items(groupModels) { (id, label) ->
          val selected = groupModelId == id
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.2f) else ClawTheme.colors.surfaceRaised)
              .clickable(enabled = modelReady && !isLoopRunning && !selected) {
                groupModelId = id
                coroutineScope.launch {
                  runCatching {
                    viewModel.runGatewayRpc(GatewayMethod.GroupchatModelSet, JSONObject().put("modelId", id).toString())
                  }
                  if (id == "main") {
                    viewModel.leaveGroupChatModel()
                    modelStatus = "いつものモデルで話します"
                  } else {
                    enterGroupModel()
                  }
                }
              }
              .padding(horizontal = 10.dp, vertical = 5.dp),
          ) {
            Text(
              text = label,
              style = ClawTheme.type.caption.copy(fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
              color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
            )
          }
        }
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
      ) {
        listOf(true to "🔊 いつも読む", false to "🔇 読まない").forEach { (on, label) ->
          val selected = groupAutoRead == on
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.2f) else ClawTheme.colors.surfaceRaised)
              .clickable {
                groupAutoRead = on
                voicePrefs.edit().putBoolean("group_auto_read", on).apply()
                if (!on) stopVoice()
              }
              .padding(horizontal = 10.dp, vertical = 5.dp),
          ) {
            Text(
              text = label,
              style = ClawTheme.type.caption.copy(fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
              color = if (selected) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
            )
          }
        }
        if (playingKey != null || voicePending > 0) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(ClawTheme.colors.primary.copy(alpha = 0.2f))
              .clickable { stopVoice() }
              .padding(horizontal = 10.dp, vertical = 5.dp),
          ) {
            Text(
              text = "⏹ 読み上げを止める",
              style = ClawTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
              color = ClawTheme.colors.primary,
            )
          }
        }
      }
      if (modelStatus.isNotBlank()) {
        Text(modelStatus, style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.primary)
      }

      // チャット履歴（Boxラップで最新ジャンプボタンをフロート表示）
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (messages.isEmpty()) {
            item {
              Box(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "パートナー達と会話を始めましょう！\nメッセージを送信すると、参加中のAI達が掛け合いで返信します。",
                  style = ClawTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                  color = ClawTheme.colors.textMuted
                )
              }
            }
          } else {
            items(messages) { msg ->
              if (msg.isUser) {
                // ユーザー吹き出し（右寄せ）
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End
                ) {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                      .background(ClawTheme.colors.primary)
                      .padding(horizontal = 14.dp, vertical = 10.dp)
                  ) {
                    Text(
                      text = msg.content,
                      style = ClawTheme.type.body.copy(fontSize = 14.sp),
                      color = ClawTheme.colors.primaryText
                    )
                  }
                }
              } else {
                // パートナー吹き出し（左寄せ）
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Start
                ) {
                  PartnerAvatar(
                    avatarUrl = msg.avatarUrl ?: "",
                    name = msg.sender,
                    size = 36.dp
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Column {
                    Text(
                      text = msg.sender,
                      style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                      color = ClawTheme.colors.textMuted,
                      modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                        .background(ClawTheme.colors.surfaceRaised)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                      Text(
                        text = msg.content,
                        style = ClawTheme.type.body.copy(fontSize = 14.sp),
                        color = ClawTheme.colors.text
                      )
                    }
                  }
                }
              }
            }
          }

          if (thinkingPartnerName != null) {
            item {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 44.dp, top = 4.dp)
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp,
                  color = ClawTheme.colors.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "${thinkingPartnerName} が思考・発言中...",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.primary
                )
              }
            }
          }
        }

        // スクロールOFF中または上部閲覧中に表示される「↓ 最新へ」ボタン
        if (!autoScrollEnabled || listState.canScrollForward) {
          Box(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(end = 8.dp, bottom = 8.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(ClawTheme.colors.surfaceRaised)
              .border(1.dp, ClawTheme.colors.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
              .clickable {
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

      // 話題提案・クイックアクションバー
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ClawTheme.colors.surfaceRaised)
            .border(1.dp, ClawTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(enabled = !isSuggestingTopic) { suggestTopic() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
          Text(
            text = if (isSuggestingTopic) "🎲 テーマ考案中..." else "🎲 話題を提案",
            style = ClawTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = ClawTheme.colors.primary,
          )
        }
      }

      // 入力フォーム（親ColumnがimePaddingを持つため、二重のimePaddingを排除してナビバー余白のみ付与）
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        ClawTextField(
          value = inputText,
          onValueChange = { inputText = it },
          placeholder = if (isLoopRunning) "会話に割り込む..." else "メッセージを入力...",
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        ClawIconButton(
          icon = Icons.AutoMirrored.Filled.Send,
          contentDescription = "送信",
          enabled = inputText.isNotBlank() && selectedPartnerIds.isNotEmpty(),
          onClick = { sendMessage() }
        )
      }
    }
  }
}
