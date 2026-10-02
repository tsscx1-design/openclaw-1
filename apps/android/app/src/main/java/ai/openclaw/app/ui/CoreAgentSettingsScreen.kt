package ai.openclaw.app.ui

import ai.openclaw.app.CoreAgentSettings
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
internal fun CoreAgentSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val scope = rememberCoroutineScope()

  var isLoading by remember { mutableStateOf(true) }
  var isSaving by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }
  var isSuccess by remember { mutableStateOf(true) }

  // フォームステート
  var name by remember { mutableStateOf("OpenClaw") }
  var emoji by remember { mutableStateOf("🐾") }
  var role by remember { mutableStateOf("メインアシスタント・自動化パートナー") }
  var vibe by remember { mutableStateOf("頼れる・親身・迅速・プロフェッショナル") }

  var firstPerson by remember { mutableStateOf("私") }
  var callMe by remember { mutableStateOf("マスター") }
  var tone by remember { mutableStateOf("丁寧で明るい口調、「〜ですね！」「お任せください！」") }
  var personality by remember { mutableStateOf("親切で礼儀正しく、かつ頼れるプロフェッショナルな相棒。") }

  var userName by remember { mutableStateOf("マスター") }
  var userNotes by remember { mutableStateOf("いつも頼りにしてくれる大切なマスター。") }

  var formModelId by remember { mutableStateOf("ternary-bonsai-2-27b") }
  var formReasoningEffort by remember { mutableStateOf("none") }

  LaunchedEffect(Unit) {
    isLoading = true
    val current = viewModel.getCoreAgentSettings()
    if (current != null) {
      name = current.name
      emoji = current.emoji
      role = current.role
      vibe = current.vibe
      firstPerson = current.firstPerson
      callMe = current.callMe
      tone = current.tone
      personality = current.personality
      userName = current.userName
      userNotes = current.userNotes
      formModelId = current.modelId
      formReasoningEffort = current.reasoningEffort
    }
    isLoading = false
  }

  fun doSave() {
    scope.launch {
      isSaving = true
      statusMessage = null
      val updated = CoreAgentSettings(
        agentId = "main",
        name = name,
        emoji = emoji,
        role = role,
        vibe = vibe,
        firstPerson = firstPerson,
        callMe = callMe,
        tone = tone,
        personality = personality,
        userName = userName,
        userNotes = userNotes,
        modelId = formModelId,
        reasoningEffort = formReasoningEffort,
      )
      val ok = viewModel.saveCoreAgentSettings(updated)
      isSaving = false
      if (ok) {
        statusMessage = "PC側の設定ファイル（SOUL/IDENTITY/USER.md）を更新しました！"
        isSuccess = true
      } else {
        statusMessage = "保存に失敗しました。Gatewayの接続をご確認ください。"
        isSuccess = false
      }
    }
  }

  ClawScaffold(
    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 6.dp),
    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
  ) {
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
      // トップバー
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ClawIconButton(
          icon = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "戻る",
          onClick = onBack,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "コアエージェント設定",
            style = ClawTheme.type.section.copy(fontSize = 16.sp),
            color = ClawTheme.colors.text,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "人格・口調・行動指針 (SOUL.md / IDENTITY / USER)",
            style = ClawTheme.type.caption.copy(fontSize = 11.sp),
            color = ClawTheme.colors.textMuted,
          )
        }
        ClawPrimaryButton(
          text = if (isSaving) "保存中..." else "保存",
          icon = Icons.Default.Save,
          enabled = !isLoading && !isSaving,
          onClick = { doSave() },
        )
      }

      if (isLoading) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(color = ClawTheme.colors.primary)
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f),
          contentPadding = PaddingValues(vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // ステータス通知
          statusMessage?.let { msg ->
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isSuccess) Color(0xFF1B382B) else Color(0xFF3E1F1F))
                  .padding(12.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (isSuccess) Color(0xFF4ADE80) else Color(0xFFF87171),
                  )
                  Text(
                    text = msg,
                    style = ClawTheme.type.body.copy(fontSize = 13.sp),
                    color = if (isSuccess) Color(0xFFE2FCEF) else Color(0xFFFEE2E2),
                  )
                }
              }
            }
          }

          // ガイド説明カード
          item {
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(text = "🐾", fontSize = 20.sp)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "コアエージェント（main）の人格形成",
                    style = ClawTheme.type.section.copy(fontSize = 14.sp),
                    color = ClawTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                  )
                }
                Text(
                  text = "ホーム画面の「ACTIVE AGENT」であるコアエージェントは、PCワークスペース直下の3つの設定ファイル（IDENTITY.md, SOUL.md, USER.md）を読み込んで口調・人格・思考を形成します。\nここで設定した内容はPC側ファイルへ即座に反映され、ツール操作やチャットで自然に発揮されます。",
                  style = ClawTheme.type.caption.copy(fontSize = 12.sp, lineHeight = 17.sp),
                  color = ClawTheme.colors.textMuted,
                )
              }
            }
          }

          // セクション1: 基本情報 (IDENTITY.md)
          item {
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = ClawTheme.colors.primary,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "1. 基本アイデンティティ (IDENTITY.md)",
                    style = ClawTheme.type.section.copy(fontSize = 14.sp),
                    color = ClawTheme.colors.text,
                    fontWeight = FontWeight.SemiBold,
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Column(modifier = Modifier.width(80.dp)) {
                    Text(
                      text = "アイコン絵文字",
                      style = ClawTheme.type.caption,
                      color = ClawTheme.colors.textMuted,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ClawTextField(
                      value = emoji,
                      onValueChange = { emoji = it },
                      placeholder = "🐾",
                      modifier = Modifier.fillMaxWidth(),
                    )
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "エージェント名",
                      style = ClawTheme.type.caption,
                      color = ClawTheme.colors.textMuted,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ClawTextField(
                      value = name,
                      onValueChange = { name = it },
                      placeholder = "OpenClaw",
                      modifier = Modifier.fillMaxWidth(),
                    )
                  }
                }

                Column {
                  Text(
                    text = "主な役割・肩書き",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  ClawTextField(
                    value = role,
                    onValueChange = { role = it },
                    placeholder = "メインアシスタント・自動化パートナー",
                    modifier = Modifier.fillMaxWidth(),
                  )
                }

                Column {
                  Text(
                    text = "全体の雰囲気・バイブス",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  ClawTextField(
                    value = vibe,
                    onValueChange = { vibe = it },
                    placeholder = "頼れる・親身・迅速・プロフェッショナル",
                    modifier = Modifier.fillMaxWidth(),
                  )
                }
              }
            }
          }

          // セクション2: 口調・一人称・人格 (SOUL.md)
          item {
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = ClawTheme.colors.primary,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "2. 口調・一人称・人格設定 (SOUL.md)",
                    style = ClawTheme.type.section.copy(fontSize = 14.sp),
                    color = ClawTheme.colors.text,
                    fontWeight = FontWeight.SemiBold,
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "一人称",
                      style = ClawTheme.type.caption,
                      color = ClawTheme.colors.textMuted,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ClawTextField(
                      value = firstPerson,
                      onValueChange = { firstPerson = it },
                      placeholder = "私 / 僕 / オレ / うち",
                      modifier = Modifier.fillMaxWidth(),
                    )
                  }

                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "あなたの呼び方 (呼ばせ方)",
                      style = ClawTheme.type.caption,
                      color = ClawTheme.colors.textMuted,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ClawTextField(
                      value = callMe,
                      onValueChange = { callMe = it },
                      placeholder = "マスター / ご主人様 / 〇〇さん",
                      modifier = Modifier.fillMaxWidth(),
                    )
                  }
                }

                // 定番プリセットボタン
                Row(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  listOf(
                    Triple("礼儀正しく", "私", "〜ですね！お任せください！"),
                    Triple("フレンドリー", "僕", "〜だよ！任せてね！"),
                    Triple("クール", "俺", "〜だ。指示を頼む。"),
                  ).forEach { (presetName, fp, tn) ->
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClawTheme.colors.surfaceRaised)
                        .clickable {
                          firstPerson = fp
                          tone = tn
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                      Text(
                        text = presetName,
                        style = ClawTheme.type.caption,
                        color = ClawTheme.colors.primary,
                      )
                    }
                  }
                }

                Column {
                  Text(
                    text = "語尾のトーン・話し方",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  ClawTextField(
                    value = tone,
                    onValueChange = { tone = it },
                    placeholder = "丁寧で明るい口調、「〜ですね！」「お任せください！」",
                    modifier = Modifier.fillMaxWidth(),
                  )
                }

                Column {
                  Text(
                    text = "性格・価値観・行動スタンス",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  ClawTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    placeholder = "親切で礼儀正しく、かつ頼れるプロフェッショナルな相棒。",
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                  )
                }
              }
            }
          }

          // セクション3: ユーザー情報 (USER.md)
          item {
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = ClawTheme.colors.primary,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "3. あなたについての情報 (USER.md)",
                    style = ClawTheme.type.section.copy(fontSize = 14.sp),
                    color = ClawTheme.colors.text,
                    fontWeight = FontWeight.SemiBold,
                  )
                }

                Column {
                  Text(
                    text = "あなたの名前・ニックネーム",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  ClawTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    placeholder = "マスター",
                    modifier = Modifier.fillMaxWidth(),
                  )
                }

                Column {
                  Text(
                    text = "あなたについてのメモ・共有したい情報（趣味、仕事、好みなど）",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  ClawTextField(
                    value = userNotes,
                    onValueChange = { userNotes = it },
                    placeholder = "いつも頼りにしてくれる大切なマスター。エンジニアで最新技術が好き。",
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                  )
                }
              }
            }
          }

          // セクション4: AIモデルと思考プロセス (LLM Settings)
          item {
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = ClawTheme.colors.primary,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "4. AIモデルと思考プロセス",
                    style = ClawTheme.type.section.copy(fontSize = 14.sp),
                    color = ClawTheme.colors.text,
                    fontWeight = FontWeight.SemiBold,
                  )
                }

                Text(
                  text = "AIモデル (Model)",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted
                )
                val modelPresets = listOf(
                  "ternary-bonsai-2-27b" to "Ternary PQ2",
                  "ternary-bonsai-ptq1" to "Ternary PTQ1",
                  "bonsai-crack" to "Crack PQ2",
                  "swift-bonsai-pq2" to "Swift PQ2",
                  "swift-bonsai-ptq1" to "Swift PTQ1",
                  "ptq1" to "PTQ1 (低負荷)",
                  "ptq2" to "PTQ2 (標準)",
                  "ptq3" to "PTQ3 (中精度)",
                  "ptq4" to "PTQ4 (高精度)",
                  "ptq5" to "PTQ5 (最高精度)"
                )
                androidx.compose.foundation.lazy.LazyRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  items(modelPresets) { (id, label) ->
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                          if (formModelId == id) ClawTheme.colors.primary.copy(alpha = 0.28f)
                          else ClawTheme.colors.surfaceRaised
                        )
                        .clickable { formModelId = id }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(text = label, style = ClawTheme.type.caption.copy(fontSize = 12.sp), color = ClawTheme.colors.text)
                    }
                  }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
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
              }
            }
          }

          // 保存ボタン
          item {
            Spacer(modifier = Modifier.height(8.dp))
            ClawPrimaryButton(
              text = if (isSaving) "設定をPCへ保存中..." else "設定をPCに保存（SOUL/IDENTITY/USER更新）",
              icon = Icons.Default.Save,
              modifier = Modifier.fillMaxWidth(),
              enabled = !isLoading && !isSaving,
              onClick = { doSave() },
            )
            Spacer(modifier = Modifier.height(16.dp))
          }
        }
      }
    }
  }
}
