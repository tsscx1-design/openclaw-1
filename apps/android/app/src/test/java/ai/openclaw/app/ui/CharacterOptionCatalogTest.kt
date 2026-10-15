package ai.openclaw.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterOptionCatalogTest {
  private val parts = CHARACTER_OPTION_CATALOG.flatMap { it.parts }
  private val glasses = parts.first { part -> part.items.any { it.first == "glasses" } }
  private val eyepatch = parts.first { part -> part.items.any { it.first == "eyepatch_left" } }

  @Test
  fun aPartForOneOnlyKeepsTheLastChoice() {
    assertTrue(eyepatch.single)
    val first = chooseCharacterOption(emptySet(), eyepatch, null, "eyepatch_left")
    assertEquals(setOf("eyepatch_right"), chooseCharacterOption(first, eyepatch, "eyepatch_left", "eyepatch_right"))
    assertEquals(emptySet<String>(), chooseCharacterOption(first, eyepatch, "eyepatch_left", null))
  }

  @Test
  fun aPartForManyKeepsEveryChoiceAndOtherPartsAreUntouched() {
    assertTrue(!glasses.single)
    val chosen = chooseCharacterOption(setOf("eyepatch_left"), glasses, null, "glasses")
    assertEquals(setOf("eyepatch_left", "glasses", "sunglasses"), chooseCharacterOption(chosen, glasses, null, "sunglasses"))
    assertEquals(setOf("eyepatch_left", "sunglasses"), chooseCharacterOption(chosen + "sunglasses", glasses, "glasses", null))
  }

  @Test
  fun notesAreOneLinePerPartAndEmptyOnesAreLeftOut() {
    val notes = mapOf("missing_legs" to " ひざから\n下 ", "glasses" to "  ", "cos_swimsuit" to "赤色", "pubic_hair" to "選んでいない項目")
    val text = lookNotesText(setOf("missing_legs", "glasses", "cos_swimsuit"), notes)
    assertEquals("両足欠損：ひざから 下 / 水着：赤色", text)
  }

  @Test
  fun everyIdIsUnique() {
    val ids = parts.flatMap { it.items }.map { it.first }
    assertEquals(ids.size, ids.toSet().size)
  }
}
