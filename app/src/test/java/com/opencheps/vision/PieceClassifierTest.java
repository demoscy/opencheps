package com.opencheps.vision;

import android.graphics.Bitmap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PieceClassifierTest {
    private final BoardDetector.Board board = new BoardDetector.Board(0, 0, 320, 1);

    private char[][] start() {
        String[] rows = {"rnbqkbnr", "pppppppp", "........", "........", "........", "........", "PPPPPPPP", "RNBQKBNR"};
        char[][] grid = new char[8][];
        for (int r = 0; r < 8; r++) grid[r] = rows[r].toCharArray();
        return grid;
    }

    private Bitmap render(char[][] grid, int... highlighted) {
        int[] pixels = new int[320 * 320];
        for (int r = 0; r < 8; r++) for (int f = 0; f < 8; f++) {
            boolean highlight = false;
            for (int square : highlighted) if (square == r * 8 + f) highlight = true;
            int background = highlight ? 0xFFCFCD64 : ((r + f) % 2 == 0 ? 0xFFE0D8B8 : 0xFF609060);
            for (int y = 0; y < 40; y++) for (int x = 0; x < 40; x++) {
                int dx = x - 20, dy = y - 20;
                boolean ink = false;
                switch (Character.toLowerCase(grid[r][f])) {
                    case 'p': ink = (Math.abs(dx) <= 3 && Math.abs(dy) <= 8) || dx * dx + (dy + 5) * (dy + 5) <= 20; break;
                    case 'n': ink = (dx >= -7 && dx <= -3 && Math.abs(dy) <= 8) || (dy >= 4 && dy <= 8 && Math.abs(dx) <= 7); break;
                    case 'b': ink = Math.abs(dx - dy) <= 2 && Math.abs(dx) <= 8 && Math.abs(dy) <= 8; break;
                    case 'r': ink = Math.abs(dx) <= 8 && Math.abs(dy) <= 8 && (Math.abs(dx) >= 5 || Math.abs(dy) >= 5); break;
                    case 'q': ink = Math.abs(dx) <= 8 && (Math.abs(dy - 6) <= 2 || Math.abs(dy + 6) <= 2 || Math.abs(dx) <= 2 && Math.abs(dy) <= 8); break;
                    case 'k': ink = Math.abs(dx) <= 8 && Math.abs(dy) <= 8 && (Math.abs(dx) <= 2 || Math.abs(dy) <= 2); break;
                }
                pixels[(r * 40 + y) * 320 + f * 40 + x] = ink ? (Character.isUpperCase(grid[r][f]) ? 0xFFF8F8F8 : 0xFF101010) : background;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, 320, 0, 0, 320, 320);
        return bitmap;
    }

    private void assertGrid(char[][] expected, char[][] actual) {
        assertNotNull(actual);
        for (int r = 0; r < 8; r++) assertArrayEquals("rank " + r, expected[r], actual[r]);
    }

    @Test public void recognizesOpeningAndComputerReplyWithLastMoveHighlights() {
        PieceClassifier classifier = new PieceClassifier();
        char[][] grid = start();
        Bitmap initial = render(grid);
        assertTrue(classifier.tryBootstrap(initial, board));
        grid[6][4] = '.'; grid[4][4] = 'P';
        Bitmap whiteMove = render(grid, 6 * 8 + 4, 4 * 8 + 4);
        assertGrid(grid, classifier.classifyBoard(whiteMove, board));
        classifier.commitConfirmed(grid, whiteMove, board);
        grid[1][4] = '.'; grid[3][4] = 'p';
        Bitmap blackReply = render(grid, 1 * 8 + 4, 3 * 8 + 4);
        assertGrid(grid, classifier.classifyBoard(blackReply, board));
        classifier.commitConfirmed(grid, blackReply, board);
        grid[7][6] = '.'; grid[5][5] = 'N';
        assertGrid(grid, classifier.classifyBoard(render(grid, 7 * 8 + 6, 5 * 8 + 5), board));
    }

    @Test public void kingCanMoveToOppositeSquareColourWithoutLosingItsIdentity() {
        PieceClassifier classifier = new PieceClassifier();
        char[][] grid = start();
        assertTrue(classifier.tryBootstrap(render(grid), board));
        grid[6][4] = '.'; grid[4][4] = 'P';
        Bitmap pawnMove = render(grid);
        classifier.commitConfirmed(grid, pawnMove, board);
        grid[7][4] = '.'; grid[6][4] = 'K';
        char[][] classified = classifier.classifyBoard(render(grid, 7 * 8 + 4, 6 * 8 + 4), board);
        assertEquals('K', classified[6][4]);
        assertEquals('.', classified[7][4]);
    }

    @Test public void flatBackgroundHighlightDoesNotChangePieceShapeFeatures() {
        char[][] grid = start();
        Bitmap plain = render(grid);
        Bitmap highlight = render(grid, 6 * 8 + 4);
        SquareSampler.Sample before = SquareSampler.sample(plain, 180, 260, 12);
        SquareSampler.Sample after = SquareSampler.sample(highlight, 180, 260, 12);
        // A change of square background must not change the normalized silhouette.
        assertEquals(0, SquareSampler.shapeDistance(before.feature, after.feature), 0.0001);
    }
    @Test public void emptyBoardCannotBootstrapThirtyTwoImaginaryPieces() {
        char[][] empty = start();
        for (int r = 0; r < 8; r++) java.util.Arrays.fill(empty[r], '.');
        PieceClassifier classifier = new PieceClassifier();
        assertFalse(classifier.tryBootstrap(render(empty), board));
        assertFalse(classifier.isTrained());
    }

}
