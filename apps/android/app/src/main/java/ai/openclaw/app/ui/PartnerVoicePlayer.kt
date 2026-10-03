package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * Plays character voices synthesized on the PC (VOICEVOX / AivisSpeech / Qwen3-TTS voice clone)
 * through the Gateway.
 */
internal object PartnerVoicePlayer {
  private var player: MediaPlayer? = null
  private val _playingKey = MutableStateFlow<String?>(null)

  /** Key of the message being read aloud (or synthesized for it), so the UI can offer "stop". */
  val playingKey: StateFlow<String?> = _playingKey.asStateFlow()

  /**
   * Synthesizes [text] with the partner's PC voice, or with [voiceOverride] for previews.
   * Returns false when no PC voice is configured so callers can fall back to the phone's TTS.
   * [key] identifies what is playing; [stop] clears it even while synthesis is still running,
   * and audio that arrives after a stop is dropped.
   */
  suspend fun speak(
    context: Context,
    viewModel: MainViewModel,
    partnerId: String,
    text: String,
    voiceOverride: JSONObject? = null,
    key: String = "preview",
  ): Boolean {
    stop()
    _playingKey.value = key
    val params = JSONObject().put("partnerId", partnerId).put("text", text)
    if (voiceOverride != null) params.put("voice", voiceOverride)
    val response =
      try {
        JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerTtsSynthesize, params.toString(), timeoutMs = 180_000))
      } catch (e: Exception) {
        if (_playingKey.value == key) _playingKey.value = null
        if (e.message?.contains("No PC voice") == true) return false
        throw e
      }
    // Stopped (or replaced by another message) while the PC was still synthesizing.
    if (_playingKey.value != key) return true
    play(context, response.optString("audioBase64"))
    return true
  }

  /**
   * Plays audio the caller already fetched (voice-studio previews and design drafts). Call
   * [markPending] before a slow request so the UI can show "stop" while the PC works.
   */
  fun playBase64(context: Context, audioBase64: String, key: String) {
    if (_playingKey.value != key) return
    play(context, audioBase64)
  }

  fun markPending(key: String) {
    stop()
    _playingKey.value = key
  }

  private fun play(context: Context, audioBase64: String) {
    val audio = Base64.decode(audioBase64, Base64.DEFAULT)
    val file = File(context.cacheDir, "partner-voice.wav")
    file.writeBytes(audio)
    player =
      MediaPlayer().apply {
        setDataSource(file.absolutePath)
        setOnCompletionListener {
          it.release()
          if (player === it) {
            player = null
            _playingKey.value = null
          }
        }
        prepare()
        start()
      }
  }

  fun stop() {
    player?.let {
      runCatching { it.stop() }
      it.release()
    }
    player = null
    _playingKey.value = null
  }
}
