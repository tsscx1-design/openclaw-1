package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawTheme
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 折りたたみ（2画面）でチャットを開いているとき、横の画面にキャラを出すための共有の状態。
 * チャット画面が、今話しているキャラを教え、2画面のシェルが、それを横に出す。
 */
internal object FoldStageBus {
  /** いまチャットしているキャラ（図鑑側のID）。チャットを閉じると空。 */
  var characterId by mutableStateOf("")
  var speaking by mutableStateOf(false)
  var lastReply by mutableStateOf("")

  /** 2画面の表示中か（シェルが知らせる）。 */
  var bookMode by mutableStateOf(false)

  /** 横の画面を、メニューではなくキャラにするか（保存する）。 */
  var showCharacter by mutableStateOf(true)

  /** 左右の境目（左の画面の幅の割合）。0 = 折りたたみの位置のまま。 */
  var splitRatio by mutableFloatStateOf(0f)

  val paneActive: Boolean get() = bookMode && showCharacter && characterId.isNotBlank()

  private const val PREFS = "shell_layout"

  fun load(context: Context) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    showCharacter = prefs.getBoolean("fold_side_character", true)
    splitRatio = prefs.getFloat("fold_split_ratio", 0f)
  }

  fun saveShowCharacter(context: Context, value: Boolean) {
    showCharacter = value
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("fold_side_character", value).apply()
  }

  fun saveSplit(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("fold_split_ratio", splitRatio).apply()
  }
}

/** 横の画面：話しているキャラを大きく出す。メニューへ戻すボタン付き。 */
@Composable
internal fun FoldCharacterPane(viewModel: MainViewModel) {
  val context = androidx.compose.ui.platform.LocalContext.current
  Column(
    modifier = Modifier.fillMaxSize().padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    ClawSecondaryButton(
      text = "☰ メニューを出す",
      onClick = { FoldStageBus.saveShowCharacter(context, false) },
      modifier = Modifier.fillMaxWidth(),
    )
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
      PartnerAvatarStage(
        viewModel = viewModel,
        characterId = FoldStageBus.characterId,
        speaking = FoldStageBus.speaking,
        lastReply = FoldStageBus.lastReply,
        height = maxHeight,
        modifier = Modifier.fillMaxWidth(),
      )
    }
    Text(
      text = "境目をドラッグすると、キャラとチャットの幅を変えられます",
      style = ClawTheme.type.captionSmall,
      color = ClawTheme.colors.textMuted,
    )
  }
}
