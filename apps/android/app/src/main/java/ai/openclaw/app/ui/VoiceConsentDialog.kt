package ai.openclaw.app.ui

import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 声の学習の前の、「許可の確認」の画面。声の出どころの種類を選び、その種類ごとの確認に、1つずつ、チェックを入れる。
// 確認の内容と、許可の根拠（本人の申告）は、声のプロジェクトに、記録として、残る。
// アプリは、許可が、本当にあるかを、確かめることは、できません。申告の責任は、あなたにあります。

private data class VoiceSource(val key: String, val label: String, val checks: List<String>, val needsEvidence: Boolean, val warning: String)

private val VOICE_SOURCES =
  listOf(
    VoiceSource(
      "own",
      "① 自分の声",
      listOf("自分で録音した、自分の声です。"),
      needsEvidence = false,
      warning = "",
    ),
    VoiceSource(
      "permitted",
      "② 許可のある録音",
      listOf(
        "声の持ち主（本人）から、AI の学習・声の再現に使うことの、許可を得ています。",
        "許可された範囲（個人で使う／公開する／商用に使う）を、確認しました。",
        "許可の証拠（メール、契約、利用規約のページなど）を、保存しました。",
      ),
      needsEvidence = true,
      warning = "「フリー素材」や「商用利用 OK」と書かれていても、AI の学習や、声の再現が、許可されているとは、限りません。",
    ),
    VoiceSource(
      "fish",
      "③ Fish Audio で作った声",
      listOf(
        "元の声は、自分の声、または、本人の許可を得た声です（出どころの分からないコミュニティボイスではありません）。",
        "Fish Audio の利用規約で、生成した音声を、自分用の学習に使うことが、問題ないと、確認しました（または、問い合わせて、許可を得ました）。",
        "利用の範囲（個人／商用）を、確認しました。",
      ),
      needsEvidence = true,
      warning = "規約には、Fish.Audio と競合するモデルを、出力で開発することの禁止が、あります。解釈に迷うときは、Fish Audio に、問い合わせてください。",
    ),
    VoiceSource(
      "real",
      "④ 声優・配信者など、実在の人の音声",
      listOf(
        "本人（または、権利を持つ事務所・会社）から、**この声を AI で学習し、再現して使うこと**の許可を、**もらいました**。",
        "許可された範囲（個人で使う／公開する／商用に使う）を、確認しました。",
        "許可の証拠（メール、契約書など）を、保存しました。",
        "許可を得ていない場合や、範囲を超える使い方は、法律（パブリシティ権、著作隣接権など）に、反する場合があると、理解しました。",
      ),
      needsEvidence = true,
      warning = "本人の許可がない、実在の人の声の学習・再現は、できません。許可が、はっきりしないときは、使わないでください。",
    ),
  )

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VoiceConsentDialog(onConfirm: (note: String) -> Unit, onDismiss: () -> Unit) {
  var kind by remember { mutableStateOf<String?>(null) }
  var checked by remember { mutableStateOf(setOf<Int>()) }
  var evidence by remember { mutableStateOf("") }
  val source = VOICE_SOURCES.firstOrNull { it.key == kind }
  val allChecked = source != null && checked.size == source.checks.size
  val evidenceOk = source != null && (!source.needsEvidence || evidence.trim().length >= 8)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("声の許可の確認") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("この声の、出どころは、どれですか。選んで、確認に、1つずつ、チェックを入れてください。", style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          VOICE_SOURCES.forEach { s ->
            ClawSecondaryButton(text = if (kind == s.key) "● ${s.label}" else s.label, onClick = { kind = s.key; checked = emptySet() })
          }
        }
        if (source != null) {
          if (source.warning.isNotBlank()) Text(source.warning, style = ClawTheme.type.caption.copy(fontSize = 12.sp), color = ClawTheme.colors.primary)
          source.checks.forEachIndexed { i, text ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Checkbox(checked = i in checked, onCheckedChange = { on -> checked = if (on) checked + i else checked - i })
              Text(text.replace("**", ""), style = ClawTheme.type.caption.copy(fontSize = 12.sp), color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
            }
          }
          if (source.needsEvidence) {
            ClawTextField(
              value = evidence,
              onValueChange = { evidence = it.take(600) },
              placeholder = "例：2026-10-10 に、本人から、メールで許可（件名・相手の名前）／規約のページの URL",
              label = "許可の根拠（記録に残ります）",
              modifier = Modifier.fillMaxWidth(),
            )
            Text("許可の証拠は、あなた自身で、保存しておいてください。アプリは、許可が本当にあるかを、確かめられません。申告の責任は、あなたにあります。", style = ClawTheme.type.captionSmall, color = ClawTheme.colors.textMuted)
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = allChecked && evidenceOk,
        onClick = {
          val s = source ?: return@TextButton
          val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPAN).format(Date())
          onConfirm("[$stamp] 出どころ：${s.label}／確認：${s.checks.size}項目すべてチェック済み" + if (s.needsEvidence) "／根拠：${evidence.trim()}" else "")
        },
      ) { Text("確認して、進む") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )
}
