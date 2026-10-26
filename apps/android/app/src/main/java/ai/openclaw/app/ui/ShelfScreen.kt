package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawSectionHeader
import ai.openclaw.app.ui.design.ClawTheme
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One stretch of a long sound, read on its own. */
private data class PartRow(val start: Double, val end: Double, val kind: String, val source: String, val text: String, val emotion: String, val intensity: Int, val heard: String)

/** The structured reading of a sound: its kind, how strong it is, how long, and its stretches. */
private data class SoundInfo(val kind: String, val source: String, val intensity: Int, val durationSec: Double, val parts: List<PartRow>)

private data class ShelfRow(
  val id: String,
  val name: String,
  val kind: String,
  val sizeMb: Double,
  val note: String?,
  val autoNote: String?,
  val verified: String?,
  val excluded: Boolean,
  val category: String?,
  val sound: SoundInfo?,
)

private data class ShelfState(
  val root: String,
  val soundKinds: List<String>,
  val counts: Map<String, Int>,
  val looked: Int,
  val lookable: Int,
  val inspecting: Boolean,
  val rows: List<ShelfRow>,
)

private val EMOTION_LABEL = mapOf(
  "angry" to "怒り", "disgusted" to "嫌悪", "fearful" to "怖れ", "happy" to "喜び", "neutral" to "平静",
  "sad" to "悲しみ", "surprised" to "驚き", "other" to "その他", "unknown" to "不明",
)

private fun parseSound(json: JSONObject): SoundInfo {
  val parts = json.optJSONArray("parts")
  return SoundInfo(
    kind = json.optString("kind"),
    source = json.optString("source"),
    intensity = json.optInt("intensity"),
    durationSec = json.optDouble("durationSec", 0.0),
    parts = (0 until (parts?.length() ?: 0)).map { index ->
      val part = parts!!.getJSONObject(index)
      val emotion = part.optJSONArray("emotion")?.optJSONObject(0)
      PartRow(
        start = part.optDouble("start"),
        end = part.optDouble("end"),
        kind = part.optString("kind"),
        source = part.optString("source"),
        text = part.optString("text"),
        emotion = emotion?.let { "${EMOTION_LABEL[it.optString("label")] ?: it.optString("label")}${(it.optDouble("score") * 100).toInt()}%" } ?: "",
        intensity = part.optInt("intensity"),
        heard = part.optString("heard"),
      )
    },
  )
}

private fun sourceLabel(source: String): String = when (source) {
  "taught" -> "教えた音に似ている"
  "feature" -> "音の数値からの推測"
  "model" -> "音声モデルの判定"
  "user" -> "あなたが決めた"
  else -> "PCの推測"
}

private fun clock(seconds: Double): String = "%d:%02d".format(seconds.toInt() / 60, seconds.toInt() % 60)

private fun parseShelf(raw: String): ShelfState {
  val json = JSONObject(raw)
  val counts = json.optJSONObject("counts") ?: JSONObject()
  val items = json.optJSONArray("items")
  val rows = (0 until (items?.length() ?: 0)).map { index ->
    val item = items!!.getJSONObject(index)
    ShelfRow(
      id = item.optString("id"),
      name = item.optString("name"),
      kind = item.optString("kind"),
      sizeMb = item.optDouble("sizeMb", 0.0),
      note = item.optString("note").ifBlank { null },
      autoNote = item.optString("autoNote").ifBlank { null },
      verified = item.optString("verified").ifBlank { null },
      excluded = item.optBoolean("excluded", false),
      category = item.optString("category").ifBlank { null },
      sound = item.optJSONObject("sound")?.let { parseSound(it) },
    )
  }
  val kinds = json.optJSONArray("soundKinds")
  return ShelfState(
    root = json.optString("root"),
    soundKinds = (0 until (kinds?.length() ?: 0)).map { kinds!!.getString(it) },
    counts = listOf("image", "video", "audio", "doc").associateWith { counts.optInt(it) },
    looked = json.optInt("looked"),
    lookable = json.optInt("lookable"),
    inspecting = json.optJSONObject("inspecting")?.optBoolean("running") == true,
    rows = rows,
  )
}

private val KIND_LABEL = mapOf("image" to "絵", "video" to "動画", "audio" to "音", "doc" to "資料")
private const val CHUNK_BYTES = 3 * 1024 * 1024

private fun displayName(context: android.content.Context, uri: Uri): String =
  context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) cursor.getString(0) else null
  } ?: (uri.lastPathSegment ?: "file")

