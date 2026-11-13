package com.opencheps.util;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudyLineTest {
    @Test public void threeMovesForEachSideAreOneAlternatingBranch() {
        assertEquals("W e2→e4   B e7→e5\nW g1→f3   B b8→c6\nW f1→c4   B g8→f6",
                StudyLine.format(Arrays.asList("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "d2d3"), Side.WHITE));
    }
    @Test public void blackToMoveAndPromotionAreShownWithoutFabricatedFutureMoves() {
        assertEquals("B a2→a1=N   W h1→h2", StudyLine.format(Arrays.asList("a2a1n", "h1h2"), Side.BLACK));
        assertEquals("", StudyLine.format(Collections.emptyList(), Side.BLACK));
        assertEquals("W e2→e4", StudyLine.format(Arrays.asList("e2e4", "bad", "e7e5"), Side.WHITE));
    }
}
