package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSectionHeader
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Switch
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
import org.json.JSONObject

@Composable
internal fun FeatureSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var statusMessage by remember { mutableStateOf("") }
  var isLoading by remember { mutableStateOf(false) }

  // Web検索設定
  var webSearchEnabled by remember { mutableStateOf(true) }
  var webSearchProvider by remember { mutableStateOf("duckduckgo") }
  var autoSaveToMemory by remember { mutableStateOf(true) }
  var webApiKey by remember { mutableStateOf("") }
  var googleCseId by remember { mutableStateOf("") }
  var searxngBaseUrl by remember { mutableStateOf("") }

  // プロバイダ定義
  data class ProviderInfo(val id: String, val label: String, val emoji: String, val desc: String)
  val providers = listOf(
    ProviderInfo("duckduckgo", "DuckDuckGo", "🦆", "APIキー不要・プライバシー重視"),
    ProviderInfo("brave", "Brave Search", "🦁", "高精度・無料枠2000回/月"),
    ProviderInfo("google", "Google Custom Search", "🔍", "最高精度・無料枠100回/日"),
    ProviderInfo("searxng", "SearXNG", "🔒", "セルフホスト・完全プライバシー"),
  )

  // 地震速報設定
  var earthquakeEnabled by remember { mutableStateOf(true) }
  var earthquakeMinIntensity by remember { mutableStateOf("3") }
  var earthquakeRegion by remember { mutableStateOf("全国") }
  var notifySound by remember { mutableStateOf(true) }

  fun fetchSettings() {
    isLoading = true
    coroutineScope.launch {
      try {
        val resultJson = viewModel.runGatewayRpc(GatewayMethod.FeaturesGet, "{}")
        val obj = JSONObject(resultJson)
        val settings = obj.optJSONObject("settings")
        if (settings != null) {
          val web = settings.optJSONObject("webSearch")
          if (web != null) {
            webSearchEnabled = web.optBoolean("enabled", true)
            webSearchProvider = web.optString("provider", "duckduckgo")
            autoSaveToMemory = web.optBoolean("autoSaveToMemory", true)
            webApiKey = web.optString("apiKey", "")
            googleCseId = web.optString("googleCseId", "")
            searxngBaseUrl = web.optString("searxngBaseUrl", "")
          }

          val eq = settings.optJSONObject("earthquake")
          if (eq != null) {
            earthquakeEnabled = eq.optBoolean("enabled", true)
            earthquakeMinIntensity = eq.optString("minIntensity", "3")
            earthquakeRegion = eq.optString("targetRegion", "全国")
            notifySound = eq.optBoolean("notifySound", true)
          }
        }
      } catch (e: Exception) {
        statusMessage = "設定取得エラー: ${e.message}"
      } finally {
        isLoading = false
      }
    }
  }

  fun saveSettings() {
    statusMessage = "保存中..."
    coroutineScope.launch {
      try {
        val params = JSONObject().apply {
          put(
            "webSearch",
            JSONObject().apply {
              put("enabled", webSearchEnabled)
              put("provider", webSearchProvider.trim())
              put("autoSaveToMemory", autoSaveToMemory)
              put("apiKey", webApiKey.trim())
              put("googleCseId", googleCseId.trim())
              put("searxngBaseUrl", searxngBaseUrl.trim())
            }
          )
          put(
            "earthquake",
            JSONObject().apply {
              put("enabled", earthquakeEnabled)
              put("minIntensity", earthquakeMinIntensity.trim())
              put("targetRegion", earthquakeRegion.trim())
              put("notifySound", notifySound)
            }
          )
        }
        viewModel.runGatewayRpc(GatewayMethod.FeaturesUpdate, params.toString())
        statusMessage = "設定を保存しました！"
        fetchSettings()
      } catch (e: Exception) {
        statusMessage = "保存エラー: ${e.message}"
      }
    }
  }

  LaunchedEffect(Unit) {
    fetchSettings()
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
                onClick = { fetchSettings() }
              )
            }
            Text(
              text = "Web検索 ＆ 地震防災設定",
              style = ClawTheme.type.display.copy(fontSize = 18.sp, lineHeight = 22.sp),
              color = ClawTheme.colors.text
            )
            Text(
              text = "Web検索プロバイダや、地震速報通知の感度・地域を設定します。",
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

        // --- Web検索設定 ---
        item {
          ClawSectionHeader(title = "Web検索機能")
        }

        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Web検索を有効化",
                    style = ClawTheme.type.section,
                    color = ClawTheme.colors.text
                  )
                  Text(
                    text = "AIが最新情報や調べ物を自動で検索できるようにします",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted
                  )
                }
                Switch(
                  checked = webSearchEnabled,
                  onCheckedChange = { webSearchEnabled = it }
                )
              }

              if (webSearchEnabled) {
                // ── プロバイダ選択（タップ式） ──
                Text(
                  text = "検索プロバイダ",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  providers.forEach { prov ->
                    val isSelected = webSearchProvider == prov.id
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                          if (isSelected) ClawTheme.colors.primary.copy(alpha = 0.15f)
                          else ClawTheme.colors.surfaceRaised
                        )
                        .border(
                          width = if (isSelected) 1.5.dp else 0.5.dp,
                          color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.textMuted.copy(alpha = 0.2f),
                          shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { webSearchProvider = prov.id }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Text(text = prov.emoji, style = ClawTheme.type.body.copy(fontSize = 18.sp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = prov.label,
                          style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold),
                          color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.text
                        )
                        Text(
                          text = prov.desc,
                          style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                          color = ClawTheme.colors.textMuted
                        )
                      }
                      if (isSelected) {
                        Text(text = "✓", style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.primary)
                      }
                    }
                  }
                }

                // ── プロバイダ別設定フィールド ──
                when (webSearchProvider) {
                  "brave" -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = "Brave Search APIキー",
                      style = ClawTheme.type.caption,
                      color = ClawTheme.colors.textMuted
                    )
                    ClawTextField(
                      value = webApiKey,
                      onValueChange = { webApiKey = it },
                      placeholder = "BSA-xxxxxxxx（brave.com/search/api で取得）",
                      modifier = Modifier.fillMaxWidth()
                    )
                  }
                  "google" -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = "Google API キー",
                      style = ClawTheme.type.caption,
                      color = ClawTheme.colors.textMuted
                    )
                    ClawTextField(
                      value = webApiKey,
                      onValueChange = { webApiKey = it },
                      placeholder = "AIza... (Google Cloud Console で取得)",
                      modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = "Custom Search Engine ID",
                      style = ClawTheme.type.caption,
                      color = ClawTheme.colors.textMuted
                    )
                    ClawTextField(
                      value = googleCseId,
                      onValueChange = { googleCseId = it },
                      placeholder = "xxxxxxx:yyyyyyy (cse.google.com で作成)",
                      modifier = Modifier.fillMaxWidth()
                    )
                  }
                  "searxng" -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = "SearXNG ベースURL",
                      style = ClawTheme.type.caption,
                      color = ClawTheme.colors.textMuted
                    )
                    ClawTextField(
                      value = searxngBaseUrl,
                      onValueChange = { searxngBaseUrl = it },
                      placeholder = "http://localhost:8888 または https://searx.example.com",
                      modifier = Modifier.fillMaxWidth()
                    )
                  }
                  // duckduckgo → 追加フィールド不要
                }

                // ── 自動記憶保存トグル ──
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "検索結果を長期記憶(RAG)に自律保存",
                      style = ClawTheme.type.body,
                      color = ClawTheme.colors.text
                    )
                    Text(
                      text = "調べた有益な知識を自動的に学習し、次回以降の会話で引用します",
                      style = ClawTheme.type.caption,
                      color = ClawTheme.colors.textMuted
                    )
                  }
                  Switch(
                    checked = autoSaveToMemory,
                    onCheckedChange = { autoSaveToMemory = it }
                  )
                }
              }
            }
          }
        }

        // --- 地震・防災通知設定 ---
        item {
          ClawSectionHeader(title = "地震速報 ＆ 防災通知")
        }

        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "地震速報通知を有効化",
                    style = ClawTheme.type.section,
                    color = ClawTheme.colors.text
                  )
                  Text(
                    text = "地震発生時にAIパートナーが即座に通知・注意喚起します",
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted
                  )
                }
                Switch(
                  checked = earthquakeEnabled,
                  onCheckedChange = { earthquakeEnabled = it }
                )
              }

              if (earthquakeEnabled) {
                Text(
                  text = "通知する最低震度 (例: 1, 2, 3, 4, 5-)",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted
                )
                ClawTextField(
                  value = earthquakeMinIntensity,
                  onValueChange = { earthquakeMinIntensity = it },
                  placeholder = "例: 3 (震度3以上で通知)",
                  modifier = Modifier.fillMaxWidth()
                )

                Text(
                  text = "対象地域 (例: 全国, 関東, 近畿, 東京など)",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted
                )
                ClawTextField(
                  value = earthquakeRegion,
                  onValueChange = { earthquakeRegion = it },
                  placeholder = "全国",
                  modifier = Modifier.fillMaxWidth()
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "緊急通知アラート音",
                    style = ClawTheme.type.body,
                    color = ClawTheme.colors.text
                  )
                  Switch(
                    checked = notifySound,
                    onCheckedChange = { notifySound = it }
                  )
                }
              }
            }
          }
        }

        // 保存ボタン
        item {
          ClawPrimaryButton(
            text = "設定を保存して適用",
            onClick = { saveSettings() },
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    }
  }
}
