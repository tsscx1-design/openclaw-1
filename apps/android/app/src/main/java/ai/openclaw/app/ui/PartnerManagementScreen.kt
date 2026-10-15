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
  /** キャラ図鑑のキャラID（連携時のみ設定） */
  val sourceCharacterId: String = "",
  /** お気に入り（キャラ図鑑と同期） */
  val isFavorite: Boolean = false,
)

@Composable
internal fun PartnerManagementScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
  onStartGroupChat: () -> Unit = {},
  onOpenPartnerChat: (String) -> Unit = {},
  onOpenCharacterSheet: (String) -> Unit = {},
  onOpenAudioStudio: () -> Unit = {},
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
  var formAvatarPrompt by remember { mutableStateOf("") }
  var avatarGenStatus by remember { mutableStateOf("") }
  var avatarGenerating by remember { mutableStateOf(false) }
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
  var setNewsDigest by remember { mutableStateOf(false) }
  var setStudioAssistant by remember { mutableStateOf(false) }
  var setVoicePitch by remember { mutableStateOf(1f) }
  var setVoiceRate by remember { mutableStateOf(1f) }
  // PC の声（VOICEVOX / AivisSpeech）。null ならスマホの読み上げ
  var setVoice by remember { mutableStateOf<JSONObject?>(null) }

  var mainPartnerId by remember { mutableStateOf<String?>(null) }
  // 紐付けキャラ図鑑のスペック編集
  var editingCharaId by remember { mutableStateOf("") }
  var editingCharaName by remember { mutableStateOf("") }
  var libraryCharaSummary by remember { mutableStateOf("") }
  val specEditMap = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>() }
  var showSpecEditor by remember { mutableStateOf(false) }
  var specSaveStatus by remember { mutableStateOf("") }
  var partnerCustomFieldKey by remember { mutableStateOf("") }
  var partnerCustomFieldValue by remember { mutableStateOf("") }

  val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      val b64 = convertUriToBase64DataUrl(context, it)
      if (b64 != null) {
        formAvatarUrl = b64
      }
    }
  }

  // Generates an avatar on the PC (CPU, a few minutes) and polls until the image is ready.
  fun generateAvatar() {
    if (avatarGenerating) return
    val description = formAvatarPrompt.trim().ifBlank { formPersona.trim().take(300) }
    coroutineScope.launch {
      avatarGenerating = true
      avatarGenStatus = "PCで画像を作っています…（数分かかります。画面を閉じても大丈夫です）"
      try {
        val start = JSONObject(
          viewModel.runGatewayRpc(
            GatewayMethod.PartnerAvatarGenerate,
            JSONObject().put("description", description).toString(),
          ),
        )
        val jobId = start.optString("jobId")
        val deadline = System.currentTimeMillis() + 20 * 60_000L
        while (System.currentTimeMillis() < deadline) {
          kotlinx.coroutines.delay(5_000)
          val res = JSONObject(
            viewModel.runGatewayRpc(
              GatewayMethod.PartnerAvatarGenerateStatus,
              JSONObject().put("jobId", jobId).toString(),
            ),
          )
          when (res.optString("status")) {
            "done" -> {
              formAvatarUrl = res.optString("imageDataUri")
              avatarGenStatus = "できました（${res.optInt("durationSec")}秒）。気に入らなければもう一度押すと別の絵になります。保存すると反映されます。"
              return@launch
            }
            "error" -> {
              avatarGenStatus = "作れませんでした: ${res.optString("error")}"
              return@launch
            }
          }
        }
        avatarGenStatus = "時間がかかりすぎたため中断しました。"
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        avatarGenStatus = "画像生成エラー: ${e.message}"
      } finally {
        avatarGenerating = false
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
              reasoningEffort = p.optString("reasoningEffort", "none"),
              sourceCharacterId = p.optString("sourceCharacterId", ""),
              isFavorite = p.optBoolean("isFavorite", false),
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
              .put("newsDigest", setNewsDigest)
              .put("studioAssistant", setStudioAssistant)
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
        setNewsDigest = st.optBoolean("newsDigest", false)
        setStudioAssistant = st.optBoolean("studioAssistant", false)
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
    // 紐付けキャラのスペックを取得
    if (partner.sourceCharacterId.isNotBlank()) {
      editingCharaId = partner.sourceCharacterId
      coroutineScope.launch {
        runCatching {
          val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ComicCharacterList, "{}"))
          val arr = res.optJSONArray("characters")
          if (arr != null) {
            for (i in 0 until arr.length()) {
              val chara = arr.getJSONObject(i)
              if (chara.optString("id") == partner.sourceCharacterId) {
                editingCharaName = chara.optString("name", partner.name)
                // キャラ図鑑の設定の要約（パートナー管理に出す。チャットでも、この設定を読んで話す）
                libraryCharaSummary = buildString {
                  chara.optString("specText").takeIf { it.isNotBlank() }?.let { append(it.replace(" / ", "\n")) }
                  chara.optString("profile").takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append("\n\n"); append("プロフィール：").append(it) }
                  chara.optJSONObject("sheetValues")?.let { sv ->
                    val lines = sv.keys().asSequence().map { k -> k to sv.optString(k) }.filter { it.second.isNotBlank() }.map { "${it.first}：${it.second}" }.toList()
                    if (lines.isNotEmpty()) { if (isNotEmpty()) append("\n\n"); append(lines.joinToString("\n")) }
                  }
                }
                specEditMap.clear()
                val spec = chara.optJSONObject("spec")
                if (spec != null) {
                  spec.keys().forEach { k -> specEditMap[k] = spec.optString(k) }
                }
                break
              }
            }
          }
        }
      }
    } else {
      editingCharaId = ""
      editingCharaName = ""
      libraryCharaSummary = ""
      specEditMap.clear()
    }
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
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                          if (partner.isFavorite) {
                            Text("⭐", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                          }
                          Text(
                            text = partner.name,
                            style = ClawTheme.type.section.copy(fontSize = 15.sp),
                            color = ClawTheme.colors.text
                          )
                        }
                        Text(
                          text = "\u30e2\u30c7\u30eb: ${partner.modelId}",
                          style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                          color = ClawTheme.colors.textMuted
                        )
                        if (partner.sourceCharacterId.isNotBlank()) {
                          Text(
                            text = "\ud83d\udc91 \u30ad\u30e3\u30e9\u56f3\u9451\u3068\u9023\u643a\u4e2d",
                            style = ClawTheme.type.captionSmall.copy(fontSize = 10.sp),
                            color = ClawTheme.colors.primary
                          )
                        }
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
                // キャラ図鑑の設定（連携しているキャラのとき）。チャットでは、この設定も読んで話します
                if (partner.sourceCharacterId.isNotBlank()) {
                  ClawPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                      Text(text = "📖 キャラ図鑑の設定（${editingCharaName.ifBlank { partner.name }}）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
                      Text(
                        text = libraryCharaSummary.ifBlank { "読み込んでいます…（または、設定がまだありません）" },
                        style = ClawTheme.type.captionSmall,
                        color = ClawTheme.colors.textMuted,
                      )
                      Text(text = "チャットでは、パートナーの設定に加えて、この図鑑の設定も読んで話します。直すときは、キャラ図鑑で。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textSubtle)
                    }
                  }
                }
              }
            }
          }
        }
      }
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
              text = "AIで画像を作る（見た目のメモ。空ならペルソナから作ります）",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted
            )
            ClawTextField(
              value = formAvatarPrompt,
              onValueChange = { formAvatarPrompt = it },
              placeholder = "例: 銀髪ショート、青い目、黒いパーカー、クールな表情",
              modifier = Modifier.fillMaxWidth()
            )
            ClawSecondaryButton(
              text = if (avatarGenerating) "作成中…" else "✨ AIでアバターを作る",
              onClick = { generateAvatar() }
            )
            if (avatarGenStatus.isNotBlank()) {
              Text(
                text = avatarGenStatus,
                style = ClawTheme.type.caption.copy(fontSize = 11.5.sp),
                color = ClawTheme.colors.textMuted
              )
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
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("📰 調べた内容をニュースとして全部届ける", style = ClawTheme.type.body, color = ClawTheme.colors.text)
                  Text(
                    "朝のメッセージの下に、テーマごとの最新情報を出典つきでまとめます（オフだと話題を1つ触れるだけ）",
                    style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                    color = ClawTheme.colors.textMuted,
                  )
                }
                Switch(checked = setNewsDigest, onCheckedChange = { setNewsDigest = it })
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("🎨 生成アシスタントにする", style = ClawTheme.type.body, color = ClawTheme.colors.text)
                  Text(
                    "このパートナーとの会話で画像・動画・音楽を作る、進み具合を聞く、止める、声の学習を始めることができます",
                    style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                    color = ClawTheme.colors.textMuted,
                  )
                }
                Switch(checked = setStudioAssistant, onCheckedChange = { setStudioAssistant = it })
              }
              // 声（PC の声・声まね・自作の声）と、高さ・速さは、「音声スタジオ」に移した
              Text(
                text = "🎙 声：" + (setVoice?.optString("label")?.takeIf { it.isNotBlank() } ?: "未設定（スマホの声で読み上げ）"),
                style = ClawTheme.type.body,
                color = ClawTheme.colors.text,
              )
              ClawSecondaryButton(
                text = "🎙 音声スタジオを開く（声を選ぶ・高さ・速さ）",
                onClick = {
                  showEditDialog = false
                  onOpenAudioStudio()
                },
                modifier = Modifier.fillMaxWidth(),
              )
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
              // ── キャラ図鑑のスペック（設定シート）直接編集 ──────────────
              if (editingCharaId.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                ClawSecondaryButton(
                  text = if (showSpecEditor) "▲ キャラ設定シートを閉じる" else "📋 【${editingCharaName}】の設定シートを編集する",
                  onClick = { showSpecEditor = !showSpecEditor },
                  modifier = Modifier.fillMaxWidth(),
                )
                if (showSpecEditor) {
                  Text(
                    text = "キャラ図鑑「${editingCharaName}」の設定シート",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.primary,
                  )
                  SPEC_GROUPS.forEach { (group, fields) ->
                    Text(text = group, style = ClawTheme.type.caption, color = ClawTheme.colors.text)
                    fields.forEach { (key, label) ->
                      ClawTextField(
                        value = specEditMap[key].orEmpty(),
                        onValueChange = { specEditMap[key] = it },
                        placeholder = label,
                        label = label,
                        minLines = if (key in setOf("background", "relations", "adultLikes", "kinks", "breathPlay")) 2 else 1,
                        modifier = Modifier.fillMaxWidth(),
                      )
                    }
                  }
                  // カスタム項目（既知キー以外）
                  val knownKeys = SPEC_GROUPS.flatMap { it.second }.map { it.first }.toSet()
                  val customKeys = specEditMap.keys.filter { it !in knownKeys && specEditMap[it]?.isNotBlank() == true }
                  if (customKeys.isNotEmpty()) {
                    Text(text = "+ 独自の追加項目", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
                    customKeys.forEach { key ->
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                      ) {
                        ClawTextField(
                          value = specEditMap[key].orEmpty(),
                          onValueChange = { specEditMap[key] = it },
                          label = key,
                          placeholder = "内容",
                          modifier = Modifier.weight(1f),
                        )
                        ClawIconButton(
                          icon = Icons.Default.Delete,
                          contentDescription = "削除",
                          onClick = { specEditMap.remove(key) },
                        )
                      }
                    }
                  }
                  // 手動追加フォーム
                  if (partnerCustomFieldKey.isNotBlank()) {
                    Text(text = "+ 新しい項目を追加", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
                    ClawTextField(
                      value = partnerCustomFieldKey,
                      onValueChange = { partnerCustomFieldKey = it },
                      label = "項目名",
                      placeholder = "例: 鼻ピアス・へそピアス・首絞め・特殊設定",
                      modifier = Modifier.fillMaxWidth(),
                    )
                    ClawTextField(
                      value = partnerCustomFieldValue,
                      onValueChange = { partnerCustomFieldValue = it },
                      label = "内容",
                      placeholder = "内容を入力",
                      modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      ClawPrimaryButton(
                        text = "追加",
                        enabled = partnerCustomFieldKey.isNotBlank(),
                        onClick = {
                          val trimmedKey = partnerCustomFieldKey.trim()
                          if (trimmedKey.isNotBlank()) {
                            specEditMap[trimmedKey] = partnerCustomFieldValue.trim()
                            partnerCustomFieldKey = ""
                            partnerCustomFieldValue = ""
                          }
                        },
                      )
                      ClawSecondaryButton(
                        text = "キャンセル",
                        onClick = { partnerCustomFieldKey = ""; partnerCustomFieldValue = "" },
                      )
                    }
                  } else {
                    ClawSecondaryButton(
                      text = "+ 項目を手動で追加",
                      onClick = { partnerCustomFieldKey = " " },
                      modifier = Modifier.fillMaxWidth(),
                    )
                  }
                  // 設定シートの保存ボタン
                  if (specSaveStatus.isNotBlank()) {
                    Text(text = specSaveStatus, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
                  }
                  ClawPrimaryButton(
                    text = "キャラの設定シートを保存",
                    onClick = {
                      val charaId = editingCharaId
                      if (charaId.isBlank()) return@ClawPrimaryButton
                      coroutineScope.launch {
                        runCatching {
                          val spec = JSONObject()
                          specEditMap.forEach { (k, v) -> if (v.isNotBlank()) spec.put(k, v.trim()) }
                          viewModel.runGatewayRpc(
                            GatewayMethod.ComicCharacterUpdate,
                            JSONObject().put("characterId", charaId).put("spec", spec).toString(),
                          )
                          specSaveStatus = "保存しました！"
                        }.onFailure { specSaveStatus = "保存エラー: ${it.message}" }
                      }
                    },
                    modifier = Modifier.fillMaxWidth(),
                  )
                }
              }
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
