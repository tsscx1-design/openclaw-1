package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawSectionHeader
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
internal fun BackupRestoreScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

  val isConnected by viewModel.isConnected.collectAsState()
  val manualHost by viewModel.manualHost.collectAsState()
  val manualPort by viewModel.manualPort.collectAsState()
  val pairedGateways by viewModel.pairedGateways.collectAsState()

  var inputJson by remember { mutableStateOf("") }
  var statusMessage by remember { mutableStateOf("") }
  var pendingBackupToSave by remember { mutableStateOf<String?>(null) }

  // File save launcher (SAF)
  val createDocumentLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      if (uri != null && pendingBackupToSave != null) {
        try {
          context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(pendingBackupToSave!!.toByteArray(Charsets.UTF_8))
          }
          statusMessage = "バックアップファイルを保存しました！"
          Toast.makeText(context, "バックアップファイルを保存しました", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          statusMessage = "ファイル保存エラー: ${e.message}"
        }
      }
    }

  // File open launcher (SAF)
  val openDocumentLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        try {
          context.contentResolver.openInputStream(uri)?.use { input ->
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            val text = reader.readText()
            inputJson = text
            val success = viewModel.importClientBackup(text)
            if (success) {
              statusMessage = "バックアップから設定とペアリングを完全復元しました！Gatewayへ再接続します。"
              Toast.makeText(context, "設定を復元しました", Toast.LENGTH_SHORT).show()
            } else {
              statusMessage = "復元エラー: 正しいバックアップJSONではありません"
            }
          }
        } catch (e: Exception) {
          statusMessage = "ファイル読み込みエラー: ${e.message}"
        }
      }
    }

  fun copyBackupToClipboard() {
    try {
      val backupJson = viewModel.exportClientBackup()
      val clip = ClipData.newPlainText("OpenClaw Backup", backupJson)
      clipboardManager.setPrimaryClip(clip)
      statusMessage = "設定とペアリングのバックアップをクリップボードにコピーしました！"
      Toast.makeText(context, "クリップボードにコピーしました", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
      statusMessage = "バックアップ作成エラー: ${e.message}"
    }
  }

  fun saveBackupToFile() {
    try {
      val backupJson = viewModel.exportClientBackup()
      pendingBackupToSave = backupJson
      val filename = "openclaw_backup_${System.currentTimeMillis()}.json"
      createDocumentLauncher.launch(filename)
    } catch (e: Exception) {
      statusMessage = "バックアップ準備エラー: ${e.message}"
    }
  }

  fun restoreFromClipboard() {
    try {
      val clip = clipboardManager.primaryClip
      if (clip != null && clip.itemCount > 0) {
        val text = clip.getItemAt(0).text?.toString() ?: ""
        if (text.isNotBlank()) {
          inputJson = text
          val success = viewModel.importClientBackup(text)
          if (success) {
            statusMessage = "クリップボードから設定とペアリングを復元しました！Gatewayへ再接続します。"
            Toast.makeText(context, "設定とペアリングを復元しました", Toast.LENGTH_SHORT).show()
          } else {
            statusMessage = "復元失敗: クリップボードの内容が正しいバックアップJSONではありません"
          }
        } else {
          statusMessage = "クリップボードが空です"
        }
      } else {
        statusMessage = "クリップボードにテキストがありません"
      }
    } catch (e: Exception) {
      statusMessage = "復元エラー: ${e.message}"
    }
  }

  fun restoreFromTextInput() {
    if (inputJson.isBlank()) {
      statusMessage = "復元するJSONテキストを入力または貼り付けてください"
      return
    }
    try {
      val success = viewModel.importClientBackup(inputJson)
      if (success) {
        statusMessage = "テキストから設定とペアリングを復元しました！Gatewayへ再接続します。"
        Toast.makeText(context, "設定とペアリングを復元しました", Toast.LENGTH_SHORT).show()
      } else {
        statusMessage = "復元失敗: 入力された内容が正しいバックアップJSONではありません"
      }
    } catch (e: Exception) {
      statusMessage = "復元エラー: ${e.message}"
    }
  }

  // PC (Gateway) 設定のバックアップ取得
  fun backupGatewayConfig() {
    coroutineScope.launch {
      try {
        statusMessage = "PC側Gateway設定をバックアップ中..."
        val result = viewModel.exportGatewayBackup()
        pendingBackupToSave = result
        val filename = "gateway_pc_backup_${System.currentTimeMillis()}.json"
        createDocumentLauncher.launch(filename)
        statusMessage = "PC側Gateway設定（openclaw.json + partners.json）の保存ダイアログを開きました。"
      } catch (e: Exception) {
        statusMessage = "Gatewayバックアップエラー: ${e.message}"
      }
    }
  }

  var isSyncing by remember { mutableStateOf(false) }

  fun performBidirectionalSync() {
    if (!isConnected) {
      statusMessage = "PC Gatewayに接続されていません。PC起動中または同一ネットワーク/Tailscale接続をご確認ください。"
      return
    }
    isSyncing = true
    statusMessage = "PC Gatewayと記憶・設定・チャット履歴を相互同期中..."
    coroutineScope.launch {
      try {
        val params = JSONObject().apply {
          put("clientTimestamp", System.currentTimeMillis())
          put("pendingBondExp", 10)
        }
        val res = viewModel.runGatewayRpc(ai.openclaw.app.gateway.GatewayMethod.SyncBidirectional, params.toString())
        val obj = JSONObject(res)
        val ok = obj.optBoolean("ok", false)
        if (ok) {
          val payload = obj.optJSONObject("payload")
          val mergedFacts = payload?.optInt("mergedFactsCount") ?: 0
          val mergedMsgs = payload?.optInt("mergedMessagesCount") ?: 0
          statusMessage = "✅ 相互同期完了！(新規記憶: ${mergedFacts}件, 会話: ${mergedMsgs}件) PCとスマホのデータが完全一致しました。"
          Toast.makeText(context, "PCとスマホの相互同期が完了しました", Toast.LENGTH_SHORT).show()
        } else {
          val err = obj.optJSONObject("error")?.optString("message") ?: "不明なエラー"
          statusMessage = "同期失敗: $err"
        }
      } catch (e: Exception) {
        statusMessage = "同期エラー: ${e.message}"
      } finally {
        isSyncing = false
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
                onClick = onBack,
              )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(
                text = "設定・記憶の相互同期とバックアップ",
                style = ClawTheme.type.display.copy(fontSize = 15.sp, lineHeight = 20.sp),
                color = ClawTheme.colors.text,
                maxLines = 1,
              )
              Text(
                text = "PCとスマホ間の記憶・設定の双方向同期、およびバックアップ保存・復元が行えます。",
                style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                color = ClawTheme.colors.textMuted,
              )
            }
          }
        }

        // 双方向相互同期 (Auto Sync) パネル
        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "🔄 PC ↔ スマホ 記憶・設定 相互同期",
                  style = ClawTheme.type.section,
                  color = ClawTheme.colors.text,
                )
                Text(
                  text = if (isConnected) "🟢 PC接続中" else "🟠 オフライン",
                  style = ClawTheme.type.caption,
                  color = if (isConnected) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
                )
              }
              Text(
                text = "PCがオフライン時にスマホで話した会話や蓄積された記憶（Facts）・絆EXPをPC側へマージし、PC側の最新設定もスマホへ反映します。",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
              ClawPrimaryButton(
                text = if (isSyncing) "同期中..." else "今すぐ双方向相互同期を実行",
                onClick = { performBidirectionalSync() },
                enabled = isConnected && !isSyncing,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }

        if (statusMessage.isNotBlank()) {
          item {
            ClawPanel {
              Text(
                text = statusMessage,
                style = ClawTheme.type.body,
                color = ClawTheme.colors.text,
              )
            }
          }
        }

        // 現在のペアリング・設定情報サマリー
        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "現在のバックアップ対象情報",
                style = ClawTheme.type.section,
                color = ClawTheme.colors.text,
              )
              Text(
                text = "• 接続先ホスト: $manualHost:$manualPort\n• ペアリング済みGateway数: ${pairedGateways.size} 件\n• 認証トークン: 保持済み（暗号化バックアップ）\n• 外観・通知・ノード設定: 保持済み",
                style = ClawTheme.type.body,
                color = ClawTheme.colors.textMuted,
                lineHeight = 20.sp,
              )
            }
          }
        }

        // バックアップ（エクスポート）セクション
        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              ClawSectionHeader(title = "バックアップ（エクスポート）")
              Text(
                text = "現在のスマホ設定とPCペアリング情報を安全に保存します。",
                style = ClawTheme.type.body,
                color = ClawTheme.colors.textMuted,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ClawPrimaryButton(
                  text = "ファイルに保存",
                  onClick = ::saveBackupToFile,
                  modifier = Modifier.weight(1f),
                  icon = Icons.Default.Save,
                )
                ClawSecondaryButton(
                  text = "コピー",
                  onClick = ::copyBackupToClipboard,
                  modifier = Modifier.weight(1f),
                  icon = Icons.Default.ContentCopy,
                )
              }
            }
          }
        }

        // リストア（インポート）セクション
        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              ClawSectionHeader(title = "復元（リストア）")
              Text(
                text = "以前保存したバックアップから設定とペアリングを復元します。",
                style = ClawTheme.type.body,
                color = ClawTheme.colors.textMuted,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ClawPrimaryButton(
                  text = "ファイルから復元",
                  onClick = { openDocumentLauncher.launch(arrayOf("application/json", "*/*")) },
                  modifier = Modifier.weight(1f),
                  icon = Icons.Default.FileOpen,
                )
                ClawSecondaryButton(
                  text = "クリップボードから復元",
                  onClick = ::restoreFromClipboard,
                  modifier = Modifier.weight(1f),
                  icon = Icons.Default.ContentPaste,
                )
              }

              Text(
                text = "またはJSONを直接貼り付けて復元:",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
              ClawTextField(
                value = inputJson,
                onValueChange = { inputJson = it },
                placeholder = "バックアップJSONをここに貼り付け",
                modifier = Modifier.fillMaxWidth(),
              )
              if (inputJson.isNotBlank()) {
                ClawPrimaryButton(
                  text = "貼り付けたJSONから復元実行",
                  onClick = ::restoreFromTextInput,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }
        }

        // パソコン側Gateway設定のバックアップ（PC連携）
        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              ClawSectionHeader(title = "パソコン側Gateway設定のバックアップ")
              Text(
                text = "PC側Gatewayの openclaw.json 設定ファイルおよび AIパートナー設定（partners.json）をファイルにダウンロード保存します。",
                style = ClawTheme.type.body,
                color = ClawTheme.colors.textMuted,
              )

              ClawSecondaryButton(
                text = "PC側Gateway設定をバックアップ保存",
                onClick = ::backupGatewayConfig,
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Download,
              )
              if (!isConnected) {
                Text(
                  text = "※ PC側Gatewayに接続中の場合のみ利用可能です",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.warning,
                )
              }
            }
          }
        }
      }
    }
  }
}
