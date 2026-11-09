package com.opencheps.capture;

import android.app.Service;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.opencheps.engine.StockfishEngine;
import com.opencheps.overlay.ArrowOverlayView;
import com.opencheps.util.Side;
import com.opencheps.vision.BoardDetector;
import com.opencheps.vision.PieceClassifier;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ScreenCaptureServiceTest {
    private ScreenCaptureService service;

    @Before public void setup() {
        service = Robolectric.buildService(ScreenCaptureService.class).create().get();
        PieceClassifier classifier = new PieceClassifier();
        ReflectionHelpers.setField(classifier, "bootstrapSide", Side.WHITE);
        ReflectionHelpers.setField(service, "classifier", classifier);
        ReflectionHelpers.setField(service, "capToScreenX", 3f);
        ReflectionHelpers.setField(service, "capToScreenY", 3f);
        ReflectionHelpers.setField(service, "currentPvs", new ArrayList<>(Collections.singletonList(
                new StockfishEngine.Pv("e2e4", 0, false))));
    }

    private List<ArrowOverlayView.Arrow> rebuild(BoardDetector.Board board) {
        return ReflectionHelpers.callInstanceMethod(service, "rebuildArrowsForNewBounds",
                from(BoardDetector.Board.class, board));
    }

    @Test public void cachedMovesRebuildAgainstCurrentCropBounds() {
        ArrowOverlayView.Arrow before = rebuild(new BoardDetector.Board(10, 100, 320, 1)).get(0);
        ArrowOverlayView.Arrow after = rebuild(new BoardDetector.Board(20, 150, 320, 1)).get(0);
        assertEquals(30, after.sx - before.sx, 0.001);
        assertEquals(150, after.sy - before.sy, 0.001);
        assertEquals(30, after.ex - before.ex, 0.001);
        assertEquals(150, after.ey - before.ey, 0.001);
        assertEquals(30 + 4.5f * 120, before.sx, 0.001);
        assertEquals(300 + 6.5f * 120, before.sy, 0.001);
    }

    @Test public void blackPerspectiveMapsActualEngineArrows() {
        PieceClassifier classifier = ReflectionHelpers.getField(service, "classifier");
        ReflectionHelpers.setField(classifier, "bootstrapSide", Side.BLACK);
        ArrowOverlayView.Arrow arrow = rebuild(new BoardDetector.Board(10, 100, 320, 1)).get(0);
        assertEquals(30 + 3.5f * 120, arrow.sx, 0.001);
        assertEquals(300 + 1.5f * 120, arrow.sy, 0.001);
        assertEquals(300 + 3.5f * 120, arrow.ey, 0.001);
    }

    @Test public void queuedResultsCannotPublishAcrossEditingPauseOrNewEpoch() {
        int[] published = {0};
        Runnable update = () -> published[0]++;
        ReflectionHelpers.callInstanceMethod(service, "publish", from(Runnable.class, update));
        AtomicInteger epoch = ReflectionHelpers.getField(service, "epoch");
        epoch.incrementAndGet();
        ShadowLooper.idleMainLooper();
        assertEquals(0, published[0]);

        ReflectionHelpers.setField(service, "frameEpoch", epoch.get());
        ReflectionHelpers.setField(service, "editing", true);
        ReflectionHelpers.callInstanceMethod(service, "publish", from(Runnable.class, update));
        ShadowLooper.idleMainLooper();
        assertEquals(0, published[0]);

        ReflectionHelpers.setField(service, "editing", false);
        ReflectionHelpers.setField(service, "paused", true);
        ReflectionHelpers.callInstanceMethod(service, "publish", from(Runnable.class, update));
        ShadowLooper.idleMainLooper();
        assertEquals(0, published[0]);

        ReflectionHelpers.setField(service, "paused", false);
        ReflectionHelpers.callInstanceMethod(service, "publish", from(Runnable.class, update));
        ShadowLooper.idleMainLooper();
        assertEquals(1, published[0]);
    }

    @Test public void processRestartRequiresFreshProjectionGrant() {
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 0));
    }
    @Test public void uncertainPositionInvalidatesCachedAndDisplayedMoves() {
        ReflectionHelpers.setField(service, "lastEngineFen", "old position");
        ReflectionHelpers.setField(service, "currentArrows", rebuild(new BoardDetector.Board(10, 100, 320, 1)));
        ReflectionHelpers.callInstanceMethod(service, "invalidateAnalysis");
        ShadowLooper.idleMainLooper();
        List<?> arrows = ReflectionHelpers.getField(service, "currentArrows");
        List<?> pvs = ReflectionHelpers.getField(service, "currentPvs");
        assertTrue(arrows.isEmpty());
        assertTrue(pvs.isEmpty());
        assertEquals("", ReflectionHelpers.getField(service, "lastEngineFen"));
    }

    @Test public void engineFailureDoesNotSuppressTheNextAttemptForTheSamePosition() {
        int[] queries = {0};
        StockfishEngine failing = new StockfishEngine() {
            @Override public synchronized List<Pv> topMoves(String fen, int time, int count) throws Exception {
                queries[0]++;
                throw new java.io.IOException("test failure");
            }
        };
        ReflectionHelpers.setField(service, "engine", failing);
        char[][] grid = ReflectionHelpers.callInstanceMethod(service, "startingGridFor", from(Side.class, Side.WHITE));
        ReflectionHelpers.setField(service, "confirmedGrid", grid);
        for (int i = 0; i < 2; i++) {
            ReflectionHelpers.callInstanceMethod(service, "queryEngineAndRender",
                    from(BoardDetector.Board.class, new BoardDetector.Board(0, 0, 320, 1)), from(Bitmap.class, null));
            assertEquals("", ReflectionHelpers.getField(service, "lastEngineFen"));
        }
        assertEquals(2, queries[0]);
    }

    @Test public void appOnlyCapturePausesWhenSelectedAppIsNotVisible() {
        assertTrue(ReflectionHelpers.callInstanceMethod(service, "canCapture", from(int.class, 0)));
        ReflectionHelpers.setField(service, "capturedContentVisible", false);
        assertFalse(ReflectionHelpers.callInstanceMethod(service, "canCapture", from(int.class, 0)));
        ReflectionHelpers.setField(service, "capturedContentVisible", true);
        assertTrue(ReflectionHelpers.callInstanceMethod(service, "canCapture", from(int.class, 0)));
    }

    @Test public void missingFreshFrameRestoresCaptureStateAndClearsStaleAnalysis() {
        ImageReader reader = ImageReader.newInstance(32, 32, PixelFormat.RGBA_8888, 2);
        ReflectionHelpers.setField(service, "reader", reader);
        ReflectionHelpers.setField(service, "worker", new Handler(Looper.getMainLooper()));
        ReflectionHelpers.setField(service, "framePending", true);
        ReflectionHelpers.setField(service, "lastEngineFen", "old position");
        ReflectionHelpers.callInstanceMethod(service, "acquireCleanFrame", from(int.class, 0),
                from(long.class, 0L), from(long.class, SystemClock.uptimeMillis() - 1));
        ShadowLooper.idleMainLooper();
        assertFalse(ReflectionHelpers.getField(service, "framePending"));
        assertEquals("", ReflectionHelpers.getField(service, "lastEngineFen"));
        reader.close();
    }

    private char[][] copy(char[][] grid) {
        char[][] result = new char[8][];
        for (int r = 0; r < 8; r++) result[r] = grid[r].clone();
        return result;
    }

    private void recognizeTwice(char[][] grid, Bitmap bitmap) {
        for (int i = 0; i < 2; i++) ReflectionHelpers.callInstanceMethod(service, "processRecognizedGrid",
                from(char[][].class, grid), from(BoardDetector.Board.class, new BoardDetector.Board(0, 0, 320, 1)),
                from(Bitmap.class, bitmap));
    }

    @Test public void analysisResumesAfterOpeningAndMultipleComputerReplies() {
        List<String> queries = new ArrayList<>();
        ReflectionHelpers.setField(service, "engine", new StockfishEngine() {
            @Override public synchronized List<Pv> topMoves(String fen, int time, int count) {
                queries.add(fen);
                return Collections.singletonList(new Pv("g1f3", 20, false));
            }
        });
        char[][] initial = ReflectionHelpers.callInstanceMethod(service, "startingGridFor", from(Side.class, Side.WHITE));
        ReflectionHelpers.setField(service, "confirmedGrid", initial);
        char[][] position = copy(initial);
        Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
        position[6][4] = '.'; position[4][4] = 'P';
        recognizeTwice(position, bitmap);
        assertEquals(Side.BLACK, ReflectionHelpers.getField(service, "observedStm"));
        assertTrue(queries.isEmpty());
        position = copy(position);
        position[1][4] = '.'; position[3][4] = 'p';
        recognizeTwice(position, bitmap);
        assertEquals(Side.WHITE, ReflectionHelpers.getField(service, "observedStm"));
        assertEquals(1, queries.size());
        assertTrue(queries.get(0).contains("4p3/4P3"));
        List<?> arrows = ReflectionHelpers.getField(service, "currentArrows");
        assertFalse(arrows.isEmpty());
        position = copy(position);
        position[7][6] = '.'; position[5][5] = 'N';
        recognizeTwice(position, bitmap);
        assertEquals(1, queries.size());
        position = copy(position);
        position[0][1] = '.'; position[2][2] = 'n';
        recognizeTwice(position, bitmap);
        assertEquals(2, queries.size());
        bitmap.recycle();
    }

    @Test public void fastComputerReplyIsAcceptedWithoutKeepingOldArrows() {
        int[] queries = {0};
        ReflectionHelpers.setField(service, "engine", new StockfishEngine() {
            @Override public synchronized List<Pv> topMoves(String fen, int time, int count) {
                queries[0]++;
                return Collections.singletonList(new Pv("g1f3", 20, false));
            }
        });
        char[][] initial = ReflectionHelpers.callInstanceMethod(service, "startingGridFor", from(Side.class, Side.WHITE));
        ReflectionHelpers.setField(service, "confirmedGrid", initial);
        ReflectionHelpers.setField(service, "currentArrows", rebuild(new BoardDetector.Board(0, 0, 320, 1)));
        char[][] both = copy(initial);
        both[6][4] = '.'; both[4][4] = 'P'; both[1][4] = '.'; both[3][4] = 'p';
        Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
        ReflectionHelpers.callInstanceMethod(service, "processRecognizedGrid", from(char[][].class, both),
                from(BoardDetector.Board.class, new BoardDetector.Board(0, 0, 320, 1)), from(Bitmap.class, bitmap));
        List<?> arrows = ReflectionHelpers.getField(service, "currentArrows");
        assertTrue(arrows.isEmpty());
        assertEquals(0, queries[0]);
        // The next matching clean frame confirms the position after both moves.
        ReflectionHelpers.callInstanceMethod(service, "processRecognizedGrid", from(char[][].class, both),
                from(BoardDetector.Board.class, new BoardDetector.Board(0, 0, 320, 1)), from(Bitmap.class, bitmap));
        assertEquals(1, queries[0]);
        assertEquals(Side.WHITE, ReflectionHelpers.getField(service, "observedStm"));
        bitmap.recycle();
    }

    @Test public void changingOnlyTheCaptureViewportDoesNotInventANewTurn() {
        char[][] grid = ReflectionHelpers.callInstanceMethod(service, "startingGridFor", from(Side.class, Side.WHITE));
        ReflectionHelpers.setField(service, "confirmedGrid", grid);
        ReflectionHelpers.setField(service, "observedStm", Side.BLACK);
        ReflectionHelpers.setField(service, "lastEngineFen", "old bounds");
        ReflectionHelpers.callInstanceMethod(service, "resetForCaptureGeometry", from(boolean.class, false));
        assertSame(grid, ReflectionHelpers.getField(service, "confirmedGrid"));
        assertEquals(Side.BLACK, ReflectionHelpers.getField(service, "observedStm"));
        assertEquals("", ReflectionHelpers.getField(service, "lastEngineFen"));
    }

    @Test public void physicalRotationStillClearsPositionTracking() {
        char[][] grid = ReflectionHelpers.callInstanceMethod(service, "startingGridFor", from(Side.class, Side.WHITE));
        ReflectionHelpers.setField(service, "confirmedGrid", grid);
        ReflectionHelpers.callInstanceMethod(service, "resetForCaptureGeometry", from(boolean.class, true));
        assertNull(ReflectionHelpers.getField(service, "confirmedGrid"));
    }

}
