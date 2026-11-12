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
        ReflectionHelpers.setField(service, "boardSide", Side.BLACK);
        ArrowOverlayView.Arrow arrow = rebuild(new BoardDetector.Board(10, 100, 320, 1)).get(0);
        assertEquals(30 + 3.5f * 120, arrow.sx, 0.001);
        assertEquals(300 + 1.5f * 120, arrow.sy, 0.001);
        assertEquals(300 + 3.5f * 120, arrow.ey, 0.001);
    }

    @Test public void arrowLabelsUseEachCandidatesOwnScoreAndRetainMateSignForEitherSide() {
        com.opencheps.util.Prefs.setMultiPv(service, 3);
        ReflectionHelpers.setField(service, "currentPvs", java.util.Arrays.asList(
                new StockfishEngine.Pv("e2e4", 80, false), new StockfishEngine.Pv("d2d4", -125, false),
                new StockfishEngine.Pv("g1f3", 99997, true)));
        BoardDetector.Board board = new BoardDetector.Board(10, 100, 320, 1);
        List<ArrowOverlayView.Arrow> arrows = rebuild(board);
        assertEquals("★ +0.80", arrows.get(0).label);
        assertEquals("-1.25", arrows.get(1).label);
        assertEquals("M3", arrows.get(2).label);
        ReflectionHelpers.setField(service, "boardSide", Side.BLACK);
        assertEquals("★ +0.80", rebuild(board).get(0).label); // Selected-side perspective.
        ReflectionHelpers.setField(service, "currentPvs", Collections.singletonList(new StockfishEngine.Pv("e2e4", -99998, true)));
        assertEquals("★ -M2", rebuild(board).get(0).label);
    }

    @Test public void missingEngineScoreIsShownAsUnavailableInsteadOfAnInventedEqualPosition() {
        StockfishEngine.Pv unknown = ReflectionHelpers.callConstructor(StockfishEngine.Pv.class,
                from(List.class, Collections.singletonList("e2e4")), from(int.class, 0),
                from(boolean.class, false), from(boolean.class, false));
        ReflectionHelpers.setField(service, "currentPvs", Collections.singletonList(unknown));
        assertEquals("★ —", rebuild(new BoardDetector.Board(10,100,320,1)).get(0).label);
    }

    @Test public void fiveChoicesColorTheHighestDisplayedScoreGreenWithoutReplacingTheEloSelectedMove() {
        com.opencheps.util.Prefs.setMultiPv(service, 5);
        List<StockfishEngine.Pv> pvs = java.util.Arrays.asList(
                new StockfishEngine.Pv("e2e4",10,false), new StockfishEngine.Pv("d2d4",300,false),
                new StockfishEngine.Pv("g1f3",100,false), new StockfishEngine.Pv("c2c4",500,false),
                new StockfishEngine.Pv("b1c3",200,false));
        ReflectionHelpers.setField(service,"currentPvs",pvs);
        BoardDetector.Board board = new BoardDetector.Board(10,100,320,1);
        List<ArrowOverlayView.Arrow> arrows = rebuild(board);
        assertEquals(5,arrows.size());
        assertEquals(0xFFFF6E8A,arrows.get(0).color);
        assertEquals("★ +0.10",arrows.get(0).label);
        assertEquals(0xFFFFB300,arrows.get(1).color);
        assertEquals(0xFFAB47BC,arrows.get(2).color);
        assertEquals(0xFF00C853,arrows.get(3).color);
        assertEquals(0xFF29B6F6,arrows.get(4).color);
        assertSame(pvs,ReflectionHelpers.getField(service,"currentPvs"));
        // One choice retains the handicapped root; its sole displayed score is green.
        com.opencheps.util.Prefs.setMultiPv(service,1);
        assertEquals(0xFF00C853,rebuild(board).get(0).color);
        assertEquals("★ +0.10",rebuild(board).get(0).label);
        com.opencheps.util.Prefs.setMultiPv(service,2);
        assertEquals(0xFF00C853,rebuild(board).get(1).color); // Hidden +5.00 does not rank.
    }

    @Test public void greenRankingHandlesMatesUnknownScoresAndStableTies() {
        com.opencheps.util.Prefs.setMultiPv(service,5);
        StockfishEngine.Pv unknown = ReflectionHelpers.callConstructor(StockfishEngine.Pv.class,
                from(List.class, Collections.singletonList("e2e4")),from(int.class,0),
                from(boolean.class,false),from(boolean.class,false));
        ReflectionHelpers.setField(service,"currentPvs",java.util.Arrays.asList(unknown,
                new StockfishEngine.Pv("d2d4",Integer.MAX_VALUE,false),
                new StockfishEngine.Pv("g1f3",-99995,true),
                new StockfishEngine.Pv("c2c4",99995,true),
                new StockfishEngine.Pv("b1c3",99998,true)));
        BoardDetector.Board board = new BoardDetector.Board(10,100,320,1);
        List<ArrowOverlayView.Arrow> arrows = rebuild(board);
        assertEquals(0xFF00C853,arrows.get(4).color); // M2 beats M5 and finite scores.
        assertEquals(0xFFFFB300,arrows.get(3).color);
        assertEquals(0xFFFF6E8A,arrows.get(0).color); // Unknown is not an invented draw.
        ReflectionHelpers.setField(service,"currentPvs",java.util.Arrays.asList(
                new StockfishEngine.Pv("e2e4",-99998,true),new StockfishEngine.Pv("d2d4",-99995,true)));
        assertEquals(0xFF00C853,rebuild(board).get(1).color); // Delaying a forced loss ranks higher.
        ReflectionHelpers.setField(service,"currentPvs",java.util.Arrays.asList(
                new StockfishEngine.Pv("e2e4",50,false),new StockfishEngine.Pv("d2d4",50,false)));
        assertEquals(0xFF00C853,rebuild(board).get(0).color);
        ReflectionHelpers.setField(service,"boardSide",Side.BLACK);
        assertEquals(0xFF00C853,rebuild(board).get(0).color);
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
            @Override public synchronized List<Pv> analyze(String fen, int time, int count, int choices, boolean studyLine) throws Exception {
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
            @Override public synchronized List<Pv> analyze(String fen, int time, int count, int choices, boolean studyLine) {
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
            @Override public synchronized List<Pv> analyze(String fen, int time, int count, int choices, boolean studyLine) {
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

    @Test public void adoptsAnExistingPuzzleAfterTwoFramesWithoutAnOpeningPosition() {
        List<String> queries = new ArrayList<>();
        ReflectionHelpers.setField(service, "engine", new StockfishEngine() {
            @Override public synchronized List<Pv> analyze(String fen, int time, int count, int choices, boolean studyLine) {
                queries.add(fen);
                return Collections.singletonList(new Pv("c7g7", 0, false));
            }
        });
        char[][] puzzle = { "......k.".toCharArray(), "..Q.....".toCharArray(),
                "........".toCharArray(), "........".toCharArray(), "........".toCharArray(),
                "..q.....".toCharArray(), "........".toCharArray(), "......K.".toCharArray() };
        recognizeTwice(puzzle, null);
        assertEquals(1, queries.size());
        assertEquals("6k1/2Q5/8/8/8/2q5/8/6K1 w - - 0 1", queries.get(0));
        assertSame(puzzle, ReflectionHelpers.getField(service, "confirmedGrid"));
    }

    @Test public void arbitraryPuzzleJumpClearsOldMovesWithoutInventingTurn() {
        char[][] initial = ReflectionHelpers.callInstanceMethod(service, "startingGridFor", from(Side.class, Side.WHITE));
        ReflectionHelpers.setField(service, "confirmedGrid", initial);
        ReflectionHelpers.setField(service, "observedStm", Side.BLACK);
        ReflectionHelpers.setField(service, "lastEngineFen", "old position");
        char[][] puzzle = { "......k.".toCharArray(), "..Q.....".toCharArray(),
                "........".toCharArray(), "........".toCharArray(), "........".toCharArray(),
                "..q.....".toCharArray(), "........".toCharArray(), "......K.".toCharArray() };
        recognizeTwice(puzzle, null);
        assertSame(initial, ReflectionHelpers.getField(service, "confirmedGrid"));
        assertEquals(Side.BLACK, ReflectionHelpers.getField(service, "observedStm"));
        assertEquals("", ReflectionHelpers.getField(service, "lastEngineFen"));
        List<?> arrows = ReflectionHelpers.getField(service, "currentArrows");
        assertTrue(arrows.isEmpty());
    }

    @Test public void onlySavedCompatibleAndRecognizableManualLayoutCanBeSelected() throws Exception {
        byte[] data;
        try (java.io.InputStream in = getClass().getResourceAsStream("/piece-model/chesscom-italian-white.png")) {
            assertNotNull(in);
            data = in.readAllBytes();
        }
        Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
        com.opencheps.util.BoardArea correct = com.opencheps.util.BoardArea.fromPixels(222, 66, 768, 1440, 900, 0);
        com.opencheps.util.BoardArea slightlyOffset = com.opencheps.util.BoardArea.fromPixels(222, 68, 768, 1440, 900, 0);
        com.opencheps.util.BoardArea wrongLayout = com.opencheps.util.BoardArea.fromPixels(20, 20, 500, 1440, 900, 0);
        com.opencheps.util.BoardArea wrongRotation = com.opencheps.util.BoardArea.fromPixels(222, 66, 768, 1440, 900, 2);
        ReflectionHelpers.setField(service, "screenW", 1440);
        ReflectionHelpers.setField(service, "screenH", 900);
        ReflectionHelpers.setField(service, "captureW", 1440);
        ReflectionHelpers.setField(service, "captureH", 900);
        ReflectionHelpers.setField(service, "captureViewport",
                com.opencheps.util.CaptureViewport.resolve(1440, 900, 1440, 900, 0, 0, 0, 0));
        ReflectionHelpers.setField(service, "manualAreas", java.util.Arrays.asList(slightlyOffset, wrongRotation, wrongLayout, correct));
        ReflectionHelpers.setField(service, "manualArea", slightlyOffset);
        try (PieceClassifier classifier = new PieceClassifier(service)) {
            ReflectionHelpers.setField(service, "classifier", classifier);
            Object recognition = ReflectionHelpers.callInstanceMethod(service, "recognizeBoard", from(Bitmap.class, bitmap));
            assertNotNull(recognition);
            assertSame(correct, ReflectionHelpers.getField(recognition, "area"));
            BoardDetector.Board bounds = ReflectionHelpers.getField(recognition, "board");
            assertEquals(222, bounds.left);
            assertEquals(66, bounds.top);
            assertEquals(768, bounds.side);
            // Manual mode must not fall back to the detector when none of its crops fit.
            ReflectionHelpers.setField(service, "manualAreas", java.util.Arrays.asList(wrongRotation, wrongLayout));
            ReflectionHelpers.setField(service, "manualArea", wrongRotation);
            assertNull(ReflectionHelpers.callInstanceMethod(service, "recognizeBoard", from(Bitmap.class, bitmap)));
        } finally { bitmap.recycle(); }
    }

    @Test public void stationaryPositionUsesProbesButConfirmationAndEngineRetryNeedCleanReads() {
        char[][] initial = ReflectionHelpers.callInstanceMethod(service, "startingGridFor", from(Side.class, Side.WHITE));
        ReflectionHelpers.setField(service, "confirmedGrid", initial);
        ReflectionHelpers.setField(service, "cleanReference", new int[1]);
        ReflectionHelpers.setField(service, "lastCleanRead", 1000L);
        ReflectionHelpers.setField(service, "lastEngineFen", "ready");
        assertFalse(ReflectionHelpers.callInstanceMethod(service, "needsCleanRead", from(long.class, 1500L)));
        ReflectionHelpers.setField(service, "candidateGrid", initial);
        ReflectionHelpers.setField(service, "candidateRepeats", 1);
        assertTrue(ReflectionHelpers.callInstanceMethod(service, "needsCleanRead", from(long.class, 1500L)));
        ReflectionHelpers.setField(service, "candidateGrid", null);
        ReflectionHelpers.setField(service, "lastEngineFen", "");
        assertTrue(ReflectionHelpers.callInstanceMethod(service, "needsCleanRead", from(long.class, 1500L)));
        ReflectionHelpers.setField(service, "observedStm", Side.BLACK);
        assertFalse(ReflectionHelpers.callInstanceMethod(service, "needsCleanRead", from(long.class, 1500L)));
        assertTrue(ReflectionHelpers.callInstanceMethod(service, "needsCleanRead", from(long.class, 17000L)));
    }

    @Test public void currentPuzzleUsesExplicitBlackTurnIndependentlyOfWhiteBoardOrientation() {
        List<String> queries = new ArrayList<>();
        ReflectionHelpers.setField(service, "userSide", Side.BLACK);
        ReflectionHelpers.setField(service, "observedStm", Side.BLACK);
        ReflectionHelpers.setField(service, "turnExplicit", true);
        ReflectionHelpers.setField(service, "engine", new StockfishEngine() {
            @Override public synchronized List<Pv> analyze(String fen, int time, int count, int choices, boolean studyLine) {
                queries.add(fen);
                return Collections.singletonList(new Pv("c3g3", 0, false));
            }
        });
        char[][] puzzle = { "......k.".toCharArray(), "..Q.....".toCharArray(),
                "........".toCharArray(), "........".toCharArray(), "........".toCharArray(),
                "..q.....".toCharArray(), "........".toCharArray(), "......K.".toCharArray() };
        recognizeTwice(puzzle, null);
        assertEquals(1, queries.size());
        assertEquals("6k1/2Q5/8/8/8/2q5/8/6K1 b - - 0 1", queries.get(0));
    }

    @Test public void savedThreeChoiceSettingReturnsThreeArrowsAndCanBeReducedToOne() {
        com.opencheps.util.Prefs.get(service).edit().putInt("multipv", 3).commit();
        ReflectionHelpers.setField(service, "currentPvs", java.util.Arrays.asList(
                new StockfishEngine.Pv("e2e4", 20, false), new StockfishEngine.Pv("d2d4", 10, false),
                new StockfishEngine.Pv("g1f3", 5, false)));
        assertEquals(3, rebuild(new BoardDetector.Board(10, 100, 320, 1)).size());
        com.opencheps.util.Prefs.setMultiPv(service, 1);
        assertEquals(1, rebuild(new BoardDetector.Board(10, 100, 320, 1)).size());
    }
    @Test public void practiceStrengthChangeRequeriesTheSamePositionAndRetainsOnlyOneRecommendation() {
        com.opencheps.util.Prefs.setMultiPv(service, 1);
        List<Integer> strengths = new ArrayList<>();
        ReflectionHelpers.setField(service, "engine", new StockfishEngine() {
            @Override public synchronized List<Pv> analyze(String fen, int time, int elo, int choices, boolean studyLine) {
                strengths.add(elo);
                return java.util.Arrays.asList(new Pv("e2e4", 20, false), new Pv("d2d4", 10, false));
            }
        });
        char[][] initial = ReflectionHelpers.callInstanceMethod(service, "startingGridFor", from(Side.class, Side.WHITE));
        ReflectionHelpers.setField(service, "confirmedGrid", initial);
        com.opencheps.util.Prefs.setLimitStrength(service, false);
        ReflectionHelpers.callInstanceMethod(service, "queryEngineAndRender",
                from(BoardDetector.Board.class, new BoardDetector.Board(0, 0, 320, 1)), from(Bitmap.class, null));
        com.opencheps.util.Prefs.setPracticeElo(service, 1700);
        com.opencheps.util.Prefs.setLimitStrength(service, true);
        ReflectionHelpers.callInstanceMethod(service, "queryEngineAndRender",
                from(BoardDetector.Board.class, new BoardDetector.Board(0, 0, 320, 1)), from(Bitmap.class, null));
        assertEquals(java.util.Arrays.asList(0, 1700), strengths);
        List<?> pvs = ReflectionHelpers.getField(service, "currentPvs");
        assertEquals(1, pvs.size());
    }

    @Test public void changingPracticeStrengthDuringSearchDiscardsTheOldResultAndAllowsRetry() {
        com.opencheps.util.Prefs.setLimitStrength(service, false);
        ReflectionHelpers.setField(service, "engine", new StockfishEngine() {
            @Override public synchronized List<Pv> analyze(String fen, int time, int elo, int choices, boolean studyLine) {
                if (elo == 0) {
                    com.opencheps.util.Prefs.setPracticeElo(service, 1700);
                    com.opencheps.util.Prefs.setLimitStrength(service, true);
                }
                return Collections.singletonList(new Pv("e2e4", 20, false));
            }
        });
        char[][] initial = ReflectionHelpers.callInstanceMethod(service, "startingGridFor", from(Side.class, Side.WHITE));
        ReflectionHelpers.setField(service, "confirmedGrid", initial);
        BoardDetector.Board board = new BoardDetector.Board(0, 0, 320, 1);
        ReflectionHelpers.callInstanceMethod(service, "queryEngineAndRender", from(BoardDetector.Board.class, board), from(Bitmap.class, null));
        assertEquals("", ReflectionHelpers.getField(service, "lastEngineFen"));
        List<?> old = ReflectionHelpers.getField(service, "currentPvs");
        assertTrue(old.isEmpty());
        ReflectionHelpers.callInstanceMethod(service, "queryEngineAndRender", from(BoardDetector.Board.class, board), from(Bitmap.class, null));
        assertEquals(1700, (int) ReflectionHelpers.getField(service, "lastEngineStrength"));
        List<?> fresh = ReflectionHelpers.getField(service, "currentPvs");
        assertEquals(1, fresh.size());
    }

    @Test public void changingChoiceCountReanalyzesAnUnchangedPosition() {
        List<Integer> requested = new ArrayList<>();
        ReflectionHelpers.setField(service, "engine", new StockfishEngine() {
            @Override public synchronized List<Pv> analyze(String fen, int time, int elo, int choices, boolean studyLine) {
                requested.add(choices);
                return java.util.Arrays.asList(new Pv("e2e4",20,false), new Pv("d2d4",10,false),
                        new Pv("g1f3",5,false), new Pv("c2c4",3,false), new Pv("b1c3",1,false));
            }
        });
        char[][] initial = ReflectionHelpers.callInstanceMethod(service, "startingGridFor", from(Side.class, Side.WHITE));
        ReflectionHelpers.setField(service, "confirmedGrid", initial);
        for (int choices : new int[]{1,3,5,4}) {
            com.opencheps.util.Prefs.setMultiPv(service, choices);
            ReflectionHelpers.callInstanceMethod(service, "queryEngineAndRender",
                    from(BoardDetector.Board.class, new BoardDetector.Board(0, 0, 320, 1)), from(Bitmap.class, null));
            List<?> arrows = ReflectionHelpers.getField(service, "currentArrows");
            assertEquals(choices, arrows.size());
        }
        assertEquals(java.util.Arrays.asList(1,3,5,4), requested);
    }

    @Test public void thinkTimeAndContinuationChangesRequeryTheSamePosition() {
        List<String> requested = new ArrayList<>();
        ReflectionHelpers.setField(service, "engine", new StockfishEngine() {
            @Override public synchronized List<Pv> analyze(String fen, int time, int elo, int choices, boolean studyLine) {
                requested.add(time + ":" + studyLine);
                return Collections.singletonList(new Pv("e2e4", 20, false));
            }
        });
        char[][] initial = ReflectionHelpers.callInstanceMethod(service, "startingGridFor", from(Side.class, Side.WHITE));
        ReflectionHelpers.setField(service, "confirmedGrid", initial);
        BoardDetector.Board board = new BoardDetector.Board(0, 0, 320, 1);
        com.opencheps.util.Prefs.setThinkMs(service, 1500);
        com.opencheps.util.Prefs.setShowStudyLine(service, false);
        ReflectionHelpers.callInstanceMethod(service, "queryEngineAndRender", from(BoardDetector.Board.class, board), from(Bitmap.class, null));
        com.opencheps.util.Prefs.setThinkMs(service, 100);
        ReflectionHelpers.callInstanceMethod(service, "queryEngineAndRender", from(BoardDetector.Board.class, board), from(Bitmap.class, null));
        com.opencheps.util.Prefs.setShowStudyLine(service, true);
        ReflectionHelpers.callInstanceMethod(service, "queryEngineAndRender", from(BoardDetector.Board.class, board), from(Bitmap.class, null));
        ReflectionHelpers.callInstanceMethod(service, "queryEngineAndRender", from(BoardDetector.Board.class, board), from(Bitmap.class, null));
        assertEquals(java.util.Arrays.asList("1500:false", "100:false", "100:true"), requested);
    }

    @Test public void hidingTheStudyLineDuringSearchDiscardsItsResultAndRetriesWithoutExtraWork() {
        List<Boolean> requested = new ArrayList<>();
        com.opencheps.util.Prefs.setShowStudyLine(service, true);
        ReflectionHelpers.setField(service, "engine", new StockfishEngine() {
            @Override public synchronized List<Pv> analyze(String fen, int time, int elo, int choices, boolean studyLine) {
                requested.add(studyLine);
                com.opencheps.util.Prefs.setShowStudyLine(service, false);
                return Collections.singletonList(new Pv("e2e4", 20, false));
            }
        });
        char[][] initial = ReflectionHelpers.callInstanceMethod(service, "startingGridFor", from(Side.class, Side.WHITE));
        ReflectionHelpers.setField(service, "confirmedGrid", initial);
        BoardDetector.Board board = new BoardDetector.Board(0, 0, 320, 1);
        ReflectionHelpers.callInstanceMethod(service, "queryEngineAndRender", from(BoardDetector.Board.class, board), from(Bitmap.class, null));
        assertEquals("", ReflectionHelpers.getField(service, "lastEngineFen"));
        assertTrue(((List<?>) ReflectionHelpers.getField(service, "currentPvs")).isEmpty());
        ReflectionHelpers.callInstanceMethod(service, "queryEngineAndRender", from(BoardDetector.Board.class, board), from(Bitmap.class, null));
        assertEquals(java.util.Arrays.asList(true, false), requested);
        assertFalse(ReflectionHelpers.getField(service, "lastEngineStudyLine"));
        assertFalse(((List<?>) ReflectionHelpers.getField(service, "currentPvs")).isEmpty());
    }

    @Test public void settingsPauseCaptureAndPublicationThenPreserveTurnAndNormalPause() {
        ReflectionHelpers.setField(service, "worker", new Handler(Looper.getMainLooper()));
        ReflectionHelpers.setField(service, "observedStm", Side.BLACK);
        ReflectionHelpers.setField(service, "boardSide", Side.BLACK);
        com.opencheps.util.Prefs.setUserSide(service, "w");
        for (boolean paused : new boolean[]{false, true}) {
            ReflectionHelpers.setField(service, "paused", paused);
            ScreenCaptureService.setSettingsVisible(true);
            ShadowLooper.idleMainLooper();
            AtomicInteger epoch = ReflectionHelpers.getField(service, "epoch");
            ReflectionHelpers.setField(service, "frameEpoch", epoch.get());
            assertFalse(ReflectionHelpers.callInstanceMethod(service, "canCapture", from(int.class, epoch.get())));
            int[] published = {0};
            ReflectionHelpers.callInstanceMethod(service, "publish", from(Runnable.class, () -> published[0]++));
            ShadowLooper.idleMainLooper();
            assertEquals(0, published[0]);
            ScreenCaptureService.setSettingsVisible(false);
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
            assertFalse(ReflectionHelpers.getField(service, "settingsVisible"));
            assertEquals(paused, ReflectionHelpers.getField(service, "paused"));
            assertEquals(Side.BLACK, ReflectionHelpers.getField(service, "observedStm"));
            assertEquals(Side.BLACK, ReflectionHelpers.getField(service, "boardSide"));
            assertEquals(!paused, ReflectionHelpers.callInstanceMethod(service, "canCapture", from(int.class, epoch.get())));
        }
    }

    @Test public void rapidSettingsReentryCancelsAnOlderResumeAndChangedSideAppliesOnReturn() {
        ReflectionHelpers.setField(service, "worker", new Handler(Looper.getMainLooper()));
        com.opencheps.util.Prefs.setUserSide(service, "w");
        ScreenCaptureService.setSettingsVisible(true);
        ScreenCaptureService.setSettingsVisible(false);
        ScreenCaptureService.setSettingsVisible(true);
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        assertTrue(ReflectionHelpers.getField(service, "settingsVisible"));
        com.opencheps.util.Prefs.setUserSide(service, "b");
        ScreenCaptureService.setSettingsVisible(false);
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        assertFalse(ReflectionHelpers.getField(service, "settingsVisible"));
        assertEquals(Side.BLACK, ReflectionHelpers.getField(service, "userSide"));
        assertEquals(Side.BLACK, ReflectionHelpers.getField(service, "boardSide"));
        assertEquals(Side.BLACK, ReflectionHelpers.getField(service, "observedStm"));
    }

    @Test public void cachedPreviewIsAnIndependentExactCropAndClearsOnGeometryChange() throws Exception {
        Bitmap frame = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        frame.eraseColor(android.graphics.Color.MAGENTA);
        frame.setPixel(12, 14, android.graphics.Color.GREEN);
        Class attemptClass = Class.forName("com.opencheps.capture.ScreenCaptureService$ReadAttempt");
        Object attempt = ReflectionHelpers.callConstructor(attemptClass,
                from(BoardDetector.Board.class, new BoardDetector.Board(10, 10, 64, 1)),
                from(PieceClassifier.Result.class, null));
        ReflectionHelpers.callInstanceMethod(service, "updateBoardPreview", from(Bitmap.class, frame), from(attemptClass, attempt));
        Bitmap cached = ReflectionHelpers.getField(service, "lastBoardPreview");
        frame.recycle();
        assertFalse(cached.isRecycled());
        assertEquals(android.graphics.Color.GREEN, cached.getPixel(2,4));
        assertEquals(64, cached.getWidth());
        ReflectionHelpers.callInstanceMethod(service, "resetForCaptureGeometry", from(boolean.class, false));
        assertTrue(cached.isRecycled());
        assertNull(ReflectionHelpers.getField(service, "lastBoardPreview"));
    }

    @Test public void previewPauseAndClosePreserveNormalPauseAndTurnState() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true);
        org.robolectric.android.controller.ServiceController<com.opencheps.overlay.OverlayService> overlay =
                Robolectric.buildService(com.opencheps.overlay.OverlayService.class).create();
        try {
            ReflectionHelpers.setField(service, "worker", new Handler(Looper.getMainLooper()));
            ReflectionHelpers.callInstanceMethod(service, "wirePanel");
            com.opencheps.overlay.FloatingPanel panel = com.opencheps.overlay.OverlayService.panel();
            android.view.View root = ReflectionHelpers.getField(panel, "root");
            root.findViewById(com.opencheps.R.id.panelToggle).performClick();
            ShadowLooper.idleMainLooper();
            ReflectionHelpers.setField(service, "observedStm", Side.BLACK);
            ReflectionHelpers.callInstanceMethod(service, "beginBoardPreview");
            ShadowLooper.idleMainLooper();
            assertTrue(ReflectionHelpers.getField(service, "editing"));
            assertNotNull(ReflectionHelpers.getField(overlay.get(), "preview"));
            com.opencheps.overlay.OverlayService.cancelCalibration();
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
            assertFalse(ReflectionHelpers.getField(service, "editing"));
            assertTrue(ReflectionHelpers.getField(service, "paused"));
            assertTrue(panel.isPaused());
            assertEquals(Side.BLACK, ReflectionHelpers.getField(service, "observedStm"));
        } finally {
            overlay.destroy();
        }
    }

    @Test public void observedOpeningRetainsCastlingInTheActualEngineFenForEitherBoardOrientation() {
        for (Side bottom : Side.values()) {
            ReflectionHelpers.callInstanceMethod(service,"resetGameState");
            ReflectionHelpers.setField(service,"boardSide",bottom);
            ReflectionHelpers.setField(service,"userSide",Side.WHITE);
            List<String> queries = new ArrayList<>();
            ReflectionHelpers.setField(service,"engine",new StockfishEngine() {
                @Override public synchronized List<Pv> analyze(String fen,int time,int elo,int choices,boolean line) {
                    queries.add(fen);
                    return Collections.singletonList(new Pv("e1g1",30,false));
                }
            });
            char[][] initial = ReflectionHelpers.callInstanceMethod(service,"startingGridFor",from(Side.class,Side.WHITE));
            recognizeTwice(bottom == Side.BLACK ? com.opencheps.vision.FenBuilder.flip(initial) : initial,null);
            char[][] after = copy(initial);
            after[6][4]='.'; after[4][4]='P'; after[1][4]='.'; after[3][4]='p';
            recognizeTwice(bottom == Side.BLACK ? com.opencheps.vision.FenBuilder.flip(after) : after,null);
            assertEquals(2,queries.size());
            assertTrue(queries.get(1).contains(" w KQkq - "));
        }
    }

    @Test public void engineReceivesObservedEnPassantAndCaptureExpiresIt() {
        char[][] before = {"....k...".toCharArray(),"...p....".toCharArray(),"........".toCharArray(),
                "....P...".toCharArray(),"........".toCharArray(),"........".toCharArray(),
                "........".toCharArray(),"....K...".toCharArray()};
        ReflectionHelpers.setField(service,"observedStm",Side.BLACK);
        ReflectionHelpers.setField(service,"turnExplicit",true);
        List<String> queries = new ArrayList<>();
        ReflectionHelpers.setField(service,"engine",new StockfishEngine() {
            @Override public synchronized List<Pv> analyze(String fen,int time,int elo,int choices,boolean line) {
                queries.add(fen); return Collections.singletonList(new Pv("e5d6",20,false));
            }
        });
        recognizeTwice(before,null);
        char[][] after = copy(before); after[1][3]='.'; after[3][3]='p';
        recognizeTwice(after,null);
        assertEquals(1,queries.size());
        assertTrue(queries.get(0).contains(" w - d6 "));
        assertEquals(Side.WHITE,ReflectionHelpers.getField(service,"observedStm"));
        char[][] captured = copy(after); captured[3][4]=captured[3][3]='.'; captured[2][3]='P';
        recognizeTwice(captured,null);
        assertEquals(Side.BLACK,ReflectionHelpers.getField(service,"observedStm"));
        com.opencheps.vision.PositionRights rights = ReflectionHelpers.getField(service,"positionRights");
        assertEquals("-",rights.enPassant());
    }

    @Test public void specialMovesEditorPausesAndCancelKeepsRightsTurnAndNormalPause() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true);
        org.robolectric.android.controller.ServiceController<com.opencheps.overlay.OverlayService> overlay =
                Robolectric.buildService(com.opencheps.overlay.OverlayService.class).create();
        try {
            ReflectionHelpers.setField(service,"worker",new Handler(Looper.getMainLooper()));
            char[][] board = ReflectionHelpers.callInstanceMethod(service,"startingGridFor",from(Side.class,Side.WHITE));
            ReflectionHelpers.setField(service,"confirmedGrid",board);
            com.opencheps.vision.PositionRights rights = ReflectionHelpers.getField(service,"positionRights");
            rights.synchronize(board,Side.WHITE);
            ReflectionHelpers.setField(service,"paused",true);
            ReflectionHelpers.callInstanceMethod(service,"beginSpecialMoves");
            ShadowLooper.idleMainLooper();
            assertTrue(ReflectionHelpers.getField(service,"editing"));
            assertNotNull(ReflectionHelpers.getField(overlay.get(),"specialMoves"));
            AtomicInteger epoch = ReflectionHelpers.getField(service,"epoch");
            assertFalse(ReflectionHelpers.callInstanceMethod(service,"canCapture",from(int.class,epoch.get())));
            com.opencheps.overlay.OverlayService.cancelCalibration();
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
            assertFalse(ReflectionHelpers.getField(service,"editing"));
            assertTrue(ReflectionHelpers.getField(service,"paused"));
            assertEquals(Side.WHITE,ReflectionHelpers.getField(service,"observedStm"));
            assertEquals("KQkq",rights.castling());
        } finally { overlay.destroy(); }
    }

    @Test public void savingMidgameRightsReanalyzesTheUnchangedBoardAndReadRetainsThem() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true);
        org.robolectric.android.controller.ServiceController<com.opencheps.overlay.OverlayService> overlay =
                Robolectric.buildService(com.opencheps.overlay.OverlayService.class).create();
        try {
            ReflectionHelpers.setField(service,"worker",new Handler(Looper.getMainLooper()));
            List<String> queries=new ArrayList<>();
            ReflectionHelpers.setField(service,"engine",new StockfishEngine() {
                @Override public synchronized List<Pv> analyze(String fen,int time,int elo,int choices,boolean line) {
                    queries.add(fen);return Collections.singletonList(new Pv("e1g1",30,false));
                }
            });
            char[][] board={"r...k..r".toCharArray(),"........".toCharArray(),"........".toCharArray(),"........".toCharArray(),
                    "........".toCharArray(),"........".toCharArray(),"........".toCharArray(),"R...K..R".toCharArray()};
            recognizeTwice(board,null);
            assertTrue(queries.get(0).contains(" w - - "));
            ReflectionHelpers.callInstanceMethod(service,"beginSpecialMoves");
            ShadowLooper.idleMainLooper();
            ReflectionHelpers.callInstanceMethod(overlay.get(),"finishSpecialMoves",from(String.class,"KQkq"),from(String.class,"-"));
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
            assertFalse(ReflectionHelpers.getField(service,"editing"));
            recognizeTwice(board,null);
            assertEquals(2,queries.size());assertTrue(queries.get(1).contains(" w KQkq - "));
            ReflectionHelpers.callInstanceMethod(service,"resetGameState");
            recognizeTwice(board,null);
            assertTrue(queries.get(2).contains(" w KQkq - "));
        } finally {overlay.destroy();}
    }

}
