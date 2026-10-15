package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * ファンブックの合体。グループ（または分類）を2つ以上選んで、新しい名前で1つにする。
 * グループ：同じ人物は1人にまとめ（空の項目を埋め、メモ・タグをつなぎ、写真は全部残す）、元のグループと、その写真フォルダは削除する。
 * 分類：中のグループを新しい分類に移し、あなたが作った元の分類は削除する。
 * 実行の前に、ファンブックのバックアップを自動で作る（名簿・分類・非表示の情報。写真そのものは、新しい側へ写してから、元のフォルダを消す）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FanBookMergeDialog(
  viewModel: MainViewModel,
  groups: List<String>,
  categories: List<String>,
  onDismiss: () -> Unit,
  onDone: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  var byGroup by remember { mutableStateOf(true) }
  var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
  var name by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }
  var confirmText by remember { mutableStateOf<String?>(null) }
  val items = if (byGroup) groups else categories

  suspend fun rpc(method: GatewayMethod, params: JSONObject, timeoutMs: Long = 600_000): JSONObject = JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = timeoutMs))

  fun payload(): JSONObject = JSONObject().put(if (byGroup) "groups" else "names", JSONArray(picked.toList())).put("name", name.trim())

  fun check() {
    if (busy) return
    busy = true
    message = ""
    scope.launch {
      runCatching {
        if (byGroup) {
          val plan = rpc(GatewayMethod.IdolMasterGroupMerge, payload().put("plan", true), 60_000).getJSONObject("plan")
          "「${picked.joinToString("」「")}」を、「${plan.optString("newName")}」に合体します。\n\n" +
            "・いまの人数：${plan.optInt("people")}人 → 合体後：${plan.optInt("result")}人（同じ人物としてまとめる人：${plan.optInt("duplicates")}人）\n" +
            "・同じ人物は1人にして、空の項目を埋めます。メモとタグはつなぎ、写真は全部残します（メインは1枚、あとは追加の写真）。\n" +
            "・元のグループは名簿から外し、元の写真フォルダは削除します。\n" +
            "・実行の前に、ファンブックのバックアップを自動で作ります（名簿・分類。写真は新しい側へ写してから、元を消します）。"
        } else {
          "分類「${picked.joinToString("」「")}」を、「${name.trim()}」に合体します。\n\n中のグループは、すべて新しい分類に移します。あなたが作った元の分類は削除します（最初からある分類は、空のまま残ります）。実行の前に、バックアップを自動で作ります。"
        }
      }
        .onSuccess { confirmText = it }
        .onFailure { message = "確認できませんでした：${it.message}" }
      busy = false
    }
  }

  fun run() {
    busy = true
    message = ""
    confirmText = null
    scope.launch {
      runCatching {
        if (byGroup) {
          val r = rpc(GatewayMethod.IdolMasterGroupMerge, payload()).getJSONObject("result")
          "合体しました：「${r.optString("newName")}」${r.optInt("result")}人（まとめた人：${r.optInt("duplicates")}人）、写真 ${r.optInt("photos")}枚・追加の写真 ${r.optInt("extraPhotos")}枚を引き継ぎ、元の写真フォルダを${r.optInt("foldersDeleted")}件削除しました"
        } else {
          val r = rpc(GatewayMethod.IdolMasterCategoryMerge, payload()).getJSONObject("result")
          "分類を合体しました：グループ ${r.optInt("groups")}個を移し、元の分類を${r.optJSONArray("removed")?.length() ?: 0}個削除しました"
        }
      }
        .onSuccess { onDone(it) }
        .onFailure { message = "合体できませんでした：${it.message}" }
      busy = false
    }
  }

  confirmText?.let { text ->
    FanBookConfirmDialog(title = "合体しますか？", text = text, confirmText = "合体する", onDismiss = { confirmText = null }, onConfirm = { run() })
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("ファンブックを合体") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ClawSecondaryButton(text = if (byGroup) "● グループを合体" else "○ グループを合体", onClick = { byGroup = true; picked = emptySet() })
          ClawSecondaryButton(text = if (!byGroup) "● 分類を合体" else "○ 分類を合体", onClick = { byGroup = false; picked = emptySet() })
        }
        Text("合体するものを、2つ以上選んでください（${picked.size}個）。", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          items.forEach { item ->
            val on = item in picked
            ClawSecondaryButton(text = (if (on) "● " else "○ ") + item, onClick = { picked = if (on) picked - item else picked + item })
          }
        }
        ClawTextField(value = name, onValueChange = { name = it }, placeholder = "新しい名前", label = "合体後の名前（元の名前とは別の名前）", modifier = Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    },
    confirmButton = { TextButton(enabled = !busy && picked.size >= 2 && name.isNotBlank(), onClick = { check() }) { Text(if (busy) "確認中…" else "確認へ") } },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("閉じる") } },
  )
}
