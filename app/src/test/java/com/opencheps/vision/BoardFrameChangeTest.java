package com.opencheps.vision;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class BoardFrameChangeTest {
    private final BoardDetector.Board board = new BoardDetector.Board(10, 10, 160, 1);
    private final int[] clean = new int[200 * 200];
    private final int[] current = new int[200 * 200];
    private final int[] mask = new int[200 * 200];

    @Test public void stationaryBoardAndSmallColorNoiseDoNotRequestHiddenReads() {
        assertFalse(BoardFrameChange.changed(clean, current, mask, 200, 200, board));
        Arrays.fill(current, 0xFF030303);
        assertFalse(BoardFrameChange.changed(clean, current, mask, 200, 200, board));
    }
    @Test public void moveRequestsCleanReadButOwnArrowAndPanelPixelsDoNot() {
        for (int y = 50; y < 65; y++) for (int x = 40; x < 55; x++) {
            current[y*200+x] = 0xFFFFFFFF;
            mask[y*200+x] = 0xFF000000;
        }
        assertFalse(BoardFrameChange.changed(clean, current, mask, 200, 200, board));
        Arrays.fill(mask, 0);
        assertTrue(BoardFrameChange.changed(clean, current, mask, 200, 200, board));
    }
    @Test public void changesOutsideBoardAreIgnoredAndUnknownReferenceNeedsCleanRead() {
        Arrays.fill(current, 0, 200*5, 0xFFFFFFFF);
        assertFalse(BoardFrameChange.changed(clean, current, mask, 200, 200, board));
        assertTrue(BoardFrameChange.changed(null, current, mask, 200, 200, board));
    }
}
