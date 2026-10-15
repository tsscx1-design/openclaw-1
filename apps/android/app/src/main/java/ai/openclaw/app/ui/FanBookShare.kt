package ai.openclaw.app.ui

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 他のアプリ（X・Instagram・TikTok・ブラウザなど）で、画像を「共有 → このアプリ」にして、ファンブックの人の写真にする受け口。
 * ファンブックの1人のページで「共有から入れる」を押したあいだ（待っているあいだ）だけ、共有された画像を、
 * チャットの下書きではなく、ここで受け取る。待っていないときの共有は、いつもどおり、チャットに入る。
 */
internal object FanBookShareInbox {
  data class Target(val group: String, val id: Int, val name: String, val asExtra: Boolean)

  private val armedMutable = MutableStateFlow<Target?>(null)
  val armed: StateFlow<Target?> = armedMutable

  private val incomingMutable = MutableStateFlow<List<Uri>>(emptyList())
  val incoming: StateFlow<List<Uri>> = incomingMutable

  fun arm(target: Target) {
    incomingMutable.value = emptyList()
    armedMutable.value = target
  }

  fun disarm() {
    armedMutable.value = null
  }

  /** 画像の共有を待っているときだけ受け取る。受け取ったら true（チャットには回さない）。 */
  @Suppress("DEPRECATION")
  fun tryCapture(intent: Intent): Boolean {
    if (armedMutable.value == null) return false
    if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return false
    val uris = mutableListOf<Uri>()
    if (intent.action == Intent.ACTION_SEND) {
      intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
    } else {
      intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
    }
    intent.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) } }
    val type = intent.type.orEmpty()
    if (uris.isEmpty() || !(type.startsWith("image/") || type == "*/*")) return false
    incomingMutable.value = uris.distinct().take(12)
    return true
  }

  fun consume(): List<Uri> {
    val taken = incomingMutable.value
    incomingMutable.value = emptyList()
    return taken
  }
}
