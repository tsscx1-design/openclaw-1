package ai.openclaw.app.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore

/** 絵を、端末の「写真」（Pictures/OpenClaw）に保存する。できたら true。 */
internal fun saveBitmapToPictures(context: Context, bitmap: Bitmap, name: String): Boolean =
  runCatching {
    val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).ifBlank { "image" }
    val values =
      ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "${safe}_${System.currentTimeMillis()}.png")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OpenClaw")
      }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
    context.contentResolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } ?: return@runCatching false
    true
  }.getOrDefault(false)
