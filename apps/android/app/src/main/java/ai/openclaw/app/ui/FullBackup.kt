package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** このアプリのデータ（設定・保存データ・データベース・キャッシュ）を、zip に書き出し、zip から戻す。 */
internal object AppDataBackup {
  private val FOLDERS = listOf("shared_prefs", "files", "databases", "cache")

  private fun root(context: Context): File = File(context.applicationInfo.dataDir)

  /** 作業用のzipを置く場所。バックアップの対象に入らない所にする。 */
  fun workDir(context: Context): File = File(context.noBackupFilesDir, "backup-tmp").also { it.mkdirs() }

  fun stampName(prefix: String): String {
    val now = java.util.Calendar.getInstance()
    fun p(n: Int) = n.toString().padStart(2, '0')
    return "$prefix-${now.get(java.util.Calendar.YEAR)}${p(now.get(java.util.Calendar.MONTH) + 1)}${p(now.get(java.util.Calendar.DAY_OF_MONTH))}-${p(now.get(java.util.Calendar.HOUR_OF_DAY))}${p(now.get(java.util.Calendar.MINUTE))}${p(now.get(java.util.Calendar.SECOND))}.zip"
  }

  fun create(context: Context, out: File) {
    val base = root(context)
    ZipOutputStream(BufferedOutputStream(out.outputStream())).use { zip ->
      zip.putNextEntry(ZipEntry("info.txt"))
      zip.write("OpenClaw phone backup\npackage=${context.packageName}\ntime=${System.currentTimeMillis()}\n".toByteArray())
      zip.closeEntry()
      for (name in FOLDERS) {
        val dir = File(base, name)
        if (!dir.exists()) continue
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
          zip.putNextEntry(ZipEntry("data/" + file.relativeTo(base).invariantSeparatorsPath))
          file.inputStream().use { it.copyTo(zip) }
          zip.closeEntry()
        }
      }
    }
  }

  /** zip の中身で置き換える（いまのデータは消えるので、確認してから呼ぶ）。 */
  fun restore(context: Context, zip: File) {
    val base = root(context)
    require(zipHasData(zip)) { "このzipは、OpenClaw のスマホのバックアップではありません" }
    for (name in FOLDERS) File(base, name).listFiles()?.forEach { it.deleteRecursively() }
    ZipInputStream(zip.inputStream().buffered()).use { input ->
      var entry = input.nextEntry
      while (entry != null) {
        if (!entry.isDirectory && entry.name.startsWith("data/")) {
          val target = File(base, entry.name.removePrefix("data/"))
          if (target.canonicalPath.startsWith(base.canonicalPath + File.separator)) {
            target.parentFile?.mkdirs()
            target.outputStream().use { input.copyTo(it) }
          }
        }
        entry = input.nextEntry
      }
    }
  }

  private fun zipHasData(zip: File): Boolean =
    ZipInputStream(zip.inputStream().buffered()).use { input ->
      generateSequence { input.nextEntry }.any { it.name == "info.txt" || it.name.startsWith("data/") }
    }

  /** 端末の「ダウンロード/OpenClaw」に保存する。 */
  fun saveToDownloads(context: Context, file: File, name: String): Boolean =
    runCatching {
      val values =
        ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, name)
          put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
          put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OpenClaw")
        }
      val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
      context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return@runCatching false
      true
    }.getOrDefault(false)

  /** アプリを開き直す（復元したデータを読み込み直すため）。 */
  fun restart(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) context.startActivity(intent)
    android.os.Process.killProcess(android.os.Process.myPid())
  }
}

private data class BackupRow(val kind: String, val name: String, val bytes: Long)

/** "OpenClaw-phone-20261008-213045.zip" → "2026/10/08 21:30:45" */
private fun backupDate(name: String): String =
  Regex("(\\d{4})(\\d{2})(\\d{2})-(\\d{2})(\\d{2})(\\d{2})").find(name)?.destructured?.let { (y, mo, d, h, mi, s) -> "$y/$mo/$d $h:$mi:$s" } ?: name

/**
 * 全データのバックアップ：このスマホのデータ（設定・保存データ・キャッシュ）と、PCのデータを、日付つきのzipにして残し、戻せる。
 * スマホのzipは「ダウンロード/OpenClaw」と、PCの D:\OpenClaw-Backups\phone に。PCのzipは D:\OpenClaw-Backups\pc に。
 */
