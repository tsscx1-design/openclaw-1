package ai.openclaw.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 分類・グループ・サブグループの「表示の名前」。この端末の中にだけ残す（名簿や Gateway の名前は、変わらない）。
 * 鍵は、「category:<元の名前>」「group:<元の名前>」。元の名前のまま、すべての処理に使い、画面に出すときだけ、置きかえる。
 */
internal object FanBookNames {
  /** 変えたら上がる。画面は、これを見て、描きなおす。 */
  var version by mutableStateOf(0)
    private set

  private const val PREFS = "openclaw-fanbook-names"

  private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun label(context: Context, key: String): String? =
    runCatching { prefs(context).getString(key, null)?.takeIf { it.isNotBlank() } }.getOrNull()

  fun set(context: Context, key: String, label: String) {
    val clean = label.trim().take(60)
    if (clean.isBlank()) {
      clear(context, key)
      return
    }
    runCatching { prefs(context).edit().putString(key, clean).apply() }
    version += 1
  }

  fun clear(context: Context, key: String) {
    runCatching { prefs(context).edit().remove(key).apply() }
    version += 1
  }
}
