package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * Takes the pieces that are ready from a reply that is still being written: every finished sentence
 * after [from], and, while nothing has been queued yet, a long first sentence cut at a comma so the
 * voice can start sooner. Returns the pieces and the new position (just after the last piece).
 */
internal fun readyPieces(text: String, from: Int, firstQueued: Boolean, firstCut: Int = 24): Pair<List<String>, Int> {
  val pieces = mutableListOf<String>()
  var position = from.coerceAtMost(text.length)
  var queued = firstQueued
  while (true) {
    val end = text.indexOfAny(charArrayOf('。', '！', '？', '!', '?', '\n'), position)
    if (end >= 0) {
      var stop = end + 1
      while (stop < text.length && text[stop] in "。！？!?」』）)") stop++
      val piece = text.substring(position, stop).trim()
      if (piece.isNotEmpty()) pieces += piece
      queued = queued || piece.isNotEmpty()
      position = stop
      continue
    }
    // No finished sentence yet: before the first piece, a long run is cut at its last comma.
    if (!queued) {
      val run = text.substring(position)
      val comma = run.lastIndexOfAny(charArrayOf('、', '，', ','))
      if (comma + 1 >= firstCut) {
        pieces += run.substring(0, comma + 1).trim()
        position += comma + 1
      }
    }
    break
  }
  return pieces to position
}

/**
 * Splits a reply into pieces to read aloud one after another. The first piece is short (one
 * sentence, cut at a comma when it is long) so the voice can start as soon as it is made; the rest
 * are grouped up to [maxLength] characters so the PC is asked fewer times.
 */
internal fun splitSpeechChunks(text: String, firstMax: Int = 48, maxLength: Int = 110): List<String> {
  val clean = text.trim()
  if (clean.isEmpty()) return emptyList()
  val sentences = Regex("[^。！？!?\\n]+[。！？!?]*").findAll(clean).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
  if (sentences.isEmpty()) return listOf(clean)
  val chunks = mutableListOf<String>()
  var rest = sentences
  // First piece: the first sentence, cut at a comma if it runs long.
  val first = sentences.first()
  if (first.length > firstMax) {
    val cut = first.take(firstMax).lastIndexOfAny(charArrayOf('、', '，', ','))
    if (cut > 8) {
      chunks += first.substring(0, cut + 1)
      rest = listOf(first.substring(cut + 1).trim()).filter { it.isNotEmpty() } + sentences.drop(1)
    } else {
      chunks += first
      rest = sentences.drop(1)
    }
  } else {
    chunks += first
    rest = sentences.drop(1)
  }
  // The others: grouped while they fit.
  var current = ""
  for (sentence in rest) {
    if (current.isNotEmpty() && current.length + sentence.length > maxLength) {
      chunks += current
      current = ""
    }
    current += sentence
  }
  if (current.isNotEmpty()) chunks += current
  return chunks
}

/**
 * Plays character voices synthesized on the PC (VOICEVOX / AivisSpeech / Qwen3-TTS voice clone)
 * through the Gateway.
 */
internal object PartnerVoicePlayer {
  private var player: MediaPlayer? = null
  /** The piece being played in [speakInPieces]; resumed with false when [stop] is called. */
  private var waiter: CancellableContinuation<Boolean>? = null
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
    val chunks = splitSpeechChunks(text)
    if (chunks.size > 1) return speakInPieces(context, viewModel, partnerId, chunks, voiceOverride, key)
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
   * Reads a reply aloud while it is still being written. [feed] gets the text so far; every finished
   * sentence is made on the PC and played in order (the next is made while one plays). [finish] gets
   * the final text and queues what is left. When the partner has no PC voice, [noPcVoice] turns true
   * and the caller reads the final text with the phone's voice instead.
   */
  class SpeechStream internal constructor(
    private val context: Context,
    private val viewModel: MainViewModel,
    private val partnerId: String,
    private val key: String,
    scope: CoroutineScope,
  ) {
    private val pieces = Channel<String>(Channel.UNLIMITED)
    private val audio = Channel<String>(1)
    private var consumed = 0
    private var firstQueued = false
    private var finished = false
    private val spoken = StringBuilder()

    @Volatile var noPcVoice = false
      private set

    private val synth: Job
    private val play: Job

    init {
      stop()
      _playingKey.value = key
      synth =
        scope.launch {
          var index = 0
          try {
            for (piece in pieces) {
              if (_playingKey.value != key) break
              val data =
                try {
                  synthesizePiece(viewModel, partnerId, piece, null)
                } catch (e: Exception) {
                  if (e is kotlinx.coroutines.CancellationException) throw e
                  if (index == 0 && e.message?.contains("No PC voice") == true) {
                    noPcVoice = true
                    break
                  }
                  throw e
                }
              index++
              audio.send(data)
            }
          } finally {
            audio.close()
          }
        }
      play =
        scope.launch {
          try {
            for (data in audio) {
              if (_playingKey.value != key) break
              if (!playAndWait(context, data)) break
            }
          } finally {
            if (_playingKey.value == key) _playingKey.value = null
          }
        }
    }

    /** The reply so far. */
    fun feed(text: String) {
      if (finished || noPcVoice) return
      val (ready, position) = readyPieces(text, consumed, firstQueued)
      for (piece in ready) {
        pieces.trySend(piece)
        spoken.append(piece)
        firstQueued = true
      }
      consumed = position
    }

    /**
     * The finished reply. Returns true when the voice is handling everything (what was already
     * read is the start of the final text); false when the caller should read the final text itself.
     */
    fun finish(finalText: String): Boolean {
      if (finished) return !noPcVoice
      finished = true
      if (noPcVoice) {
        pieces.close()
        return false
      }
      val clean = finalText.trim()
      val before = spoken.toString().filterNot { it.isWhitespace() }
      val cleanNoSpace = clean.filterNot { it.isWhitespace() }
      if (before.isNotEmpty() && !cleanNoSpace.startsWith(before)) {
        // The finished text differs from what was read (it was rewritten): start over with it.
        pieces.close()
        stop()
        return false
      }
      // What is left is what comes after the part already queued.
      var skip = 0
      var counted = 0
      while (skip < clean.length && counted < before.length) {
        if (!clean[skip].isWhitespace()) counted++
        skip++
      }
      for (piece in splitSpeechChunks(clean.substring(skip))) pieces.trySend(piece)
      pieces.close()
      return true
    }

    /** Stops reading (the next message, or the stop button). */
    fun cancel() {
      pieces.close()
      if (_playingKey.value == key) stop()
    }
  }

