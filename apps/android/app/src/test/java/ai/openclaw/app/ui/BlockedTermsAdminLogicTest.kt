package ai.openclaw.app.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockedTermsAdminLogicTest {
  private val config =
    JSONObject(
      """
      {
        "blockedTermsJa": {"enabled": true, "terms": ["a", "b"]},
        "blockedBodyModifications": {"enabled": false, "ja": ["c"], "enPatterns": ["d+"]},
        "negativePromptTerms": {"imageNegative": {"terms": ["n1"]}},
        "characterRules": {"adultsOnly": {"enabled": false}},
        "sexualTerms": {"enabled": true, "terms": ["nsfw"], "patterns": ["ex+"]},
        "blockedAges": {"enabled": true, "from": 1, "to": 20}
      }
      """.trimIndent(),
    )

  @Test
  fun readsEverySectionOfTheFileIncludingNestedOnes() {
    val byLabel = BLOCKED_TERMS_SECTIONS.associateBy { it.label }
    assertEquals(listOf("a", "b"), blockedTermsListOf(config, byLabel.getValue("日本語の禁止語句")))
    assertEquals(listOf("c"), blockedTermsListOf(config, byLabel.getValue("身体変更の禁止語句（日本語）")))
    assertEquals(listOf("d+"), blockedTermsListOf(config, byLabel.getValue("身体変更の禁止パターン（英語）")))
    assertEquals(listOf("n1"), blockedTermsListOf(config, byLabel.getValue("ネガティブタグ（画像）")))
    assertEquals(emptyList<String>(), blockedTermsListOf(config, byLabel.getValue("ネガティブタグ（動画）")))
    assertEquals(emptyList<String>(), blockedTermsListOf(null, byLabel.getValue("日本語の禁止語句")))
    assertEquals(listOf("nsfw"), blockedTermsListOf(config, byLabel.getValue("性的な語句（成人向けの判定）")))
    assertEquals(listOf("ex+"), blockedTermsListOf(config, byLabel.getValue("性的なパターン（成人向けの判定・正規表現）")))
  }

  @Test
  fun anEmptyFileStillShowsEverySectionAsEmpty() {
    val empty = JSONObject("{}")
    BLOCKED_TERMS_SECTIONS.forEach { assertEquals(emptyList<String>(), blockedTermsListOf(empty, it)) }
    BLOCKED_TERMS_RULES.forEach { assertTrue(blockedTermsEnabled(empty, it.path)) }
  }

  @Test
  fun enabledFollowsTheFileAndDefaultsToOn() {
    assertTrue(blockedTermsEnabled(config, "blockedTermsJa"))
    assertFalse(blockedTermsEnabled(config, "blockedBodyModifications"))
    assertFalse(blockedTermsEnabled(config, "characterRules.adultsOnly"))
    assertTrue(blockedTermsEnabled(config, "characterRules.forbidPetite"))
  }

  @Test
  fun theAgeRangeIsReadFromTheFileAndSwitchedByEnabled() {
    assertEquals("1", blockedTermsAgeText(config, "from"))
    assertEquals("20", blockedTermsAgeText(config, "to"))
    assertEquals("", blockedTermsAgeText(JSONObject("{}"), "from"))
    assertTrue(blockedTermsAgesOn(config))
    assertFalse(blockedTermsAgesOn(JSONObject("{}")))
    assertFalse(blockedTermsAgesOn(JSONObject("""{"blockedAges": {"enabled": false, "from": 1, "to": 20}}""")))
  }

  @Test
  fun theAgeFieldTakesAnyNumberAndOnlyChecksThatItIsOne() {
    assertEquals(7L, blockedTermsNumber("7"))
    assertEquals(-5L, blockedTermsNumber(" -5 "))
    assertEquals(0L, blockedTermsNumber("0"))
    assertEquals(3.5, blockedTermsNumber("3.5"))
    assertEquals(150L, blockedTermsNumber("150"))
    assertEquals(1_000_000_000_000L, blockedTermsNumber("1000000000000"))
    assertNull(blockedTermsNumber(""))
    assertNull(blockedTermsNumber("abc"))
    assertNull(blockedTermsNumber("NaN"))
  }
}
