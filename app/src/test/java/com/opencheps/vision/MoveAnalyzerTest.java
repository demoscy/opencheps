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
    @Test public void bothSidesAndBothOrientationsRecognizeEitherCastle() {
        for (Side side : Side.values()) for (boolean kingSide : new boolean[]{true,false}) {
            char[][] before = PositionRightsTest.rooks(), after = PositionRightsTest.copy(before);
            int row = side == Side.WHITE ? 7 : 0;
            after[row][4] = after[row][kingSide ? 7 : 0] = '.';
            after[row][kingSide ? 6 : 2] = side == Side.WHITE ? 'K' : 'k';
            after[row][kingSide ? 5 : 3] = side == Side.WHITE ? 'R' : 'r';
            for (boolean flip : new boolean[]{false,true}) {
                MoveAnalyzer.Result result = MoveAnalyzer.analyze(flip ? FenBuilder.flip(before) : before,
                        flip ? FenBuilder.flip(after) : after);
                assertEquals(MoveAnalyzer.Kind.CASTLE,result.kind);
                assertEquals(side,result.mover);
            }
        }
    }

    @Test public void specialMovesMustHaveTheCorrectSquaresRatherThanJustPieceCounts() {
        char[][] before = PositionRightsTest.rooks(), after = PositionRightsTest.copy(before);
        after[7][4] = after[7][7] = '.'; after[6][4] = 'K'; after[6][7] = 'R';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN,MoveAnalyzer.analyze(before,after).kind);
        before = PositionRightsTest.board("....k...","........","........","...pP...","........","........","........","....K...");
        after = PositionRightsTest.copy(before);
        after[3][3] = after[3][4] = '.'; after[1][3] = 'P';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN,MoveAnalyzer.analyze(before,after).kind);
        after[1][3] = '.'; after[2][3] = 'P';
        assertEquals(MoveAnalyzer.Kind.EN_PASSANT,MoveAnalyzer.analyze(before,after).kind);
    }
}
