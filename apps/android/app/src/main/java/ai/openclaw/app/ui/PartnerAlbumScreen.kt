package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.ui.design.ClawIconButton
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.convertUriToBase64DataUrl
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class AlbumPhoto(
  val id: String,
  val caption: String,
  val comment: String,
  val status: String,
  val error: String,
  val addedAt: String,
)

private const val ALBUM_PREVIEW_MAX_PX = 720
private const val ALBUM_UPLOAD_MAX_PX = 1024
private const val ALBUM_POLL_MS = 6_000L

private fun decodeAlbumPreview(dataUri: String): Bitmap? =
  try {
    val bytes = Base64.decode(dataUri.substringAfter(","), Base64.DEFAULT)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ALBUM_PREVIEW_MAX_PX) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
  } catch (_: Exception) {
    null
  }

private fun parseAlbumPhotos(items: JSONArray?): List<AlbumPhoto> {
  val arr = items ?: return emptyList()
  return (0 until arr.length()).map { i ->
    val o = arr.getJSONObject(i)
    AlbumPhoto(
      id = o.optString("id"),
      caption = o.optString("caption"),
      comment = o.optString("comment"),
      status = o.optString("captionStatus", "pending"),
      error = o.optString("captionError"),
      addedAt = o.optString("addedAt").take(10),
    )
  }
}