/** The material shelf on the PC: what is on it, sending files from the phone, sorting, looking into the files. */
@Composable
internal fun ShelfScreen(viewModel: MainViewModel, onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var state by remember { mutableStateOf<ShelfState?>(null) }
  var message by remember { mutableStateOf<String?>(null) }
  var progress by remember { mutableStateOf<String?>(null) }
  var progressFraction by remember { mutableStateOf<Float?>(null) }
  var reload by remember { mutableIntStateOf(0) }
  var editing by remember { mutableStateOf<ShelfRow?>(null) }
  var editText by remember { mutableStateOf("") }
  var quizText by remember { mutableStateOf<String?>(null) }
  var kindFilter by remember { mutableStateOf<String?>(null) }
  // null=すべて、true=分析済みだけ、false=未分析だけ。
  var analyzedFilter by remember { mutableStateOf<Boolean?>(null) }
  // The sound (and the stretch of it, when not null) whose kind the user is choosing.
  var kindTarget by remember { mutableStateOf<Pair<ShelfRow, Int?>?>(null) }
  var kindText by remember { mutableStateOf("") }
  var opened by remember { mutableStateOf(setOf<String>()) }
  // 「止めるまで」調べ続ける：1回(最大50件)終わるたびに、残りがあれば自動で次を頼む。
  var autoInspect by remember { mutableStateOf(false) }

  LaunchedEffect(autoInspect, state?.inspecting) {
    if (autoInspect && state?.inspecting == false) {
      val s = state
      if (s != null && s.looked < s.lookable) {
        runCatching { viewModel.runGatewayRpc(GatewayMethod.ShelfInspect, "{\"limit\":50}", timeoutMs = 60_000) }
      } else {
        autoInspect = false
      }
    }
  }

  LaunchedEffect(reload) {
    while (true) {
      runCatching { state = parseShelf(viewModel.runGatewayRpc(GatewayMethod.ShelfList, "{}", timeoutMs = 60_000)) }
        .onFailure { message = "素材棚を読めませんでした：${it.message?.take(120)}" }
      // while the PC looks into files, the numbers move
      delay(if (state?.inspecting == true || progress != null) 3_000 else 15_000)
    }
  }

  // Sends the chosen files to the PC piece by piece (the same way a film comes back), one file after the other.
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
    if (uris.isEmpty()) return@rememberLauncherForActivityResult
    scope.launch {
      var sent = 0
      val failed = mutableListOf<String>()
      for ((index, uri) in uris.withIndex()) {
        val name = displayName(context, uri)
        progress = "送っています（${index + 1}/${uris.size}）：$name"
        progressFraction = 0f
        try {
          withContext(Dispatchers.IO) {
            val total = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            val uploadId = UUID.randomUUID().toString().replace("-", "").take(24)
            context.contentResolver.openInputStream(uri)!!.use { input ->
              val buffer = ByteArray(CHUNK_BYTES)
              var offset = 0L
              while (true) {
                var filled = 0
                while (filled < buffer.size) {
                  val read = input.read(buffer, filled, buffer.size - filled)
                  if (read < 0) break
                  filled += read
                }
                val last = filled < buffer.size
                val body = JSONObject()
                  .put("uploadId", uploadId)
                  .put("name", name)
                  .put("offset", offset)
                  .put("last", last)
                  .put("dataBase64", Base64.encodeToString(buffer, 0, filled, Base64.NO_WRAP))
                var attempt = 0
                while (true) {
                  try {
                    viewModel.runGatewayRpc(GatewayMethod.ShelfUpload, body.toString(), timeoutMs = 120_000)
                    break
                  } catch (e: Exception) {
                    attempt += 1
                    if (attempt >= 3) throw e
                    delay(1_500)
                  }
                }
                offset += filled
                if (total > 0) progressFraction = (offset.toFloat() / total).coerceIn(0f, 1f)
                if (last) break
              }
            }
          }
          sent += 1
        } catch (e: Exception) {
          failed += "$name（${e.message?.take(80) ?: "失敗"}）"
        }
      }
      progress = null
      progressFraction = null
      message = if (failed.isEmpty()) "${sent}件を、素材棚（受信フォルダ）に送りました。" else "${sent}件を送りました。送れなかったもの：${failed.joinToString("、")}"
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
      reload += 1
    }
  }

  fun call(method: GatewayMethod, params: String, done: String) {
    scope.launch {
      message = runCatching { withContext(Dispatchers.IO) { viewModel.runGatewayRpc(method, params, timeoutMs = 10 * 60_000) } }
        .fold({ done }, { "うまくいきませんでした：${it.message?.take(120)}" })
      reload += 1
    }
  }

  fun review(row: ShelfRow, verdict: String?, text: String?, kind: String? = null, part: Int? = null) {
    scope.launch {
      val body = JSONObject().put("id", row.id)
      if (verdict != null) body.put("verdict", verdict)
      if (text != null) body.put("text", text)
      if (kind != null) body.put("kind", kind)
      if (part != null) body.put("part", part)
      message = runCatching { withContext(Dispatchers.IO) { viewModel.runGatewayRpc(GatewayMethod.ShelfReview, body.toString(), timeoutMs = 60_000) } }
        .fold({ "「${row.name}」について書き留めました。" }, { "うまくいきませんでした：${it.message?.take(120)}" })
      reload += 1
    }
  }

  editing?.let { row ->
    AlertDialog(
      onDismissRequest = { editing = null },
      title = { Text("正しい説明を書く") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(row.name, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          row.autoNote?.let { Text("ネムの推測：$it", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted) }
          OutlinedTextField(value = editText, onValueChange = { editText = it }, label = { Text("これは何か（場面・状況・使い方も）") }, minLines = 3)
        }
      },
      confirmButton = { TextButton(onClick = { review(row, null, editText); editing = null }, enabled = editText.isNotBlank()) { Text("保存") } },
      dismissButton = { TextButton(onClick = { editing = null }) { Text("やめる") } },
    )
  }

  kindTarget?.let { (row, part) ->
    val partRow = part?.let { row.sound?.parts?.getOrNull(it) }
    AlertDialog(
      onDismissRequest = { kindTarget = null },
      title = { Text(if (part == null) "この音の種類" else "この区間の種類（${partRow?.let { clock(it.start) } ?: ""}〜）") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
          Text(row.name, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          (state?.soundKinds ?: emptyList()).forEach { kind ->
            TextButton(onClick = { review(row, null, null, kind = kind, part = part); kindTarget = null }, modifier = Modifier.fillMaxWidth()) { Text(kind) }
          }
          OutlinedTextField(value = kindText, onValueChange = { kindText = it.take(20) }, label = { Text("ほかの種類（自分の言葉）") }, singleLine = true)
        }
      },
      confirmButton = {
        TextButton(onClick = { review(row, null, null, kind = kindText.trim(), part = part); kindTarget = null }, enabled = kindText.isNotBlank()) { Text("この言葉にする") }
      },
      dismissButton = { TextButton(onClick = { kindTarget = null }) { Text("やめる") } },
    )
  }

  ClawScaffold(contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 6.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nativeString("Back"), onClick = onBack)
          Text("素材棚", style = ClawTheme.type.display.copy(fontSize = 20.sp), color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
        }
        Text(
          "絵・動画・音・文章・圧縮ファイルを、まとめて送れます。ネムが、素材棚から見つけて使います。",
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textMuted,
        )
      }
      item {
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ClawPrimaryButton(
              text = "ファイルを選んで送る（まとめて）",
              onClick = { picker.launch(arrayOf("image/*", "video/*", "audio/*", "text/*", "application/zip", "application/x-7z-compressed", "application/x-rar-compressed", "application/octet-stream")) },
              modifier = Modifier.fillMaxWidth(),
              enabled = progress == null,
            )
            progress?.let { text ->
              Text(text, style = ClawTheme.type.body, color = ClawTheme.colors.text)
              progressFraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ClawSecondaryButton(text = "仕分ける", onClick = { call(GatewayMethod.ShelfSort, "{}", "仕分けました（直下のファイルを移し、圧縮ファイルを展開しました）。") }, modifier = Modifier.weight(1f))
              ClawSecondaryButton(text = "小分けにする", onClick = { call(GatewayMethod.ShelfOrganize, "{}", "小分けにしました（中身を調べたものを、種類ごとの小さなフォルダに分けました）。") }, modifier = Modifier.weight(1f))
              ClawSecondaryButton(
                text = "理解チェック",
                onClick = {
                  scope.launch {
                    quizText = "チェック中…（1分ほどかかります）"
                    quizText = runCatching { JSONObject(withContext(Dispatchers.IO) { viewModel.runGatewayRpc(GatewayMethod.ShelfQuiz, "{}", timeoutMs = 10 * 60_000) }).optString("text") }
                      .getOrElse { "うまくいきませんでした：${it.message?.take(120)}" }
                  }
                },
                modifier = Modifier.weight(1f),
              )
              ClawSecondaryButton(
                text = if (autoInspect) "■ 止める" else "中身を調べる（全部）",
                onClick = {
                  if (autoInspect) {
                    autoInspect = false
                  } else {
                    autoInspect = true
                    call(GatewayMethod.ShelfInspect, "{\"limit\":50}", "中身を調べ始めました（終わるか、止めるまで続けます。絵は数秒、音は約10秒かかります）。")
                  }
                },
                modifier = Modifier.weight(1f),
              )
            }
            ClawSecondaryButton(
              text = "音声モデルに聞かせる（区間100件ずつ）",
              onClick = { call(GatewayMethod.ShelfJudge, "{\"limit\":100}", "音声モデルに聞かせ始めました（PCのチャットモデルを止めて、GPUで動かします。終わると自動で戻ります）。") },
              modifier = Modifier.fillMaxWidth(),
            )
            message?.let { Text(it, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted) }
            quizText?.let { Text(it, style = ClawTheme.type.body, color = ClawTheme.colors.text) }
          }
        }
      }
      state?.let { s ->
        item {
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(s.counts.entries.joinToString("・") { "${KIND_LABEL[it.key]} ${it.value}" }, style = ClawTheme.type.body, color = ClawTheme.colors.text)
              Text(
                "中身を調べたもの ${s.looked}/${s.lookable}${if (s.inspecting) "（調べています…）" else ""}",
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
              Text("PCの置き場所：${s.root}", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            }
          }
        }
        // Sounds by kind: the kinds that exist among the sounds, with how many each.
        val kindCounts = s.rows.filter { it.kind == "audio" }.groupingBy { it.category ?: "未分類" }.eachCount()
        if (kindCounts.isNotEmpty()) {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              ClawSectionHeader(title = "音を種類で見る")
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ClawSecondaryButton(text = if (kindFilter == null) "● すべて" else "すべて", onClick = { kindFilter = null })
                kindCounts.entries.sortedByDescending { it.value }.forEach { (kind, count) ->
                  ClawSecondaryButton(text = "${if (kindFilter == kind) "● " else ""}$kind $count", onClick = { kindFilter = if (kindFilter == kind) null else kind })
                }
              }
            }
          }
        }
        item {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ClawSectionHeader(title = "分析済み／未分析で見る")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
              ClawSecondaryButton(text = if (analyzedFilter == null) "● すべて" else "すべて", onClick = { analyzedFilter = null })
              ClawSecondaryButton(
                text = "${if (analyzedFilter == true) "● " else ""}分析済み ${s.rows.count { it.autoNote != null }}",
                onClick = { analyzedFilter = if (analyzedFilter == true) null else true },
              )
              ClawSecondaryButton(
                text = "${if (analyzedFilter == false) "● " else ""}未分析 ${s.rows.count { it.autoNote == null }}",
                onClick = { analyzedFilter = if (analyzedFilter == false) null else false },
              )
            }
          }
        }
        item { ClawSectionHeader(title = "新しい順（最大200件）") }
        items(
          s.rows.filter {
            (kindFilter == null || (it.kind == "audio" && (it.category ?: "未分類") == kindFilter)) &&
              (analyzedFilter == null || (it.autoNote != null) == analyzedFilter)
          },
        ) { row ->
          ClawPanel {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text("${KIND_LABEL[row.kind] ?: row.kind}：${row.name}${when { row.excluded -> "（使わない）"; row.verified != null -> "（確認済み）"; else -> "" }}", style = ClawTheme.type.body, color = ClawTheme.colors.text)
              row.note?.let { Text(it, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, maxLines = 3) }
              row.sound?.let { sound ->
                Text(
                  "種類：${sound.kind}（${sourceLabel(sound.source)}）｜強さ ${sound.intensity}/100｜${clock(sound.durationSec)}｜区間 ${sound.parts.size}",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.text,
                )
                if (sound.parts.isNotEmpty()) {
                  val open = row.id in opened
                  TextButton(onClick = { opened = if (open) opened - row.id else opened + row.id }) { Text(if (open) "区間をたたむ" else "区間を見る（${sound.parts.size}）") }
                  if (open) {
                    sound.parts.forEachIndexed { index, part ->
                      Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                          "${clock(part.start)}〜${clock(part.end)}｜${part.kind}（${sourceLabel(part.source)}）｜強さ ${part.intensity}" +
                            (if (part.emotion.isNotEmpty()) "｜${part.emotion}" else ""),
                          style = ClawTheme.type.caption,
                          color = ClawTheme.colors.text,
                        )
                        if (part.text.isNotBlank()) Text("「${part.text}」", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                        if (part.heard.isNotBlank()) Text("音声モデル：${part.heard}", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                        TextButton(onClick = { kindText = ""; kindTarget = row to index }) { Text("この区間の種類を直す") }
                      }
                    }
                  }
                }
              }
              if (row.kind == "audio") {
                TextButton(onClick = { kindText = ""; kindTarget = row to null }) { Text("音全体の種類を直す") }
              }
              if (row.kind != "doc") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  ClawSecondaryButton(text = "○合ってる", onClick = { review(row, "ok", null) }, modifier = Modifier.weight(1f), enabled = row.note != null)
                  ClawSecondaryButton(text = "×違う", onClick = { review(row, "wrong", null) }, modifier = Modifier.weight(1f))
                  ClawSecondaryButton(text = "✎説明", onClick = { editText = row.verified?.let { row.note } ?: ""; editing = row }, modifier = Modifier.weight(1f))
                }
              }
            }
          }
        }
      }
    }
  }
}
