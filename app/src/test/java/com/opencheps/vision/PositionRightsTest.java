package com.opencheps.vision;

import com.opencheps.util.Side;
import org.junit.Test;
import static org.junit.Assert.*;

public class PositionRightsTest {
    static char[][] board(String... rows) {
        char[][] result = new char[8][];
        for (int r = 0; r < 8; r++) result[r] = rows[r].toCharArray();
        return result;
    }
    static char[][] start() {
        return board("rnbqkbnr", "pppppppp", "........", "........", "........", "........", "PPPPPPPP", "RNBQKBNR");
    }
    static char[][] rooks() {
        return board("r...k..r", "........", "........", "........", "........", "........", "........", "R...K..R");
    }
    static char[][] copy(char[][] source) {
        char[][] result = new char[8][];
        for (int r = 0; r < 8; r++) result[r] = source[r].clone();
        return result;
    }
    static char[][] move(char[][] before, int fromR, int fromF, int toR, int toF) {
        char[][] after = copy(before);
        after[toR][toF] = after[fromR][fromF];
        after[fromR][fromF] = '.';
        return after;
    }
    private void advance(PositionRights rights, char[][] before, char[][] after, Side expected) {
        MoveAnalyzer.Result change = MoveAnalyzer.analyze(before, after, expected);
        assertNotEquals(MoveAnalyzer.Kind.UNKNOWN, change.kind);
        rights.advance(after, change.mover == Side.WHITE ? Side.BLACK : Side.WHITE, change);
    }

    @Test public void onlyAnExactStartingPositionAutomaticallyGrantsCastling() {
        PositionRights rights = new PositionRights();
        rights.synchronize(rooks(), Side.WHITE);
        assertEquals("-", rights.castling());
        rights.synchronize(start(), Side.WHITE);
        assertEquals("KQkq", rights.castling());
        char[][] opening = move(start(),6,4,4,4);
        rights.synchronize(opening,Side.BLACK); // A jump, not an observed move.
        assertEquals("-",rights.castling());
    }

    @Test public void kingAndRookRightsAreRevokedPermanentlyAndIndependently() {
        PositionRights rights = new PositionRights();
        char[][] a = rooks();
        assertTrue(rights.configure(a, Side.WHITE, "KQkq", "-"));
        char[][] b = move(a,7,7,6,7);
        advance(rights,a,b,Side.WHITE);
        assertEquals("Qkq",rights.castling());
        char[][] c = move(b,6,7,7,7);
        advance(rights,b,c,Side.WHITE);
        assertEquals("Qkq",rights.castling());
        char[][] d = move(c,0,4,1,4);
        advance(rights,c,d,Side.BLACK);
        assertEquals("Q",rights.castling());
        char[][] e = move(d,1,4,0,4);
        advance(rights,d,e,Side.BLACK);
        assertEquals("Q",rights.castling());
        rights.synchronize(e,Side.WHITE); // Re-reading never restores a moved rook/king.
        assertEquals("Q",rights.castling());
    }

    @Test public void capturedHomeRookAndCastledKingRemoveRights() {
        PositionRights rights = new PositionRights();
        char[][] a = rooks();
        a[1][1] = 'B';
        rights.configure(a,Side.WHITE,"KQkq","-");
        char[][] b = move(a,1,1,0,0);
        advance(rights,a,b,Side.WHITE);
        assertEquals("KQk",rights.castling());
        char[][] c = copy(b);
        c[0][4] = c[0][7] = '.'; c[0][6] = 'k'; c[0][5] = 'r';
        advance(rights,b,c,Side.BLACK);
        assertEquals("KQ",rights.castling());
    }

    @Test public void whiteEnPassantLastsOneReplyAndOnlyAnObservedDoubleStepCreatesIt() {
        char[][] a = board("....k...", "...p....", "........", "....P...", "........", "........", "........", "....K...");
        char[][] b = move(a,1,3,3,3);
        PositionRights rights = new PositionRights();
        rights.synchronize(a,Side.BLACK);
        advance(rights,a,b,Side.BLACK);
        assertEquals("d6",rights.enPassant());
        assertTrue(FenBuilder.build(b,Side.WHITE,Side.WHITE,rights.castling(),rights.enPassant()).contains(" w - d6 "));
        rights.synchronize(b,Side.WHITE);
        assertEquals("d6",rights.enPassant());
        char[][] c = move(b,7,4,6,4);
        advance(rights,b,c,Side.WHITE);
        assertEquals("-",rights.enPassant());
        PositionRights unknown = new PositionRights();
        unknown.synchronize(b,Side.WHITE);
        assertEquals("-",unknown.enPassant());
    }

