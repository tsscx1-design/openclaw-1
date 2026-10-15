package ai.openclaw.app.ui

import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class MotionProfileField(val key: String, val label: String, val min: Double, val max: Double)

private val motionProfileFields = listOf(
  MotionProfileField("shoulderScale", "肩幅の比率", 0.6, 1.6),
  MotionProfileField("hipScale", "腰幅の比率", 0.6, 1.6),
  MotionProfileField("armScale", "腕の長さの比率", 0.7, 1.3),
  MotionProfileField("legScale", "脚の長さの比率", 0.7, 1.3),
  MotionProfileField("torsoScale", "胴の長さの比率", 0.7, 1.3),
  MotionProfileField("chestMotion", "胸の揺れの強さ", 0.0, 1.0),
  MotionProfileField("chestSoftness", "胸の柔らかさ", 0.0, 1.0),
  MotionProfileField("chestDamping", "胸の揺れの収まり（大きいほど早い）", 0.0, 1.0),
  MotionProfileField("clothingSupport", "衣装による胸の支え", 0.0, 1.0),
  MotionProfileField("hairMotion", "髪の揺れの強さ", 0.0, 1.0),
  MotionProfileField("accessoryMotion", "アクセサリーの揺れの強さ", 0.0, 1.0),
)

internal fun characterMotionProfileValid(values: Map<String, String>): Boolean =
  motionProfileFields.all { field ->
    val raw = values[field.key].orEmpty()
    raw.isBlank() || raw.toDoubleOrNull()?.let { it.isFinite() && it in field.min..field.max } == true
  }

@Composable
internal fun CharacterMotionProfileEditor(values: Map<String, String>, onChange: (String, String) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  ClawSecondaryButton(text = if (expanded) "体格・揺れの設定を閉じる" else "体格・揺れを調整する", onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth())
  if (expanded) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("動画AIへの指示として使います。比率は1が標準、揺れは0〜1。空欄は自動です。身長は設定シートの値を使います。骨格による物理シミュレーションではありません。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
      motionProfileFields.forEach { field ->
        ClawTextField(value = values[field.key].orEmpty(), onValueChange = { onChange(field.key, it) }, label = field.label, placeholder = "${field.min}〜${field.max}（空欄は自動）", modifier = Modifier.fillMaxWidth())
      }
      if (!characterMotionProfileValid(values)) Text("範囲内の数値を入力してください", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
    }
  }
}
