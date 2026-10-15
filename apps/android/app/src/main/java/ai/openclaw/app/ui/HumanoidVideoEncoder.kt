package ai.openclaw.app.ui

import android.graphics.BitmapFactory
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import android.util.Base64
import java.io.File
import java.nio.ByteBuffer

/** One bounded export. Frames are acknowledged after queueing; rendering cannot drop frames. */
internal class HumanoidVideoEncoder(private val cacheDir: File) {
  private var codec: MediaCodec? = null
  private var muxer: MediaMuxer? = null
  private var audio: MediaExtractor? = null
  private var videoTrack = -1
  private var audioTrack = -1
  private var started = false
  private var pending: File? = null
  private var expectedFrames = 0
  private var frames = 0
  private var outputEnded = false
  private var audioFormat: MediaFormat? = null

  @Synchronized
  fun begin(seconds: Int, audioFile: File?) {
    check(codec == null) { "書き出し中です" }
    require(seconds in 1..30) { "動画は1〜30秒で指定してください" }
    try {
      expectedFrames = seconds * 30
      frames = 0
      outputEnded = false
      videoTrack = -1
      audioTrack = -1
      started = false
      pending = File.createTempFile("humanoid-video-", ".mp4", cacheDir)
      if (audioFile != null) {
        val extractor = MediaExtractor()
        audio = extractor
        extractor.setDataSource(audioFile.absolutePath)
        val index = (0 until extractor.trackCount).firstOrNull {
          extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
        } ?: error("音声はAAC形式のM4A・MP4を選んでください")
        extractor.selectTrack(index)
        audioFormat = extractor.getTrackFormat(index)
        require((audioFormat?.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: 0) in 1..2) { "音声はモノラルまたはステレオを選んでください" }
      }
      val format = MediaFormat.createVideoFormat("video/avc", 1280, 720).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        setInteger(MediaFormat.KEY_BIT_RATE, 12_000_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)
        setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
        setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
      }
      val encoder = MediaCodec.createEncoderByType("video/avc")
      codec = encoder
      encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      muxer = MediaMuxer(pending!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      encoder.start()
    } catch (error: Exception) {
      cancel()
      throw error
    }
  }

