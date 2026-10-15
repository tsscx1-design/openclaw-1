package ai.openclaw.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunksTest {
  @Test
  fun aShortReplyStaysOnePiece() {
    assertEquals(listOf("こんにちは！"), splitSpeechChunks("こんにちは！"))
    assertEquals(emptyList<String>(), splitSpeechChunks("   "))
  }

  @Test
  fun theFirstPieceIsTheFirstSentenceAndTheRestAreGrouped() {
    val chunks = splitSpeechChunks("おはよう！今日はいい天気だね。散歩に行かない？お弁当も作ろうよ。")
    assertEquals("おはよう！", chunks.first())
    assertEquals(listOf("おはよう！", "今日はいい天気だね。散歩に行かない？お弁当も作ろうよ。"), chunks)
  }

  @Test
  fun aLongFirstSentenceIsCutAtACommaAndNothingIsLost() {
    val text = "今日は朝からずっと雨が降っていて、洗濯物が乾かなくて困っているんだけど、あなたのところはどうなっているのかな。まだ続きがあるよ。"
    val chunks = splitSpeechChunks(text)
    assertTrue(chunks.first().length <= 48)
    assertTrue(chunks.first().endsWith("、"))
    assertEquals(text, chunks.joinToString(""))
  }

  @Test
  fun everyPieceStaysWithinTheLimitWhenSentencesAreShort() {
    val text = (1..30).joinToString("") { "これは${it}番目の文です。" }
    val chunks = splitSpeechChunks(text, firstMax = 48, maxLength = 110)
    assertTrue(chunks.drop(1).all { it.length <= 110 })
    assertEquals(text, chunks.joinToString(""))
  }
}

class ReadyPiecesTest {
  @Test
  fun finishedSentencesAreReadyAndTheRestWaits() {
    val (pieces, position) = readyPieces("おはよう！今日はいい天気だね。散歩に", 0, firstQueued = false)
    assertEquals(listOf("おはよう！", "今日はいい天気だね。"), pieces)
    assertEquals("おはよう！今日はいい天気だね。".length, position)
  }

  @Test
  fun aLongFirstRunIsCutAtItsLastCommaButOnlyBeforeAnythingIsQueued() {
    val text = "今日は朝からずっと雨が降っていて洗濯物が乾かなくて困っているんだけど、あなたのところは"
    val (first, position) = readyPieces(text, 0, firstQueued = false)
    assertEquals(listOf("今日は朝からずっと雨が降っていて洗濯物が乾かなくて困っているんだけど、"), first)
    assertEquals(first.single().length, position)
    val (later, same) = readyPieces(text, position, firstQueued = true)
    assertEquals(emptyList<String>(), later)
    assertEquals(position, same)
  }

  @Test
  fun callingAgainWithMoreTextContinuesFromTheLastPosition() {
    val (a, pa) = readyPieces("やあ！元気", 0, firstQueued = false)
    assertEquals(listOf("やあ！"), a)
    val (b, pb) = readyPieces("やあ！元気にしてた？", pa, firstQueued = true)
    assertEquals(listOf("元気にしてた？"), b)
    assertEquals("やあ！元気にしてた？".length, pb)
  }
}
