package com.opencheps.overlay;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.WindowManager;
import android.view.View;
import org.robolectric.util.ReflectionHelpers;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowSettings;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 34})
public class OverlayServiceTest {
    private ServiceController<OverlayService> controller;

    @After public void cleanup() {
        if (controller != null) controller.destroy();
    }

    @Test public void rotationCancelsEditorOnceAndPreservesSavedSetting() {
        ShadowSettings.setCanDrawOverlays(true);
        controller = Robolectric.buildService(OverlayService.class).create();
        assertNotNull(OverlayService.panel());
        int[] cancelled = {0};
        OverlayService.suspendForCalibration();
        OverlayService.showCalibration(1080, 2340, 0, new Rect(40, 400, 1040, 1400),
                (changed, area) -> {
                    assertFalse(changed);
                    assertNull(area);
                    cancelled[0]++;
                });
        controller.get().onConfigurationChanged(new Configuration());
        OverlayService.cancelCalibration();
        assertEquals(1, cancelled[0]);
        OverlayService.resumeAfterCalibration();
        assertNotNull(OverlayService.panel());
    }

    @Test public void missingPermissionRejectsEditorWithoutLeakingService() {
        ShadowSettings.setCanDrawOverlays(false);
        controller = Robolectric.buildService(OverlayService.class).create();
        int[] cancelled = {0};
        OverlayService.showCalibration(1080, 2340, 0, new Rect(40, 400, 1040, 1400),
                (changed, area) -> { assertFalse(changed); cancelled[0]++; });
        assertEquals(1, cancelled[0]);
        assertNull(OverlayService.panel());
    }

