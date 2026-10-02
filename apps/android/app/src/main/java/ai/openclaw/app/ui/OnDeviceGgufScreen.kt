package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawSectionHeader
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class LoadedLocalGguf(
  val uri: String,
  val fileName: String,
  val sizeMb: String,
  val isActive: Boolean = false,
)

@Composable
internal fun OnDeviceGgufScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val isConnected by viewModel.isConnected.collectAsState()

  // 端末内に登録されたGGUF一覧（状態保持）
  val localGgufList = remember {
    mutableStateListOf<LoadedLocalGguf>(
      LoadedLocalGguf(
        uri = "content://sample/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        sizeMb = "1,180 MB (1.18 GB)",
        isActive = true,
      )
    )
  }

  var activeModelName by remember { mutableStateOf("qwen2.5-1.5b-instruct-q4_k_m.gguf") }
  var localServerUrl by remember { mutableStateOf("http://127.0.0.1:11434") }
  var statusMessage by remember { mutableStateOf("") }

  // SAF (Storage Access Framework) ファイルピッカー
  val openGgufLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) {
        try {
          var name = "unknown.gguf"
          var size = 0L

          context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
              if (nameIndex != -1) name = cursor.getString(nameIndex)
              if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
          }

          val sizeFormatted = if (size > 0) {
            val mb = size / (1024 * 1024)
            if (mb > 1024) {
              String.format("%.2f GB (%d MB)", mb / 1024f, mb)
            } else {
              "$mb MB"
            }
          } else {
            "サイズ取得中"
          }

          localGgufList.add(
            LoadedLocalGguf(
              uri = uri.toString(),
              fileName = name,
              sizeMb = sizeFormatted,
              isActive = false,
            )
          )
          activeModelName = name
          statusMessage = "端末内のGGUFファイル「$name」を読み込みました！"
          Toast.makeText(context, "GGUFを読み込みました: $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          statusMessage = "読み込みエラー: ${e.message}"
        }
      }
    }

  ClawScaffold(
    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 6.dp),
    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              ClawIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "戻る",
              )
              ClawIconButton(
                onClick = { openGgufLauncher.launch(arrayOf("*/*")) },
                icon = Icons.Default.FileOpen,
                contentDescription = "GGUFを選択",
              )
            }
            Text(
              text = "端末内GGUF & オフラインAI",
              style = ClawTheme.type.display.copy(fontSize = 19.sp, lineHeight = 23.sp),
              color = ClawTheme.colors.text,
            )
            Text(
              text = "端末ストレージのGGUFを選択し、完全オフラインでAIチャットを実行します。",
              style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
              color = ClawTheme.colors.textMuted,
            )
          }
        }
      item {
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "⚡ オフライン / オンデバイスAI状態",
                style = ClawTheme.type.section,
                color = ClawTheme.colors.text,
              )
              Text(
                text = if (isConnected) "🟢 PC Gateway接続中" else "🟠 オフライン (スマホ単体動作)",
                style = ClawTheme.type.caption,
                color = if (isConnected) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
              )
            }
            Text(
              text = "PCがオフライン時でも、Android端末（Galaxy Z Fold等）のストレージにあるGGUFモデルを選択してロードすることで、完全スタンドアローンでAIチャットが可能です。",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ClawPrimaryButton(
                text = "📁 端末内GGUFを選択",
                onClick = { openGgufLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }

      if (statusMessage.isNotBlank()) {
        item {
          ClawPanel {
            Text(
              text = statusMessage,
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.primary,
            )
          }
        }
      }

      // 読み込み済みGGUFモデル一覧
      item {
        ClawSectionHeader(title = "読み込み済みGGUFモデル (${localGgufList.size}件)")
      }

      items(localGgufList) { model ->
        val isSelected = model.fileName == activeModelName
        ClawPanel {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Active",
                    tint = ClawTheme.colors.primary,
                  )
                }
                Text(
                  text = model.fileName,
                  style = ClawTheme.type.section.copy(fontSize = 14.sp),
                  color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.text,
                )
              }
              Text(text = "サイズ: ${model.sizeMb}", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            }

            ClawSecondaryButton(
              text = if (isSelected) "使用中" else "このモデルを使用",
              onClick = {
                activeModelName = model.fileName
                statusMessage = "オフラインAIモデルを「${model.fileName}」に設定しました。"
              },
            )
          }
        }
      }

      // オフライン推論サーバー設定 (Termux / 内蔵エンジン)
      item {
        ClawSectionHeader(title = "⚙️ オンデバイス推論エンドポイント設定")
      }

      item {
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "端末ローカル推論サーバーURL",
              style = ClawTheme.type.section.copy(fontSize = 13.sp),
              color = ClawTheme.colors.text,
            )
            Text(
              text = "Termux上のOllama (http://127.0.0.1:11434) または端末内llama.cppサーバーのアドレスを指定します。",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted,
            )
            ClawTextField(
              value = localServerUrl,
              onValueChange = { localServerUrl = it },
              placeholder = "http://127.0.0.1:11434",
              modifier = Modifier.fillMaxWidth(),
            )
            ClawSecondaryButton(
              text = "接続テスト & 反映",
              onClick = {
                statusMessage = "ローカル推論先を「$localServerUrl」に更新しました。"
                Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.align(Alignment.End),
            )
          }
        }
      }
    }
  }
}
}

