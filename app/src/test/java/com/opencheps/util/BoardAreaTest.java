package com.opencheps.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class BoardAreaTest {
    @Test public void normalizedRoundTripRetainsAllEdges() {
        BoardArea area = BoardArea.fromPixels(40, 480, 1000, 1080, 2340, 0);
        assertNotNull(area);
        BoardArea decoded = BoardArea.decode(area.encode());
        assertNotNull(decoded);
        BoardArea.Pixels pixels = decoded.toPixels(1080, 2340, 0);
        assertEquals(40, pixels.left);
        assertEquals(480, pixels.top);
        assertEquals(1000, pixels.side);
    }

    @Test public void proportionalResolutionChangesAndCaptureRoundingAreSafe() {
        BoardArea area = BoardArea.fromPixels(0, 700, 1344, 1344, 2992, 0);
        BoardArea.Pixels half = area.toPixels(672, 1496, 0);
        assertEquals(350, half.top);
        assertEquals(672, half.side);
        BoardArea.Pixels capture = area.toPixels(359, 800, 0);
        assertNotNull(capture);
        assertTrue(capture.left >= 0 && capture.left + capture.side <= 359);
        assertTrue(capture.top >= 0 && capture.top + capture.side <= 800);
    }

    @Test public void rotationAndChangedAspectNeedRecalibration() {
        BoardArea area = BoardArea.fromPixels(10, 200, 1000, 1080, 2340, 0);
        assertNull(area.toPixels(2340, 1080, 1));
        assertNull(area.toPixels(1080, 2340, 2));
        assertNull(area.toPixels(1080, 1920, 0));
        assertNotNull(area.toPixels(1080, 2340, 0));
    }

    @Test public void invalidAndCorruptValuesAreRejected() {
        String[] invalid = {null, "", "garbage", "2,0,0,1,1,1,0", "1,NaN,0,1,1,1,0",
                "1,0,0,Infinity,1,1,0", "1,0,0,1,1,NaN,0", "1,-0.1,0,1,1,1,0",
                "1,0,0,1.1,1,1,0", "1,0,0,0,1,1,0", "1,0,0,1,1,1,4",
                "1,0,0,0.5,1,1,0", "1,0,0,1,1,-1,0", "1,0,0,1,1,1,0,"};
        for (String value : invalid) assertNull(value, BoardArea.decode(value));
        assertNull(BoardArea.fromPixels(-1, 0, 100, 1000, 2000, 0));
        assertNull(BoardArea.fromPixels(1, 0, 1000, 1000, 2000, 0));
        assertNull(BoardArea.fromPixels(0, 1500, 600, 1000, 2000, 0));
        assertNull(BoardArea.fromPixels(0, 0, 0, 1000, 2000, 0));
        assertNull(BoardArea.fromPixels(0, 0, 8, 0, 2000, 0));
        assertNull(BoardArea.fromPixels(Integer.MAX_VALUE, 0, 100, 1000, 2000, 0));
    }

    @Test public void edgeBoundsRemainWithinEveryRoundedCapture() {
        for (int width = 320; width <= 1600; width += 17) {
            int height = width * 2 + 1;
            BoardArea area = BoardArea.fromPixels(0, height - width, width, width, height, 0);
            int capWidth = Math.round(width * 800f / height);
            BoardArea.Pixels pixels = area.toPixels(capWidth, 800, 0);
            assertNotNull(pixels);
            assertTrue(pixels.left >= 0 && pixels.top >= 0);
            assertTrue(pixels.left + pixels.side <= capWidth);
            assertTrue(pixels.top + pixels.side <= 800);
        }
    }
}
