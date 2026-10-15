package ai.openclaw.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceSwapLogicTest {
  @Test
  fun durationsReadNaturally() {
    assertEquals("約45秒", formatSwapDuration(45))
    assertEquals("約12分30秒", formatSwapDuration(750))
    assertEquals("約12分", formatSwapDuration(722))
    assertEquals("約1時間5分", formatSwapDuration(3900))
    assertEquals("約0秒", formatSwapDuration(-3))
  }

  @Test
  fun sourcesKeepOnlyChosenFaces() {
    val rows =
      listOf(
        SwapRow(characterId = "chara-1", position = 0),
        SwapRow(),
        SwapRow(inputId = "in:face.jpg"),
      )
    val maps = swapSourceMaps(rows)
    assertEquals(2, maps.size)
    assertEquals("chara-1", maps[0]["characterId"])
    assertEquals(0, maps[0]["position"])
    assertEquals("in:face.jpg", maps[1]["inputId"])
    assertEquals(false, maps[1].containsKey("position"))
  }

  @Test
  fun secondsParseOnlyPlainNumbers() {
    assertEquals(12.5, parseSeconds(" 12.5 ")!!, 0.0)
    assertNull(parseSeconds(""))
    assertNull(parseSeconds("abc"))
    assertNull(parseSeconds("-3"))
  }
}

class SampleFactoryPagesTest {
  private fun section(title: String, vararg counts: Int) =
    SampleSection(title, counts.mapIndexed { i, n -> "g$i" to List(n) { SampleRowItem("k$title$i$it", "l$it", "g$i") } })

  @Test
  fun pagesSplitTheSectionsAndCountTheItems() {
    val sections = listOf(section("a", 3, 2), section("b", 4), section("c", 1), section("d"))
    assertEquals(listOf(5, 5, 0), pageCounts(sections))
    assertEquals(listOf("b", "c"), pageSections(sections, 1).map { it.title })
    assertEquals(emptyList<SampleSection>(), pageSections(sections, 9))
  }

  @Test
  fun searchKeepsOnlyMatchingRows() {
    val rows = listOf(SampleRowItem("1", "ロングブーツ", "g"), SampleRowItem("2", "ソックス", "g"))
    assertEquals(listOf("1"), filterSampleRows(rows, "ブーツ").map { it.key })
    assertEquals(2, filterSampleRows(rows, "  ").size)
  }
}

class GpuBusyNoticeTest {
  @Test
  fun noNoticeWhenIdle() {
    assertNull(buildGpuNotice(paused = false, running = false, kind = "", stage = "", elapsedSec = 0))
  }

  @Test
  fun pausedChatIsTold() {
    val notice = buildGpuNotice(paused = true, running = true, kind = "faceswap", stage = "顔を入れ替えています（42%）", elapsedSec = 190)!!
    val text = gpuNoticeText(notice)
    assertEquals(true, text.contains("顔入れ替え"))
    assertEquals(true, text.contains("チャットAIを一時停止"))
    assertEquals(true, text.contains("3分経過"))
    assertEquals(true, text.contains("42%"))
  }

  @Test
  fun runningWithoutPauseOnlyShowsProgress() {
    val text = gpuNoticeText(buildGpuNotice(paused = false, running = true, kind = "image", stage = "", elapsedSec = 10)!!)
    assertEquals(true, text.contains("画像生成を作っています"))
    assertEquals(false, text.contains("一時停止"))
  }
}
