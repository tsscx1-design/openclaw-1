package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** モデル導入画面の1件（ゲートウェイの model.store.list の1行）。 */
internal data class StoreModel(
  val id: String,
  val name: String,
  val audience: String,
  val kind: String,
  val arch: String,
  val genre: String,
  val sizeGB: Double,
  val url: String,
  val desc: String,
  val popular: String,
  val usable: Boolean,
  val state: String,
  val received: Long,
  val total: Long,
  val error: String,
)

/** 導入画面の一覧。ダウンロード済みで使えるモデルは、絵のモデル選択欄にも出ます。 */
internal object ModelStoreCache {
  var models by mutableStateOf<List<StoreModel>>(emptyList())
  var hasKey by mutableStateOf(false)

  suspend fun refresh(viewModel: MainViewModel) {
    runCatching {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.ModelStoreList, "{}", timeoutMs = 60_000))
      val rows = res.optJSONArray("models")
      hasKey = res.optBoolean("hasKey")
      models = (0 until (rows?.length() ?: 0)).map { i ->
        val o = rows!!.getJSONObject(i)
        StoreModel(
          id = o.optString("id"), name = o.optString("name"), audience = o.optString("audience"), kind = o.optString("kind"),
          arch = o.optString("arch"), genre = o.optString("genre"), sizeGB = o.optDouble("sizeGB", 0.0), url = o.optString("url"),
          desc = o.optString("desc"), popular = o.optString("popular"), usable = o.optBoolean("usable"), state = o.optString("state"),
          received = o.optLong("received"), total = o.optLong("total"), error = o.optString("error"),
        )
      }
    }
  }

  fun find(id: String): StoreModel? = models.firstOrNull { it.id == id }
}

private val GENRE_LABEL = mapOf("anime" to "🎌 アニメ", "real" to "📷 実写", "3d" to "🧊 3D・CG調", "manga" to "📖 漫画調")

/**
 * 調べたモデルの一覧。ダウンロードすると、そのまま絵のモデルとして使えます（SDXL系）。
 * Civitai のモデルは、自分の API 鍵が必要です。Hugging Face のものは鍵なしで入ります。
 */
