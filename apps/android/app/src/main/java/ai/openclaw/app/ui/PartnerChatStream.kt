package ai.openclaw.app.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

/**
 * The partner's reply as the gateway writes it ("partner.chat.delta" events: the text so far).
 * The chat screen shows it at once and starts reading it aloud before the reply is finished.
 */
internal object PartnerChatStream {
  internal data class Delta(val partnerId: String, val text: String)

  private val flow = MutableSharedFlow<Delta>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val deltas: SharedFlow<Delta> = flow.asSharedFlow()

  /** Called with the event's payload; anything unreadable is ignored. */
  fun publish(payloadJson: String?) {
    val delta =
      runCatching {
        val obj = JSONObject(payloadJson ?: return)
        Delta(obj.optString("partnerId"), obj.optString("text"))
      }.getOrNull() ?: return
    if (delta.partnerId.isNotBlank() && delta.text.isNotBlank()) flow.tryEmit(delta)
  }
}
