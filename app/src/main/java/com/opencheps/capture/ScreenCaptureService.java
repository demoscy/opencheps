package com.opencheps.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.Choreographer;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.opencheps.R;
import com.opencheps.engine.StockfishEngine;
import com.opencheps.overlay.ArrowOverlayView;
import com.opencheps.overlay.FloatingPanel;
import com.opencheps.overlay.OverlayService;
import com.opencheps.util.Prefs;
import com.opencheps.util.BoardArea;
import com.opencheps.util.BoardMapping;
import com.opencheps.util.CaptureViewport;
import com.opencheps.util.Side;
import com.opencheps.util.StudyLine;
import com.opencheps.vision.BoardDetector;
import com.opencheps.vision.BoardFrameChange;
import com.opencheps.vision.FenBuilder;
import com.opencheps.vision.MoveAnalyzer;
import com.opencheps.vision.PieceClassifier;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ScreenCaptureService extends Service {

    private static final String TAG = "OC/Capture";
    private static final String CHANNEL_ID = "opencheps_capture";

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_USER_SIDE   = "user_side";
    public static final String ACTION_STOP       = "com.opencheps.STOP";

    private static final long    SAMPLE_PERIOD_MS = 500;

    private MediaProjection projection;
    private VirtualDisplay   virtualDisplay;
    private ImageReader      reader;
    private HandlerThread    workerThread;
    private Handler          worker;

    private StockfishEngine  engine;
    private boolean engineReady;
    private String startupFailure;
    private PieceClassifier  classifier;

    private int captureW, captureH, density;
    private int screenW, screenH;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean editing, stopping;
    private final AtomicInteger epoch = new AtomicInteger();
    private int frameEpoch, displayRotation;
    private long captureNotBefore;
    private volatile int projectedW, projectedH;
    private volatile boolean capturedContentVisible = true;
    private volatile Rect systemInsets = new Rect();
    private CaptureViewport captureViewport;
    private boolean framePending;
    private int[] cleanReference;
    private long lastCleanRead, nextCleanAllowed;

    private BoardArea manualArea;
    private float capToScreenX = 1f, capToScreenY = 1f;
    private volatile boolean busy = false;
    private volatile boolean paused = false;

    private Side userSide = Side.WHITE;
    private Side boardSide = Side.WHITE;
    private boolean turnExplicit;
    private List<BoardArea> manualAreas = new ArrayList<>();

    private char[][] confirmedGrid;
    private char[][] candidateGrid;
    private int candidateRepeats;
    private Side observedStm = Side.WHITE;
    private String lastEngineFen = "";
    private int lastEngineStrength;
    private String lastDisplayedArrowFen = "";
    private BoardDetector.Board lastKnownBoard;
    private List<StockfishEngine.Pv> currentPvs = new ArrayList<>();
    private List<ArrowOverlayView.Arrow> currentArrows = new ArrayList<>();

    @Nullable @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (projection != null) return START_NOT_STICKY;
        if (intent == null || !intent.hasExtra(EXTRA_RESULT_DATA)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startInForeground();

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        String sideStr = intent.getStringExtra(EXTRA_USER_SIDE);
        if (sideStr == null) sideStr = Prefs.userSide(this);
        userSide = "b".equals(sideStr) ? Side.BLACK : Side.WHITE;

        boardSide = userSide;
        observedStm = userSide;

        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        try {
            projection = mpm.getMediaProjection(resultCode, data);
        } catch (RuntimeException e) {
            Log.e(TAG, "Screen capture grant unavailable; request a fresh grant", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (projection == null) {
            Log.e(TAG, "MediaProjection acquisition failed");
            stopSelf();
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopSelf(); }
            @Override public void onCapturedContentResize(int width, int height) {
                if (stopping || projectedW == width && projectedH == height) return;
                projectedW = width;
                projectedH = height;
                systemInsets = OverlayService.displayInsets();
                epoch.incrementAndGet();
                OverlayService.clearAll();
                if (worker != null) worker.post(() -> {
                    if (stopping) return;
                    refreshDisplayGeometry();
                    drainReader();
                    captureNotBefore = System.nanoTime();
                });
            }
            @Override public void onCapturedContentVisibilityChanged(boolean visible) {
                if (stopping || capturedContentVisible == visible) return;
                capturedContentVisible = visible;
                epoch.incrementAndGet();
                OverlayService.clearAll();
                if (worker != null) worker.post(() -> {
                    invalidateAnalysis();
                    candidateGrid = null;
                    candidateRepeats = 0;
                    drainReader();
                    captureNotBefore = System.nanoTime();
                });
            }
        }, main);

        workerThread = new HandlerThread("oc-worker", Thread.NORM_PRIORITY);
        workerThread.start();
        worker = new Handler(workerThread.getLooper());

        engine = new StockfishEngine();
        manualAreas = Prefs.boardAreas(this);
        manualArea = manualAreas.isEmpty() ? null : manualAreas.get(0);

        worker.post(() -> {
            try {
                classifier = new PieceClassifier(getApplicationContext());
                engine.start(getApplicationContext());
                engineReady = true;
                panelStatus("Engine ready · playing as " + userSide);
            } catch (Exception | LinkageError e) {
                Log.e(TAG, "offline recognizer/engine start failed", e);
                startupFailure = "Recognizer/engine unavailable · restart capture";
                panelStatus(startupFailure);
            }
        });

        try {
            setupVirtualDisplay();
        } catch (RuntimeException e) {
            Log.e(TAG, "Screen capture setup failed", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        wirePanel();
        worker.postDelayed(sampleLoop, 1000);
        panelStatus("Starting as " + userSide);
        return START_NOT_STICKY;
    }

    private void wirePanel() {
        if (stopping) return;
        FloatingPanel p = OverlayService.panel();
        if (p == null) {
            main.postDelayed(this::wirePanel, 200);
            return;
        }
        p.placeBottomLeft(screenW, screenH);
        p.setUserSide(userSide);
        p.setPosition(observedStm, boardSide);
        p.setListener(new FloatingPanel.Listener() {
            @Override public void onPauseToggle(boolean isPaused) {
                paused = isPaused;
                epoch.incrementAndGet();
                OverlayService.setCapturing(false);
                OverlayService.clearAll();
                p.setStatus(isPaused ? "Paused" : "Resumed");
                p.setEval("");
                if (worker != null) worker.post(() -> {
                    lastEngineFen = "";
                    lastDisplayedArrowFen = "";
                    candidateGrid = null;
                    candidateRepeats = 0;
                    drainReader();
                    captureNotBefore = System.nanoTime();
                });
            }
            @Override public void onStop() { stopSelf(); }
            @Override public void onSetBoardArea() { beginCalibration(); }
            @Override public void onRecalibrate() {
                epoch.incrementAndGet();
                OverlayService.clearAll();
                p.setEval("");
                p.setStatus("Reading current position");
                worker.post(() -> { resetGameState(); frameEpoch = epoch.get(); updatePositionControls(); });
            }
            @Override public void onTurnChange(Side side) {
                epoch.incrementAndGet();
                OverlayService.clearAll();
                p.setEval("");
                worker.post(() -> {
                    resetGameState();
                    observedStm = side;
                    turnExplicit = true;
                    frameEpoch = epoch.get();
                    updatePositionControls();
                    panelStatus("Reading current position · " + side + " to move");
                });
            }
            @Override public void onFlipBoard() {
                epoch.incrementAndGet();
                OverlayService.clearAll();
                p.setEval("");
                worker.post(() -> {
                    Side turn = observedStm;
                    boardSide = boardSide == Side.WHITE ? Side.BLACK : Side.WHITE;
                    resetGameState();
                    observedStm = turn;
                    turnExplicit = true;
                    frameEpoch = epoch.get();
                    updatePositionControls();
                });
            }
            @Override public void onSideChange(Side newSide) {
                epoch.incrementAndGet();
                Prefs.setUserSide(getApplicationContext(), newSide == Side.BLACK ? "b" : "w");
                OverlayService.clearAll();
                p.setEval("");
                worker.post(() -> {
                    userSide = newSide;
                    boardSide = newSide;
                    resetGameState();
                    frameEpoch = epoch.get();
                    updatePositionControls();
                    panelStatus("Reading current position · you play " + newSide);
                });
            }
        });
    }

    private void beginCalibration() {
        if (editing || stopping || worker == null) return;
        editing = true;
        epoch.incrementAndGet();
        OverlayService.suspendForCalibration();
        worker.post(() -> {
            if (stopping) return;
            try {
                // Detach before the editor is visible: its grid/handles never enter a sampled frame.
                virtualDisplay.setSurface(null);
                drainReader();
                refreshDisplayGeometry();
                BoardArea.Pixels saved = manualArea == null ? null
                        : manualArea.toPixels(screenW, screenH, displayRotation);
                Rect initial;
                if (saved != null) initial = new Rect(saved.left, saved.top,
                        saved.left + saved.side, saved.top + saved.side);
                else if (lastKnownBoard != null) initial = boardBoundsOnScreen(lastKnownBoard);
                else {
                    int side = Math.round(Math.min(screenW, screenH) * 0.85f);
                    initial = new Rect((screenW - side) / 2, (screenH - side) / 2,
                            (screenW + side) / 2, (screenH + side) / 2);
                }
                int width = screenW, height = screenH, rotation = displayRotation;
                main.post(() -> {
                    if (!stopping && editing) OverlayService.showCalibration(width, height, rotation,
                            initial, this::finishCalibration);
                });
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not suspend capture for calibration", e);
                main.post(this::stopSelf);
            }
        });
    }

    private void finishCalibration(boolean changed, BoardArea area) {
        if (stopping || !editing) return;
        if (changed) Prefs.setBoardArea(this, area);
        epoch.incrementAndGet();
        // removeViewImmediate has run; allow the compositor to remove the editor before reattaching.
        main.postDelayed(() -> {
            if (stopping || worker == null) return;
            worker.post(() -> {
                if (stopping) return;
                try {
                    if (changed) {
                        manualAreas = Prefs.boardAreas(this);
                        manualArea = manualAreas.isEmpty() ? null : manualAreas.get(0);
                        resetGameState();
                        lastKnownBoard = null;
                    }
                    lastEngineFen = "";
                    candidateGrid = null;
                    candidateRepeats = 0;
                    refreshDisplayGeometry();
                    drainReader();
                    captureNotBefore = System.nanoTime();
                    main.post(() -> {
                        if (stopping) return;
                        OverlayService.resumeAfterCalibration();
                        editing = false;
                        FloatingPanel panel = OverlayService.panel();
                        if (panel != null) {
                            panel.setEval("");
                            panel.setStatus(paused ? "Paused" : changed
                                    ? (area == null ? "Auto board detection" : "Manual board area saved")
                                    : "Calibration cancelled");
                        }
                    });
                } catch (RuntimeException e) {
                    Log.e(TAG, "Could not resume capture after calibration", e);
                    main.post(this::stopSelf);
                }
            });
        }, 250);
    }

    private void resetGameState() {
        confirmedGrid = null;
        candidateGrid = null;
        candidateRepeats = 0;
        lastEngineFen = "";
        lastDisplayedArrowFen = "";
        observedStm = userSide;
        turnExplicit = false;
        cleanReference = null;
        nextCleanAllowed = 0;
        currentArrows.clear();
        currentPvs.clear();
    }

    private final Runnable sampleLoop = new Runnable() {
        @Override public void run() {
            try {
                if (stopping) return;
                if (!Settings.canDrawOverlays(ScreenCaptureService.this)) {
                    main.post(ScreenCaptureService.this::stopSelf);
                    return;
                }
                main.post(() -> { if (!stopping) systemInsets = OverlayService.displayInsets(); });
                refreshDisplayGeometry();
                frameEpoch = epoch.get();
                if (paused || editing) drainReader();
                else if (!capturedContentVisible) {
                    invalidateAnalysis();
                    panelStatus("Shared app not visible · waiting");
                    drainReader();
                } else if (captureViewport == null) {
                    invalidateAnalysis();
                    panelStatus("Shared app layout unsupported · use full-screen app or entire screen");
                    drainReader();
                } else if (SystemClock.uptimeMillis() >= nextCleanAllowed) {
                    if (!lastEngineFen.isEmpty() && lastEngineStrength != Prefs.strengthElo(ScreenCaptureService.this))
                        invalidateAnalysis();
                    refreshStudyLine();
                    boolean appOnly = captureViewport.width != screenW || captureViewport.height != screenH;
                    if (appOnly) requestVisibleFrame(true);
                    else if (needsCleanRead(SystemClock.uptimeMillis())) requestCleanFrame();
                    else requestVisibleFrame(false);
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "sample error", e);
                frameEpoch = epoch.get();
                invalidateAnalysis();
                panelStatus("Capture unavailable · retrying");
                finishFrameCapture();
            }
            if (!stopping && worker != null) worker.postDelayed(this, SAMPLE_PERIOD_MS);
        }
    };

    private boolean needsCleanRead(long now) {
        return cleanReference == null || candidateGrid != null && candidateRepeats < 2
                || confirmedGrid != null && candidateGrid == null && observedStm == userSide && lastEngineFen.isEmpty()
                || now - lastCleanRead >= 15000;
    }

    private boolean canCapture(int expected) {
        return !stopping && !paused && !editing && capturedContentVisible && epoch.get() == expected;
    }

    /** App-only frames exclude other apps' windows. Whole-display probes only detect change. */
    private void requestVisibleFrame(boolean appOnly) {
        if (framePending || reader == null) return;
        framePending = true;
        int expected = epoch.get();
        int width = captureW, height = captureH;
        CaptureViewport viewport = captureViewport;
        main.post(() -> {
            if (!canCapture(expected)) {
                if (!stopping) worker.post(this::finishFrameCapture);
                return;
            }
            int[] mask = appOnly ? null : OverlayService.captureMask(width, height, viewport);
            worker.post(() -> {
                if (!canCapture(expected)) { finishFrameCapture(); return; }
                try {
                    drainReader();
                    long after = System.nanoTime();
                    virtualDisplay.setSurface(reader.getSurface());
                    worker.postDelayed(() -> acquireFrame(expected, after,
                            SystemClock.uptimeMillis() + 250, appOnly, mask), 16);
                } catch (RuntimeException e) {
                    frameEpoch = expected;
                    invalidateAnalysis();
                    finishFrameCapture();
                }
            });
        });
    }

    private void rememberCleanFrame(Bitmap bitmap) {
        cleanReference = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(cleanReference, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        lastCleanRead = SystemClock.uptimeMillis();
    }

    private void requestCleanFrame() {
        if (framePending || reader == null) return;
        framePending = true;
        virtualDisplay.setSurface(null);
        drainReader();
        int expected = epoch.get();
        main.post(() -> {
            if (!canCapture(expected)) {
                if (!stopping) worker.post(this::finishFrameCapture);
                return;
            }
            OverlayService.setCapturing(true);
            // Let visibility changes reach the compositor. Then require an image produced
            // after the second display frame, rather than sleeping and accepting a queued image.
            Choreographer.getInstance().postFrameCallback(first ->
                    Choreographer.getInstance().postFrameCallback(second -> {
                        if (!stopping) worker.post(() -> attachForCleanFrame(expected, second));
                    }));
        });
    }

    private void attachForCleanFrame(int expected, long cleanAfter) {
        if (!canCapture(expected) || reader == null) { finishFrameCapture(); return; }
        try {
            drainReader();
            captureNotBefore = Math.max(cleanAfter, System.nanoTime());
            // Re-enabling the surface requests a new frame even if a shared app is static.
            // Keep it detached while our overlays are visible so they never enter analysis.
            virtualDisplay.setSurface(reader.getSurface());
            worker.postDelayed(() -> acquireCleanFrame(expected, captureNotBefore,
                    SystemClock.uptimeMillis() + 250), 16);
        } catch (RuntimeException e) {
            Log.w(TAG, "clean frame surface unavailable", e);
            frameEpoch = expected;
            invalidateAnalysis();
            panelStatus("Capture unavailable · retrying");
            finishFrameCapture();
        }
    }

    private void detachCaptureSurface() {
        try { if (virtualDisplay != null) virtualDisplay.setSurface(null); }
        catch (RuntimeException e) { Log.w(TAG, "capture surface detach failed", e); }
    }

    private void acquireCleanFrame(int expected, long cleanAfter, long deadline) {
        acquireFrame(expected, cleanAfter, deadline, true, null);
    }

    private void acquireFrame(int expected, long cleanAfter, long deadline, boolean clean, int[] mask) {
        if (!canCapture(expected) || reader == null) { finishFrameCapture(); return; }
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            boolean waitingForSourceSize = Build.VERSION.SDK_INT >= 34 && (projectedW <= 0 || projectedH <= 0);
            if (waitingForSourceSize || image == null || image.getTimestamp() <= Math.max(cleanAfter, captureNotBefore)) {
                if (image != null) { image.close(); image = null; }
                if (SystemClock.uptimeMillis() < deadline) {
                    worker.postDelayed(() -> acquireFrame(expected, cleanAfter, deadline, clean, mask), 16);
                } else {
                    frameEpoch = expected;
                    invalidateAnalysis();
                    panelStatus("Waiting for fresh capture frame");
                    finishFrameCapture();
                }
                return;
            }
            Image owned = image;
            image = null;
            if (clean) sampleOnce(owned, expected);
            else {
                Bitmap bitmap = null;
                try {
                    bitmap = imageToBitmap(owned);
                    int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
                    bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
                    boolean changed = BoardFrameChange.changed(cleanReference, pixels, mask,
                            bitmap.getWidth(), bitmap.getHeight(), lastKnownBoard);
                    finishFrameCapture();
                    if (changed && canCapture(expected)) {
                        frameEpoch = expected;
                        invalidateAnalysis();
                        requestCleanFrame();
                    }
                } finally {
                    owned.close();
                    if (bitmap != null) bitmap.recycle();
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "clean frame acquisition failed", e);
            frameEpoch = expected;
            invalidateAnalysis();
            panelStatus("Capture unavailable · retrying");
            finishFrameCapture();
        } finally {
            if (image != null) image.close();
        }
    }

    private void finishFrameCapture() {
        detachCaptureSurface();
        framePending = false;
        main.post(() -> { if (!stopping) OverlayService.setCapturing(false); });
    }

    private void drainReader() {
        if (reader == null) return;
        while (true) {
            Image im;
            try { im = reader.acquireNextImage(); }
            catch (Exception e) { break; }
            if (im == null) break;
            im.close();
        }
    }

    private void sampleOnce(Image img, int expected) {
        if (busy || !canCapture(expected)) {
            img.close();
            finishFrameCapture();
            return;
        }
        frameEpoch = expected;
        busy = true;
        Bitmap bmp = null;
        try {
            bmp = imageToBitmap(img);
            img.close();
            img = null;
            // Restore controls as soon as the clean bitmap is copied, before engine work.
            detachCaptureSurface();
            main.post(() -> { if (!stopping) OverlayService.setCapturing(false); });

            if (classifier == null || !engineReady) {
                panelStatus(startupFailure == null ? "Waiting for offline recognizer / engine" : startupFailure);
                return;
            }
            Recognition recognition = recognizeBoard(bmp);
            if (recognition == null) {
                candidateGrid = null;
                candidateRepeats = 0;
                invalidateAnalysis();
                panelStatus("Board unclear · Set board area / use 2D pieces");
                publish(OverlayService::clearAll);
                cleanReference = null;
                nextCleanAllowed = SystemClock.uptimeMillis() + 2000;
                return;
            }
            BoardDetector.Board board = recognition.board;
            char[][] grid = recognition.grid;
            if (recognition.area != manualArea) {
                // A better saved crop changes geometry, not the known side to move.
                invalidateAnalysis();
                manualArea = recognition.area;
            }
            lastKnownBoard = board;
            rememberCleanFrame(bmp);
            Rect screenBounds = boardBoundsOnScreen(board);
            publish(() -> {
                OverlayService.setBoardBounds(screenBounds);
                OverlayService.setBoardFrameColor(0x6600C853);
            });

            processRecognizedGrid(grid, board, bmp);
        } catch (Exception e) {
            Log.w(TAG, "loop iter failed: " + e.getMessage(), e);
            invalidateAnalysis();
            panelStatus("Recognition unavailable · retrying");
            cleanReference = null;
        } finally {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            if (img != null) try { img.close(); } catch (Exception ignored) {}
            busy = false;
            finishFrameCapture();
        }
    }

    private static final class Recognition {
        final BoardDetector.Board board;
        final char[][] grid;
        final BoardArea area;
        Recognition(BoardDetector.Board board, char[][] grid, BoardArea area) {
            this.board = board; this.grid = grid; this.area = area;
        }
    }

    private Recognition recognizeBoard(Bitmap bitmap) throws Exception {
        if (manualAreas.isEmpty()) {
            BoardDetector.Board board = BoardDetector.locate(bitmap);
            char[][] grid = board == null ? null : classifier.classifyBoard(bitmap, board);
            return grid != null && validGrid(grid) ? new Recognition(board, grid, null) : null;
        }
        // Manual mode never substitutes an approximate automatic detector rectangle.
        List<BoardArea> candidates = new ArrayList<>(manualAreas);
        if (manualArea != null) { candidates.remove(manualArea); candidates.add(0, manualArea); }
        Recognition best = null;
        float bestConfidence = -1;
        for (BoardArea area : candidates) {
            BoardArea.Pixels physical = area.toPixels(screenW, screenH, displayRotation);
            BoardArea.Pixels pixels = captureViewport == null ? null
                    : captureViewport.crop(physical, captureW, captureH);
            if (pixels == null) continue;
            BoardDetector.Board board = new BoardDetector.Board(pixels.left, pixels.top, pixels.side, 1);
            PieceClassifier.Result result = classifier.readBoard(bitmap, board);
            // Prefer the active crop on near ties to avoid oscillating between close saves.
            if (result != null && validGrid(result.grid) && result.confidence > bestConfidence + .01f) {
                best = new Recognition(board, result.grid, area);
                bestConfidence = result.confidence;
            }
        }
        return best;
    }

    private void updatePositionControls() {
        Side turn = observedStm, bottom = boardSide;
        publish(() -> {
            FloatingPanel panel = OverlayService.panel();
            if (panel != null) panel.setPosition(turn, bottom);
        });
    }

    private void processRecognizedGrid(char[][] grid, BoardDetector.Board board, Bitmap bmp) {
        if (!validGrid(grid)) {
            candidateGrid = null;
            candidateRepeats = 0;
            invalidateAnalysis();
            panelStatus("Position unclear · check area and board orientation");
            return;
        }

        if (gridsEqual(grid, confirmedGrid)) {
            candidateGrid = null;
            candidateRepeats = 0;

            if (observedStm == userSide && !lastEngineFen.equals(fenOf(confirmedGrid))) {
                queryEngineAndRender(board, bmp);
            } else if (observedStm == userSide) {
                repushCurrentArrows(board);
                panelStatus("Your move (ready)");
            } else {
                publish(OverlayService::clearArrowsOnly);
                panelStatus(observedStm + "'s turn — waiting");
            }
            return;
        }

        // A move or uncertain recognition invalidates the old recommendation immediately.
        invalidateAnalysis();
        if (!gridsEqual(grid, candidateGrid)) {
            candidateGrid = grid;
            candidateRepeats = 1;
            panelStatus("Saw change…");
            return;
        }
        candidateRepeats++;

        if (candidateRepeats < 2) {
            panelStatus("Saw change… confirming");
            return;
        }

        if (confirmedGrid == null) {
            confirmedGrid = grid;
            if (!turnExplicit && gridsEqual(grid, startingGridFor(boardSide))) observedStm = Side.WHITE;
            candidateGrid = null;
            candidateRepeats = 0;
            updatePositionControls();
            if (observedStm == userSide) queryEngineAndRender(board, bmp);
            else panelStatus(observedStm + "'s turn · tap Turn to correct");
            return;
        }

        MoveAnalyzer.Result m = MoveAnalyzer.analyze(confirmedGrid, grid, observedStm);
        if (m.kind == MoveAnalyzer.Kind.UNKNOWN || m.mover == null) {
            // A new puzzle/history jump has no recoverable turn. Require the visible Turn
            // control or Read current position instead of silently forcing the user's turn.
            panelStatus("New position · tap ↻ / Turn");
            return;
        }

        FenBuilder.Validation v = FenBuilder.validate(
                boardSide == Side.BLACK ? FenBuilder.flip(grid) : grid);
        if (!v.ok) {
            panelStatus("Position invalid (" + v.reason + ") · keeping previous");
            return;
        }

        confirmedGrid = grid;
        observedStm = m.mover == Side.WHITE ? Side.BLACK : Side.WHITE;
        updatePositionControls();
        candidateGrid = null;
        candidateRepeats = 0;

        panelStatus(m.kind == MoveAnalyzer.Kind.TWO_PLIES ? "Caught up after both moves"
                : m.mover + " played " + m.kind);

        if (observedStm == userSide) {
            queryEngineAndRender(board, bmp);
        } else {
            publish(OverlayService::clearArrowsOnly);
            currentArrows.clear();
            currentPvs.clear();
            lastDisplayedArrowFen = "";
            panelStatus(observedStm + "'s turn — waiting");
            panelEval("");
        }
    }

    private void invalidateAnalysis() {
        currentArrows.clear();
        currentPvs.clear();
        lastEngineFen = "";
        lastDisplayedArrowFen = "";
        publish(OverlayService::clearArrowsOnly);
        panelEval("");
    }

    private boolean validGrid(char[][] grid) {
        return grid != null && FenBuilder.validate(boardSide == Side.BLACK ? FenBuilder.flip(grid) : grid).ok;
    }

    private void queryEngineAndRender(BoardDetector.Board board, Bitmap currentBitmap) {
        if (confirmedGrid == null) return;
        FenBuilder.Validation v = FenBuilder.validate(
                boardSide == Side.BLACK
                        ? FenBuilder.flip(confirmedGrid) : confirmedGrid);
        if (!v.ok) {
            panelStatus("Invalid: " + v.reason);
            return;
        }
        String fen = FenBuilder.build(confirmedGrid, boardSide, userSide);
        if (fen == null) { panelStatus("FEN build failed"); return; }
        int strength = Prefs.strengthElo(getApplicationContext());
        if (fen.equals(lastEngineFen) && strength == lastEngineStrength) {
            repushCurrentArrows(board);
            return;
        }
        int think = Prefs.thinkMs(getApplicationContext());
        panelStatus("Thinking " + think + " ms…");
        try {
            long t0 = System.currentTimeMillis();
            List<StockfishEngine.Pv> pvs = engine.analyze(fen, think, strength);
            long dt = System.currentTimeMillis() - t0;
            if (strength != Prefs.strengthElo(getApplicationContext())) {
                invalidateAnalysis();
                panelStatus("Practice strength changed · updating");
                return;
            }
            if (pvs.isEmpty()) { panelStatus("No legal move"); return; }
            lastEngineFen = fen;
            lastEngineStrength = strength;
            currentPvs = new ArrayList<>(pvs.subList(0, 1));
            currentArrows = buildArrows(currentPvs, board);
            lastDisplayedArrowFen = fen;
            List<ArrowOverlayView.Arrow> rendered = new ArrayList<>(currentArrows);
            Rect renderedBounds = boardBoundsOnScreen(board);
            publish(() -> OverlayService.pushArrows(rendered, renderedBounds));
            StockfishEngine.Pv best = pvs.get(0);
            String evalStr = !best.hasScore ? "" : best.mate
                    ? "M" + Math.abs(plyToMate(best.cp))
                    : String.format("%+.2f", best.cp / 100.0);
            panelEval("YOU (" + userSide + ") " + formatUci(best.uci) + "  " + evalStr);
            refreshStudyLine();
            panelStatus(strength == 0 ? "Your move (" + dt + " ms)" : "Practice · " + strength + " Elo (approx.)");
        } catch (Exception e) {
            Log.w(TAG, "engine query failed", e);
            panelStatus("Engine query failed");
        }
    }

    private void repushCurrentArrows(BoardDetector.Board board) {
        if (currentArrows == null || currentArrows.isEmpty()) return;
        List<ArrowOverlayView.Arrow> rebuilt = rebuildArrowsForNewBounds(board);
        Rect bounds = boardBoundsOnScreen(board);
        publish(() -> OverlayService.pushArrows(rebuilt, bounds));
    }

    private List<ArrowOverlayView.Arrow> rebuildArrowsForNewBounds(BoardDetector.Board board) {
        return buildArrows(currentPvs, board);
    }

    private List<ArrowOverlayView.Arrow> buildArrows(
            List<StockfishEngine.Pv> pvs, BoardDetector.Board board) {
        List<ArrowOverlayView.Arrow> out = new ArrayList<>();
        Side persp = boardSide;
        BoardMapping mapping = mappingFor(board);
        float cellAvg = (mapping.width + mapping.height) / 16;
        for (int i = 0; i < Math.min(1, pvs.size()); i++) {
            String uci = pvs.get(i).uci;
            if (uci == null || uci.length() < 4) continue;
            int fx = uci.charAt(0) - 'a', fy = 8 - (uci.charAt(1) - '0');
            int tx = uci.charAt(2) - 'a', ty = 8 - (uci.charAt(3) - '0');
            float[] from = mapping.squareCenter(fx, fy, persp == Side.BLACK);
            float[] to = mapping.squareCenter(tx, ty, persp == Side.BLACK);
            String lbl = (i == 0 ? "★ " : "") + uci;
            float width = Math.max(8f, 18f * Math.min(1.5f, cellAvg / 60f));
            out.add(new ArrowOverlayView.Arrow(from[0], from[1], to[0], to[1], 0xFF00C853, width, lbl));
        }
        return out;
    }

    private String fenOf(char[][] grid) {
        if (grid == null) return "";
        String f = FenBuilder.build(grid, boardSide, userSide);
        return f == null ? "" : f;
    }

    private boolean gridsEqual(char[][] a, char[][] b) {
        if (a == null || b == null) return false;
        for (int r = 0; r < 8; r++) if (!Arrays.equals(a[r], b[r])) return false;
        return true;
    }

    private char[][] startingGridFor(Side userSide) {
        char[][] base = {
            {'r','n','b','q','k','b','n','r'},
            {'p','p','p','p','p','p','p','p'},
            {'.','.','.','.','.','.','.','.'},
            {'.','.','.','.','.','.','.','.'},
            {'.','.','.','.','.','.','.','.'},
            {'.','.','.','.','.','.','.','.'},
            {'P','P','P','P','P','P','P','P'},
            {'R','N','B','Q','K','B','N','R'},
        };
        if (userSide == Side.BLACK) {
            char[][] o = new char[8][8];
            for (int r = 0; r < 8; r++) for (int f = 0; f < 8; f++) o[r][f] = base[7-r][7-f];
            return o;
        }
        return base;
    }

    private int plyToMate(int cp) { return cp > 0 ? 100000 - cp : -(100000 + cp); }

    private String formatUci(String uci) {
        if (uci == null || uci.length() < 4) return uci == null ? "?" : uci;
        return uci.substring(0, 2) + "→" + uci.substring(2, 4)
                + (uci.length() > 4 ? "=" + Character.toUpperCase(uci.charAt(4)) : "");
    }

    private BoardMapping mappingFor(BoardDetector.Board board) {
        return new BoardMapping(board.left, board.top, board.side, capToScreenX, capToScreenY,
                captureViewport == null ? 0 : captureViewport.left,
                captureViewport == null ? 0 : captureViewport.top);
    }

    private Rect boardBoundsOnScreen(BoardDetector.Board board) {
        BoardMapping mapping = mappingFor(board);
        return new Rect(Math.round(mapping.left), Math.round(mapping.top),
                Math.round(mapping.left + mapping.width), Math.round(mapping.top + mapping.height));
    }

    private void publish(Runnable action) {
        int expected = frameEpoch;
        main.post(() -> {
            if (!stopping && !editing && !paused && epoch.get() == expected) action.run();
        });
    }

    private void panelStatus(String s) {
        publish(() -> {
            FloatingPanel p = OverlayService.panel();
            if (p != null) p.setStatus(s);
        });
    }

    private void refreshStudyLine() {
        String line = Prefs.showStudyLine(this) && !lastEngineFen.isEmpty() && !currentPvs.isEmpty()
                ? StudyLine.format(currentPvs.get(0).moves, userSide) : "";
        publish(() -> {
            FloatingPanel panel = OverlayService.panel();
            if (panel != null) panel.setContinuation(line);
        });
    }

    private void panelEval(String s) {
        publish(() -> {
            FloatingPanel p = OverlayService.panel();
            if (p != null) p.setEval(s);
        });
    }

    /** Reuse the existing VirtualDisplay: Android 14 grants allow only one creation. */
    private void refreshDisplayGeometry() {
        if (stopping || virtualDisplay == null) return;
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(dm);
        int rotation = wm.getDefaultDisplay().getRotation();
        Rect insets = systemInsets;
        CaptureViewport next = CaptureViewport.resolve(dm.widthPixels, dm.heightPixels,
                projectedW, projectedH, insets.left, insets.top, insets.right, insets.bottom);
        boolean physicalChange = screenW != dm.widthPixels || screenH != dm.heightPixels || displayRotation != rotation;
        boolean sameViewport = next == null ? captureViewport == null : next.sameAs(captureViewport);
        if (!physicalChange && sameViewport) return;
        epoch.incrementAndGet();
        frameEpoch = epoch.get();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        displayRotation = rotation;
        density = dm.densityDpi;
        captureViewport = next;
        int sourceW = next == null ? screenW : next.width;
        int sourceH = next == null ? screenH : next.height;
        float scale = Math.min(1f, 800f / Math.max(sourceW, sourceH));
        captureW = Math.round(sourceW * scale);
        captureH = Math.round(sourceH * scale);
        capToScreenX = (float) sourceW / captureW;
        capToScreenY = (float) sourceH / captureH;
        virtualDisplay.setSurface(null);
        if (reader != null) reader.close();
        reader = ImageReader.newInstance(captureW, captureH, PixelFormat.RGBA_8888, 4);
        virtualDisplay.resize(captureW, captureH, density);
        captureNotBefore = System.nanoTime();
        resetForCaptureGeometry(physicalChange);
        main.post(() -> {
            if (stopping) return;
            if (physicalChange) OverlayService.cancelCalibration();
            OverlayService.setCapturing(false);
            OverlayService.clearAll();
            FloatingPanel p = OverlayService.panel();
            if (p != null) p.placeBottomLeft(screenW, screenH);
        });
    }

    private void resetForCaptureGeometry(boolean physicalChange) {
        lastKnownBoard = null;
        cleanReference = null;
        nextCleanAllowed = 0;
        if (physicalChange) resetGameState();
        else {
            candidateGrid = null;
            candidateRepeats = 0;
            invalidateAnalysis();
        }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (stopping) return;
        epoch.incrementAndGet();
        OverlayService.cancelCalibration();
        OverlayService.clearAll();
        if (worker != null) worker.post(this::refreshDisplayGeometry);
    }

    private void setupVirtualDisplay() {
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        displayRotation = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        captureViewport = CaptureViewport.resolve(screenW, screenH, 0, 0, 0, 0, 0, 0);

        float maxEdge = Math.max(screenW, screenH);
        float scale = Math.min(1f, 800f / maxEdge);
        captureW = Math.round(screenW * scale);
        captureH = Math.round(screenH * scale);
        density  = dm.densityDpi;
        capToScreenX = (float) screenW / captureW;
        capToScreenY = (float) screenH / captureH;

        Log.i(TAG, "screen=" + screenW + "x" + screenH
                + " capture=" + captureW + "x" + captureH);

        reader = ImageReader.newInstance(captureW, captureH, PixelFormat.RGBA_8888, 4);
        virtualDisplay = projection.createVirtualDisplay(
                "OpenChepsVD", captureW, captureH, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null, null, worker);
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int w = image.getWidth();
        int h = image.getHeight();
        int rowPadding = rowStride - pixelStride * w;
        int bitmapW = w + rowPadding / pixelStride;

        Bitmap raw = Bitmap.createBitmap(bitmapW, h, Bitmap.Config.ARGB_8888);
        buf.rewind();
        raw.copyPixelsFromBuffer(buf);
        if (bitmapW != w) {
            Bitmap cropped = Bitmap.createBitmap(raw, 0, 0, w, h);
            raw.recycle();
            return cropped;
        }
        return raw;
    }

    private void startInForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Intent stopIntent = new Intent(this, ScreenCaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .addAction(0, "Stop", stopPi)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(7, n);
        }
        startService(new Intent(this, com.opencheps.overlay.OverlayService.class));
    }

    @Override public void onDestroy() {
        stopping = true;
        epoch.incrementAndGet();
        main.removeCallbacksAndMessages(null);
        OverlayService.cancelCalibration();
        if (worker != null) {
            worker.removeCallbacksAndMessages(null);
            // Serialize cleanup with any in-flight classifier / Stockfish operation.
            worker.post(() -> {
                try { if (engine != null) engine.stop(); } catch (Exception ignored) {}
                try { if (classifier != null) classifier.close(); } catch (Exception ignored) {}
                cleanReference = null;
                try {
                    if (virtualDisplay != null) virtualDisplay.release();
                } catch (RuntimeException e) { Log.w(TAG, "display cleanup", e); }
                try {
                    if (reader != null) reader.close();
                } catch (RuntimeException e) { Log.w(TAG, "reader cleanup", e); }
                try {
                    if (projection != null) projection.stop();
                } catch (RuntimeException e) { Log.w(TAG, "projection cleanup", e); }
                workerThread.quitSafely();
            });
        } else if (projection != null) projection.stop();
        stopService(new Intent(this, OverlayService.class));
        super.onDestroy();
    }
}
