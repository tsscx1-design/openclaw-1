package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// 音声スタジオ：声に関することを、1か所に集めた画面。
//   🧩 声のエンジン … 使える声のエンジンが、入っているか・動いているか・足りないものは何か（見るだけ）
//   🎙 声を作る・学習 … 声デザイン、録音から作る、話者の抽出、今から学習／今夜学習、学習済みモデル（これまでの「声を作る」）
//   🔊 声の一覧・試聴 … VOICEVOX・AivisSpeech・声まね（Qwen3-TTS）・Irodori・自作の声を、1か所に並べて、聞ける
//   👤 パートナーの声 … パートナーに、声を付ける（高さ・速さも）。パートナー管理の「声を選ぶ」は、ここに移した
//   ⚙ 読み上げの設定 … 読み上げの提供元（Fish Audio など）の選択と、設定済みかどうか

private const val SAMPLE_TEXT = "こんにちは。今日もよろしくお願いします。"

private data class StudioSpeaker(val engine: String, val speakerId: Int, val voiceId: String, val label: String, val icon: String)

private fun parseSpeakers(json: String): List<StudioSpeaker> {
  val arr = JSONObject(json).optJSONArray("speakers") ?: JSONArray()
  return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { sp ->
    StudioSpeaker(sp.optString("engine"), sp.optInt("speakerId"), sp.optString("voiceId"), "${sp.optString("speakerName")}（${sp.optString("styleName")}）", sp.optString("iconBase64"))
  }
}

private fun engineName(engine: String): String =
  when (engine) {
    "aivis" -> "AivisSpeech"
    "clone" -> "声まね（Qwen3-TTS）"
    "irodori" -> "Irodori"
    "gptsovits" -> "GPT-SoVITS"
    "outetts" -> "OuteTTS"
    "fishlocal" -> "Fish S2（ローカル）"
    "voxcpm2" -> "VoxCPM2"
    else -> "VOICEVOX"
  }

private fun StudioSpeaker.voiceJson(): JSONObject =
  JSONObject().put("engine", engine).put("speakerId", speakerId).put("label", label).also { if (voiceId.isNotEmpty()) it.put("voiceId", voiceId) }

private fun StudioSpeaker.same(voice: JSONObject?): Boolean =
  voice != null && voice.optString("engine") == engine && voice.optInt("speakerId") == speakerId && voice.optString("voiceId").orEmpty() == voiceId

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AudioStudioScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  var tab by remember { mutableStateOf(0) }
  val scope = rememberCoroutineScope()

  // 声の一覧は、2つのタブで使う。初めて必要になったときに、1回だけ読む（初回は、エンジンの起動に、少しかかる）
  var speakers by remember { mutableStateOf<List<StudioSpeaker>>(emptyList()) }
  var speakersStatus by remember { mutableStateOf("") }
  var speakersLoading by remember { mutableStateOf(false) }
  var partners by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

  fun loadSpeakers(force: Boolean = false) {
    if (speakersLoading || (!force && speakers.isNotEmpty())) return
    speakersLoading = true
    speakersStatus = "声の一覧を読み込み中…（初回は、エンジンの起動に、少しかかります）"
    scope.launch {
      runCatching {
        speakers = parseSpeakers(viewModel.runGatewayRpc(GatewayMethod.PartnerTtsSpeakers, "{}", timeoutMs = 200_000))
        val arr = JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerList, "{}")).optJSONArray("partners") ?: JSONArray()
        partners = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { it.optString("id") to it.optString("name") }
        speakersStatus = if (speakers.isEmpty()) "声が見つかりません。PC で VOICEVOX / AivisSpeech が動いているか、確認してください。" else ""
      }.onFailure { speakersStatus = "読み込みエラー：${it.message}" }
      speakersLoading = false
    }
  }

  ClawScaffold(contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 6.dp)) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
        Text("🎙 音声スタジオ", style = ClawTheme.type.display.copy(fontSize = 20.sp), color = ClawTheme.colors.text)
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("🎙 声を作る・学習", "🔊 声の一覧・試聴", "👤 パートナーの声", "🧩 声のエンジン", "⚙ 読み上げの設定").forEachIndexed { i, label ->
          ClawSecondaryButton(text = if (tab == i) "● $label" else label, onClick = { tab = i })
        }
      }
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (tab) {
          0 -> VoiceStudioScreen(viewModel = viewModel, onBack = onBack, embedded = true)
          1 -> {
            LaunchedEffect(Unit) { loadSpeakers() }
            AudioVoiceListTab(viewModel, speakers, speakersStatus, partners, onReload = { loadSpeakers(force = true) })
          }
          2 -> {
            LaunchedEffect(Unit) { loadSpeakers() }
            AudioPartnerVoiceTab(viewModel, speakers, speakersStatus, partners)
          }
          3 -> AudioEnginesTab(viewModel)
          else -> AudioTtsSettingsTab(viewModel)
        }
      }
    }
  }
}