  fun startStream(context: Context, viewModel: MainViewModel, partnerId: String, key: String, scope: CoroutineScope): SpeechStream =
    SpeechStream(context, viewModel, partnerId, key, scope)

  /** One piece of the reply, synthesized on the PC. */
  private suspend fun synthesizePiece(viewModel: MainViewModel, partnerId: String, piece: String, voiceOverride: JSONObject?): String {
    val params = JSONObject().put("partnerId", partnerId).put("text", piece)
    if (voiceOverride != null) params.put("voice", voiceOverride)
    return JSONObject(viewModel.runGatewayRpc(GatewayMethod.PartnerTtsSynthesize, params.toString(), timeoutMs = 180_000)).optString("audioBase64")
  }

  /**
   * Reads a long reply in pieces: the first one is made and played at once, and while a piece
   * plays the next is already being made, so the voice starts after one sentence, not the whole reply.
   */
  private suspend fun speakInPieces(
    context: Context,
    viewModel: MainViewModel,
    partnerId: String,
    pieces: List<String>,
    voiceOverride: JSONObject?,
    key: String,
  ): Boolean {
    stop()
    _playingKey.value = key
    return try {
      coroutineScope {
        var next = async { synthesizePiece(viewModel, partnerId, pieces[0], voiceOverride) }
        for (index in pieces.indices) {
          val audio =
            try {
              next.await()
            } catch (e: Exception) {
              if (e is kotlinx.coroutines.CancellationException) throw e
              if (index == 0 && e.message?.contains("No PC voice") == true) return@coroutineScope false
              throw e
            }
          // Stopped (or replaced by another message) while the PC was still synthesizing.
          if (_playingKey.value != key) return@coroutineScope true
          if (index + 1 < pieces.size) next = async { synthesizePiece(viewModel, partnerId, pieces[index + 1], voiceOverride) }
          if (!playAndWait(context, audio) || _playingKey.value != key) {
            next.cancel()
            return@coroutineScope true
          }
        }
        true
      }
    } finally {
      if (_playingKey.value == key) _playingKey.value = null
    }
  }

  /** Plays one piece and returns when it ends (true) or when it is stopped (false). */
  private suspend fun playAndWait(context: Context, audioBase64: String): Boolean =
    suspendCancellableCoroutine { continuation ->
      waiter = continuation
      try {
        val file = File(context.cacheDir, "partner-voice-piece.wav")
        file.writeBytes(Base64.decode(audioBase64, Base64.DEFAULT))
        player =
          MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
              it.release()
              if (player === it) player = null
              if (waiter === continuation) waiter = null
              if (continuation.isActive) continuation.resume(true)
            }
            prepare()
            start()
          }
      } catch (e: Exception) {
        if (waiter === continuation) waiter = null
        if (continuation.isActive) continuation.resume(false)
      }
      continuation.invokeOnCancellation { stopPlayerOnly() }
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

  private fun stopPlayerOnly() {
    player?.let {
      runCatching { it.stop() }
      it.release()
    }
    player = null
  }

  fun stop() {
    stopPlayerOnly()
    waiter?.let { if (it.isActive) it.resume(false) }
    waiter = null
    _playingKey.value = null
  }
}
