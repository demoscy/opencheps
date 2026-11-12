package com.opencheps.util;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class PositionEvaluationTest {
    @Test public void positiveAlwaysFavorsSelectedColorAcrossAlternatingTurns() {
        PositionEvaluation advantage=new PositionEvaluation(125,false,Collections.emptyList());
        assertEquals("+1.25",advantage.score(Side.WHITE,Side.WHITE));
        assertEquals("-1.25",advantage.score(Side.BLACK,Side.WHITE));
        assertEquals("+1.25",advantage.score(Side.BLACK,Side.BLACK));
        assertEquals("-1.25",advantage.score(Side.WHITE,Side.BLACK));
        assertEquals("+0.00",new PositionEvaluation(0,false,Collections.emptyList()).score(Side.BLACK,Side.WHITE));
    }
    @Test public void mateSignDistanceAndTerminalZeroRetainTheSelectedPerspective() {
        PositionEvaluation winning=new PositionEvaluation(99997,true,Arrays.asList("a1a2","h8h7","a2a3","h7h6","a3a4"));
        assertEquals("M3",winning.score(Side.WHITE,Side.WHITE));
        assertEquals("-M3",winning.score(Side.WHITE,Side.BLACK));
        assertEquals(5,winning.matePlies());assertTrue(winning.completeMateLine());
        PositionEvaluation losing=new PositionEvaluation(-99998,true,Arrays.asList("a1a2","h8h7","a2a3","h7h6"));
        assertEquals(4,losing.matePlies());assertTrue(losing.completeMateLine());
        assertEquals("M2",losing.score(Side.WHITE,Side.BLACK));
        PositionEvaluation checkmate=new PositionEvaluation(-100000,true,Collections.emptyList());
        assertEquals("-M0",checkmate.score(Side.WHITE,Side.WHITE));
        assertEquals("M0",checkmate.score(Side.WHITE,Side.BLACK));
        assertFalse(checkmate.hasMateLine());
    }
    @Test public void mateLineCanExceedThreeMovesAndIncompleteLinesAreIdentified() {
        PositionEvaluation mate=new PositionEvaluation(99996,true,Arrays.asList("a7a8q","h8h7","a8a7","h7h6","a7a6","h6h5","a6a5"));
        assertEquals(7,mate.matePlies());assertTrue(mate.completeMateLine());
        assertTrue(mate.mateLine(Side.WHITE).endsWith("W a6→a5"));
        assertTrue(mate.mateLine(Side.WHITE).startsWith("W a7→a8=Q"));
        PositionEvaluation shortLine=new PositionEvaluation(99996,true,Collections.singletonList("a7a8q"));
        assertFalse(shortLine.completeMateLine());
        assertTrue(shortLine.hasMateLine());
    }
}
