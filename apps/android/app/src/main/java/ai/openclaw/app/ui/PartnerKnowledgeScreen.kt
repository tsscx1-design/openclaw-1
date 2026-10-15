package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Bytes per partner.knowledge.add call; keeps each Gateway frame small on slow links. */
private const val KNOWLEDGE_CHUNK_BYTES = 384 * 1024

private data class KnowledgeRow(
  val id: String,
  val title: String,
  val kind: String,
  val source: String,
  val chars: Int,
  val addedAt: Long,
)

/** A partner's reference shelf: PDFs, web pages and notes it draws on in chat. */
@Composable
internal fun PartnerKnowledgeScreen(
  viewModel: MainViewModel,
  partnerId: String,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var partnerName by remember { mutableStateOf("") }
  var docs by remember { mutableStateOf<List<KnowledgeRow>>(emptyList()) }
  var busy by remember { mutableStateOf(false) }
  var progress by remember { mutableStateOf<Float?>(null) }
  var status by remember { mutableStateOf("") }
  var url by remember { mutableStateOf("") }
  var noteTitle by remember { mutableStateOf("") }
  var noteText by remember { mutableStateOf("") }
  var confirmDelete by remember { mutableStateOf<KnowledgeRow?>(null) }
  // The document open in the viewer/editor: its row, the loaded text and the edits.
  var editing by remember { mutableStateOf<KnowledgeRow?>(null) }
  var editTitle by remember { mutableStateOf("") }
  var editText by remember { mutableStateOf("") }
  var editLoaded by remember { mutableStateOf(false) }
  var editSaving by remember { mutableStateOf(false) }

  fun refresh() {
    if (partnerId.isBlank()) return
    scope.launch {
      runCatching {
        val res =
          JSONObject(
            viewModel.runGatewayRpc(
              GatewayMethod.PartnerKnowledgeList,
              JSONObject().put("partnerId", partnerId).toString(),
            ),
          )
        partnerName = res.optString("partnerName")
        val arr = res.optJSONArray("docs")
        docs =
          (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val d = arr?.optJSONObject(i) ?: return@mapNotNull null
            KnowledgeRow(
              id = d.optString("id"),
              title = d.optString("title"),
              kind = d.optString("kind"),
              source = d.optString("source"),
              chars = d.optInt("chars"),
              addedAt = d.optLong("addedAt"),
            )
          }.reversed()
      }.onFailure { status = "読み込みエラー: ${it.message}" }
    }
  }

  fun add(params: JSONObject, label: String) {
    scope.launch {
      busy = true
      status = "$label を読み込み中…"
      runCatching {
        val res =
          JSONObject(
            viewModel.runGatewayRpc(
              GatewayMethod.PartnerKnowledgeAdd,
              params.put("partnerId", partnerId).toString(),
              timeoutMs = 180_000,
            ),
          )
        val doc = res.getJSONObject("doc")
        status = "📚「${doc.optString("title")}」を入れました（${"%,d".format(doc.optInt("chars"))}文字）"
      }.onFailure { status = "入れられませんでした: ${it.message}" }
      busy = false
      refresh()
    }
  }

  fun open(doc: KnowledgeRow) {
    editing = doc
    editTitle = doc.title
    editText = ""
    editLoaded = false
    scope.launch {
      runCatching {
        val res =
          JSONObject(
            viewModel.runGatewayRpc(
              GatewayMethod.PartnerKnowledgeGet,
              JSONObject().put("partnerId", partnerId).put("docId", doc.id).toString(),
              timeoutMs = 60_000,
            ),
          )
        editText = res.optString("text")
        editLoaded = true
      }.onFailure {
        status = "開けませんでした: ${it.message}"
        editing = null
      }
    }
  }

  fun save(doc: KnowledgeRow) {
    scope.launch {
      editSaving = true
      runCatching {
        val res =
          JSONObject(
            viewModel.runGatewayRpc(
              GatewayMethod.PartnerKnowledgeUpdate,
              JSONObject()
                .put("partnerId", partnerId)
                .put("docId", doc.id)
                .put("title", editTitle.trim())
                .put("text", editText)
                .toString(),
              timeoutMs = 60_000,
            ),
          )
        val saved = res.getJSONObject("doc")
        status = "📚「${saved.optString("title")}」を保存しました（${"%,d".format(saved.optInt("chars"))}文字）"
        editing = null
      }.onFailure { status = "保存できませんでした: ${it.message}" }
      editSaving = false
      refresh()
    }
  }

  fun displayName(uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    } ?: "document.pdf"

  val pdfPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        busy = true
        val fileName = displayName(uri)
        runCatching {
          val bytes =
            withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
              ?: error("ファイルを開けませんでした")
          val uploadId = UUID.randomUUID().toString().take(12)
          val chunks = maxOf(1, (bytes.size + KNOWLEDGE_CHUNK_BYTES - 1) / KNOWLEDGE_CHUNK_BYTES)
          var result: JSONObject? = null
          for (i in 0 until chunks) {
            val part = bytes.copyOfRange(i * KNOWLEDGE_CHUNK_BYTES, minOf(bytes.size, (i + 1) * KNOWLEDGE_CHUNK_BYTES))
            status = if (i < chunks - 1) "送信中: $fileName (${i + 1}/$chunks)" else "PCで文字を読み取り中: $fileName"
            progress = (i + 1f) / chunks
            val params =
              JSONObject()
                .put("partnerId", partnerId)
                .put("uploadId", uploadId)
                .put("fileName", fileName)
                .put("chunkIndex", i)
                .put("chunkCount", chunks)
                .put("dataBase64", Base64.encodeToString(part, Base64.NO_WRAP))
            result =
              JSONObject(
                viewModel.runGatewayRpc(
                  GatewayMethod.PartnerKnowledgeAdd,
                  params.toString(),
                  timeoutMs = if (i < chunks - 1) 60_000 else 300_000,
                ),
              )
          }
          val doc = result?.optJSONObject("doc") ?: error("PCから結果が返りませんでした")
          status = "📚「${doc.optString("title")}」を入れました（${"%,d".format(doc.optInt("chars"))}文字）"
        }.onFailure { status = "入れられませんでした: ${it.message}" }
        progress = null
        busy = false
        refresh()
      }
    }

  LaunchedEffect(partnerId) { refresh() }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(text = "📚 資料棚", style = ClawTheme.type.title, color = ClawTheme.colors.text)
            if (partnerName.isNotBlank()) {
              Text(text = "${partnerName}が会話で参照する資料", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
            }
          }
        }
      }
      item {
        Text(
          text = "入れた資料から、会話の話題に合う部分だけを毎回探して返事に使います。チャットで「このURLを覚えて https://…」と送っても入ります。資料をタップすると全文を見たり書き直したりできます。",
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textMuted,
        )
      }
      item {
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "PDFを入れる", style = ClawTheme.type.section, color = ClawTheme.colors.text)
            ClawPrimaryButton(
              text = "📄 PDFを選ぶ",
              onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
              enabled = !busy && partnerId.isNotBlank(),
              modifier = Modifier.fillMaxWidth(),
            )
            Text(
              text = "文字のあるPDFが対象です（スキャンした画像だけのPDFは読めません）。40MBまで。",
              style = ClawTheme.type.captionSmall,
              color = ClawTheme.colors.textSubtle,
            )
          }
        }
      }
      item {
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Webページを入れる", style = ClawTheme.type.section, color = ClawTheme.colors.text)
            ClawTextField(value = url, onValueChange = { url = it }, placeholder = "https://…", modifier = Modifier.fillMaxWidth())
            ClawSecondaryButton(
              text = "🌐 このページを入れる",
              onClick = {
                add(JSONObject().put("url", url.trim()), "ページ")
                url = ""
              },
              enabled = !busy && url.trim().startsWith("http"),
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
      item {
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "文章を入れる", style = ClawTheme.type.section, color = ClawTheme.colors.text)
            ClawTextField(value = noteTitle, onValueChange = { noteTitle = it }, placeholder = "題名（例: 設定資料）", modifier = Modifier.fillMaxWidth())
            ClawTextField(
              value = noteText,
              onValueChange = { noteText = it },
              placeholder = "覚えてほしい内容を貼り付け",
              minLines = 4,
              modifier = Modifier.fillMaxWidth(),
            )
            ClawSecondaryButton(
              text = "✏️ 文章を入れる",
              onClick = {
                add(JSONObject().put("title", noteTitle.trim()).put("text", noteText), "文章")
                noteTitle = ""
                noteText = ""
              },
              enabled = !busy && noteText.trim().length >= 20,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
      if (status.isNotBlank() || progress != null) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            Text(text = status, style = ClawTheme.type.body, color = ClawTheme.colors.text)
          }
        }
      }
      item {
        Text(text = "入っている資料（${docs.size}件）", style = ClawTheme.type.section, color = ClawTheme.colors.text)
      }
      if (docs.isEmpty()) {
        item { Text(text = "まだ資料がありません", style = ClawTheme.type.body, color = ClawTheme.colors.textMuted) }
      }
      items(docs, key = { it.id }) { doc ->
        ClawPanel {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).clickable { open(doc) }) {
              val icon = when (doc.kind) { "pdf" -> "📄"; "url" -> "🌐"; else -> "✏️" }
              Text(text = "$icon ${doc.title}", style = ClawTheme.type.body, color = ClawTheme.colors.text)
              val date = SimpleDateFormat("M/d HH:mm", Locale.JAPAN).format(Date(doc.addedAt))
              Text(
                text = "${"%,d".format(doc.chars)}文字・$date" + if (doc.source.startsWith("http")) "\n${doc.source}" else "",
                style = ClawTheme.type.captionSmall,
                color = ClawTheme.colors.textMuted,
              )
            }
            ClawIconButton(icon = Icons.Default.Edit, contentDescription = "見る・編集", onClick = { open(doc) })
            ClawIconButton(icon = Icons.Default.Delete, contentDescription = "削除", onClick = { confirmDelete = doc })
          }
        }
      }
    }
  }

  editing?.let { doc ->
    AlertDialog(
      onDismissRequest = { if (!editSaving) editing = null },
      title = { Text("📚 資料を見る・編集") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (!editLoaded) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("読み込み中…", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          } else {
            ClawTextField(value = editTitle, onValueChange = { editTitle = it }, placeholder = "題名", modifier = Modifier.fillMaxWidth())
            ClawTextField(
              value = editText,
              onValueChange = { editText = it },
              placeholder = "本文",
              minLines = 8,
              modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            )
            Text(
              text = "${"%,d".format(editText.length)}文字" + if (doc.source.startsWith("http")) "・元: ${doc.source}" else "",
              style = ClawTheme.type.captionSmall,
              color = ClawTheme.colors.textSubtle,
            )
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = { save(doc) },
          enabled = editLoaded && !editSaving && editText.trim().length >= 20,
        ) { Text(if (editSaving) "保存中…" else "保存") }
      },
      dismissButton = { TextButton(onClick = { editing = null }, enabled = !editSaving) { Text("閉じる") } },
    )
  }

  confirmDelete?.let { doc ->
    AlertDialog(
      onDismissRequest = { confirmDelete = null },
      title = { Text("資料を削除") },
      text = { Text("「${doc.title}」を資料棚から削除しますか？") },
      confirmButton = {
        TextButton(onClick = {
          confirmDelete = null
          scope.launch {
            runCatching {
              viewModel.runGatewayRpc(
                GatewayMethod.PartnerKnowledgeDelete,
                JSONObject().put("partnerId", partnerId).put("docId", doc.id).toString(),
              )
            }.onFailure { status = "削除エラー: ${it.message}" }
            refresh()
          }
        }) { Text("削除") }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("やめる") } },
    )
  }
}
