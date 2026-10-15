package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** モデルを選び直したときに、アバターの表示を更新するための合図。 */
internal object AvatarModelBus {
  var version by mutableIntStateOf(0)
}

private val KIND_LABEL = mapOf("live2d" to "Live2D", "vrm" to "VRM（3D）", "glb" to "glb（3D）", "motion" to "モーション", "3d-source" to "3D元データ")

/**
 * キャラの Live2D・3D モデルを管理する画面：PCにあるファイルの取り込み、スマホからのアップロード、使う・外す・削除。
 * Live2D はアバター欄に表示できます。VRM・glb はアバター欄で表示でき、VRM は共通骨格の動作も試せます。
 */
@Composable
internal fun AvatarModelsDialog(viewModel: MainViewModel, ownerId: String, onClose: () -> Unit) {
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var models by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var local by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var studioId by remember { mutableStateOf<String?>(null) }

    suspend fun rpc(method: GatewayMethod, params: JSONObject, timeoutMs: Long = 60_000): JSONObject =
      JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = timeoutMs))

    suspend fun reload() {
      runCatching {
        val rows = rpc(GatewayMethod.AvatarModelList, JSONObject().put("ownerId", ownerId)).optJSONArray("models")
        models = (0 until (rows?.length() ?: 0)).map { rows!!.getJSONObject(it) }
        val files = rpc(GatewayMethod.AvatarModelLocalList, JSONObject()).optJSONArray("files")
        local = (0 until (files?.length() ?: 0)).map { files!!.getJSONObject(it) }
      }.onFailure { message = "読み込めませんでした：${it.message}" }
    }
    LaunchedEffect(Unit) { reload() }
    // 3Dモデルを作っているあいだは、一覧を時々読み直して、できたら知らせる
    var generating by remember { mutableStateOf(false) }
    LaunchedEffect(generating) {
      if (!generating) return@LaunchedEffect
      val before = models.size
      var waited = 0
      while (waited < 60 * 40) {
        kotlinx.coroutines.delay(15_000)
        waited += 15
        reload()
        if (models.size > before) {
          message = "✅ 3Dモデルができました（下の一覧にあります）"
          generating = false
          return@LaunchedEffect
        }
      }
      generating = false
    }

    fun run(label: String, block: suspend () -> Unit) {
      if (busy) return
      scope.launch {
        busy = true
        message = label
        runCatching { block() }.onFailure { message = "できませんでした：${it.message}" }.onSuccess { if (message == label) message = "" }
        reload()
        AvatarModelBus.version += 1
        busy = false
      }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      if (uri == null) return@rememberLauncherForActivityResult
      run("スマホのファイルを送っています…") {
        withContext(Dispatchers.IO) {
          val name = context.contentResolver.query(uri, null, null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow("_display_name")) else null } ?: "model.zip"
          val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("ファイルを開けませんでした")
          val chunk = 1_000_000
          val total = maxOf(1, (bytes.size + chunk - 1) / chunk)
          var uploadId = ""
          for (i in 0 until total) {
            val part = bytes.copyOfRange(i * chunk, minOf(bytes.size, (i + 1) * chunk))
            val params = JSONObject().put("ownerId", ownerId).put("name", name).put("index", i).put("total", total).put("dataBase64", Base64.encodeToString(part, Base64.NO_WRAP))
            if (uploadId.isNotEmpty()) params.put("uploadId", uploadId)
            val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.AvatarModelUpload, params.toString(), timeoutMs = 120_000))
            uploadId = res.optString("uploadId")
          }
        }
      }
    }

    studioId?.let { initialId ->
      val rigged = models.filter { it.optString("kind") in setOf("vrm", "glb") }.map {
        Live2dInfo(it.optString("id"), it.optString("name"), it.optString("entry"), it.optLong("bytes"), it.optString("kind"))
      }
      HumanoidStudioDialog(viewModel, ownerId, rigged, initialId, onClose = { studioId = null })
    }

    Box(modifier = Modifier.fillMaxSize().background(ClawTheme.colors.canvas)) {
      LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically) {
            ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onClose)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "🎭 Live2D・3Dモデル", style = ClawTheme.type.title, color = ClawTheme.colors.text)
          }
        }
        item {
          Text(
            text = "このキャラのアバターに使うモデルを入れます。Live2D（zip）と、VRM・glb（3D）は、アバター欄にそのまま表示されます。VRM は返事に合わせて表情・口・まばたきが付き、glb はゆっくり回ります。Live2D の表示には、Live2D 公式の表示部品をネットから読みます。配布元の規約（再配布の禁止など）を守ってお使いください。",
            style = ClawTheme.type.captionSmall,
            color = ClawTheme.colors.textMuted,
          )
        }
        if (message.isNotBlank()) item { Text(text = message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary) }

        item { Text(text = "入っているモデル", style = ClawTheme.type.section, color = ClawTheme.colors.text) }
        if (models.isEmpty()) item { Text(text = "まだありません。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted) }
        items(models.size) { index ->
          val m = models[index]
          val active = m.optBoolean("active")
          Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(text = (if (active) "✅ " else "") + m.optString("name"), style = ClawTheme.type.body.copy(fontWeight = FontWeight.Bold), color = ClawTheme.colors.text)
            Text(text = "${KIND_LABEL[m.optString("kind")] ?: m.optString("kind")}　${m.optLong("bytes") / 1_000_000}MB　${m.optString("info")}", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            val warnings = m.optJSONArray("warnings")
            if (warnings != null && warnings.length() > 0) {
              Text(text = "⚠️ " + (0 until warnings.length()).joinToString("、") { warnings.getString(it) }, style = ClawTheme.type.captionSmall, color = ClawTheme.colors.primary)
            }
            if (m.optString("kind") in setOf("vrm", "glb")) {
              ClawSecondaryButton(text = "共通骨格で動作・揺れを試す", onClick = { studioId = m.optString("id") }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
              if (active) {
                ClawSecondaryButton(text = "外す（絵のアバターに戻す）", onClick = { run("切り替えています…") { rpc(GatewayMethod.AvatarModelDeactivate, JSONObject().put("ownerId", ownerId)) } }, modifier = Modifier.weight(1f))
              } else {
                ClawSecondaryButton(text = "使う", onClick = { run("切り替えています…") { rpc(GatewayMethod.AvatarModelActivate, JSONObject().put("ownerId", ownerId).put("id", m.optString("id"))) } }, modifier = Modifier.weight(1f))
              }
              ClawSecondaryButton(text = "削除", onClick = { run("削除しています…") { rpc(GatewayMethod.AvatarModelDelete, JSONObject().put("ownerId", ownerId).put("id", m.optString("id"))) } })
            }
          }
        }

        item { Text(text = "PCにあるファイルから取り込む", style = ClawTheme.type.section, color = ClawTheme.colors.text, modifier = Modifier.padding(top = 8.dp)) }
        item { Text(text = "PCの D:\\media-tools\\downloads に入れた zip・vrm・glb などが並びます。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted) }
        if (local.isEmpty()) item { Text(text = "ファイルがありません。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted) }
        items(local.size) { index ->
          val f = local[index]
          Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(text = f.optString("name"), style = ClawTheme.type.caption, color = ClawTheme.colors.text)
              Text(text = "${f.optLong("bytes") / 1_000_000}MB", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
            }
            ClawSecondaryButton(text = "取り込む", onClick = { run("取り込んでいます（大きいと少しかかります）…") { rpc(GatewayMethod.AvatarModelImport, JSONObject().put("ownerId", ownerId).put("name", f.optString("name")), 300_000) } })
          }
        }

        item {
          ClawSecondaryButton(
            text = if (generating) "🧊 3Dモデルを作っています…（できたら上の一覧に出ます）" else "🧊 設定画から3Dモデルを作る（約5分・PCで作成）",
            onClick = {
              if (generating) return@ClawSecondaryButton
              scope.launch {
                runCatching { rpc(GatewayMethod.Model3dGenerate, JSONObject().put("characterId", ownerId)) }
                  .onSuccess { generating = true; message = "3Dモデルの作成を始めました。PCのGPUを使うので、チャットは少しのあいだ止まります。" }
                  .onFailure { message = "始められませんでした：${it.message}" }
              }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          )
        }
        item { ClawSecondaryButton(text = "📱 スマホのファイルを選んで送る", onClick = { picker.launch("*/*") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
        item { Spacer(modifier = Modifier.heightIn(min = 24.dp)) }
      }
    }
  }
}
