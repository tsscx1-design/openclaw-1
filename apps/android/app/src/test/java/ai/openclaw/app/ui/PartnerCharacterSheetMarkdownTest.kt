package ai.openclaw.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerCharacterSheetMarkdownTest {
  @Test
  fun freeTextSurvivesTheCategoryEditorRoundTrip() {
    val markdown =
      """
      # ラファエル 詳細キャラクター設定（9大属性シート）

      ## 【4. コミュニケーションの癖・話し方】
      - **一人称**: 私

      ## 【10. 自由記述】
      ### セリフ例
      「告。解析が完了しました」
      """.trimIndent()

    val map = parseCharacterSheetToMap(markdown)
    assertEquals("私", map["firstPerson"] ?: map.values.first { it == "私" })
    assertTrue(map[FREE_TEXT_KEY]!!.contains("### セリフ例"))

    val rebuilt = buildMarkdownFromMap("ラファエル", map)
    assertTrue(rebuilt.contains("## 【10. 自由記述】"))
    assertTrue(rebuilt.contains("「告。解析が完了しました」"))
    assertEquals(map[FREE_TEXT_KEY], parseCharacterSheetToMap(rebuilt)[FREE_TEXT_KEY])
  }

  @Test
  fun sheetWithoutFreeTextAddsNoSection() {
    val rebuilt = buildMarkdownFromMap("ルナ", mapOf("race" to "AI"))
    assertFalse(rebuilt.contains("自由記述"))
  }
}
