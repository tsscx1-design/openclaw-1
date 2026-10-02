package ai.openclaw.app.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * シマー (スケルトンローディング) エフェクト用 Modifier。
 *
 * drawBehind で描画レイヤーのみ更新するため、再コンポジションのコストが極めて低い。
 * 読み込み中のプレースホルダーとして、Box / Text / Image などの代わりに使用する。
 */
@Composable
internal fun Modifier.shimmerEffect(
  durationMillis: Int = 1200,
): Modifier {
  val baseColor = ClawTheme.colors.surfaceRaised
  val highlightColor = ClawTheme.colors.border.copy(alpha = 0.4f)

  val transition = rememberInfiniteTransition(label = "shimmer")
  val translateX by transition.animateFloat(
    initialValue = -300f,
    targetValue = 600f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = durationMillis, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "shimmerTranslate",
  )

  val brush = Brush.linearGradient(
    colors = listOf(baseColor, highlightColor, baseColor),
    start = Offset(translateX, 0f),
    end = Offset(translateX + 300f, 0f),
  )

  return this.drawBehind {
    drawRect(brush = brush, size = size)
  }
}

/**
 * チャットメッセージのスケルトンプレースホルダー。
 * メッセージ読み込み中に1行分の骨格を表示。
 */
@Composable
internal fun MessageSkeleton(
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.Top,
  ) {
    // アバター骨格
    Box(
      modifier = Modifier
        .size(32.dp)
        .clip(CircleShape)
        .shimmerEffect(),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Column(modifier = Modifier.weight(1f)) {
      // 名前行
      Box(
        modifier = Modifier
          .width(80.dp)
          .height(12.dp)
          .clip(RoundedCornerShape(4.dp))
          .shimmerEffect(),
      )
      Spacer(modifier = Modifier.height(6.dp))
      // 本文行1
      Box(
        modifier = Modifier
          .fillMaxWidth(0.9f)
          .height(14.dp)
          .clip(RoundedCornerShape(4.dp))
          .shimmerEffect(),
      )
      Spacer(modifier = Modifier.height(4.dp))
      // 本文行2
      Box(
        modifier = Modifier
          .fillMaxWidth(0.65f)
          .height(14.dp)
          .clip(RoundedCornerShape(4.dp))
          .shimmerEffect(),
      )
    }
  }
}

/**
 * 設定行のスケルトンプレースホルダー。
 */
@Composable
internal fun SettingsRowSkeleton(
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // アイコン
    Box(
      modifier = Modifier
        .size(24.dp)
        .clip(RoundedCornerShape(6.dp))
        .shimmerEffect(),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Box(
        modifier = Modifier
          .fillMaxWidth(0.5f)
          .height(14.dp)
          .clip(RoundedCornerShape(4.dp))
          .shimmerEffect(),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth(0.35f)
          .height(10.dp)
          .clip(RoundedCornerShape(4.dp))
          .shimmerEffect(),
      )
    }
    Spacer(modifier = Modifier.width(8.dp))
    Box(
      modifier = Modifier
        .size(16.dp)
        .clip(CircleShape)
        .shimmerEffect(),
    )
  }
}

/**
 * 汎用のスケルトンボックス。幅・高さ・角丸を指定して任意の場所で使用。
 */
@Composable
internal fun SkeletonBox(
  width: Dp? = null,
  height: Dp = 14.dp,
  radius: Dp = 4.dp,
  modifier: Modifier = Modifier,
) {
  val widthModifier = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
  Box(
    modifier = modifier
      .then(widthModifier)
      .height(height)
      .clip(RoundedCornerShape(radius))
      .shimmerEffect(),
  )
}
