package ai.openclaw.app.ui

import ai.openclaw.app.ui.design.ClawTheme
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.times

/**
 * 項目（外見オプション・サンプル画像の一覧）の表示の大きさ。文字・サムネイル・行の高さが、この倍率で変わる。
 * 選んだ大きさは、アプリを閉じても覚えている。
 */
internal object ItemDisplay {
  /** 選べる大きさ（名前と倍率）。 */
  val SIZES: List<Pair<String, Float>> = listOf("小" to 1.0f, "中" to 1.25f, "大" to 1.55f, "特大" to 2.0f)

  private const val PREFS = "item_display"
  private const val KEY = "scale"
  private const val KEY_COLUMNS = "columns"

  /** 項目を横に何列ならべるか（1〜3）。1列は横幅いっぱい。 */
  var columns by androidx.compose.runtime.mutableIntStateOf(2)
    private set

  /** いまの倍率。画面は、これを読むだけで、変わったらすぐ描き直される。 */
  var scale by mutableFloatStateOf(1.25f)
    private set
  private var loaded = false

  fun load(context: Context) {
    if (loaded) return
    loaded = true
    scale = runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY, 1.25f) }.getOrDefault(1.25f).coerceIn(1.0f, 2.0f)
    columns = runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_COLUMNS, 2) }.getOrDefault(2).coerceIn(1, 3)
  }

  fun setColumns(context: Context, value: Int) {
    columns = value.coerceIn(1, 3)
    runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_COLUMNS, columns).apply() }
  }

  fun set(context: Context, value: Float) {
    scale = value.coerceIn(1.0f, 2.0f)
    runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY, scale).apply() }
  }
}

/** 文字の大きさを、いまの倍率にした書式。大きさの決まっていない書式は、そのまま。 */
internal fun scaledItemStyle(style: androidx.compose.ui.text.TextStyle): androidx.compose.ui.text.TextStyle =
  if (style.fontSize.isSpecified) style.copy(fontSize = style.fontSize * ItemDisplay.scale) else style

/** 項目の表示サイズを選ぶ（小・中・大・特大）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ItemSizePicker(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  ItemDisplay.load(context)
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("項目の表示の大きさ", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      for ((label, value) in ItemDisplay.SIZES) {
        val selected = kotlin.math.abs(ItemDisplay.scale - value) < 0.01f
        Box(
          modifier =
            Modifier
              .heightIn(min = 40.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
              .border(if (selected) 1.5.dp else 1.dp, if (selected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
              .clickable { ItemDisplay.set(context, value) }
              .padding(horizontal = 14.dp, vertical = 6.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(label, style = ClawTheme.type.body, color = ClawTheme.colors.text)
        }
      }
    }
    Text("横にならべる列の数", style = ClawTheme.type.caption, color = ClawTheme.colors.text)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      for ((label, value) in listOf("1列（横幅いっぱい）" to 1, "2列" to 2, "3列" to 3)) {
        val selected = ItemDisplay.columns == value
        Box(
          modifier =
            Modifier
              .heightIn(min = 40.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(if (selected) ClawTheme.colors.primary.copy(alpha = 0.15f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.6f))
              .border(if (selected) 1.5.dp else 1.dp, if (selected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
              .clickable { ItemDisplay.setColumns(context, value) }
              .padding(horizontal = 14.dp, vertical = 6.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(label, style = ClawTheme.type.body, color = ClawTheme.colors.text)
        }
      }
    }
  }
}
