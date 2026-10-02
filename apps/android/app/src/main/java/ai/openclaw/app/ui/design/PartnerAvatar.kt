package ai.openclaw.app.ui.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * AIパートナー用アバター画像コンポーネント
 * Base64データ (data:image/...)、リモートURL (http...)、絵文字、またはフォールバックアイコンを自動判別して描画
 */
@Composable
internal fun PartnerAvatar(
  avatarUrl: String?,
  name: String,
  size: Dp = 44.dp,
  modifier: Modifier = Modifier,
) {
  val cleanUrl = avatarUrl?.trim().orEmpty()

  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(ClawTheme.colors.surfaceRaised),
    contentAlignment = Alignment.Center,
  ) {
    when {
      // 1. Base64 画像
      cleanUrl.startsWith("data:image/") -> {
        val bitmap = remember(cleanUrl) { decodeBase64Bitmap(cleanUrl) }
        if (bitmap != null) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size),
          )
        } else {
          FallbackAvatar(name, size)
        }
      }

      // 2. リモート URL（Coil SubcomposeAsyncImage でシマー + エラーフォールバック）
      cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") -> {
        SubcomposeAsyncImage(
          model = ImageRequest.Builder(LocalContext.current)
            .data(cleanUrl)
            .crossfade(true)
            .build(),
          contentDescription = name,
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(size),
          loading = {
            Box(modifier = Modifier.fillMaxSize().shimmerEffect())
          },
          error = {
            FallbackAvatar(name, size)
          },
          success = {
            SubcomposeAsyncImageContent()
          },
        )
      }

      // 3. 絵文字 (2文字以内の短い文字)
      cleanUrl.isNotBlank() && cleanUrl.length <= 4 -> {
        Text(
          text = cleanUrl,
          fontSize = (size.value * 0.52f).sp,
          lineHeight = (size.value * 0.52f).sp,
        )
      }

      // 4. デフォルト
      else -> {
        FallbackAvatar(name, size)
      }
    }
  }
}

@Composable
private fun FallbackAvatar(name: String, size: Dp) {
  val initial = name.firstOrNull()?.toString()?.uppercase() ?: ""
  if (initial.isNotBlank() && initial.first().isLetter()) {
    Text(
      text = initial,
      style = ClawTheme.type.section.copy(
        fontSize = (size.value * 0.42f).sp,
        fontWeight = FontWeight.Bold,
      ),
      color = ClawTheme.colors.primary,
    )
  } else {
    Icon(
      imageVector = Icons.Default.Person,
      contentDescription = name,
      tint = ClawTheme.colors.primary,
      modifier = Modifier.size((size.value * 0.6f).dp),
    )
  }
}

private fun decodeBase64Bitmap(dataUri: String): Bitmap? {
  return try {
    val commaIdx = dataUri.indexOf(",")
    val base64Data = if (commaIdx != -1) dataUri.substring(commaIdx + 1) else dataUri
    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
  } catch (_: Exception) {
    null
  }
}

/**
 * 端末の写真・画像URIから適切なサイズのBase64データURLを生成するユーティリティ
 */
internal fun convertUriToBase64DataUrl(context: Context, uri: Uri, maxDimension: Int = 256): String? {
  return try {
    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
    val original = BitmapFactory.decodeStream(inputStream) ?: return null
    inputStream?.close()

    // リサイズ処理 (メモリ節約・転送高速化)
    val width = original.width
    val height = original.height
    val scale = if (width > maxDimension || height > maxDimension) {
      maxDimension.toFloat() / maxOf(width, height)
    } else {
      1.0f
    }

    val scaled = if (scale < 1.0f) {
      Bitmap.createScaledBitmap(original, (width * scale).toInt(), (height * scale).toInt(), true)
    } else {
      original
    }

    val outputStream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
    val bytes = outputStream.toByteArray()
    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    "data:image/jpeg;base64,$base64"
  } catch (e: Exception) {
    null
  }
}