    @Test public void blackEnPassantAndActualCaptureWorkWithAFlippedView() {
        char[][] a = board("....k...", "........", "........", "........", "...p....", "........", "....P...", "....K...");
        char[][] b = move(a,6,4,4,4);
        PositionRights rights = new PositionRights();
        rights.synchronize(a,Side.WHITE);
        advance(rights,a,b,Side.WHITE);
        assertEquals("e3",rights.enPassant());
        String fen = FenBuilder.build(FenBuilder.flip(b),Side.BLACK,Side.BLACK,rights.castling(),rights.enPassant());
        assertTrue(fen.contains(" b - e3 "));
        char[][] c = move(b,4,3,5,4); c[4][4] = '.';
        assertEquals(MoveAnalyzer.Kind.EN_PASSANT,MoveAnalyzer.analyze(b,c).kind);
        assertEquals(MoveAnalyzer.Kind.EN_PASSANT,MoveAnalyzer.analyze(FenBuilder.flip(b),FenBuilder.flip(c)).kind);
        advance(rights,b,c,Side.BLACK);
        assertEquals("-",rights.enPassant());
    }

    @Test public void lastOfTwoQuietPliesDeterminesEnPassantAndPreservesCastling() {
        char[][] a = board("r...k..r", "...p....", "........", "....P...", "........", "........", "P.......", "R...K..R");
        PositionRights rights = new PositionRights();
        rights.configure(a,Side.WHITE,"KQkq","-");
        char[][] b = move(move(a,6,0,5,0),1,3,3,3);
        advance(rights,a,b,Side.WHITE);
        assertEquals("d6",rights.enPassant());
        assertEquals("KQkq",rights.castling());
    }

    @Test public void lastPlyNotFirstDeterminesEnPassantWhenBothPawnsMove() {
        char[][] a = board("....k...", "...p....", "........", "........", "..p.....", "........", "...PP...", "....K...");
        PositionRights rights = new PositionRights();
        rights.synchronize(a,Side.WHITE);
        char[][] b = move(move(a,6,3,4,3),1,3,2,3);
        advance(rights,a,b,Side.WHITE);
        assertEquals("-",rights.enPassant()); // White's double step expired with Black's single step.
    }

    @Test public void impossibleManualMetadataIsRejectedWithoutChangingTheSavedRights() {
        PositionRights rights = new PositionRights();
        char[][] a = rooks();
        assertTrue(rights.configure(a,Side.WHITE,"KQkq","-"));
        assertFalse(rights.configure(a,Side.WHITE,"KK","-"));
        assertFalse(rights.configure(a,Side.WHITE,"KQkq","e6"));
        assertEquals("KQkq",rights.castling());
        a[7][7] = '.';
        assertFalse(rights.configure(a,Side.WHITE,"KQkq","-"));
        assertNull(FenBuilder.build(a,Side.WHITE,Side.WHITE,"KQkq","-"));
        assertNotNull(FenBuilder.build(a,Side.WHITE,Side.WHITE,"Qkq","-"));
    }

    @Test public void manualEnPassantMustMatchTurnPawnOriginAndDestination() {
        char[][] a = board("....k...", "........", "........", "...pP...", "........", "........", "........", "....K...");
        PositionRights rights = new PositionRights();
        assertTrue(rights.configure(a,Side.WHITE,"","d6"));
        assertFalse(rights.configure(a,Side.BLACK,"","d6"));
        a[1][3] = 'n';
        assertFalse(rights.configure(a,Side.WHITE,"","d6"));
        a[1][3] = '.'; a[2][3] = 'N';
        assertFalse(rights.configure(a,Side.WHITE,"","d6"));
    }

    @Test public void callerMutationsAndUnrelatedPositionsCannotLeakHistory() {
        char[][] a = rooks();
        PositionRights rights = new PositionRights();
        rights.configure(a,Side.WHITE,"KQkq","-");
        a[6][0] = 'P';
        rights.synchronize(a,Side.WHITE);
        assertEquals("-",rights.castling());
    }
}