/** 🔊 声の一覧・試聴：全部の声を、1か所に並べて、聞く。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioVoiceListTab(viewModel: MainViewModel, speakers: List<StudioSpeaker>, status: String, partners: List<Pair<String, String>>, onReload: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var filter by remember { mutableStateOf("all") }
  var text by remember { mutableStateOf(SAMPLE_TEXT) }
  var message by remember { mutableStateOf("") }
  val engines = speakers.map { it.engine }.distinct()
  val shown = speakers.filter { filter == "all" || it.engine == filter }
  LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("全部の声を、1か所に並べています。「▶ 試聴」で、同じ文章を、聞き比べられます。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        ClawTextField(value = text, onValueChange = { text = it }, placeholder = "試聴する文章", label = "試聴する文章", modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          ClawSecondaryButton(text = if (filter == "all") "● すべて（${speakers.size}）" else "すべて（${speakers.size}）", onClick = { filter = "all" })
          engines.forEach { e -> ClawSecondaryButton(text = (if (filter == e) "● " else "") + "${engineName(e)}（${speakers.count { it.engine == e }}）", onClick = { filter = e }) }
          ClawSecondaryButton(text = "⏹ 止める", onClick = { PartnerVoicePlayer.stop() })
          ClawSecondaryButton(text = "🔄 読み直す", onClick = onReload)
        }
        if (status.isNotBlank()) Text(status, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        if (partners.isEmpty() && speakers.isNotEmpty()) Text("パートナーが、まだいないので、試聴できません（試聴は、パートナーの設定を、使って、読み上げます）。", style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    }
    items(shown) { sp ->
      Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (sp.engine == "clone") VoiceIcon(sp.icon, 36.dp)
        Column(modifier = Modifier.weight(1f)) {
          Text(sp.label, style = ClawTheme.type.body.copy(fontSize = 14.sp), color = ClawTheme.colors.text)
          Text(engineName(sp.engine), style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted)
        }
        ClawSecondaryButton(
          text = "▶ 試聴",
          onClick = {
            val pid = partners.firstOrNull()?.first ?: return@ClawSecondaryButton
            message = "「${sp.label}」を再生中…"
            scope.launch {
              message = runCatching { PartnerVoicePlayer.speak(context, viewModel, pid, text.ifBlank { SAMPLE_TEXT }, voiceOverride = sp.voiceJson()); "" }.getOrElse { "試聴エラー：${it.message}" }
            }
          },
        )
      }
    }
  }
}

/** 👤 パートナーの声：パートナーを選んで、声と、高さ・速さを決める。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioPartnerVoiceTab(viewModel: MainViewModel, speakers: List<StudioSpeaker>, status: String, partners: List<Pair<String, String>>) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var partnerId by remember { mutableStateOf<String?>(null) }
  var voice by remember { mutableStateOf<JSONObject?>(null) }
  var pitch by remember { mutableStateOf(1f) }
  var rate by remember { mutableStateOf(1f) }
  var choosing by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf("") }

  fun load(id: String) {
    partnerId = id
    choosing = false
    message = ""
    scope.launch {
      runCatching {
        val st = JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerSettingsGet, JSONObject().put("partnerId", id).toString())).optJSONObject("settings") ?: JSONObject()
        pitch = st.optDouble("voicePitch", 1.0).toFloat()
        rate = st.optDouble("voiceRate", 1.0).toFloat()
        voice = st.optJSONObject("voice")
      }.onFailure { message = "読み込めませんでした：${it.message}" }
    }
  }

  fun save() {
    val id = partnerId ?: return
    scope.launch {
      runCatching {
        // 高さ・速さは、PC の声にも効かせる（VOICEVOX の高さは、±0.15）
        val v = voice?.let { JSONObject(it.toString()).put("speed", rate.toDouble()).put("pitch", ((pitch - 1f) * 0.15f).toDouble().coerceIn(-0.15, 0.15)) } ?: JSONObject.NULL
        val settings = JSONObject().put("voicePitch", pitch.toDouble()).put("voiceRate", rate.toDouble()).put("voice", v)
        viewModel.runGatewayRpc(GatewayMethod.PartnerSettingsSet, JSONObject().put("partnerId", id).put("settings", settings).toString())
      }.onSuccess { message = "保存しました" }.onFailure { message = "保存できませんでした：${it.message}" }
    }
  }

  LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("パートナーに、声を付けます。パートナー管理の「声を選ぶ」は、ここに移りました。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        if (status.isNotBlank()) Text(status, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        if (partners.isEmpty()) Text("パートナーを、読み込み中、またはいません。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          partners.forEach { (id, name) -> ClawSecondaryButton(text = if (id == partnerId) "● $name" else name, onClick = { load(id) }) }
        }
        if (partnerId != null) {
          Text("いまの声：" + (voice?.optString("label")?.takeIf { it.isNotBlank() } ?: "未設定（スマホの声で読み上げ）"), style = ClawTheme.type.body, color = ClawTheme.colors.text)
          Text("声の高さ: ${String.format(Locale.US, "%.1f", pitch)}　話す速さ: ${String.format(Locale.US, "%.1f", rate)}", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.5f..2f)
          Slider(value = rate, onValueChange = { rate = it }, valueRange = 0.5f..2f)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ClawSecondaryButton(text = if (choosing) "▲ 声の一覧を閉じる" else "🎙 声を選ぶ", onClick = { choosing = !choosing })
            ClawSecondaryButton(text = "PC の声を使わない（スマホの声）", onClick = { voice = null })
            ClawPrimaryButton(text = "💾 保存する", onClick = { save() })
          }
        }
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    }
    if (choosing) {
      items(speakers) { sp ->
        val selected = sp.same(voice)
        Row(
          modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.18f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (sp.engine == "clone") VoiceIcon(sp.icon, 36.dp)
          Column(modifier = Modifier.weight(1f)) {
            Text((if (selected) "● " else "") + sp.label, style = ClawTheme.type.body.copy(fontSize = 14.sp), color = ClawTheme.colors.text)
            Text(engineName(sp.engine), style = ClawTheme.type.caption.copy(fontSize = 11.sp), color = ClawTheme.colors.textMuted)
          }
          ClawSecondaryButton(
            text = "▶",
            onClick = {
              val pid = partnerId ?: return@ClawSecondaryButton
              scope.launch { message = runCatching { PartnerVoicePlayer.speak(context, viewModel, pid, SAMPLE_TEXT, voiceOverride = sp.voiceJson()); "" }.getOrElse { "試聴エラー：${it.message}" } }
            },
          )
          ClawSecondaryButton(text = "選ぶ", onClick = { voice = sp.voiceJson(); PartnerVoicePlayer.stop(); message = "「${sp.label}」を選びました。「保存する」を押すと、決まります" })
        }
      }
    }
  }
}

/** ⚙ 読み上げの設定：読み上げの提供元（Fish Audio など）を、選ぶ。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioTtsSettingsTab(viewModel: MainViewModel) {
  val scope = rememberCoroutineScope()
  var providers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
  var active by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("読み込み中…") }

  fun load() {
    scope.launch {
      runCatching {
        val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.TtsProviders, "{}", timeoutMs = 60_000))
        val arr = res.optJSONArray("providers") ?: JSONArray()
        providers = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        active = res.optString("active")
        message = if (providers.isEmpty()) "読み上げの提供元が、見つかりません" else ""
      }.onFailure { message = "読み込めませんでした：${it.message}" }
    }
  }
  LaunchedEffect(Unit) { load() }

  LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("チャットなどの返信を、声で読み上げるときの、提供元です。「設定済み」のものだけ、使えます。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        Text("Fish Audio を使うには、PC の ~/.openclaw/.env に FISH_API_KEY を入れて、Gateway を再起動します（鍵は、この画面には、出しません）。声は、サイトで作った、声の ID（speakerVoiceId）を、設定で指定します。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    }
    items(providers) { p ->
      val id = p.optString("id")
      val configured = p.optBoolean("configured")
      Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (id == active) ClawTheme.colors.primary.copy(alpha = 0.18f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text((if (id == active) "● " else "") + p.optString("name").ifBlank { id }, style = ClawTheme.type.body, color = ClawTheme.colors.text)
          Text(if (configured) "設定済み" else "未設定（鍵などが、まだありません）", style = ClawTheme.type.captionSmall, color = if (configured) ClawTheme.colors.textMuted else ClawTheme.colors.primary)
        }
        if (id != active) ClawSecondaryButton(text = "これを使う", onClick = {
          scope.launch {
            message = runCatching { viewModel.runGatewayRpc(GatewayMethod.TtsSetProvider, JSONObject().put("provider", id).toString()); load(); "「${p.optString("name").ifBlank { id }}」を、使うようにしました" }.getOrElse { "変えられませんでした：${it.message}" }
          }
        })
      }
    }
  }
}

/** 🧩 声のエンジン：使える声のエンジンの状態を、一覧で見る（見るだけで、起動・停止はしない）。 */
@Composable
private fun AudioEnginesTab(viewModel: MainViewModel) {
  val scope = rememberCoroutineScope()
  var engines by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
  var message by remember { mutableStateOf("読み込み中…") }

  fun load() {
    scope.launch {
      runCatching {
        val arr = JSONObject(viewModel.runGatewayRpc(GatewayMethod.VoiceEngines, "{}", timeoutMs = 30_000)).optJSONArray("engines") ?: JSONArray()
        engines = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        message = ""
      }.onFailure { message = "読み込めませんでした（ゲートウェイを新しくしたか、確認してください）：${it.message}" }
    }
  }
  LaunchedEffect(Unit) { load() }

  LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("使える声のエンジンです。入っているか、いま動いているか、足りないものは何かを、見られます（見るだけです。起動も停止も、しません）。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          ClawSecondaryButton(text = "🔄 読み直す", onClick = { message = "読み込み中…"; load() })
        }
        if (message.isNotBlank()) Text(message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)
      }
    }
    items(engines) { e ->
      val installed = e.optBoolean("installed")
      val running = if (e.isNull("running")) null else e.optBoolean("running")
      Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(e.optString("name"), style = ClawTheme.type.body, color = ClawTheme.colors.text)
        Text(
          (if (installed) "✅ 入っている" else "⬜ 入っていない") + when (running) { true -> "　🟢 いま動いている"; false -> "　⚪ 停止中（必要なときに起動）"; null -> "" },
          style = ClawTheme.type.caption,
          color = if (installed) ClawTheme.colors.text else ClawTheme.colors.primary,
        )
        Text(e.optString("detail"), style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
        Text("ライセンス：" + e.optString("license"), style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      }
    }
  }
}
