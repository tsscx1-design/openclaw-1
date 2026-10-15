package ai.openclaw.app.ui

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * ファンブックの写真・画像を、端末のキャッシュに保存して、次からは、すぐ出す。
 * 写真の版（PC の写真ファイルの時刻）が変わると、別の名前で保存されるので、古い写真が出続けることはない。
 * 合計が上限（約 300MB）を超えたら、古いものから消す。端末の「キャッシュを消去」でも、消せる。
 */
internal object FanBookImageCache {
  private const val MAX_BYTES = 300L * 1024 * 1024

  @Volatile private var dir: File? = null
  private var writes = 0

  fun attach(context: Context) {
    if (dir == null) dir = File(context.applicationContext.cacheDir, "fanbook-images").apply { mkdirs() }
  }

  private fun fileOf(key: String): File? {
    val folder = dir ?: return null
    val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
    return File(folder, digest.joinToString("") { "%02x".format(it) }.take(24))
  }

  fun read(key: String): ByteArray? =
    runCatching {
      val file = fileOf(key)
      if (file != null && file.isFile && file.length() > 0) {
        file.setLastModified(System.currentTimeMillis())
        file.readBytes()
      } else {
        null
      }
    }.getOrNull()

  fun write(key: String, bytes: ByteArray) {
    runCatching {
      val file = fileOf(key) ?: return
      val temp = File(file.parentFile, file.name + ".tmp")
      temp.writeBytes(bytes)
      temp.renameTo(file)
      synchronized(this) {
        writes += 1
        if (writes % 40 == 0) trim()
      }
    }
  }

  private fun trim() {
    val folder = dir ?: return
    val files = folder.listFiles()?.filter { it.isFile } ?: return
    var total = files.sumOf { it.length() }
    if (total <= MAX_BYTES) return
    for (file in files.sortedBy { it.lastModified() }) {
      total -= file.length()
      file.delete()
      if (total <= MAX_BYTES * 8 / 10) break
    }
  }
}
