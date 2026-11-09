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

}