@Composable
internal fun ModelStoreDialog(viewModel: MainViewModel, onClose: () -> Unit) {
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var keyText by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf<Set<String>>(emptySet()) }
    val models = ModelStoreCache.models

    LaunchedEffect(Unit) {
      while (true) {
        ModelStoreCache.refresh(viewModel)
        delay(if (ModelStoreCache.models.any { it.state == "downloading" }) 2_500 else 10_000)
      }
    }

    suspend fun call(method: GatewayMethod, params: JSONObject) {
      runCatching { viewModel.runGatewayRpc(method, params.toString(), timeoutMs = 60_000) }
        .onFailure { message = "できませんでした：${it.message}" }
      ModelStoreCache.refresh(viewModel)
    }

    Box(modifier = Modifier.fillMaxSize().background(ClawTheme.colors.canvas)) {
      LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically) {
            ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onClose)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "📦 モデルを追加で導入", style = ClawTheme.type.title, color = ClawTheme.colors.text)
          }
        }
        item {
          Text(
            text = "調べたモデルの一覧です。「ダウンロード」を押すとPCに入り、終わると絵のモデル選択欄に出て、そのまま使えます（1本あたり約7GB）。一般向けと成人向けに分け、ジャンルごとに折りたたんでいます。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textMuted,
          )
        }
        item {
          Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = if (ModelStoreCache.hasKey) "🔑 Civitai の鍵：登録済み" else "🔑 Civitai の鍵：未登録（Civitai のモデルには必要です）",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.text,
            )
            Text(
              text = "Civitai のサイトで作った自分の API 鍵を入れてください。鍵はPCの中に保存され、アプリには戻りません。空にして保存すると削除します。",
              style = ClawTheme.type.captionSmall,
              color = ClawTheme.colors.textMuted,
            )
            ClawTextField(value = keyText, onValueChange = { keyText = it }, placeholder = "API 鍵", label = "Civitai API 鍵", modifier = Modifier.fillMaxWidth(), secret = true)
            ClawSecondaryButton(
              text = "鍵を保存する",
              onClick = {
                scope.launch {
                  call(GatewayMethod.ModelStoreCivitaiKey, JSONObject().put("key", keyText.trim()))
                  keyText = ""
                  message = "鍵を保存しました。"
                }
              },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
        if (message.isNotBlank()) item { Text(text = message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary) }
        if (models.isEmpty()) item { Text(text = "読み込んでいます…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted) }

        val sections = listOf("general" to "🌿 一般", "adult" to "🔞 成人向け")
        sections.forEach { (audience, label) ->
          val mine = models.filter { it.audience == audience }
          if (mine.isEmpty()) return@forEach
          item { Text(text = "$label（${mine.size}件）", style = ClawTheme.type.section, color = ClawTheme.colors.text, modifier = Modifier.padding(top = 6.dp)) }
          val groups = mine.filter { it.usable }.groupBy { it.genre }.toList().sortedBy { GENRE_LABEL.keys.indexOf(it.first) } +
            listOf("ref" to mine.filter { !it.usable })
          groups.forEach { (genre, rows) ->
            if (rows.isEmpty()) return@forEach
            val key = "$audience/$genre"
            val isOpen = key in opened
            val title = if (genre == "ref") "📚 参考（別の読み込み方が必要で、まだ使えません）" else GENRE_LABEL[genre] ?: genre
            item(key = "head-$key") {
              Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.4f))
                  .clickable { opened = if (isOpen) opened - key else opened + key }.padding(horizontal = 10.dp, vertical = 8.dp),
              ) {
                Text(text = "${if (isOpen) "▾" else "▸"} $title（${rows.size}）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
              }
            }
            if (isOpen) {
              rows.forEach { m ->
                item(key = "row-${m.id}") {
                  StoreRow(
                    m,
                    onDownload = { scope.launch { call(GatewayMethod.ModelStoreDownload, JSONObject().put("id", m.id)) } },
                    onCancel = { scope.launch { call(GatewayMethod.ModelStoreCancel, JSONObject().put("id", m.id)) } },
                    onDelete = { scope.launch { call(GatewayMethod.ModelStoreDelete, JSONObject().put("id", m.id)) } },
                  )
                }
              }
            }
          }
        }
        item { Spacer(modifier = Modifier.heightIn(min = 24.dp)) }
      }
    }
  }
}

@Composable
private fun StoreRow(m: StoreModel, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Text(text = m.name, style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.text)
    if (m.desc.isNotBlank()) Text(text = m.desc, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    val meta = listOf(
      if (m.sizeGB > 0) "約${"%.1f".format(m.sizeGB)}GB" else "",
      m.arch,
      when (m.kind) { "lora" -> "LoRA"; "tool" -> "ツール"; else -> "" },
      m.popular,
    ).filter { it.isNotBlank() }.joinToString("　")
    if (meta.isNotBlank()) Text(text = meta, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    if (m.error.isNotBlank() && m.state != "installed") Text(text = "⚠️ ${m.error}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
    when (m.state) {
      "installed" -> Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "✅ 導入済み（絵のモデルに出ます）", style = ClawTheme.type.caption, color = ClawTheme.colors.primary, modifier = Modifier.weight(1f))
        ClawSecondaryButton(text = "削除", onClick = onDelete)
      }
      "downloading" -> Row(verticalAlignment = Alignment.CenterVertically) {
        val percent = if (m.total > 0) (m.received * 100 / m.total).toInt() else 0
        Text(text = "⬇️ ダウンロード中 ${percent}%（${m.received / 1_000_000}MB）", style = ClawTheme.type.caption, color = ClawTheme.colors.primary, modifier = Modifier.weight(1f))
        ClawSecondaryButton(text = "中止", onClick = onCancel)
      }
      "downloadable" -> ClawSecondaryButton(text = "⬇️ ダウンロード", onClick = onDownload, modifier = Modifier.fillMaxWidth())
      "needs-key" -> Text(text = "🔑 Civitai の鍵を登録すると、ダウンロードできます", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      else -> Text(text = "ℹ️ ${m.url}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    }
  }
}
