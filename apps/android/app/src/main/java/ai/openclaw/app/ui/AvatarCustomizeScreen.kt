package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.PartnerAvatar
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import org.json.JSONObject

@Composable
internal fun AvatarCustomizeScreen(
  viewModel: MainViewModel,
  partnerId: String? = null,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var partnerName by remember { mutableStateOf("AIパートナー") }
  var selectedAvatarType by remember { mutableStateOf("cat") }
  var selectedColorHex by remember { mutableStateOf("#6366F1") }
  var isSaving by remember { mutableStateOf(false) }
  var saveSuccessMessage by remember { mutableStateOf("") }

  val avatarOptions = listOf(
    Pair("cat", "🐱 猫ちゃん"),
    Pair("dog", "🐶 わんこ"),
    Pair("fox", "🦊 こぎつね"),
    Pair("rabbit", "🐰 うさぎ"),
    Pair("robot", "🤖 アンドロイド"),
    Pair("human", "👤 人間・パートナー"),
    Pair("dragon", "🐲 ドラゴン"),
    Pair("owl", "🦉 ふくろう博士"),
  )

  val colorOptions = listOf(
    Pair("#6366F1", Color(0xFF6366F1)), // インディゴ
    Pair("#EC4899", Color(0xFFEC4899)), // ピンク
    Pair("#10B981", Color(0xFF10B981)), // エメラルド
    Pair("#F59E0B", Color(0xFFF59E0B)), // アンバー
    Pair("#8B5CF6", Color(0xFF8B5CF6)), // パープル
    Pair("#06B6D4", Color(0xFF06B6D4)), // シアン
    Pair("#EF4444", Color(0xFFEF4444)), // ローズレッド
    Pair("#3B82F6", Color(0xFF3B82F6)), // ブルー
  )

  fun loadAvatar() {
    val pid = partnerId ?: return
    coroutineScope.launch {
      try {
        val res = viewModel.getPartnerAvatar(pid)
        val obj = JSONObject(res)
        partnerName = obj.optString("partnerName", "AIパートナー")
        val avatarObj = obj.optJSONObject("avatar")
        if (avatarObj != null) {
          selectedAvatarType = avatarObj.optString("avatarType", "cat")
          selectedColorHex = avatarObj.optString("avatarColor", "#6366F1")
        }
      } catch (_: Exception) {
      }
    }
  }

  fun saveAvatar() {
    val pid = partnerId ?: return
    coroutineScope.launch {
      isSaving = true
      try {
        val ok = viewModel.setPartnerAvatar(pid, selectedAvatarType, selectedColorHex)
        saveSuccessMessage = if (ok) "アバター設定を保存しました！" else "保存に失敗しました"
      } catch (e: Exception) {
        saveSuccessMessage = "エラーが発生しました"
      } finally {
        isSaving = false
      }
    }
  }

  LaunchedEffect(partnerId) {
    loadAvatar()
  }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
    Column(modifier = Modifier.fillMaxSize()) {
      // ヘッダー
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ClawIconButton(
          icon = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "戻る",
          onClick = onBack,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(text = "🎨 アバターカスタマイズ", style = ClawTheme.type.title, color = ClawTheme.colors.text)
          Text(text = "${partnerName}の見た目・テーマカラーを変更", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        }
      }

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // プレビューカード
        item {
          ClawPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Box(
                modifier = Modifier
                  .size(96.dp)
                  .clip(CircleShape)
                  .background(Color(android.graphics.Color.parseColor(selectedColorHex)).copy(alpha = 0.2f))
                  .border(3.dp, Color(android.graphics.Color.parseColor(selectedColorHex)), CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = when (selectedAvatarType) {
                    "cat" -> "🐱"
                    "dog" -> "🐶"
                    "fox" -> "🦊"
                    "rabbit" -> "🐰"
                    "robot" -> "🤖"
                    "dragon" -> "🐲"
                    "owl" -> "🦉"
                    else -> "👤"
                  },
                  fontSize = 44.sp,
                )
              }
              Spacer(modifier = Modifier.height(12.dp))
              Text(text = partnerName, style = ClawTheme.type.title, color = ClawTheme.colors.text)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "チャットやグループ会話でこのアイコンが表示されます",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
            }
          }
        }

        // キャラクタータイプ選択
        item {
          Text(text = "キャラクターアイコン", style = ClawTheme.type.section, color = ClawTheme.colors.text)
        }

        items(avatarOptions.chunked(2).size) { rowIndex ->
          val chunk = avatarOptions.chunked(2)[rowIndex]
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((type, label) in chunk) {
              val isSelected = selectedAvatarType == type
              ClawPanel(
                modifier = Modifier
                  .weight(1f)
                  .border(
                    1.5.dp,
                    if (isSelected) ClawTheme.colors.primary else Color.Transparent,
                    RoundedCornerShape(12.dp)
                  )
                  .clickable { selectedAvatarType = type }
              ) {
                Box(modifier = Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                  Text(
                    text = label,
                    style = ClawTheme.type.body.copy(
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.text,
                  )
                }
              }
            }
          }
        }

        // テーマカラー選択
        item {
          Text(text = "テーマカラー", style = ClawTheme.type.section, color = ClawTheme.colors.text)
        }

        item {
          ClawPanel(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              horizontalArrangement = Arrangement.SpaceAround,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              for ((hex, color) in colorOptions) {
                val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                      width = if (isSelected) 3.dp else 1.dp,
                      color = if (isSelected) Color.White else Color.Transparent,
                      shape = CircleShape
                    )
                    .clickable { selectedColorHex = hex }
                )
              }
            }
          }
        }

        // 保存ボタン
        item {
          if (saveSuccessMessage.isNotBlank()) {
            Text(
              text = saveSuccessMessage,
              style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold),
              color = ClawTheme.colors.primary,
              modifier = Modifier.padding(vertical = 4.dp),
            )
          }
          ClawPrimaryButton(
            text = if (isSaving) "保存中..." else "設定を保存する",
            onClick = { saveAvatar() },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}
