package com.opencheps.capture;

import android.app.Service;
import com.opencheps.engine.StockfishEngine;
import com.opencheps.overlay.ArrowOverlayView;
import com.opencheps.util.Side;
import com.opencheps.vision.BoardDetector;
import com.opencheps.vision.PieceClassifier;

import java.util.Collections;
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
        ReflectionHelpers.setField(service, "currentPvs", Collections.singletonList(
                new StockfishEngine.Pv("e2e4", 0, false)));
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
}
