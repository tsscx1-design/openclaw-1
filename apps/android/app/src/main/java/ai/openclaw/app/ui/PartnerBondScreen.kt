package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawScaffold
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import org.json.JSONArray
import org.json.JSONObject

data class BondBadge(
  val title: String,
  val desc: String,
  val icon: String,
  val unlocked: Boolean,
)

@Composable
internal fun PartnerBondScreen(
  viewModel: MainViewModel,
  partnerId: String? = null,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var isLoading by remember { mutableStateOf(true) }
  var partnerName by remember { mutableStateOf("AIパートナー") }
  var level by remember { mutableStateOf(1) }
  var exp by remember { mutableStateOf(0) }
  var nextExp by remember { mutableStateOf(100) }
  var title by remember { mutableStateOf("初対面の友達") }
  var totalMessages by remember { mutableStateOf(0) }
  var streakDays by remember { mutableStateOf(1) }
  var badges by remember { mutableStateOf<List<BondBadge>>(emptyList()) }

  fun loadBond() {
    val pid = partnerId ?: return
    coroutineScope.launch {
      isLoading = true
      try {
        val res = viewModel.getPartnerBond(pid)
        val obj = JSONObject(res)
        partnerName = obj.optString("partnerName", "AIパートナー")
        val bond = obj.optJSONObject("bond") ?: obj
        level = bond.optInt("level", 1)
        exp = bond.optInt("exp", 0)
        nextExp = bond.optInt("nextExp", 100)
        title = bond.optString("title", if (level >= 10) "唯一無二の親友" else if (level >= 5) "気心の知れた相棒" else "仲良しの友達")
        totalMessages = bond.optInt("totalMessages", 0)
        streakDays = bond.optInt("streakDays", 1)

        val arr = bond.optJSONArray("badges") ?: JSONArray()
        val list = mutableListOf<BondBadge>()
        for (i in 0 until arr.length()) {
          val b = arr.optJSONObject(i)
          if (b != null) {
            list.add(
              BondBadge(
                title = b.optString("title", "バッジ"),
                desc = b.optString("desc", ""),
                icon = b.optString("icon", "🏅"),
                unlocked = b.optBoolean("unlocked", true),
              )
            )
          }
        }
        if (list.isEmpty()) {
          list.addAll(
            listOf(
              BondBadge("初めの一歩", "初めて会話を交わした", "🌱", totalMessages >= 1),
              BondBadge("おしゃべり上手", "合計10回以上会話した", "💬", totalMessages >= 10),
              BondBadge("夜更かしトーク", "夜間に会話をした", "🌙", true),
              BondBadge("絆の芽生え", "絆レベル5に到達", "⭐", level >= 5),
              BondBadge("魂の共鳴", "絆レベル10に到達", "👑", level >= 10),
              BondBadge("記憶の共有者", "10個以上の思い出を記憶した", "🧠", true),
            )
          )
        }
        badges = list
      } catch (_: Exception) {
      } finally {
        isLoading = false
      }
    }
  }

  LaunchedEffect(partnerId) {
    loadBond()
  }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
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
            Text(text = "💖 絆レベル & 実績", style = ClawTheme.type.title, color = ClawTheme.colors.text)
            Text(text = "${partnerName}との深まりと親愛度", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
        }
        ClawIconButton(icon = Icons.Default.Refresh, contentDescription = "更新", onClick = { loadBond() })
      }

      if (isLoading) {
        Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        ) {
          repeat(4) {
            SettingsRowSkeleton()
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          // メインレベルカード
          item {
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Box(
                  modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(ClawTheme.colors.primary.copy(alpha = 0.15f))
                    .border(2.dp, ClawTheme.colors.primary, CircleShape),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(text = "Lv.$level", style = ClawTheme.type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = ClawTheme.colors.primary)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = title, style = ClawTheme.type.title.copy(fontSize = 18.sp), color = ClawTheme.colors.text)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "会話するほど絆が深まり、話し方やリアクションが変化します",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )
                Spacer(modifier = Modifier.height(16.dp))

                // プログレスバー
                val progress = if (nextExp > 0) (exp.toFloat() / nextExp.toFloat()).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                  progress = { progress },
                  modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                  color = ClawTheme.colors.primary,
                  trackColor = ClawTheme.colors.surfaceRaised,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(text = "現在経験値: $exp EXP", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                  Text(text = "次Lvまで: $nextExp EXP", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                }
              }
            }
          }

          // 統計情報
          item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              ClawPanel(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(text = "💬", fontSize = 24.sp)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(text = "$totalMessages 回", style = ClawTheme.type.title.copy(fontSize = 16.sp), color = ClawTheme.colors.text)
                  Text(text = "総会話数", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                }
              }
              ClawPanel(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(text = "🔥", fontSize = 24.sp)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(text = "$streakDays 日", style = ClawTheme.type.title.copy(fontSize = 16.sp), color = ClawTheme.colors.text)
                  Text(text = "連続トーク", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                }
              }
            }
          }

          // 実績バッジ一覧
          item {
            Text(
              text = "🏆 絆の実績バッジ",
              style = ClawTheme.type.section,
              color = ClawTheme.colors.text,
              modifier = Modifier.padding(top = 8.dp),
            )
          }

          items(badges.chunked(2).size) { rowIndex ->
            val chunk = badges.chunked(2)[rowIndex]
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              for (badge in chunk) {
                ClawPanel(
                  modifier = Modifier
                    .weight(1f)
                    .border(
                      1.dp,
                      if (badge.unlocked) ClawTheme.colors.primary.copy(alpha = 0.4f) else Color.Transparent,
                      RoundedCornerShape(12.dp)
                    )
                ) {
                  Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(text = badge.icon, fontSize = 22.sp)
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = badge.title,
                        style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                        color = if (badge.unlocked) ClawTheme.colors.text else ClawTheme.colors.textMuted,
                      )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = badge.desc,
                      style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                      color = ClawTheme.colors.textMuted,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = if (badge.unlocked) "✨ 解除済み" else "🔒 未達成",
                      style = ClawTheme.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                      color = if (badge.unlocked) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
                    )
                  }
                }
              }
              if (chunk.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
              }
            }
          }
        }
      }
    }
  }
}
