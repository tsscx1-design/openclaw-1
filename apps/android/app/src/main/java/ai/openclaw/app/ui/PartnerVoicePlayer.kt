package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import org.json.JSONObject
import java.io.File

/** Plays character voices synthesized on the PC (VOICEVOX / AivisSpeech) through the Gateway. */
internal object PartnerVoicePlayer {
  private var player: MediaPlayer? = null

  /**
   * Synthesizes [text] with the partner's PC voice, or with [voiceOverride] for previews.
   * Returns false when no PC voice is configured so callers can fall back to the phone's TTS.
   */
  suspend fun speak(
    context: Context,
    viewModel: MainViewModel,
    partnerId: String,
    text: String,
    voiceOverride: JSONObject? = null,
  ): Boolean {
    val params = JSONObject().put("partnerId", partnerId).put("text", text)
    if (voiceOverride != null) params.put("voice", voiceOverride)
    val response =
      try {
        JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerTtsSynthesize, params.toString(), timeoutMs = 180_000))
      } catch (e: Exception) {
        if (e.message?.contains("No PC voice") == true) return false
        throw e
      }
    val audio = Base64.decode(response.optString("audioBase64"), Base64.DEFAULT)
    val file = File(context.cacheDir, "partner-voice.wav")
    file.writeBytes(audio)
    stop()
    player =
      MediaPlayer().apply {
        setDataSource(file.absolutePath)
        setOnCompletionListener { it.release(); if (player === it) player = null }
        prepare()
        start()
      }
    return true
  }

  fun stop() {
    player?.let {
      runCatching { it.stop() }
      it.release()
    }
    player = null
  }
}
