package com.opencheps.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class BoardSelectionTest {
    @Test public void movingClampsAllEdgesWithoutResizing() {
        BoardSelection selection = new BoardSelection(100, 200, 400, 1000, 2000, 100);
        selection.moveTo(-100, 5000);
        assertEquals(0, selection.left, 0);
        assertEquals(1600, selection.top, 0);
        assertEquals(400, selection.side, 0);
        selection.moveTo(5000, -100);
        assertEquals(600, selection.left, 0);
        assertEquals(0, selection.top, 0);
    }

    @Test public void everyCornerKeepsItsOppositeAnchorAndSquareShape() {
        for (int corner = 0; corner < 4; corner++) {
            BoardSelection selection = new BoardSelection(200, 400, 400, 1000, 2000, 100);
            double ax = corner == 0 || corner == 3 ? 600 : 200;
            double ay = corner < 2 ? 800 : 400;
            int sx = corner == 0 || corner == 3 ? -1 : 1;
            int sy = corner < 2 ? -1 : 1;
            selection.resize(corner, ax, ay, ax + sx * 500, ay + sy * 700);
            assertEquals(600, selection.side, 0);
            assertEquals(ax, sx < 0 ? selection.left + selection.side : selection.left, 0);
            assertEquals(ay, sy < 0 ? selection.top + selection.side : selection.top, 0);
            assertTrue(selection.left >= 0 && selection.top >= 0);
            assertTrue(selection.left + selection.side <= 1000);
            assertTrue(selection.top + selection.side <= 2000);
        }
    }

    @Test public void shrinkingAcrossAnchorNeverInvertsOrDisappears() {
        BoardSelection selection = new BoardSelection(200, 400, 400, 1000, 2000, 100);
        selection.resize(0, 600, 800, 900, 1500);
        assertEquals(100, selection.side, 0);
        assertEquals(500, selection.left, 0);
        assertEquals(700, selection.top, 0);
    }
}
