package ai.openclaw.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomableImageDialogLogicTest {
  @Test
  fun zoomStaysBetweenOneAndEightTimes() {
    assertEquals(1f, clampZoom(0.2f), 0f)
    assertEquals(1f, clampZoom(1f), 0f)
    assertEquals(3.5f, clampZoom(3.5f), 0f)
    assertEquals(8f, clampZoom(30f), 0f)
  }

  @Test
  fun theImageCanOnlyBeMovedByWhatTheZoomPushesOutsideTheScreen() {
    assertEquals(0f, zoomPanLimit(1000f, 1f), 0f)
    assertEquals(500f, zoomPanLimit(1000f, 2f), 0f)
    assertEquals(1000f, zoomPanLimit(1000f, 3f), 0f)
    assertEquals(0f, zoomPanLimit(0f, 4f), 0f)
  }
}
