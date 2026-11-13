package com.opencheps.overlay;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.graphics.Rect;

import com.opencheps.util.BoardArea;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 34})
public class CalibrationOverlayTest {
    private CalibrationOverlay editor;
    private BoardArea saved;
    private int saves, autos, cancels;

    @Before public void setup() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        editor = new CalibrationOverlay(activity, 1080, 2340, 0,
                new Rect(100, 500, 900, 1300), new CalibrationOverlay.Listener() {
                    public void onSave(BoardArea area) { saved = area; saves++; }
                    public void onAuto() { autos++; }
                    public void onCancel() { cancels++; }
                });
        editor.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
        editor.layout(0, 0, 1080, 2340);
    }

    private void touch(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 0, action, x, y, 0);
        editor.getChildAt(0).onTouchEvent(event);
        event.recycle();
    }

    private void action(int index) {
        LinearLayout controls = (LinearLayout) editor.getChildAt(1);
        LinearLayout row = (LinearLayout) controls.getChildAt(1);
        row.getChildAt(index).performClick();
    }

    @Test public void draggingMovesSavedSquareAndDoesNotSaveUntilRequested() {
        touch(MotionEvent.ACTION_DOWN, 500, 900);
        touch(MotionEvent.ACTION_MOVE, 550, 1050);
        touch(MotionEvent.ACTION_UP, 550, 1050);
        assertEquals(0, saves);
        action(0);
        BoardArea.Pixels pixels = saved.toPixels(1080, 2340, 0);
        assertEquals(150, pixels.left);
        assertEquals(650, pixels.top);
        assertEquals(800, pixels.side);
    }

    @Test public void cornerResizingUsesInitialSizeAndPreservesFingerOffset() {
        touch(MotionEvent.ACTION_DOWN, 890, 1290);
        touch(MotionEvent.ACTION_MOVE, 990, 1390);
        touch(MotionEvent.ACTION_UP, 990, 1390);
        action(0);
        BoardArea.Pixels pixels = saved.toPixels(1080, 2340, 0);
        assertEquals(100, pixels.left);
        assertEquals(500, pixels.top);
        assertEquals(900, pixels.side);
    }

    @Test public void cancelAndAutoAreDistinctAndNeverSaveAnEditedArea() {
        action(2);
        assertEquals(1, cancels);
        assertEquals(0, autos);
        assertNull(saved);
        action(1);
        assertEquals(1, autos);
        assertEquals(0, saves);
    }
}
