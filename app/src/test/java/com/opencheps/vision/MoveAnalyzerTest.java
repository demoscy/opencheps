package com.opencheps.vision;

import com.opencheps.util.Side;
import org.junit.Test;
import static org.junit.Assert.*;

public class MoveAnalyzerTest {
    private char[][] position() {
        String[] rows = {"rnbqkbnr", "pppppppp", "........", "........", "........", "........", "PPPPPPPP", "RNBQKBNR"};
        char[][] grid = new char[8][];
        for (int r = 0; r < 8; r++) grid[r] = rows[r].toCharArray();
        return grid;
    }

    @Test public void oneMoveStillHandsTurnToTheOpponent() {
        char[][] before = position(), after = position();
        after[6][4] = '.'; after[4][4] = 'P';
        MoveAnalyzer.Result move = MoveAnalyzer.analyze(before, after, Side.WHITE);
        assertEquals(MoveAnalyzer.Kind.QUIET, move.kind);
        assertEquals(Side.WHITE, move.mover);
    }

    @Test public void openingAndFastComputerReplyKeepTheUsersTurn() {
        char[][] before = position(), after = position();
        after[6][4] = '.'; after[4][4] = 'P';
        after[1][4] = '.'; after[3][4] = 'p';
        MoveAnalyzer.Result move = MoveAnalyzer.analyze(before, after, Side.WHITE);
        assertEquals(MoveAnalyzer.Kind.TWO_PLIES, move.kind);
        assertEquals(Side.BLACK, move.mover);
        assertEquals(4, move.changeCount);
    }

    @Test public void twoQuietMovesAlsoWorkWithBlackAtBottom() {
        char[][] before = FenBuilder.flip(position()), after = FenBuilder.flip(position());
        after[1][3] = '.'; after[3][3] = 'P';
        after[6][3] = '.'; after[4][3] = 'p';
        MoveAnalyzer.Result move = MoveAnalyzer.analyze(before, after, Side.BLACK);
        assertEquals(MoveAnalyzer.Kind.TWO_PLIES, move.kind);
        assertEquals(Side.WHITE, move.mover);
    }

    @Test public void unrelatedOrMisclassifiedChangesAreNotTreatedAsAComputerReply() {
        char[][] before = position(), after = position();
        after[6][4] = '.'; after[4][4] = 'Q';
        after[1][4] = '.'; after[3][4] = 'p';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN, MoveAnalyzer.analyze(before, after, Side.WHITE).kind);
        after[4][4] = 'P'; after[7][4] = '.';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN, MoveAnalyzer.analyze(before, after, Side.WHITE).kind);
    }
}
