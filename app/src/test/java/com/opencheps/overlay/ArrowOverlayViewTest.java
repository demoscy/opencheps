package com.opencheps.overlay;

import android.graphics.Rect;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class ArrowOverlayViewTest {
    @Test public void scoreChipsForOverlappingCandidatesSeparateWithinTheBoard() {
        ArrowOverlayView view = new ArrowOverlayView(RuntimeEnvironment.getApplication());
        Rect board = new Rect(20,20,340,340);
        view.setBoardBounds(board);
        List<RectF> placed = new ArrayList<>();
        for (int i=0; i<3; i++) {
            ArrowOverlayView.Arrow arrow = new ArrowOverlayView.Arrow(160,240,160+i*5,160,0xFF00C853,12,i==0?"★ +7.00":"+2.25");
            RectF box = view.labelBounds(arrow,placed);
            assertTrue(new RectF(board).contains(box));
            for (RectF other : placed) assertFalse(RectF.intersects(box,other));
            placed.add(box);
        }
    }
    @Test public void scoreChipsClampToEdgesAndVerySmallBoardBounds() {
        ArrowOverlayView view = new ArrowOverlayView(RuntimeEnvironment.getApplication());
        Rect board = new Rect(20,20,340,340);
        view.setBoardBounds(board);
        for (int x : new int[]{20,340}) for (int y : new int[]{20,340}) {
            RectF box = view.labelBounds(new ArrowOverlayView.Arrow(x,y,x,y,0,10,"★ +15.80"),new ArrayList<>());
            assertTrue(new RectF(board).contains(box));
        }
        board = new Rect(20,20,50,50);
        view.setBoardBounds(board);
        RectF box = view.labelBounds(new ArrowOverlayView.Arrow(20,20,50,50,0,10,"★ +15.80"),new ArrayList<>());
        assertTrue(new RectF(board).contains(box));
    }
}
