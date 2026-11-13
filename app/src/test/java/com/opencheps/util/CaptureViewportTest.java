package com.opencheps.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureViewportTest {
    @Test public void entireDisplayRetainsItsOrigin() {
        CaptureViewport viewport = CaptureViewport.resolve(1080, 2400, 1080, 2400, 0, 80, 0, 120);
        assertEquals(0, viewport.top);
        assertEquals(2400, viewport.height);
        BoardArea.Pixels crop = viewport.crop(new BoardArea.Pixels(30, 600, 1020), 360, 800);
        assertEquals(10, crop.left);
        assertEquals(200, crop.top);
        assertEquals(340, crop.side);
    }

    @Test public void appOnlyCaptureExcludesBarsWithoutShiftingBoardOrArrows() {
        CaptureViewport viewport = CaptureViewport.resolve(1080, 2400, 1080, 2200, 0, 80, 0, 120);
        assertNotNull(viewport);
        assertEquals(80, viewport.top);
        assertEquals(2200, viewport.height);
        BoardArea.Pixels crop = viewport.crop(new BoardArea.Pixels(30, 620, 1020), 540, 1100);
        assertEquals(15, crop.left);
        assertEquals(270, crop.top);
        BoardMapping map = new BoardMapping(crop.left, crop.top, crop.side, 2, 2, viewport.left, viewport.top);
        assertEquals(30, map.left, 0);
        assertEquals(620, map.top, 0);
        assertEquals(1020, map.width, 0);
        assertEquals(620 + 6.5f * 127.5f, map.squareCenter(4, 6, false)[1], 0.001);
    }

    @Test public void landscapeInsetsAndBlackOrientationUseSameTransform() {
        CaptureViewport viewport = CaptureViewport.resolve(2400, 1080, 2200, 1080, 80, 0, 120, 0);
        BoardArea.Pixels crop = viewport.crop(new BoardArea.Pixels(620, 30, 1020), 1100, 540);
        BoardMapping map = new BoardMapping(crop.left, crop.top, crop.side, 2, 2, viewport.left, viewport.top);
        assertEquals(620, map.left, 0);
        assertEquals(30, map.top, 0);
        assertArrayEquals(map.squareCenter(0, 7, false), map.squareCenter(7, 0, true), 0.001f);
    }

    @Test public void unknownOrFloatingAppGeometryIsRejected() {
        assertNull(CaptureViewport.resolve(1080, 2400, 800, 1800, 0, 80, 0, 120));
        assertNull(CaptureViewport.resolve(1080, 2400, 1080, 2300, 0, 80, 0, 120));
        assertNull(CaptureViewport.resolve(0, 2400, 0, 0, 0, 0, 0, 0));
    }

    @Test public void manualSelectionMustFitSharedAppAndCaptureRoundingStaysInBounds() {
        CaptureViewport viewport = CaptureViewport.resolve(1080, 2400, 1080, 2200, 0, 80, 0, 120);
        assertNull(viewport.crop(new BoardArea.Pixels(0, 0, 1000), 393, 800));
        BoardArea.Pixels edge = viewport.crop(new BoardArea.Pixels(0, 1200, 1080), 393, 800);
        assertNotNull(edge);
        assertTrue(edge.top + edge.side <= 800);
        assertTrue(edge.left + edge.side <= 393);
    }
}