@Composable
internal fun FullBackupPanel(viewModel: MainViewModel) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var message by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var rows by remember { mutableStateOf<List<BackupRow>>(emptyList()) }
  var includeOutputs by remember { mutableStateOf(true) }
  var confirmRestore by remember { mutableStateOf<BackupRow?>(null) }
  var confirmFile by remember { mutableStateOf<File?>(null) }

  suspend fun rpc(method: GatewayMethod, params: JSONObject, timeoutMs: Long = 60_000): JSONObject =
    JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = timeoutMs))

  suspend fun reload() {
    runCatching {
      val arr = rpc(GatewayMethod.BackupList, JSONObject()).optJSONArray("backups")
      rows = (0 until (arr?.length() ?: 0)).map { i -> arr!!.getJSONObject(i).let { BackupRow(it.optString("kind"), it.optString("name"), it.optLong("bytes")) } }
    }
  }
  LaunchedEffect(Unit) { reload() }

  fun guarded(label: String, block: suspend () -> Unit) {
    if (busy) return
    scope.launch {
      busy = true
      message = label
      runCatching { block() }.onFailure { message = "できませんでした：${it.message}" }
      reload()
      busy = false
    }
  }

  // ファイルから復元（スマホのzipを選ぶ）
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    guarded("zipを読み込んでいます…") {
      val temp = File(AppDataBackup.workDir(context), "picked.zip")
      withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } } }
      confirmFile = temp
      message = "復元するか、確認してください。"
    }
  }

  ClawPanel(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(text = "💾 全データのバックアップ（zip・日付つき）", style = ClawTheme.type.section, color = ClawTheme.colors.text)
      Text(
        text = "スマホのデータ（設定・保存データ・キャッシュ）は、zipにして「ダウンロード/OpenClaw」とPCの D:\\OpenClaw-Backups\\phone に残します。PCのデータ（キャラ・パートナー・設定・声・作品など）は D:\\OpenClaw-Backups\\pc に残します。blocked-terms.json は入れません。",
        style = ClawTheme.type.captionSmall,
        color = ClawTheme.colors.textMuted,
      )
      if (message.isNotBlank()) Text(text = message, style = ClawTheme.type.caption, color = ClawTheme.colors.primary)

      ClawSecondaryButton(
        text = if (busy) "お待ちください…" else "📱 このスマホのデータをバックアップする",
        enabled = !busy,
        onClick = {
          guarded("スマホのデータをまとめています…") {
            val name = AppDataBackup.stampName("OpenClaw-phone")
            val zip = File(AppDataBackup.workDir(context), name)
            withContext(Dispatchers.IO) { AppDataBackup.create(context, zip) }
            val saved = AppDataBackup.saveToDownloads(context, zip, name)
            message = "PCへ送っています…（${zip.length() / 1_000_000}MB）"
            val chunk = 1_000_000
            val total = maxOf(1, ((zip.length() + chunk - 1) / chunk).toInt())
            var uploaded = false
            runCatching {
              withContext(Dispatchers.IO) {
                zip.inputStream().use { input ->
                  for (i in 0 until total) {
                    val buffer = ByteArray(chunk)
                    val n = input.read(buffer)
                    val part = if (n < chunk) buffer.copyOf(maxOf(n, 0)) else buffer
                    val params = JSONObject().put("name", name).put("index", i).put("total", total).put("dataBase64", Base64.encodeToString(part, Base64.NO_WRAP))
                    viewModel.runGatewayRpc(GatewayMethod.BackupPhoneUpload, params.toString(), timeoutMs = 120_000)
                  }
                }
              }
              uploaded = true
            }
            zip.delete()
            message = "✅ $name を作りました（" + listOfNotNull(if (saved) "スマホの「ダウンロード/OpenClaw」" else null, if (uploaded) "PC" else null).joinToString("と").ifBlank { "保存先なし" } + "に保存）"
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        CreatorChipLike(label = if (includeOutputs) "作品（絵・動画）も入れる：入" else "作品（絵・動画）も入れる：切", selected = includeOutputs, onClick = { includeOutputs = !includeOutputs })
      }
      ClawSecondaryButton(
        text = if (busy) "お待ちください…" else "🖥 PCのデータをバックアップする",
        enabled = !busy,
        onClick = {
          guarded("PCのデータをまとめています…（数分かかることがあります）") {
            val taskId = rpc(GatewayMethod.BackupPcCreate, JSONObject().put("includeOutputs", includeOutputs)).optJSONObject("task")?.optString("id").orEmpty()
            var state = "running"
            while (state == "running") {
              delay(3_000)
              val task = rpc(GatewayMethod.BackupStatus, JSONObject().put("taskId", taskId)).optJSONObject("task")
              state = task?.optString("state") ?: "error"
              message = (if (state == "done") "✅ " else "") + task?.optString("message").orEmpty()
            }
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )
      ClawSecondaryButton(text = "📂 zipファイルから、スマホのデータを復元する", enabled = !busy, onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth())

      Text(text = "PCにあるバックアップ（新しい順）", style = ClawTheme.type.caption, color = ClawTheme.colors.text, modifier = Modifier.padding(top = 6.dp))
      if (rows.isEmpty()) Text(text = "まだありません。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = (if (row.kind == "pc") "🖥 PC　" else "📱 スマホ　") + backupDate(row.name), style = ClawTheme.type.caption, color = ClawTheme.colors.text)
            Text(text = "${row.bytes / 1_000_000}MB", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          }
          ClawSecondaryButton(text = "復元", enabled = !busy, onClick = { confirmRestore = row })
          ClawSecondaryButton(
            text = "削除",
            enabled = !busy,
            onClick = { guarded("削除しています…") { rpc(GatewayMethod.BackupDelete, JSONObject().put("kind", row.kind).put("name", row.name)); message = "削除しました" } },
          )
        }
      }
    }
  }

  confirmRestore?.let { row ->
    AlertDialog(
      onDismissRequest = { confirmRestore = null },
      title = { Text("${backupDate(row.name)} のバックアップを復元しますか？") },
      text = {
        Text(
          if (row.kind == "pc") "PCのデータが、このバックアップの内容で上書きされます（バックアップにないファイルは、そのまま残ります）。終わったら、PCのゲートウェイを再起動してください。"
          else "このスマホの設定・保存データ・キャッシュが、バックアップの内容に置き換わり、アプリが開き直されます。いまのデータは消えます。",
        )
      },
      confirmButton = {
        TextButton(onClick = {
          confirmRestore = null
          if (row.kind == "pc") {
            guarded("PCのデータを復元しています…") {
              val taskId = rpc(GatewayMethod.BackupPcRestore, JSONObject().put("name", row.name).put("confirm", true)).optJSONObject("task")?.optString("id").orEmpty()
              var state = "running"
              while (state == "running") {
                delay(3_000)
                val task = rpc(GatewayMethod.BackupStatus, JSONObject().put("taskId", taskId)).optJSONObject("task")
                state = task?.optString("state") ?: "error"
                message = (if (state == "done") "✅ " else "") + task?.optString("message").orEmpty()
              }
            }
          } else {
            guarded("PCからスマホのzipを受け取っています…") {
              val temp = File(AppDataBackup.workDir(context), "download.zip")
              withContext(Dispatchers.IO) {
                temp.outputStream().use { out ->
                  var offset = 0L
                  var total = Long.MAX_VALUE
                  while (offset < total) {
                    val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.BackupRead, JSONObject().put("kind", "phone").put("name", row.name).put("offset", offset).put("length", 1_000_000).toString(), timeoutMs = 60_000))
                    total = res.optLong("total")
                    val bytes = Base64.decode(res.optString("dataBase64"), Base64.DEFAULT)
                    if (bytes.isEmpty()) break
                    out.write(bytes)
                    offset += bytes.size
                  }
                }
              }
              message = "復元しています…"
              withContext(Dispatchers.IO) { AppDataBackup.restore(context, temp) }
              temp.delete()
              AppDataBackup.restart(context)
            }
          }
        }) { Text("復元する") }
      },
      dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("やめる") } },
    )
  }
  confirmFile?.let { file ->
    AlertDialog(
      onDismissRequest = { confirmFile = null; file.delete() },
      title = { Text("このzipで、スマホのデータを復元しますか？") },
      text = { Text("このスマホの設定・保存データ・キャッシュが、zipの内容に置き換わり、アプリが開き直されます。いまのデータは消えます。") },
      confirmButton = {
        TextButton(onClick = {
          confirmFile = null
          guarded("復元しています…") {
            withContext(Dispatchers.IO) { AppDataBackup.restore(context, file) }
            file.delete()
            AppDataBackup.restart(context)
          }
        }) { Text("復元する") }
      },
      dismissButton = { TextButton(onClick = { confirmFile = null; file.delete() }) { Text("やめる") } },
    )
  }
}

@Composable
private fun CreatorChipLike(label: String, selected: Boolean, onClick: () -> Unit) {
  ClawSecondaryButton(text = (if (selected) "✓ " else "") + label, onClick = onClick, modifier = Modifier.fillMaxWidth())
}
