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
import ai.openclaw.app.ui.design.PartnerAvatar
import ai.openclaw.app.ui.design.convertUriToBase64DataUrl
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import java.util.Locale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class PartnerItem(
  val id: String,
  val name: String,
  val avatarUrl: String,
  val persona: String,
  val modelId: String,
  val reasoningEffort: String,
)

@Composable
internal fun PartnerManagementScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
  onStartGroupChat: () -> Unit = {},
  onOpenPartnerChat: (String) -> Unit = {},
  onOpenCharacterSheet: (String) -> Unit = {},
) {
  BackHandler(onBack = onBack)

  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  var partners by remember { mutableStateOf<List<PartnerItem>>(emptyList()) }
  var isLoading by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf("") }

  var showEditDialog by remember { mutableStateOf(false) }
  var editingPartnerId by remember { mutableStateOf<String?>(null) }
  var formName by remember { mutableStateOf("") }
  var formAvatarUrl by remember { mutableStateOf("") }
  var formPersona by remember { mutableStateOf("") }
  var formModelId by remember { mutableStateOf("ternary-bonsai-2-27b") }
  var formReasoningEffort by remember { mutableStateOf("none") }
  // パートナーごとの会話設定（partner.settings）
  var setReplyLength by remember { mutableStateOf("normal") }
  var setEmoji by remember { mutableStateOf("some") }
  var setThinking by remember { mutableStateOf("auto") }
  var setTemperature by remember { mutableStateOf(0.6f) }
  var setProactive by remember { mutableStateOf(false) }
  var setTopics by remember { mutableStateOf("") }
  var setVoicePitch by remember { mutableStateOf(1f) }
  var setVoiceRate by remember { mutableStateOf(1f) }
  // PC の声（VOICEVOX / AivisSpeech）。null ならスマホの読み上げ
  var setVoice by remember { mutableStateOf<JSONObject?>(null) }
  var showVoicePicker by remember { mutableStateOf(false) }
  var voiceSpeakers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
  var voicePickerStatus by remember { mutableStateOf("") }

  var mainPartnerId by remember { mutableStateOf<String?>(null) }

  val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      val b64 = convertUriToBase64DataUrl(context, it)
      if (b64 != null) {
        formAvatarUrl = b64
      }
    }
  }

  fun fetchPartners() {
    isLoading = true
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
        partners = list
        mainPartnerId =
          runCatching {
            JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerMainGet, "{}")).optString("partnerId").takeIf { it.isNotBlank() && it != "null" }
          }.getOrNull()
      } catch (e: Exception) {
        statusMessage = "読み込みエラー: ${e.message}"
      } finally {
        isLoading = false
      }
    }
  }

  // Character card import (TavernAI / SillyTavern PNG or JSON).
  val cardPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri ?: return@rememberLauncherForActivityResult
    coroutineScope.launch {
      try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
        val isPng = bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
        val params = JSONObject().apply {
          if (isPng) {
            put("pngBase64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
          } else {
            put("card", String(bytes, Charsets.UTF_8))
          }
        }
        val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerImportCard, params.toString()))
        val name = res.optJSONObject("partner")?.optString("name").orEmpty()
        statusMessage = "「$name」を取り込みました。キャラクターシートは裏作業員が数分で自動作成します。"
        fetchPartners()
      } catch (e: Exception) {
        statusMessage = "カード取り込みエラー: ${e.message}"
      }
    }
  }

  fun setMainPartner(partnerId: String?) {
    coroutineScope.launch {
      try {
        val params = JSONObject().apply { put("partnerId", partnerId ?: JSONObject.NULL) }
        viewModel.runGatewayRpc(GatewayMethod.PartnerMainSet, params.toString())
        mainPartnerId = partnerId
        statusMessage =
          if (partnerId == null) {
            "メインチャットを通常のアシスタントに戻しました。"
          } else {
            "メインチャットで「${partners.firstOrNull { it.id == partnerId }?.name ?: ""}」として話すようにしました。"
          }
      } catch (e: Exception) {
        statusMessage = "設定エラー: ${e.message}"
      }
    }
  }

  fun savePartner() {
    if (formName.isBlank()) return
    coroutineScope.launch {
      try {
        val isNew = editingPartnerId == null
        val params = JSONObject().apply {
          if (!isNew) put("id", editingPartnerId)
          put("name", formName.trim())
          put("avatarUrl", formAvatarUrl.trim())
          put("persona", formPersona.trim())
          put("modelId", formModelId.trim())
          put("reasoningEffort", formReasoningEffort.trim())
        }
        val method = if (isNew) GatewayMethod.PartnerCreate else GatewayMethod.PartnerUpdate
        viewModel.runGatewayRpc(method, params.toString())
        editingPartnerId?.let { pid ->
          val topics = JSONArray()
          setTopics.split('、', ',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(5).forEach { topics.put(it) }
          val settings =
            JSONObject()
              .put("replyLength", setReplyLength)
              .put("emoji", setEmoji)
              .put("thinkingDefault", setThinking)
              .put("temperature", setTemperature.toDouble())
              .put("proactive", setProactive)
              .put("dailyTopics", topics)
              .put("voicePitch", setVoicePitch.toDouble())
              .put("voiceRate", setVoiceRate.toDouble())
              .put(
                "voice",
                setVoice?.let { v ->
                  // The pitch/speed sliders tune the PC voice too (VOICEVOX pitch range is ±0.15).
                  JSONObject(v.toString())
                    .put("speed", setVoiceRate.toDouble())
                    .put("pitch", ((setVoicePitch - 1f) * 0.15f).toDouble().coerceIn(-0.15, 0.15))
                } ?: JSONObject.NULL,
              )
          viewModel.runGatewayRpc(GatewayMethod.PartnerSettingsSet, JSONObject().put("partnerId", pid).put("settings", settings).toString())
        }
        showEditDialog = false
        statusMessage = if (isNew) "「$formName」を作成しました！" else "「$formName」の設定を更新しました！"
        fetchPartners()
      } catch (e: Exception) {
        statusMessage = "保存エラー: ${e.message}"
      }
    }
  }

  fun deletePartner(id: String, name: String) {
    coroutineScope.launch {
      try {
        val params = JSONObject().apply { put("id", id) }
        viewModel.runGatewayRpc(GatewayMethod.PartnerDelete, params.toString())
        statusMessage = "「$name」を削除しました。"
        fetchPartners()
      } catch (e: Exception) {
        statusMessage = "削除エラー: ${e.message}"
      }
    }
  }

  fun openCreateDialog() {
    editingPartnerId = null
    formName = ""
    formAvatarUrl = ""
    formPersona = "優しく親しみやすいAIパートナー。ユーザーの相談に乗り、明るく励ます。"
    formModelId = "ternary-bonsai-2-27b"
    formReasoningEffort = "none"
    showEditDialog = true
  }

  fun openEditDialog(partner: PartnerItem) {
    coroutineScope.launch {
      runCatching {
        val st =
          JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerSettingsGet, JSONObject().put("partnerId", partner.id).toString()))
            .optJSONObject("settings") ?: JSONObject()
        setReplyLength = st.optString("replyLength", "normal")
        setEmoji = st.optString("emoji", "some")
        setThinking = st.optString("thinkingDefault", "auto")
        setTemperature = st.optDouble("temperature", 0.6).toFloat()
        setProactive = st.optBoolean("proactive", false)
        val topics = st.optJSONArray("dailyTopics") ?: JSONArray()
        setTopics = (0 until topics.length()).joinToString("、") { topics.optString(it) }
        setVoicePitch = st.optDouble("voicePitch", 1.0).toFloat()
        setVoiceRate = st.optDouble("voiceRate", 1.0).toFloat()
        setVoice = st.optJSONObject("voice")
      }
    }
    editingPartnerId = partner.id
    formName = partner.name
    formAvatarUrl = partner.avatarUrl
    formPersona = partner.persona
    formModelId = partner.modelId
    formReasoningEffort = partner.reasoningEffort
    showEditDialog = true
  }

  LaunchedEffect(Unit) {
    fetchPartners()
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
              Row {
                ClawIconButton(
                  icon = Icons.Default.Refresh,
                  contentDescription = "再読み込み",
                  onClick = { fetchPartners() }
                )
                ClawIconButton(
                  icon = Icons.Default.FileOpen,
                  contentDescription = "キャラクターカードを取り込む",
                  onClick = { cardPickerLauncher.launch("*/*") }
                )
                ClawIconButton(
                  icon = Icons.Default.Add,
                  contentDescription = "新規パートナー作成",
                  onClick = { openCreateDialog() }
                )
              }
            }
            Text(
              text = "AIパートナー管理",
              style = ClawTheme.type.display.copy(fontSize = 18.sp, lineHeight = 22.sp),
              color = ClawTheme.colors.text
            )
            Text(
              text = "複数のAIパートナーを自由に追加・カスタマイズできます。名前、性格、アイコン、モデルを個別設定可能。",
              style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
              color = ClawTheme.colors.textMuted
            )
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

        // --- パートナー一覧 ---
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            ClawSectionHeader(title = "パートナー一覧 (${partners.size}人)")
            ClawSecondaryButton(
              text = "グループチャットを開始",
              onClick = onStartGroupChat
            )
          }
        }

        if (partners.isEmpty()) {
          item {
            ClawPanel {
              Text(
                text = if (isLoading) "パートナーを読み込み中..." else "パートナーがいません。「+」ボタンから作成できます。",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted
              )
            }
          }
        } else {
          items(partners) { partner ->
            ClawPanel(
              modifier = Modifier.clickable {
                viewModel.selectPartnerForChat(partner.id)
                onOpenPartnerChat(partner.id)
              }
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    PartnerAvatar(
                      avatarUrl = partner.avatarUrl,
                      name = partner.name,
                      size = 46.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                      Text(
                        text = partner.name,
                        style = ClawTheme.type.section.copy(fontSize = 15.sp),
                        color = ClawTheme.colors.text
                      )
                      Text(
                        text = "モデル: ${partner.modelId}",
                        style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                        color = ClawTheme.colors.textMuted
                      )
                    }
                  }
                  Row {
                    ClawIconButton(
                      icon = Icons.Default.Edit,
                      contentDescription = "編集",
                      onClick = { openEditDialog(partner) }
                    )
                    ClawIconButton(
                      icon = Icons.Default.Delete,
                      contentDescription = "削除",
                      onClick = { deletePartner(partner.id, partner.name) }
                    )
                  }
                }

                // ペルソナ設定
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClawTheme.colors.surface)
                    .padding(8.dp)
                ) {
                  Text(
                    text = partner.persona,
                    style = ClawTheme.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = ClawTheme.colors.text,
                    maxLines = 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                  )
                }

                // 個別チャットボタン
                ClawPrimaryButton(
                  text = "💬 ${partner.name}と個別チャットする",
                  onClick = {
                    viewModel.selectPartnerForChat(partner.id)
                    onOpenPartnerChat(partner.id)
                  },
                  modifier = Modifier.fillMaxWidth()
                )

                // メインチャット（ツールが使える方）でこのパートナーとして話す
                val isMain = mainPartnerId == partner.id
                ClawSecondaryButton(
                  text = if (isMain) "✅ メインチャットで話し中（タップで解除）" else "⭐ メインチャットでこのパートナーと話す",
                  onClick = { setMainPartner(if (isMain) null else partner.id) },
                  modifier = Modifier.fillMaxWidth()
                )

                // 9大属性キャラクター設定ボタン
                ClawSecondaryButton(
                  text = "📝 9大属性キャラクター設定",
                  onClick = {
                    onOpenCharacterSheet(partner.id)
                  },
                  modifier = Modifier.fillMaxWidth()
                )
              }
            }
          }
        }
      }
    }

    // --- PCの声の選択（試聴つき） ---
    if (showVoicePicker) {
      val pid = editingPartnerId
      AlertDialog(
        onDismissRequest = { showVoicePicker = false; PartnerVoicePlayer.stop() },
        title = { Text("声を選ぶ", style = ClawTheme.type.section, color = ClawTheme.colors.text) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (voicePickerStatus.isNotBlank()) {
              Text(voicePickerStatus, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            }
            ClawSecondaryButton(
              text = "PCの声を使わない（スマホの声）",
              onClick = { setVoice = null; showVoicePicker = false },
              modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().height(380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              items(voiceSpeakers) { sp ->
                val engine = sp.optString("engine")
                val label = "${sp.optString("speakerName")}（${sp.optString("styleName")}）"
                val voice = JSONObject().put("engine", engine).put("speakerId", sp.optInt("speakerId")).put("label", label)
                val selected = setVoice?.optString("engine") == engine && setVoice?.optInt("speakerId") == sp.optInt("speakerId")
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.18f) else ClawTheme.colors.surfaceRaised)
                      .padding(horizontal = 10.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = ClawTheme.type.body.copy(fontSize = 14.sp), color = ClawTheme.colors.text)
                    Text(if (engine == "aivis") "AivisSpeech" else "VOICEVOX", style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted)
                  }
                  TextButton(onClick = {
                    if (pid == null) return@TextButton
                    voicePickerStatus = "「$label」を再生中…"
                    coroutineScope.launch {
                      voicePickerStatus =
                        try {
                          PartnerVoicePlayer.speak(context, viewModel, pid, "こんにちは。今日もよろしくお願いします。", voiceOverride = voice)
                          ""
                        } catch (e: Exception) {
                          "試聴エラー: ${e.message}"
                        }
                    }
                  }) { Text("▶ 試聴") }
                  TextButton(onClick = { setVoice = voice; showVoicePicker = false; PartnerVoicePlayer.stop() }) { Text("選ぶ") }
                }
              }
            }
          }
        },
        confirmButton = { TextButton(onClick = { showVoicePicker = false; PartnerVoicePlayer.stop() }) { Text("閉じる") } },
      )
    }

    // --- 編集 / 作成ダイアログ ---
    if (showEditDialog) {
      AlertDialog(
        onDismissRequest = { showEditDialog = false },
        title = {
          Text(
            text = if (editingPartnerId == null) "新規AIパートナー作成" else "パートナー詳細設定",
            style = ClawTheme.type.section,
            color = ClawTheme.colors.text
          )
        },
        text = {
          Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
          ) {
            Text(
              text = "パートナー名",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted
            )
            ClawTextField(
              value = formName,
              onValueChange = { formName = it },
              placeholder = "例: ルナ (Luna)",
              modifier = Modifier.fillMaxWidth()
            )

            Text(
              text = "アイコン / アバター画像",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              PartnerAvatar(
                avatarUrl = formAvatarUrl,
                name = formName.ifBlank { "AI" },
                size = 56.dp
              )
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ClawSecondaryButton(
                  text = "📁 写真・画像から選ぶ",
                  onClick = { imagePickerLauncher.launch("image/*") }
                )
                if (formAvatarUrl.isNotBlank()) {
                  ClawSecondaryButton(
                    text = "アイコン消去",
                    onClick = { formAvatarUrl = "" }
                  )
                }
              }
            }

            Text(
              text = "またはプリセットから選ぶ:",
              style = ClawTheme.type.caption.copy(fontSize = 11.5.sp),
              color = ClawTheme.colors.textMuted
            )
            val avatarPresets = listOf("🌙", "🎀", "⭐", "🐱", "🐶", "🦊", "🤖", "🌸", "⚡", "☕", "🎮", "🎨", "👑", "🔮")
            androidx.compose.foundation.lazy.LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(avatarPresets) { emo ->
                Box(
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                      if (formAvatarUrl == emo) ClawTheme.colors.primary.copy(alpha = 0.28f)
                      else ClawTheme.colors.surfaceRaised
                    )
                    .clickable { formAvatarUrl = emo },
                  contentAlignment = Alignment.Center
                ) {
                  Text(text = emo, fontSize = 18.sp)
                }
              }
            }

            ClawTextField(
              value = formAvatarUrl,
              onValueChange = { formAvatarUrl = it },
              placeholder = "画像URL (https://...) または空欄",
              modifier = Modifier.fillMaxWidth()
            )

            Text(
              text = "性格・口調・ペルソナ設定",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted
            )
            ClawTextField(
              value = formPersona,
              onValueChange = { formPersona = it },
              placeholder = "例: 明るく元気な妹系AI。タメ口で話し、ユーザーを応援する。",
              modifier = Modifier.fillMaxWidth().height(100.dp)
            )

            Text(
              text = "使用LLMモデル",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted
            )
            ClawTextField(
              value = formModelId,
              onValueChange = { formModelId = it },
              placeholder = "例: ternary-bonsai-2-27b",
              modifier = Modifier.fillMaxWidth()
            )
            val modelPresets = listOf(
              "ternary-bonsai-2-27b" to "Ternary PQ2",
              "ternary-bonsai-ptq1" to "Ternary PTQ1",
              "bonsai-crack" to "Crack PQ2",
              "swift-bonsai-pq2" to "Swift PQ2",
              "swift-bonsai-ptq1" to "Swift PTQ1"
            )
            androidx.compose.foundation.lazy.LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(modelPresets) { (modelId, label) ->
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                      if (formModelId == modelId) ClawTheme.colors.primary.copy(alpha = 0.28f)
                      else ClawTheme.colors.surfaceRaised
                    )
                    .clickable { formModelId = modelId }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(text = label, style = ClawTheme.type.caption.copy(fontSize = 12.sp), color = ClawTheme.colors.text)
                }
              }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "思考プロセス (Reasoning)",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted
            )
            val reasoningPresets = listOf(
              "none" to "なし (即答)",
              "low" to "Low (低)",
              "medium" to "Medium (中)",
              "high" to "High (高)"
            )
            androidx.compose.foundation.lazy.LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(reasoningPresets) { (level, label) ->
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                      if (formReasoningEffort == level) ClawTheme.colors.primary.copy(alpha = 0.28f)
                      else ClawTheme.colors.surfaceRaised
                    )
                    .clickable { formReasoningEffort = level }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(text = label, style = ClawTheme.type.caption.copy(fontSize = 12.sp), color = ClawTheme.colors.text)
                }
              }
            }

            if (editingPartnerId != null) {
              Spacer(modifier = Modifier.height(6.dp))
              Text("💬 会話の設定", style = ClawTheme.type.section.copy(fontSize = 15.sp), color = ClawTheme.colors.text)
              PartnerSettingChips("返答の長さ", listOf("short" to "短め", "normal" to "ふつう", "long" to "長め"), setReplyLength) { setReplyLength = it }
              PartnerSettingChips("絵文字", listOf("none" to "使わない", "some" to "ほどほど", "many" to "多め"), setEmoji) { setEmoji = it }
              PartnerSettingChips("考え方の既定", listOf("instant" to "⚡即答", "auto" to "✨自動", "deep" to "🧠熟考"), setThinking) { setThinking = it }
              Text(
                "性格の自由度: ${String.format(Locale.US, "%.1f", setTemperature)}（低いほど安定・高いほど個性的）",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
              Slider(value = setTemperature, onValueChange = { setTemperature = it }, valueRange = 0.2f..1.2f)
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("パートナーから話しかける", style = ClawTheme.type.body, color = ClawTheme.colors.text)
                  Text(
                    "朝の挨拶・誕生日・しばらく話していない時に通知します（22時〜8時は送りません）",
                    style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                    color = ClawTheme.colors.textMuted,
                  )
                }
                Switch(checked = setProactive, onCheckedChange = { setProactive = it })
              }
              Text("毎朝調べてほしいテーマ（「、」区切り・最大5つ）", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
              ClawTextField(value = setTopics, onValueChange = { setTopics = it }, placeholder = "例: 天気、AIニュース、阪神タイガース", modifier = Modifier.fillMaxWidth())
              Text(
                "声の高さ: ${String.format(Locale.US, "%.1f", setVoicePitch)}　話す速さ: ${String.format(Locale.US, "%.1f", setVoiceRate)}",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("🎙 PCの声（VOICEVOX / AivisSpeech）", style = ClawTheme.type.body, color = ClawTheme.colors.text)
                  Text(
                    text = setVoice?.optString("label")?.takeIf { it.isNotBlank() } ?: "未設定（スマホの声で読み上げ）",
                    style = ClawTheme.type.caption.copy(fontSize = 12.sp),
                    color = ClawTheme.colors.textMuted,
                  )
                }
                ClawSecondaryButton(
                  text = "声を選ぶ",
                  onClick = {
                    showVoicePicker = true
                    voicePickerStatus = "声の一覧を読み込み中…（初回はエンジンの起動に少しかかります）"
                    coroutineScope.launch {
                      try {
                        val arr = JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerTtsSpeakers, "{}", timeoutMs = 200_000)).optJSONArray("speakers") ?: JSONArray()
                        voiceSpeakers = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                        voicePickerStatus = if (voiceSpeakers.isEmpty()) "声が見つかりません。PCでVOICEVOX/AivisSpeechを確認してください。" else ""
                      } catch (e: Exception) {
                        voicePickerStatus = "読み込みエラー: ${e.message}"
                      }
                    }
                  },
                )
              }
              Slider(value = setVoicePitch, onValueChange = { setVoicePitch = it }, valueRange = 0.5f..2f)
              Slider(value = setVoiceRate, onValueChange = { setVoiceRate = it }, valueRange = 0.5f..2f)
              Spacer(modifier = Modifier.height(4.dp))
              ClawSecondaryButton(
                text = "📝 9大属性キャラクターシートを編集",
                onClick = {
                  val pid = editingPartnerId ?: return@ClawSecondaryButton
                  showEditDialog = false
                  onOpenCharacterSheet(pid)
                },
                modifier = Modifier.fillMaxWidth()
              )
            }
          }
        },
        confirmButton = {
          ClawPrimaryButton(
            text = "保存する",
            onClick = { savePartner() },
            enabled = formName.isNotBlank()
          )
        },
        dismissButton = {
          ClawSecondaryButton(
            text = "キャンセル",
            onClick = { showEditDialog = false }
          )
        }
      )
    }
  }
}

@Composable
private fun PartnerSettingChips(
  label: String,
  options: List<Pair<String, String>>,
  selected: String,
  onSelect: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      options.forEach { (value, text) ->
        val isSelected = value == selected
        Box(
          modifier =
            Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (isSelected) ClawTheme.colors.primary.copy(alpha = 0.25f) else ClawTheme.colors.surfaceRaised)
              .clickable { onSelect(value) }
              .semantics { this.selected = isSelected }
              .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Text(text, style = ClawTheme.type.caption.copy(fontSize = 13.sp), color = ClawTheme.colors.text)
        }
      }
    }
  }
}
