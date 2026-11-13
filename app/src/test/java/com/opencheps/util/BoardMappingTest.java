package com.opencheps.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class BoardMappingTest {
    @Test public void arrowsAndRecognitionShareCalibratedCaptureSquare() {
        BoardArea saved = BoardArea.fromPixels(20, 600, 1040, 1080, 2400, 0);
        BoardArea.Pixels crop = saved.toPixels(360, 800, 0);
        BoardMapping mapping = new BoardMapping(crop.left, crop.top, crop.side, 3, 3);
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                float[] point = mapping.squareCenter(file, rank, false);
                assertEquals((crop.left + (file + 0.5f) * crop.side / 8) * 3, point[0], 0.001);
                assertEquals((crop.top + (rank + 0.5f) * crop.side / 8) * 3, point[1], 0.001);
            }
        }
        assertEquals(20, mapping.left, 2); // At most one downscaled pixel of rounding.
        assertEquals(600, mapping.top, 2);
    }

    @Test public void blackOrientationRotatesBothEndpointsWithinSameBounds() {
        BoardMapping mapping = new BoardMapping(10, 200, 320, 3.01f, 3f);
        assertArrayEquals(mapping.squareCenter(0, 7, false), mapping.squareCenter(7, 0, true), 0.001f);
        assertArrayEquals(mapping.squareCenter(4, 6, false), mapping.squareCenter(3, 1, true), 0.001f);
    }

    @Test public void movingBoardRebuildsArrowCoordinates() {
        float[] before = new BoardMapping(10, 100, 320, 3, 3).squareCenter(4, 6, false);
        float[] after = new BoardMapping(20, 150, 320, 3, 3).squareCenter(4, 6, false);
        assertEquals(30, after[0] - before[0], 0.001);
        assertEquals(150, after[1] - before[1], 0.001);
    }
}
