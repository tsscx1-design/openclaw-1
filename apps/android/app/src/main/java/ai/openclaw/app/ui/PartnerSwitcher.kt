package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private data class SwitcherPartner(val id: String, val name: String)

/**
 * Settings screens for one partner (memory, dreams, bond, ...): a row of partner chips on top
 * so every partner can be opened, not only the one last picked in a chat.
 */
@Composable
internal fun PartnerScopedScreen(
  viewModel: MainViewModel,
  content: @Composable (partnerId: String) -> Unit,
) {
  val partnerId = rememberSettingsPartnerId(viewModel)
  var partners by remember { mutableStateOf<List<SwitcherPartner>>(emptyList()) }
  LaunchedEffect(Unit) {
    partners =
      runCatching {
        val arr = JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerList, "{}")).optJSONArray("partners")
        (0 until (arr?.length() ?: 0)).mapNotNull { i ->
          val p = arr?.optJSONObject(i) ?: return@mapNotNull null
          val id = p.optString("id")
          if (id.isBlank()) return@mapNotNull null
          SwitcherPartner(id, p.optString("name", id))
        }
      }.getOrDefault(emptyList())
  }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(ClawTheme.colors.canvas)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
  ) {
    if (partners.size > 1) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        partners.forEach { p ->
          val selected = p.id == partnerId
          Box(
            modifier =
              Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) ClawTheme.colors.accentSoft else ClawTheme.colors.surface)
                .border(
                  1.dp,
                  if (selected) ClawTheme.colors.accentBorder else ClawTheme.colors.border,
                  RoundedCornerShape(16.dp),
                )
                .clickable { viewModel.selectPartnerForChat(p.id) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Text(
              text = p.name,
              style = ClawTheme.type.label,
              color = if (selected) ClawTheme.colors.accent else ClawTheme.colors.text,
            )
          }
        }
      }
    }
    Box(modifier = Modifier.weight(1f)) {
      // A fresh screen per partner, so no state of the previous partner leaks over.
      key(partnerId) { content(partnerId) }
    }
  }
}
