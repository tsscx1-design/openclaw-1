package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * アプリの中で、Google の画像検索を開く（別のアプリに移らない）。気に入った画像を長押しして、この人の写真にする。
 * 長押しした画像は、何枚でも選べる（もう一度長押しすると、選びなおし）。まとめて保存できる。
 * 画像を一度タップして、大きく表示してから長押しすると、大きいほうの画像が入る。
 * Google の画面を、そのまま表示しているだけです（中身を、自動で読み取ることは、しません）。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdolWebSearchDialog(
  viewModel: MainViewModel,
  group: String,
  member: IdolMemberRow,
  query: String,
  onDismiss: () -> Unit,
  onSaved: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  // 長押しで選んだ画像（何枚でも）。もう一度長押しすると、選びなおし
  var picks by remember { mutableStateOf<List<String>>(emptyList()) }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }
  var saved by remember { mutableStateOf(0) }
  val searchUrl = "https://www.google.com/search?tbm=isch&hl=ja&q=" + Uri.encode(query.trim())

  suspend fun saveOne(src: String, extra: Boolean) {
    if (src.startsWith("data:image/")) {
      val comma = src.indexOf(',')
      require(comma > 0) { "画像を読めませんでした" }
      val mime = src.substring(5, comma).substringBefore(';')
      val raw = src.substring(comma + 1)
      val base64 = if (raw.contains('%')) Uri.decode(raw) else raw
      val params = JSONObject().put("group", group).put("id", member.id).put("mime", mime).put("base64", base64)
      if (extra) {
        viewModel.runGatewayRpc(GatewayMethod.IdolMasterPhotoExtras, params.put("action", "add").toString(), timeoutMs = 120_000)
      } else {
        viewModel.runGatewayRpc(GatewayMethod.IdolMasterPhotoSet, params.toString(), timeoutMs = 120_000)
      }
    } else {
      viewModel.runGatewayRpc(GatewayMethod.IdolMasterPhotoFromUrl, JSONObject().put("group", group).put("id", member.id).put("url", src).put("asExtra", extra).toString(), timeoutMs = 120_000)
    }
  }

  // firstIsMain=true：1枚目をメインの写真にして、残りは追加の写真／false：すべて追加の写真
  fun saveAll(firstIsMain: Boolean) {
    val list = picks
    if (list.isEmpty() || busy) return
    busy = true
    message = ""
    scope.launch {
      var ok = 0
      var failed = 0
      for ((i, src) in list.withIndex()) {
        message = "入れています…（${i + 1} / ${list.size}）"
        runCatching { saveOne(src, extra = !(firstIsMain && i == 0)) }.onSuccess { ok += 1 }.onFailure { failed += 1 }
      }
      saved += ok
      picks = emptyList()
      message = "入れました（${ok}枚）" + (if (failed > 0) "（入れられなかった画像：${failed}枚）" else "") + "。続けて選べます。終わったら、「閉じる」を押してください"
      busy = false
    }
  }

  Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
    Column(modifier = Modifier.fillMaxSize().background(ClawTheme.colors.surface)) {
      Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("「${member.name}」の画像を、Google で探す", style = ClawTheme.type.caption, color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
        TextButton(enabled = !busy, onClick = { if (saved > 0) onSaved("「${member.name}」に、Google の画像を${saved}枚入れました") else onDismiss() }) { Text("閉じる") }
      }
      Text("気に入った画像を、タップして大きく出してから、長押しすると、選べます（何枚でも。もう一度長押しで、選びなおし）。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted, modifier = Modifier.padding(horizontal = 12.dp))
      AndroidView(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        factory = { ctx ->
          WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            setOnLongClickListener {
              val hit = hitTestResult
              val extra = hit.extra
              if ((hit.type == WebView.HitTestResult.IMAGE_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) && !extra.isNullOrBlank()) {
                picks = if (extra in picks) picks - extra else picks + extra
                true
              } else {
                false
              }
            }
            loadUrl(searchUrl)
          }
        },
      )
      Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (picks.isNotEmpty()) {
          Text("${picks.size}枚を選んでいます。どうしますか？", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ClawPrimaryButton(text = if (member.hasPhoto) "1枚目をメイン（置き換え）、残りは追加" else "1枚目をメイン、残りは追加", onClick = { saveAll(true) })
            ClawSecondaryButton(text = "すべて追加の写真に", onClick = { saveAll(false) })
            ClawSecondaryButton(text = "選びなおす", onClick = { picks = emptyList() })
          }
        }
        if (busy) Text("入れています…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    }
  }
}
