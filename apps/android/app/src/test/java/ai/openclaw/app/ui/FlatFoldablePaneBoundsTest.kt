package ai.openclaw.app.ui

import android.graphics.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.window.layout.FoldingFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FlatFoldablePaneBoundsTest {
  private val density = Density(1f)
  private val host = IntRect(0, 0, 840, 700)
  private val flatHinge = testFold(Rect(420, 0, 420, 700), separating = false, state = FoldingFeature.State.FLAT)

  @Test
  fun fullyOpenedFoldableSplitsIntoListAndDetail() {
    val panes = flatFoldablePaneBounds(host, listOf(flatHinge), LayoutDirection.Ltr, density)!!
    assertEquals(IntRect(0, 0, 336, 700), panes.start)
    assertEquals(IntRect(336, 0, 840, 700), panes.end)
  }

  @Test
  fun rtlPutsTheListOnTheRight() {
    val panes = flatFoldablePaneBounds(host, listOf(flatHinge), LayoutDirection.Rtl, density)!!
    assertEquals(IntRect(504, 0, 840, 700), panes.start)
    assertEquals(IntRect(0, 0, 504, 700), panes.end)
  }

  @Test
  fun phonesTabletsAndHalfOpenedPosturesKeepTheirLayouts() {
    assertNull(flatFoldablePaneBounds(host, emptyList(), LayoutDirection.Ltr, density))
    assertNull(flatFoldablePaneBounds(IntRect(0, 0, 400, 800), listOf(flatHinge), LayoutDirection.Ltr, density))
    val halfOpened = testFold(Rect(420, 0, 420, 700), state = FoldingFeature.State.HALF_OPENED)
    assertNull(flatFoldablePaneBounds(host, listOf(halfOpened), LayoutDirection.Ltr, density))
  }
}
