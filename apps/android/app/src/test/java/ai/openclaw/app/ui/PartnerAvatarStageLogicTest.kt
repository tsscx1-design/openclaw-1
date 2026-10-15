package ai.openclaw.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerAvatarStageLogicTest {
  @Test
  fun theFrameNamesMatchTheGatewaysSet() {
    assertEquals(17, AVATAR_FRAME_KEYS.size)
    assertTrue("normal_closed" in AVATAR_FRAME_KEYS)
    assertTrue("shy_open" in AVATAR_FRAME_KEYS)
    assertEquals("blink", AVATAR_FRAME_KEYS.last())
  }

  @Test
  fun theEmotionFollowsTheWordsOfTheReply() {
    assertEquals(AvatarEmotion.HAPPY, avatarEmotionOf("やった！ありがとう、嬉しいな 😊"))
    assertEquals(AvatarEmotion.SAD, avatarEmotionOf("ごめんね、悲しい思いをさせちゃった"))
    assertEquals(AvatarEmotion.SURPRISED, avatarEmotionOf("えっ、本当に！？"))
    assertEquals(AvatarEmotion.ANGRY, avatarEmotionOf("いい加減にして！ムカつく 💢"))
    assertEquals(AvatarEmotion.SHY, avatarEmotionOf("えへへ、照れちゃうな"))
    assertEquals(AvatarEmotion.THINKING, avatarEmotionOf("うーん、ちょっと考えるね"))
    assertEquals(AvatarEmotion.NORMAL, avatarEmotionOf("今日は天気がいいですね"))
    assertEquals(AvatarEmotion.NORMAL, avatarEmotionOf(""))
  }

  @Test
  fun theShownFrameIsTheBlinkWhileBlinkingOtherwiseTheExpressionAndMouth() {
    assertEquals("blink", avatarFrameKey(AvatarEmotion.HAPPY, mouthOpen = false, blinking = true))
    assertEquals("happy_open", avatarFrameKey(AvatarEmotion.HAPPY, mouthOpen = true, blinking = false))
    assertEquals("sad_closed", avatarFrameKey(AvatarEmotion.SAD, mouthOpen = false, blinking = false))
  }

  @Test
  fun strokingTheClothesMovesThroughTheSteps() {
    // 胸（0.3）を下へ → 開け始める、横へ ×4 → 上着を脱ぐ、下半身（0.6）を下へ ×6 → ズボン・スカートを脱ぐ、
    // 胸を横へ ×3 → ブラを外す、下半身を下へ ×2 → 下着を脱ぐ（全部で16段階）
    assertEquals(1, undressNext(0, 5f, 120f, 0.3f, 48f))
    for (stage in listOf(1, 2, 3, 4, 11, 12, 13)) assertEquals(stage + 1, undressNext(stage, 120f, 5f, 0.3f, 48f))
    for (stage in listOf(5, 6, 7, 8, 9, 10, 14, 15)) assertEquals(stage + 1, undressNext(stage, 0f, 120f, 0.6f, 48f))
    // 短い・場所や向きが違う・順番が違うときは、進まない
    assertEquals(null, undressNext(0, 0f, 20f, 0.3f, 48f))
    assertEquals(null, undressNext(0, 0f, 120f, 0.1f, 48f))
    assertEquals(null, undressNext(0, 120f, 0f, 0.3f, 48f))
    assertEquals(null, undressNext(4, 0f, 120f, 0.6f, 48f))
    assertEquals(null, undressNext(16, 0f, 120f, 0.6f, 48f))
  }

  @Test
  fun touchingAdultPlacesRaisesTheReaction() {
    assertEquals("blush", arousalTouch(1).face)
    assertEquals("excited", arousalTouch(2).face)
    assertEquals("ahegao", arousalTouch(4).face)
    assertEquals("climax", arousalTouch(9).face)
    assertEquals(listOf("collapse", "climax", "ahegao", "aroused_closed"), avatarFaceFallbacks("collapse"))
  }

  @Test
  fun theRepliesWordsPickTheExtraFace() {
    assertEquals("climax", avatarExtraFaceOf("あっ、いっちゃう…"))
    assertEquals("disgust", avatarExtraFaceOf("うわぁ、気持ち悪い"))
    assertEquals("blush", avatarExtraFaceOf("顔が熱いよ"))
    assertEquals(null, avatarExtraFaceOf("今日はいい天気だね"))
  }
}
