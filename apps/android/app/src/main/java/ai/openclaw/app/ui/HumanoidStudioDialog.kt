package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.HumanoidRigCorrection
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private fun humanoidStudioNumber(value: Float): Double = kotlin.math.round(value.toDouble() * 10_000) / 10_000

/** Rig corrections are durable per model; other preview controls remain session state. */
@Composable
internal fun HumanoidStudioDialog(viewModel: MainViewModel, ownerId: String, models: List<Live2dInfo>, initialId: String, onClose: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var selected by remember { mutableStateOf(initialId) }
  val gatewayId by viewModel.activeGatewayStableId.collectAsState()
  var motion by remember { mutableStateOf("idle") }
  var speed by remember { mutableStateOf(1f) }
  var flexibility by remember { mutableStateOf(1f) }
  var physics by remember { mutableStateOf(true) }
  var paused by remember { mutableStateOf(false) }
  var accessory by remember { mutableStateOf("none") }
  var accessoryScale by remember { mutableStateOf(1f) }
  var accessoryY by remember { mutableStateOf(0f) }
  var accessoryZ by remember { mutableStateOf(0f) }
  var expression by remember { mutableStateOf("normal") }
  var speaking by remember { mutableStateOf(false) }
  var autoRig by remember(gatewayId, ownerId, selected) { mutableStateOf(false) }
  var showBones by remember { mutableStateOf(false) }
  var shoulderWidth by remember(gatewayId, ownerId, selected) { mutableStateOf(0.24f) }
  var hipHeight by remember(gatewayId, ownerId, selected) { mutableStateOf(0.53f) }
  var kneeHeight by remember(gatewayId, ownerId, selected) { mutableStateOf(0.28f) }
  var armAngle by remember(gatewayId, ownerId, selected) { mutableStateOf(0f) }
  var appliedRigProfile by remember(gatewayId, ownerId, selected) { mutableStateOf<String?>(null) }
  var rigLoading by remember(gatewayId, ownerId, selected) { mutableStateOf(true) }
  var rigSaving by remember { mutableStateOf(false) }
  var rigLoadFailed by remember(gatewayId, ownerId, selected) { mutableStateOf(false) }
  var rigLoadRevision by remember { mutableStateOf(0) }
  var bvh by remember { mutableStateOf<String?>(null) }
  var bvhRevision by remember { mutableStateOf(0) }
  var message by remember { mutableStateOf("") }
  var exportRequest by remember { mutableStateOf(0) }
  var exportSeconds by remember { mutableStateOf(5) }
  var exporting by remember { mutableStateOf(false) }
  var viewerReady by remember { mutableStateOf(false) }
  var audioImporting by remember { mutableStateOf(false) }
  var savingVideo by remember { mutableStateOf(false) }
  var exportAudio by remember { mutableStateOf<File?>(null) }
  var pendingVideo by remember { mutableStateOf<File?>(null) }
  LaunchedEffect(gatewayId, ownerId, selected, rigLoadRevision) {
    rigLoading = true
    rigLoadFailed = false
    try {
      val gateway = gatewayId ?: error("接続先を選んでから開き直してください")
      val saved = viewModel.loadHumanoidRigCorrection(gateway, ownerId, selected)
      if (saved != null) {
        shoulderWidth = saved.shoulderWidth.toFloat(); hipHeight = saved.hipHeight.toFloat()
        kneeHeight = saved.kneeHeight.toFloat(); armAngle = saved.armAngle.toFloat()
        appliedRigProfile = saved.profile().toString(); autoRig = saved.enabled
        message = "このモデルの補正値を端末から復元しました。"
      }
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      rigLoadFailed = true
      message = "補正値を読み込めませんでした：${error.message}。再読み込みしてください。"
    } finally {
      rigLoading = false
    }
  }
  fun saveRig(enabled: Boolean) {
    val gateway = gatewayId ?: return
    val modelId = selected
    // Disabling must retain the applied correction rather than commit unreviewed slider edits.
    val applied = if (!enabled && autoRig) appliedRigProfile?.let { JSONObject(it) } else null
    val correction = HumanoidRigCorrection(enabled,
      applied?.getDouble("shoulderWidth") ?: humanoidStudioNumber(shoulderWidth),
      applied?.getDouble("hipHeight") ?: humanoidStudioNumber(hipHeight),
      applied?.getDouble("kneeHeight") ?: humanoidStudioNumber(kneeHeight),
      applied?.getDouble("armAngle") ?: humanoidStudioNumber(armAngle))
    rigSaving = true
    scope.launch {
      try {
        viewModel.saveHumanoidRigCorrection(gateway, ownerId, modelId, correction)
        if (gatewayId == gateway && selected == modelId) {
          appliedRigProfile = correction.profile().toString()
          if (autoRig != enabled || enabled) viewerReady = false
          autoRig = enabled
          message = "このモデルの補正値を端末に保存しました。次回も自動で復元します。"
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        message = "補正値を保存できませんでした：${error.message}。もう一度適用してください。"
      } finally {
        rigSaving = false
      }
    }
  }
  val latestAudio by rememberUpdatedState(exportAudio)
  val latestVideo by rememberUpdatedState(pendingVideo)
  DisposableEffect(Unit) {
    onDispose { latestAudio?.delete(); latestVideo?.delete() }
  }
  val saveVideo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
    val video = pendingVideo
    if (uri != null && video != null) scope.launch {
      savingVideo = true
      runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri, "wt")?.use { output -> video.inputStream().use { it.copyTo(output) } }
            ?: error("保存先を開けません")
        }
      }.onSuccess { message = "MP4動画を保存しました。"; video.delete(); if (pendingVideo == video) pendingVideo = null }
        .onFailure { message = "保存できませんでした：${it.message}。保存を再試行できます。" }
      savingVideo = false
    } else if (video != null) message = "保存先の選択を中止しました。保存を再試行できます。"
  }
  val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null && !exporting) scope.launch {
      audioImporting = true
      var candidate: File? = null
      runCatching {
        withContext(Dispatchers.IO) {
          val file = File.createTempFile("humanoid-audio-", ".m4a", context.cacheDir)
          candidate = file
          try {
            context.contentResolver.openInputStream(uri)?.use { input ->
              file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                  val count = input.read(buffer)
                  if (count < 0) break
                  total += count
                  require(total <= 30_000_000) { "音声は30MBまでです" }
                  output.write(buffer, 0, count)
                }
              }
            } ?: error("音声を開けません")
            require(file.length() > 0) { "音声ファイルが空です" }
            file
          } catch (error: Exception) { file.delete(); throw error }
        }
      }.onSuccess { exportAudio?.delete(); exportAudio = it; candidate = null; message = "音声を選択しました。動画と合わせて保存します。" }
        .onFailure { message = "音声を読み込めませんでした：${it.message}" }
      candidate?.delete()
      audioImporting = false
    }
  }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            while (true) {
              val count = input.read(buffer)
              if (count < 0) break
              require(output.size() + count <= 5_000_000) { "BVHは5MBまでです" }
              output.write(buffer, 0, count)
            }
            val bytes = output.toByteArray()
            require(bytes.size <= 5_000_000) { "BVHは5MBまでです" }
            val text = bytes.toString(Charsets.UTF_8)
            require(text.trimStart().startsWith("HIERARCHY")) { "BVHファイルを選んでください" }
            text
          } ?: error("ファイルを開けません")
        }
      }.onSuccess { bvh = it; bvhRevision++; motion = "bvh"; message = "BVHを選択しました。対応付けの結果は映像上に表示されます。" }
        .onFailure { message = it.message.orEmpty() }
    }
  }
  val rigProfile = JSONObject().put("shoulderWidth", humanoidStudioNumber(shoulderWidth)).put("hipHeight", humanoidStudioNumber(hipHeight))
    .put("kneeHeight", humanoidStudioNumber(kneeHeight)).put("armAngle", humanoidStudioNumber(armAngle))
  val options = JSONObject().put("autoRig", autoRig).put("showBones", showBones).put("rigProfile", appliedRigProfile?.let { JSONObject(it) } ?: JSONObject().put("shoulderWidth", 0.24).put("hipHeight", 0.53).put("kneeHeight", 0.28).put("armAngle", 0)).put("motion", motion).put("speed", humanoidStudioNumber(speed))
    .put("flexibility", humanoidStudioNumber(flexibility)).put("physics", physics).put("paused", paused)
    .put("accessory", accessory).put("accessoryScale", humanoidStudioNumber(accessoryScale))
    .put("accessoryY", humanoidStudioNumber(accessoryY)).put("accessoryZ", humanoidStudioNumber(accessoryZ))
    .put("bvhRevision", bvhRevision).toString()
  Dialog(onDismissRequest = { if (savingVideo || rigSaving) message = "保存しています。完了をお待ちください。" else onClose() }) {
    Column(modifier = Modifier.fillMaxWidth().background(ClawTheme.colors.canvas).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("3D動作スタジオ", style = ClawTheme.type.title, color = ClawTheme.colors.text)
      val model = models.firstOrNull { it.id == selected }
      if (model != null && !rigLoading && !rigLoadFailed) key(gatewayId, model.id) {
        Live2dAvatar(viewModel, ownerId, model, speaking, "", 320.dp, studioOptions = options, bvhText = bvh, expressionKey = expression,
          exportRequest = exportRequest, exportSeconds = exportSeconds, exportAudio = exportAudio,
          onViewerReady = { viewerReady = it }, onVideoStatus = { message = it },
          onVideoExport = { file, result ->
            exporting = false; exportRequest = 0; message = result
            if (file != null) { pendingVideo?.delete(); pendingVideo = file; saveVideo.launch("OpenClaw-3D-${System.currentTimeMillis()}.mp4") }
          })
      }
      if (model?.kind == "glb") {
        val rigControlsEnabled = !exporting && !rigLoading && !rigSaving && !rigLoadFailed
        Text("静止モデルの自動骨付け・補正", style = ClawTheme.type.section, color = ClawTheme.colors.text)
        Text("直立したTポーズ・Aポーズの人型GLBに対応します。形から骨と変形の割り当てを推定するため、関節の曲がり方を確認してください。元モデルは変更しません。顔・指・衣装の専用骨は自動作成しません。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        ClawSecondaryButton(text = if (autoRig) "骨を外して元モデルを表示" else "自動で骨を付ける", onClick = { saveRig(!autoRig) }, enabled = rigControlsEnabled, modifier = Modifier.fillMaxWidth())
        if (autoRig) ClawSecondaryButton(text = if (showBones) "骨格表示を隠す" else "骨格を表示して確認", onClick = { showBones = !showBones }, enabled = !exporting, modifier = Modifier.fillMaxWidth())
        Text("肩幅の補正", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        Slider(value = shoulderWidth, onValueChange = { shoulderWidth = it }, valueRange = 0.16f..0.32f, enabled = rigControlsEnabled)
        Text("腰の高さの補正", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        Slider(value = hipHeight, onValueChange = { hipHeight = it }, valueRange = 0.42f..0.62f, enabled = rigControlsEnabled)
        Text("膝の高さの補正", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        Slider(value = kneeHeight, onValueChange = { kneeHeight = it }, valueRange = 0.18f..0.34f, enabled = rigControlsEnabled)
        Text("元モデルの腕の下がり角度（Tポーズは0°）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        Slider(value = armAngle, onValueChange = { armAngle = it }, valueRange = 0f..60f, enabled = rigControlsEnabled)
        if (autoRig) ClawSecondaryButton(text = "補正を適用して保存", onClick = { saveRig(true) }, enabled = rigControlsEnabled && appliedRigProfile != rigProfile.toString(), modifier = Modifier.fillMaxWidth())
        Text("適用した補正値と骨付けのオン・オフをモデルごとに端末へ保存します。未適用の変更は保存されません。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      }
      Text("キャラを交換（動作・装飾品の選択を引き継ぎます）", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
      models.forEach { item ->
        ClawSecondaryButton(text = (if (item.id == selected) "✓ " else "") + item.name, onClick = { if (selected != item.id) { viewerReady = false; exportRequest = 0; selected = item.id } }, modifier = Modifier.fillMaxWidth(), enabled = !exporting && !rigSaving)
      }
      Text("共通の動作", style = ClawTheme.type.section, color = ClawTheme.colors.text)
      listOf("idle" to "立つ", "walk" to "歩く", "run" to "走る", "wave" to "手を振る", "sit" to "座る", "look" to "見回す").forEach { (id, label) ->
        ClawSecondaryButton(text = (if (motion == id) "✓ " else "") + label, onClick = { motion = id }, modifier = Modifier.fillMaxWidth(), enabled = !exporting)
      }
      ClawSecondaryButton(text = "BVH動作を読み込む", onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), enabled = !exporting)
      if (bvh != null) ClawSecondaryButton(text = "読み込んだBVHを再生", onClick = { motion = "bvh" }, modifier = Modifier.fillMaxWidth(), enabled = !exporting)
      Text("BVHはY軸が上の標準人型・Tポーズ基準を使ってください。対応しない骨格にはエラーを表示します。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      Text("再生速度：${"%.2f".format(speed)}倍", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
      Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.25f..2f, enabled = !exporting)
      ClawSecondaryButton(text = if (paused) "再生する" else "一時停止", onClick = { paused = !paused }, modifier = Modifier.fillMaxWidth(), enabled = !exporting)
      ClawSecondaryButton(text = if (physics) "揺れの物理演算：オン" else "揺れの物理演算：オフ", onClick = { physics = !physics }, modifier = Modifier.fillMaxWidth(), enabled = !exporting)
      Text("揺れの柔らかさ：${"%.2f".format(flexibility)}", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
      Slider(value = flexibility, onValueChange = { flexibility = it }, valueRange = 0.25f..2f, enabled = !exporting)
      Text("髪・衣装の揺れと衝突は、VRMに設定された揺れ骨と衝突形状を使います。追加イヤリングにも揺れを適用します。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      listOf("none" to "装飾品なし", "hat" to "帽子", "glasses" to "眼鏡", "earrings" to "イヤリング").forEach { (id, label) ->
        ClawSecondaryButton(text = (if (accessory == id) "✓ " else "") + label, onClick = { accessory = id }, modifier = Modifier.fillMaxWidth(), enabled = !exporting)
      }
      if (accessory != "none") {
        Text("装飾品の大きさ", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        Slider(value = accessoryScale, onValueChange = { accessoryScale = it }, valueRange = 0.5f..1.5f, enabled = !exporting)
        Text("装飾品の上下位置", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        Slider(value = accessoryY, onValueChange = { accessoryY = it }, valueRange = -0.1f..0.1f, enabled = !exporting)
        Text("装飾品の前後位置", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
        Slider(value = accessoryZ, onValueChange = { accessoryZ = it }, valueRange = -0.1f..0.1f, enabled = !exporting)
      }
      Text("表情・口の動き", style = ClawTheme.type.section, color = ClawTheme.colors.text)
      listOf("normal" to "通常", "happy" to "笑顔", "angry" to "怒り", "sad" to "悲しみ", "shy" to "穏やか", "surprised" to "驚き").forEach { (id, label) ->
        ClawSecondaryButton(text = (if (expression == id) "✓ " else "") + label, onClick = { expression = id }, modifier = Modifier.fillMaxWidth(), enabled = !exporting && model?.kind != "glb")
      }
      ClawSecondaryButton(text = if (speaking) "口の動き：オン" else "口の動き：オフ", onClick = { speaking = !speaking }, modifier = Modifier.fillMaxWidth(), enabled = !exporting && model?.kind != "glb")
      Text("MP4動画を書き出す", style = ClawTheme.type.section, color = ClawTheme.colors.text)
      listOf(5, 10, 30).forEach { seconds ->
        ClawSecondaryButton(text = (if (exportSeconds == seconds) "✓ " else "") + "${seconds}秒", onClick = { exportSeconds = seconds }, enabled = !exporting, modifier = Modifier.fillMaxWidth())
      }
      ClawSecondaryButton(text = if (exportAudio == null) "音声を選ぶ（AAC形式のM4A・MP4）" else "音声を選び直す", onClick = { audioPicker.launch(arrayOf("audio/*", "video/mp4")) }, enabled = !exporting && !audioImporting, modifier = Modifier.fillMaxWidth())
      if (exportAudio != null) ClawSecondaryButton(text = "音声を外す", onClick = { exportAudio?.delete(); exportAudio = null }, enabled = !exporting, modifier = Modifier.fillMaxWidth())
      ClawSecondaryButton(text = if (exporting) "動画を書き出しています…" else "1280×720・30fpsでMP4を作る", onClick = {
        exporting = true; message = "動画の書き出しを始めます。画面を開いたままお待ちください。"; exportRequest++
      }, enabled = viewerReady && !exporting && !audioImporting && !savingVideo && !rigLoading && !rigSaving && !rigLoadFailed, modifier = Modifier.fillMaxWidth())
      if (exporting) ClawSecondaryButton(text = "書き出しを中止", onClick = { exportRequest = 0 }, modifier = Modifier.fillMaxWidth())
      if (pendingVideo != null) ClawSecondaryButton(text = "完成したMP4を保存する", onClick = { saveVideo.launch("OpenClaw-3D-${System.currentTimeMillis()}.mp4") }, enabled = !exporting, modifier = Modifier.fillMaxWidth())
      Text("全コマを描画して保存するため、動画の長さより処理に時間がかかる場合があります。音声が長い場合は動画の長さで区切ります。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
      if (rigLoading) Text("保存した補正値を読み込んでいます…", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      if (rigLoadFailed) ClawSecondaryButton(text = "補正値を再読み込み", onClick = { rigLoadRevision++ }, modifier = Modifier.fillMaxWidth())
      Text("骨付けの補正値は端末に保存します。動作・装飾品などの試用設定はこの画面を閉じると戻ります。元モデルは変更されません。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      ClawSecondaryButton(text = "閉じる", onClick = onClose, modifier = Modifier.fillMaxWidth(), enabled = !exporting && !savingVideo && !rigSaving)
    }
  }
}
