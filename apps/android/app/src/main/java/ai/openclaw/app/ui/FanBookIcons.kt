package ai.openclaw.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 分類・グループに付ける、好きな写真のアイコン。スマホの中（アプリの専用フォルダ）にだけ残す。
 * 鍵は、「category:<分類名>」「group:<グループ名>」。名簿や Gateway には、何も送らない。
 */
internal object FanBookIcons {
  /** アイコンを変えたら上がる。タイルは、これを見て、描きなおす。 */
  var version by mutableStateOf(0)
    private set

  private const val SIDE = 320

  private fun file(context: Context, key: String): File {
    val dir = File(context.filesDir, "fanbook-icons").apply { mkdirs() }
    val name = key.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }.take(180)
    return File(dir, "$name.jpg")
  }

  fun has(context: Context, key: String): Boolean = runCatching { file(context, key).isFile }.getOrDefault(false)

  fun load(context: Context, key: String): Bitmap? =
    runCatching { file(context, key).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) } }.getOrNull()

  /** 選んだ写真（JPEG の base64）を、真ん中で正方形に切って、小さくして残す。 */
  fun saveFromBase64(context: Context, key: String, base64: String): Boolean =
    runCatching {
      val bytes = Base64.decode(base64, Base64.DEFAULT)
      val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching false
      val side = minOf(source.width, source.height)
      val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
      val icon = Bitmap.createScaledBitmap(square, SIDE, SIDE, true)
      val out = ByteArrayOutputStream()
      icon.compress(Bitmap.CompressFormat.JPEG, 90, out)
      file(context, key).writeBytes(out.toByteArray())
      version += 1
      true
    }.getOrDefault(false)

  fun clear(context: Context, key: String) {
    runCatching { file(context, key).delete() }
    version += 1
  }

  /** タイルの題名から、鍵を作る（分類は、先頭の「📖 」「📁 」で見分ける。ほかは、グループ）。 */
  fun keyOfTitle(title: String): String =
    when {
      title.startsWith("📖 ") -> "category:" + title.removePrefix("📖 ").trim()
      title.startsWith("📁 ") -> "category:" + title.removePrefix("📁 ").trim()
      else -> "group:" + title.trim()
    }
}