  @Synchronized
  fun frame(encoded: String, index: Int) {
    val encoder = codec ?: error("書き出しが開始されていません")
    require(index == frames && index < expectedFrames) { "映像の順序が正しくありません" }
    require(encoded.length <= 8_000_000) { "映像フレームが大きすぎます" }
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth == 1280 && bounds.outHeight == 720) { "映像サイズが正しくありません" }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("映像フレームを読み込めません")
    try {
      val input = inputIndex(encoder)
      val image = encoder.getInputImage(input) ?: error("この端末の動画エンコーダーは画像入力に対応していません")
      val pixels = IntArray(1280 * 720)
      bitmap.getPixels(pixels, 0, 1280, 0, 0, 1280, 720)
      fillYuv(image, pixels)
      // Buffer size includes the codec's row padding, not just packed width * height.
      val size = encoder.getInputBuffer(input)?.remaining() ?: error("映像入力バッファを取得できません")
      encoder.queueInputBuffer(input, 0, size, frames * 1_000_000L / 30, 0)
      frames++
      drain(false)
    } finally {
      bitmap.recycle()
    }
  }

  private fun inputIndex(encoder: MediaCodec): Int {
    val deadline = SystemClock.elapsedRealtime() + 10_000
    while (SystemClock.elapsedRealtime() < deadline) {
      val index = encoder.dequeueInputBuffer(10_000)
      if (index >= 0) return index
      drain(false)
    }
    error("動画エンコーダーが応答しません。書き出しを中止しました")
  }

  private fun fillYuv(image: Image, pixels: IntArray) {
    val planes = image.planes
    require(planes.size == 3 && image.cropRect.width() == 1280 && image.cropRect.height() == 720) { "この端末の映像入力形式は使えません" }
    val buffers = planes.map { it.buffer }
    val positions = buffers.map { it.position() }
    val rows = planes.map { it.rowStride }
    val strides = planes.map { it.pixelStride }
    fun put(plane: Int, x: Int, y: Int, value: Int) {
      buffers[plane].put(positions[plane] + y * rows[plane] + x * strides[plane], value.coerceIn(0, 255).toByte())
    }
    for (y in 0 until 720 step 2) for (x in 0 until 1280 step 2) {
      var sumR = 0; var sumG = 0; var sumB = 0
      for (dy in 0..1) for (dx in 0..1) {
        val pixel = pixels[(y + dy) * 1280 + x + dx]
        val r = (pixel shr 16) and 255; val g = (pixel shr 8) and 255; val b = pixel and 255
        put(0, x + dx, y + dy, ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
        sumR += r; sumG += g; sumB += b
      }
      val r = sumR / 4; val g = sumG / 4; val b = sumB / 4
      put(1, x / 2, y / 2, ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128)
      put(2, x / 2, y / 2, ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128)
    }
  }

  private fun drain(waitForEnd: Boolean) {
    val encoder = codec ?: error("エンコーダーがありません")
    val writer = muxer ?: error("動画保存先がありません")
    val info = MediaCodec.BufferInfo()
    val deadline = SystemClock.elapsedRealtime() + 10_000
    while (true) {
      when (val index = encoder.dequeueOutputBuffer(info, if (waitForEnd) 10_000 else 0)) {
        MediaCodec.INFO_TRY_AGAIN_LATER -> {
          if (!waitForEnd) return
          check(SystemClock.elapsedRealtime() < deadline) { "動画の仕上げが完了しませんでした" }
        }
        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
          check(!started) { "映像形式が途中で変わりました" }
          videoTrack = writer.addTrack(encoder.outputFormat)
          audioFormat?.let { audioTrack = writer.addTrack(it) }
          writer.start()
          started = true
        }
        else -> if (index >= 0) {
          try {
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
            if (info.size > 0) {
              check(started) { "動画の保存を開始できませんでした" }
              val data = encoder.getOutputBuffer(index) ?: error("映像出力を取得できません")
              data.position(info.offset); data.limit(info.offset + info.size)
              writer.writeSampleData(videoTrack, data, info)
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { outputEnded = true; return }
          } finally {
            encoder.releaseOutputBuffer(index, false)
          }
        }
      }
    }
  }

  @Synchronized
  fun finish(): File {
    try {
      check(frames == expectedFrames) { "映像フレームが不足しています" }
      val encoder = codec ?: error("書き出しが開始されていません")
      val input = inputIndex(encoder)
      encoder.queueInputBuffer(input, 0, 0, frames * 1_000_000L / 30, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
      drain(true)
      check(outputEnded && started) { "動画の仕上げを確認できませんでした" }
      audio?.let { extractor ->
        val buffer = ByteBuffer.allocateDirect(1_000_000)
        val info = MediaCodec.BufferInfo()
        val origin = extractor.sampleTime.coerceAtLeast(0)
        val duration = frames * 1_000_000L / 30
        while (extractor.sampleTime >= 0 && extractor.sampleTime - origin < duration) {
          buffer.clear()
          require(extractor.sampleSize <= buffer.capacity()) { "音声のフレームが大きすぎます" }
          val size = extractor.readSampleData(buffer, 0)
          if (size < 0) break
          require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "暗号化された音声は使えません" }
          info.set(0, size, extractor.sampleTime - origin, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
          buffer.position(0); buffer.limit(size)
          muxer!!.writeSampleData(audioTrack, buffer, info)
          if (!extractor.advance()) break
        }
      }
      muxer!!.stop()
      started = false
      val result = pending ?: error("完成した動画がありません")
      check(result.length() > 0) { "動画が空です" }
      pending = null // The caller now owns the completed artifact.
      release()
      return result
    } catch (error: Exception) {
      cancel()
      throw error
    }
  }

  private fun release() {
    codec?.let { runCatching { it.stop() }; runCatching { it.release() } }; codec = null
    muxer?.let { runCatching { it.release() } }; muxer = null
    audio?.release(); audio = null
    audioFormat = null
    started = false
  }

  @Synchronized
  fun cancel() {
    release()
    pending?.delete(); pending = null
  }
}