@Composable
internal fun PartnerAlbumScreen(
  viewModel: MainViewModel,
  partnerId: String? = null,
  onBack: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  var partnerName by remember { mutableStateOf("AIパートナー") }
  var photos by remember { mutableStateOf<List<AlbumPhoto>>(emptyList()) }
  var statusMessage by remember { mutableStateOf("") }
  var isBusy by remember { mutableStateOf(false) }
  var deleteTarget by remember { mutableStateOf<AlbumPhoto?>(null) }
  val previews = remember { mutableStateMapOf<String, Bitmap>() }

  suspend fun loadAlbum() {
    val pid = partnerId ?: return
    try {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerAlbumList, JSONObject().put("partnerId", pid).toString()))
      partnerName = res.optString("partnerName", partnerName)
      photos = parseAlbumPhotos(res.optJSONArray("items"))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      statusMessage = "読み込みエラー: ${e.message}"
    }
  }

  suspend fun loadPreview(photoId: String) {
    val pid = partnerId ?: return
    if (previews.containsKey(photoId)) return
    try {
      val params = JSONObject().put("partnerId", pid).put("itemId", photoId)
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerAlbumGet, params.toString()))
      decodeAlbumPreview(res.optString("imageDataUri"))?.let { previews[photoId] = it }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
    }
  }

  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    val pid = partnerId
    if (uri == null || pid == null) return@rememberLauncherForActivityResult
    coroutineScope.launch {
      isBusy = true
      try {
        val dataUri = convertUriToBase64DataUrl(context, uri, ALBUM_UPLOAD_MAX_PX)
        if (dataUri == null) {
          statusMessage = "この画像は読み込めませんでした。"
        } else {
          val params = JSONObject().put("partnerId", pid).put("imageDataUri", dataUri)
          viewModel.runGatewayRpc(GatewayMethod.PartnerAlbumAdd, params.toString())
          statusMessage = "写真を追加しました。${partnerName}が見て、説明と感想を書いています（数分かかります）。"
          loadAlbum()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        statusMessage = "追加エラー: ${e.message}"
      } finally {
        isBusy = false
      }
    }
  }

  fun recaption(photo: AlbumPhoto) {
    val pid = partnerId ?: return
    coroutineScope.launch {
      try {
        val params = JSONObject().put("partnerId", pid).put("itemId", photo.id)
        viewModel.runGatewayRpc(GatewayMethod.PartnerAlbumRecaption, params.toString())
        loadAlbum()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        statusMessage = "再解析エラー: ${e.message}"
      }
    }
  }

  fun deletePhoto(photo: AlbumPhoto) {
    val pid = partnerId ?: return
    coroutineScope.launch {
      try {
        val params = JSONObject().put("partnerId", pid).put("itemId", photo.id)
        viewModel.runGatewayRpc(GatewayMethod.PartnerAlbumDelete, params.toString())
        previews.remove(photo.id)
        loadAlbum()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        statusMessage = "削除エラー: ${e.message}"
      }
    }
  }

  LaunchedEffect(partnerId) { loadAlbum() }

  // Captions arrive in the background; keep refreshing while any photo is still being analysed.
  val hasPending = photos.any { it.status == "pending" }
  LaunchedEffect(hasPending, partnerId) {
    while (hasPending) {
      delay(ALBUM_POLL_MS)
      loadAlbum()
    }
  }

  ClawScaffold(contentPadding = PaddingValues(16.dp)) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          ClawIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", onClick = onBack)
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(text = "📷 写真アルバム", style = ClawTheme.type.title, color = ClawTheme.colors.text)
            Text(
              text = "${partnerName}に見せた写真と、その感想",
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted,
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          ClawIconButton(
            icon = Icons.Default.Refresh,
            contentDescription = "更新",
            onClick = { coroutineScope.launch { loadAlbum() } },
          )
          Spacer(modifier = Modifier.width(4.dp))
          ClawPrimaryButton(
            text = if (isBusy) "送信中..." else "＋ 写真を追加",
            onClick = { if (!isBusy) photoPicker.launch("image/*") },
          )
        }
      }

      if (statusMessage.isNotBlank()) {
        Text(
          text = statusMessage,
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textMuted,
          modifier = Modifier.padding(bottom = 8.dp),
        )
      }

      if (photos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = "まだ写真がありません。\n「写真を追加」から見せたい写真を選ぶと、${partnerName}が内容を見て感想を伝えます。\n写真はこのPCの中だけに保存されます。",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
            modifier = Modifier.padding(horizontal = 24.dp),
          )
        }
      } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          items(photos, key = { it.id }) { photo ->
            LaunchedEffect(photo.id) { loadPreview(photo.id) }
            ClawPanel(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val bitmap = previews[photo.id]
                if (bitmap != null) {
                  Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = photo.caption.ifBlank { "アルバムの写真" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(10.dp)),
                  )
                } else {
                  Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(text = "読み込み中...", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                  }
                }
                when (photo.status) {
                  "done" -> {
                    if (photo.comment.isNotBlank()) {
                      Text(
                        text = "💬 ${photo.comment}",
                        style = ClawTheme.type.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                        color = ClawTheme.colors.text,
                      )
                    }
                    Text(
                      text = photo.caption,
                      style = ClawTheme.type.caption.copy(fontSize = 12.sp),
                      color = ClawTheme.colors.textMuted,
                    )
                  }
                  "error" -> {
                    Text(
                      text = "解析できませんでした: ${photo.error.ifBlank { "原因不明" }}",
                      style = ClawTheme.type.caption,
                      color = ClawTheme.colors.textMuted,
                    )
                  }
                  else -> {
                    Text(
                      text = "🔍 ${partnerName}が写真を見ています…（CPUで解析するため数分かかります）",
                      style = ClawTheme.type.caption,
                      color = ClawTheme.colors.textMuted,
                    )
                  }
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(text = photo.addedAt, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (photo.status != "pending") {
                      ClawSecondaryButton(text = "もう一度見てもらう", onClick = { recaption(photo) })
                    }
                    ClawSecondaryButton(text = "削除", onClick = { deleteTarget = photo })
                  }
                }
              }
            }
          }
        }
      }
    }

    deleteTarget?.let { target ->
      AlertDialog(
        onDismissRequest = { deleteTarget = null },
        title = { Text(text = "この写真を削除しますか？", color = ClawTheme.colors.text) },
        text = { Text(text = "PC内の写真と感想が消えます。元に戻せません。", color = ClawTheme.colors.textMuted) },
        confirmButton = {
          ClawPrimaryButton(
            text = "削除する",
            onClick = {
              deletePhoto(target)
              deleteTarget = null
            },
          )
        },
        dismissButton = { ClawSecondaryButton(text = "キャンセル", onClick = { deleteTarget = null }) },
      )
    }
  }
}
