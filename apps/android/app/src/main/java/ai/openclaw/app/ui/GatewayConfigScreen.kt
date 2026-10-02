package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun GatewayConfigScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var configJsonText by remember { mutableStateOf("Loading...") }
  var statusMessage by remember { mutableStateOf("") }
  var isEditing by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    try {
      val response = viewModel.runGatewayRpc(GatewayMethod.ConfigGet, "{}")
      val json = JSONObject(response)
      val configObj = json.optJSONObject("config")
      configJsonText = configObj?.toString(2) ?: "{}"
    } catch (e: Exception) {
      configJsonText = "{}"
      statusMessage = "Failed to load config: ${e.message}"
    }
  }

  fun saveConfig() {
    coroutineScope.launch {
      try {
        statusMessage = "Saving..."
        val parsedConfig = JSONObject(configJsonText)
        val params = JSONObject().apply { put("config", parsedConfig) }
        viewModel.runGatewayRpc(GatewayMethod.ConfigSet, params.toString())
        statusMessage = "Saved successfully."
        isEditing = false
      } catch (e: Exception) {
        statusMessage = "Failed to save: ${e.message}"
      }
    }
  }

  ClawScaffold(
    contentPadding = PaddingValues(start = 20.dp, top = 13.dp, end = 20.dp, bottom = 6.dp),
    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
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
            }
            Text(
              text = "Gateway Config Editor",
              style = ClawTheme.type.display.copy(fontSize = 14.8.sp, lineHeight = 18.sp),
              color = ClawTheme.colors.text
            )
            Text(
              text = "Edit raw Gateway configuration JSON. Warning: Invalid changes may break functionality.",
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

        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              if (isEditing) {
                ClawTextField(
                  value = configJsonText,
                  onValueChange = { configJsonText = it },
                  placeholder = "{}",
                  modifier = Modifier.fillMaxWidth().height(400.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                  ClawSecondaryButton(text = "Cancel", onClick = { isEditing = false })
                  ClawPrimaryButton(text = "Save", onClick = { saveConfig() })
                }
              } else {
                Text(
                  text = configJsonText,
                  style = ClawTheme.type.mono.copy(fontSize = 12.sp),
                  color = ClawTheme.colors.text
                )
                ClawPrimaryButton(
                  text = "Edit",
                  onClick = { isEditing = true },
                  modifier = Modifier.align(Alignment.End)
                )
              }
            }
          }
        }
      }
    }
  }
}
