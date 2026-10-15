package ai.openclaw.app.ui

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * モーションコミック用の音声プレイヤー。
 * TTS合成済み音声（WAV/Base64）を再生しながら、経過時間に応じた
 * 口パク形状（リップシンク）の推定値を提供する。
 *
 * 口パクのアルゴリズム:
 * - 日本語のモーラ（拍）は平均約0.1〜0.15秒
 * - テキストから母音/子音を推定し、再生経過時間に合わせてマウス形状を変化させる
 * - あいうえお の5段階 + 閉口（閉じた口）を交互に切り替え
 */
internal object MotionComicVoicePlayer {

    private var player: MediaPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    private val _mouthShape = MutableStateFlow(MouthShape.CLOSED)
    private val _currentCharIndex = MutableStateFlow(0)

    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    val mouthShape: StateFlow<MouthShape> = _mouthShape.asStateFlow()
    val currentCharIndex: StateFlow<Int> = _currentCharIndex.asStateFlow()

    /** 口パク形状: 日本語の母音に対応 */
    enum class MouthShape(val openness: Float) {
        CLOSED(0.0f),  // 閉じた口
        A(1.0f),       // あ（大きく開く）
        I(0.3f),       // い（横に引く）
        U(0.5f),       // う（すぼめる）
        E(0.7f),       // え（やや開く）
        O(0.8f),       // お（丸く開く）
        BREATH(0.2f),  // 息（喘ぎ声）
    }

    /**
     * テキストから口パクシーケンスを生成する
     * （各文字 → 推定口形 → 表示時間 ms）
     */
    private fun buildLipSyncTimings(text: String, durationMs: Long): List<Pair<MouthShape, Long>> {
        // 表示可能な文字（セリフ本体）を抽出
        val chars = text.replace(Regex("（[^）]*）|\\([^)]*\\)"), "")
            .replace(Regex("[\\s\\r\\n]+"), "")
            .toList()
        if (chars.isEmpty()) return emptyList()

        // 1文字あたりの平均時間（ただし句読点・記号は短め）
        val avgMs = durationMs / chars.size.coerceAtLeast(1)

        return chars.map { c ->
            val shape = when {
                // 喘ぎ絵文字（サロゲートペアなので高サロゲートコードで判定）
                c.isHighSurrogate() -> MouthShape.BREATH
                // 句読点・記号は閉口
                c in "。、！？!?…ー～・　" -> MouthShape.CLOSED
                // 母音で終わる文字
                c in "あかさたなはまやらわがざだばぱ" -> MouthShape.A
                c in "いきしちにひみりゐぎじぢびぴ" -> MouthShape.I
                c in "うくすつぬふむゆるぐずづぶぷ" -> MouthShape.U
                c in "えけせてねへめれゑげぜでべぺ" -> MouthShape.E
                c in "おこそとのほもよろをごぞどぼぽ" -> MouthShape.O
                c in "ん" -> MouthShape.CLOSED
                // カタカナ
                c in "アカサタナハマヤラワガザダバパ" -> MouthShape.A
                c in "イキシチニヒミリギジヂビピ" -> MouthShape.I
                c in "ウクスツヌフムユルグズヅブプ" -> MouthShape.U
                c in "エケセテネヘメレゲゼデベペ" -> MouthShape.E
                c in "オコソトノホモヨロヲゴゾドボポ" -> MouthShape.O
                c in "ン" -> MouthShape.CLOSED
                // 漢字などは中間値
                else -> MouthShape.A
            }
            // 句読点は短時間、通常は平均時間
            val ms = if (shape == MouthShape.CLOSED && c in "。、") avgMs / 2 else avgMs
            shape to ms
        }
    }

    /**
     * 音声（Base64 WAV）を再生しながら口パク同期を開始する。
     * [text] はセリフ本文（口パクタイミング生成に使用）。
     * [durationEstimateMs] は推定再生時間（サーバー提供のseedから計算、デフォルト2秒）。
     */
    suspend fun speakWithLipSync(
        context: Context,
        audioBase64: String,
        text: String,
        durationEstimateMs: Long = 2000L,
        onCharIndex: (Int) -> Unit = {},
    ) {
        stop()
        _isPlaying.value = true
        _mouthShape.value = MouthShape.CLOSED
        _currentCharIndex.value = 0

        // ファイルに書き出して MediaPlayer で再生
        val audio = try { Base64.decode(audioBase64, Base64.DEFAULT) } catch (e: Exception) {
            _isPlaying.value = false
            return
        }

        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "motion-comic-voice.wav")
            file.writeBytes(audio)

            val latch = java.util.concurrent.CountDownLatch(1)
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { latch.countDown() }
                setOnErrorListener { _, _, _ -> latch.countDown(); true }
                prepare()
                start()
            }
            player = mp

            // 口パクシーケンスを並行実行
            val actualDuration = mp.duration.toLong().takeIf { it > 0 } ?: durationEstimateMs
            val timings = buildLipSyncTimings(text, actualDuration)
            var elapsed = 0L
            var charIdx = 0
            for ((shape, ms) in timings) {
                if (!_isPlaying.value) break
                _mouthShape.value = shape
                _currentCharIndex.value = charIdx
                onCharIndex(charIdx)
                charIdx++
                delay(ms)
                elapsed += ms
                // 次の文字の前に一瞬閉じる（自然な口パク）
                if (shape != MouthShape.CLOSED && ms > 80) {
                    _mouthShape.value = MouthShape.CLOSED
                    delay(30)
                }
            }
            _mouthShape.value = MouthShape.CLOSED

            // 音声終了まで待機
            latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
            mp.release()
            if (player === mp) player = null
        }
        _isPlaying.value = false
    }

    fun stop() {
        _isPlaying.value = false
        _mouthShape.value = MouthShape.CLOSED
        player?.runCatching { stop(); release() }
        player = null
    }
}
