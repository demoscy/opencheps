package com.opencheps.vision;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.opencheps.util.Side;
import java.io.InputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PieceClassifierTest {
    private PieceClassifier classifier;
    @Before public void setup() throws Exception { classifier = new PieceClassifier(RuntimeEnvironment.getApplication()); }
    @After public void close() throws Exception { classifier.close(); }

    private byte[] fixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/piece-model/" + name)) {
            assertNotNull("Run python3 tools/prepare_piece_model.py --tests", in);
            return in.readAllBytes();
        }
    }
    private Bitmap screenshot(String name) throws Exception {
        byte[] data = fixture(name);
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }
    private void assertPlacement(String expected, char[][] grid, Side bottom) {
        assertNotNull(grid);
        String fen = FenBuilder.build(grid, bottom, Side.WHITE);
        assertNotNull(fen);
        assertEquals(expected, fen.split(" ")[0]);
    }

    @Test public void realChessComMidgameNeedsNoOpeningSetup() throws Exception {
        Bitmap bitmap = screenshot("chesscom-italian-white.png");
        assertPlacement("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R",
                classifier.classifyBoard(bitmap, new BoardDetector.Board(222, 66, 768, 1)), Side.WHITE);
        bitmap.recycle();
    }

    @Test public void realBlackPerspectiveKeepsPieceColorsAndFlipsOnlyCoordinates() throws Exception {
        Bitmap bitmap = screenshot("lichess-italian-black.png");
        assertPlacement("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R",
                classifier.classifyBoard(bitmap, new BoardDetector.Board(378, 65, 696, 1)), Side.BLACK);
        bitmap.recycle();
    }

    @Test public void readsSparseCurrentPositionWithoutStartingPieces() throws Exception {
        byte[] gray = fixture("example-gray.u8.bin");
        int[] pixels = new int[gray.length];
        for (int i = 0; i < pixels.length; i++) {
            int v = gray[i] & 255;
            pixels[i] = 0xFF000000 | (v<<16) | (v<<8) | v;
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, 606, 548, Bitmap.Config.ARGB_8888);
        BoardDetector.Board board = new BoardDetector.Board(30, 14, 512, 1);
        assertPlacement("bn4kN/p5bp/1p3npB/3p4/8/5Q2/PPP2PPP/R3R1K1",
                classifier.classifyBoard(bitmap, board), Side.WHITE);
        bitmap.recycle();
    }

    @Test public void preprocessingUsesBottomRowFirstAndHalfPixelBilinearSamples() {
        int[] pixels = new int[256*256];
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
            int v = (y/32)*8 + x/32;
            pixels[y*256+x] = 0xFF000000 | (v<<16) | (v<<8) | v;
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, 256, 256, Bitmap.Config.ARGB_8888);
        float[] tiles = PieceClassifier.extractTiles(bitmap, new BoardDetector.Board(0, 0, 256, 1));
        for (int tile = 0; tile < 64; tile++) for (int i = 0; i < 1024; i++)
            assertEquals(((7-tile/8)*8+tile%8)/255f, tiles[tile*1024+i], .000001f);
        bitmap.recycle();
        pixels = new int[512*512];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i%2 == 0 ? 0xFF000000 : 0xFFFFFFFF;
        bitmap = Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888);
        tiles = PieceClassifier.extractTiles(bitmap, new BoardDetector.Board(0, 0, 512, 1));
        for (float value : tiles) assertEquals(.5f, value, .000001f);
        bitmap.recycle();
    }

    @Test public void unsupportedCartoonStyleIsRejectedForLowConfidence() throws Exception {
        Bitmap bitmap = screenshot("lichess-italian-horsey.png");
        assertNull(classifier.classifyBoard(bitmap, new BoardDetector.Board(378, 65, 696, 1)));
        bitmap.recycle();
    }

    @Test public void rejectedThemeStillProvidesDiagnosticConfidenceWithoutBecomingAnalyzable() throws Exception {
        Bitmap bitmap = screenshot("lichess-italian-horsey.png");
        BoardDetector.Board board = new BoardDetector.Board(378, 65, 696, 1);
        PieceClassifier.Result report = classifier.inspectBoard(bitmap, board);
        assertNotNull(report);
        assertFalse(report.isConfident());
        assertTrue(report.uncertainSquares > 0);
        assertTrue(report.minimumConfidence < .70f);
        assertEquals(8, report.grid.length);
        assertTrue(report.weakestSquare(Side.WHITE).matches("[a-h][1-8]"));
        assertNull(classifier.readBoard(bitmap, board));
        bitmap.recycle();
    }
    @Test public void diagnosticSquareNamesUseTheSameWhiteBlackMappingAsArrows() {
        assertEquals("a8", PieceClassifier.squareName(0, Side.WHITE));
        assertEquals("h1", PieceClassifier.squareName(63, Side.WHITE));
        assertEquals("h1", PieceClassifier.squareName(0, Side.BLACK));
        assertEquals("a8", PieceClassifier.squareName(63, Side.BLACK));
        assertEquals("e4", PieceClassifier.squareName(36, Side.WHITE));
        assertEquals("d5", PieceClassifier.squareName(36, Side.BLACK));
    }

    @Test public void invalidCropNeverClampsToAHiddenDifferentBoard() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
        assertNull(classifier.classifyBoard(bitmap, new BoardDetector.Board(-1, 0, 320, 1)));
        assertNull(classifier.classifyBoard(bitmap, new BoardDetector.Board(0, 1, 320, 1)));
        assertNull(classifier.classifyBoard(bitmap, new BoardDetector.Board(0, 0, 56, 1)));
        char[][] empty = classifier.classifyBoard(bitmap, new BoardDetector.Board(0, 0, 320, 1));
        assertTrue(empty == null || !FenBuilder.validate(empty).ok);
        bitmap.recycle();
    }
}
