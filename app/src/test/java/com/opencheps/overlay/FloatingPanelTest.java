package com.opencheps.overlay;

import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import com.opencheps.R;
import com.opencheps.ui.MainActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 34})
public class FloatingPanelTest {
    private ServiceController<OverlayService> service;
    private FloatingPanel panel;
    private View root, bubble;
    private int size;

    @Before public void setup() {
        ShadowSettings.setCanDrawOverlays(true);
        service = Robolectric.buildService(OverlayService.class).create();
        panel = OverlayService.panel();
        root = ReflectionHelpers.getField(panel, "root");
        bubble = root.findViewById(R.id.panelBubble);
        size = Math.round(48 * root.getResources().getDisplayMetrics().density);
        panel.placeBottomLeft(400, 800);
        ShadowLooper.idleMainLooper();
    }
    @After public void cleanup() { if (service != null) service.destroy(); }
    private void measure() {
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        ShadowLooper.idleMainLooper();
    }
    private void touch(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 10, action, x, y, 0);
        bubble.dispatchTouchEvent(event);
        event.recycle();
    }
    @Test public void minimizeUsesOnlyBubbleBoundsAndTapRestoresPausedControls() {
        root.findViewById(R.id.panelToggle).performClick();
        root.findViewById(R.id.panelMinimize).performClick();
        measure();
        assertTrue(panel.isMinimized());
        assertEquals(size, root.getWidth());
        assertEquals(size, root.getHeight());
        assertEquals(View.GONE, root.findViewById(R.id.panelExpanded).getVisibility());
        assertEquals(size, panel.boundsOnScreen().width());
        touch(MotionEvent.ACTION_DOWN, 10, 10);
        touch(MotionEvent.ACTION_UP, 10, 10);
        assertFalse(panel.isMinimized());
        assertTrue(panel.isPaused());
        assertEquals(View.VISIBLE, root.findViewById(R.id.panelExpanded).getVisibility());
        assertEquals(View.GONE, bubble.getVisibility());
    }
    @Test public void draggingClampsBothEdgesWithoutExpandingAndCancelIsNotATap() {
        panel.setMinimized(true);
        measure();
        touch(MotionEvent.ACTION_DOWN, 10, 10);
        touch(MotionEvent.ACTION_MOVE, 2000, 2000);
        touch(MotionEvent.ACTION_UP, 2000, 2000);
        WindowManager.LayoutParams lp = ReflectionHelpers.getField(panel, "lp");
        assertEquals(400-size, lp.x);
        assertEquals(800-size, lp.y);
        assertTrue(panel.isMinimized());
        touch(MotionEvent.ACTION_DOWN, 10, 10);
        touch(MotionEvent.ACTION_MOVE, -2000, -2000);
        touch(MotionEvent.ACTION_CANCEL, -2000, -2000);
        assertEquals(0, lp.x);
        assertEquals(0, lp.y);
        assertTrue(panel.isMinimized());
        touch(MotionEvent.ACTION_DOWN, 10, 10);
        touch(MotionEvent.ACTION_CANCEL, 10, 10);
        assertTrue(panel.isMinimized());
    }
    @Test public void ordinaryDragUpdatesWindowEvenWithoutHittingAnEdge() {
        panel.setMinimized(true);
        measure();
        WindowManager.LayoutParams lp = ReflectionHelpers.getField(panel, "lp");
        lp.x=40; lp.y=100;
        touch(MotionEvent.ACTION_DOWN, 10, 10);
        touch(MotionEvent.ACTION_MOVE, 50, 60);
        touch(MotionEvent.ACTION_UP, 50, 60);
        assertEquals(80, lp.x);
        assertEquals(150, lp.y);
        WindowManager.LayoutParams actual = (WindowManager.LayoutParams) root.getLayoutParams();
        assertEquals(lp.x, actual.x);
        assertEquals(lp.y, actual.y);
        assertTrue(panel.isMinimized());
    }
    @Test public void screenResizeAndCleanCapturePreserveBubbleState() {
        panel.setMinimized(true);
        measure();
        panel.placeBottomLeft(800,400);
        ShadowLooper.idleMainLooper();
        WindowManager.LayoutParams lp = ReflectionHelpers.getField(panel,"lp");
        assertTrue(lp.x >= 0 && lp.x+size <= 800);
        assertTrue(lp.y >= 0 && lp.y+size <= 400);
        OverlayService.setCapturing(true);
        assertEquals(View.INVISIBLE, root.getVisibility());
        OverlayService.suspendForCalibration();
        OverlayService.setCapturing(false);
        assertEquals(View.INVISIBLE, root.getVisibility());
        OverlayService.resumeAfterCalibration();
        assertEquals(View.VISIBLE, root.getVisibility());
        assertTrue(panel.isMinimized());
        service.destroy(); service = null;
        assertFalse(root.isAttachedToWindow());
        assertNull(OverlayService.panel());
    }
    @Test public void settingsLaunchesExistingActivityWithoutStartingAnotherService() {
        root.findViewById(R.id.panelSettings).performClick();
        Intent intent = shadowOf(service.get()).getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(MainActivity.class.getName(), intent.getComponent().getClassName());
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
        assertNull(shadowOf(service.get()).getNextStartedService());
    }
    @Test public void expandedControlsScrollOnShortScreensAndBubbleHasTheSharedPhysicalOrigin() {
        measure();
        panel.placeBottomLeft(400, 160);
        ShadowLooper.idleMainLooper();
        WindowManager.LayoutParams lp = ReflectionHelpers.getField(panel, "lp");
        assertEquals(160, lp.height);
        assertEquals(OverlayWindow.fullScreen(true).gravity, lp.gravity);
        if (android.os.Build.VERSION.SDK_INT >= 30) assertEquals(0, lp.getFitInsetsTypes());
        panel.setMinimized(true);
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, lp.height);
        assertTrue(panel.isMinimized());
    }
}