    @Test @Config(sdk = 34) public void windowsShareFullDisplayOriginAndOnlyEditorAcceptsTouches() {
        WindowManager.LayoutParams arrows = OverlayWindow.fullScreen(false);
        WindowManager.LayoutParams editor = OverlayWindow.fullScreen(true);
        assertEquals(arrows.gravity, editor.gravity);
        assertEquals(0, editor.getFitInsetsTypes());
        assertEquals(0, arrows.getFitInsetsTypes());
        assertTrue((arrows.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
        assertEquals(0, editor.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, editor.type);
    }
    @Test public void cleanFrameCaptureHidesOwnPixelsAndDoesNotRevealCalibration() {
        ShadowSettings.setCanDrawOverlays(true);
        controller = Robolectric.buildService(OverlayService.class).create();
        ArrowOverlayView arrows = ReflectionHelpers.getField(controller.get(), "arrows");
        FloatingPanel panel = OverlayService.panel();
        View panelRoot = ReflectionHelpers.getField(panel, "root");
        assertEquals(View.VISIBLE, arrows.getVisibility());
        OverlayService.setCapturing(true);
        assertEquals(View.INVISIBLE, arrows.getVisibility());
        assertEquals(View.INVISIBLE, panelRoot.getVisibility());
        OverlayService.suspendForCalibration();
        OverlayService.setCapturing(false);
        assertEquals(View.INVISIBLE, arrows.getVisibility());
        assertEquals(View.INVISIBLE, panelRoot.getVisibility());
        OverlayService.resumeAfterCalibration();
        assertEquals(View.VISIBLE, arrows.getVisibility());
        assertEquals(View.VISIBLE, panelRoot.getVisibility());
    }

    @Test @Config(sdk = 34)
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void changeProbeMaskUsesActualArrowPixelsAndCaptureViewportOrigin() {
        ShadowSettings.setCanDrawOverlays(true);
        controller = Robolectric.buildService(OverlayService.class).create();
        ArrowOverlayView arrows = ReflectionHelpers.getField(controller.get(), "arrows");
        arrows.layout(0, 0, 400, 800);
        arrows.setArrows(java.util.Collections.singletonList(
                new ArrowOverlayView.Arrow(100, 300, 300, 200, 0xFF00C853, 12, "e2e4")));
        com.opencheps.util.CaptureViewport viewport = com.opencheps.util.CaptureViewport.resolve(
                400, 800, 400, 760, 0, 40, 0, 0);
        int[] mask = OverlayService.captureMask(200, 380, viewport);
        assertNotNull(mask);
        // Midpoint is physical (200,250): subtract top inset before downscaling.
        assertTrue((mask[105*200+100] >>> 24) != 0);
        assertEquals(0, mask[300*200+190]);
    }

    @Test public void clearingTheEvaluationAlsoClearsTheStudyContinuation() {
        ShadowSettings.setCanDrawOverlays(true);
        controller = Robolectric.buildService(OverlayService.class).create();
        FloatingPanel panel = OverlayService.panel();
        View root = ReflectionHelpers.getField(panel, "root");
        View line = root.findViewById(com.opencheps.R.id.panelContinuation);
        panel.setContinuation("W e2→e4   B e7→e5");
        assertEquals(View.VISIBLE, line.getVisibility());
        panel.setEval("");
        assertEquals(View.GONE, line.getVisibility());
    }

    @Test public void boardPreviewClosesOnceOnRotationAndRestoresOverlayVisibility() {
        ShadowSettings.setCanDrawOverlays(true);
        controller = Robolectric.buildService(OverlayService.class).create();
        int[] closed = {0};
        OverlayService.suspendForCalibration();
        OverlayService.showBoardPreview(android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888),
                "Uncertain e4 52%", () -> closed[0]++);
        assertNotNull(ReflectionHelpers.getField(controller.get(), "preview"));
        controller.get().onConfigurationChanged(new Configuration());
        OverlayService.cancelCalibration();
        assertEquals(1, closed[0]);
        assertNull(ReflectionHelpers.getField(controller.get(), "preview"));
        OverlayService.resumeAfterCalibration();
        ArrowOverlayView arrows = ReflectionHelpers.getField(controller.get(), "arrows");
        assertEquals(View.VISIBLE, arrows.getVisibility());
    }
    @Test @Config(sdk = 34)
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void minimizedPanelMasksOnlyTheBubbleRatherThanTheExpandedCard() {
        ShadowSettings.setCanDrawOverlays(true);
        controller = Robolectric.buildService(OverlayService.class).create();
        FloatingPanel panel = OverlayService.panel();
        panel.setMinimized(true);
        View root = ReflectionHelpers.getField(panel, "root");
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        int[] mask = OverlayService.captureMask(400, 800,
                com.opencheps.util.CaptureViewport.resolve(400,800,0,0,0,0,0,0));
        int opaque = 0;
        for (int pixel : mask) if ((pixel >>> 24) != 0) opaque++;
        assertTrue(opaque > 0);
        assertTrue(opaque <= root.getWidth()*root.getHeight());
        assertEquals(Math.round(152*root.getResources().getDisplayMetrics().density), root.getWidth());
    }

    @Test public void settingsVisibilityKeepsBubbleHiddenWhenACleanFrameFinishes() {
        ShadowSettings.setCanDrawOverlays(true);
        controller = Robolectric.buildService(OverlayService.class).create();
        FloatingPanel panel = OverlayService.panel();
        panel.setMinimized(true);
        View root = ReflectionHelpers.getField(panel, "root");
        OverlayService.setSettingsVisible(true);
        OverlayService.setCapturing(false);
        OverlayService.resumeAfterCalibration();
        assertEquals(View.INVISIBLE, root.getVisibility());
        OverlayService.setSettingsVisible(false);
        assertEquals(View.VISIBLE, root.getVisibility());
        assertTrue(panel.isMinimized());
    }

    @Test public void rejectedPreviewReleasesUnattachedBitmapWithoutAnOrphanWindow() {
        ShadowSettings.setCanDrawOverlays(false);
        controller = Robolectric.buildService(OverlayService.class).create();
        android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);
        int[] closed = {0};
        OverlayService.showBoardPreview(image, "test", () -> closed[0]++);
        assertEquals(1, closed[0]);
        assertTrue(image.isRecycled());
        assertNull(OverlayService.panel());
    }

}
