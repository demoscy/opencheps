package com.opencheps.overlay;

import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
        for (int i=0; i<5; i++) {
            ArrowOverlayView.Arrow arrow = new ArrowOverlayView.Arrow(160,240,160+i*5,160,0xFF00C853,12,i==0?"★ +7.00":"+2.25");
            RectF box = view.labelBounds(arrow,placed);
            assertTrue(new RectF(board).contains(box));
            for (RectF other : placed) assertFalse(RectF.intersects(box,other));
            placed.add(box);
        }
    }

    @Test public void laterCollinearArrowCannotPaintOverAnEarlierScoreOrItsColorBorder() throws Exception {
        ArrowOverlayView view = new ArrowOverlayView(RuntimeEnvironment.getApplication());
        view.layout(0,0,360,360);
        view.setBoardBounds(new Rect(20,20,340,340));
        ArrowOverlayView.Arrow first = new ArrowOverlayView.Arrow(120,300,120,220,0xFF00C853,18,"★ +7.00");
        ArrowOverlayView.Arrow later = new ArrowOverlayView.Arrow(120,320,120,80,0xFF29B6F6,12,"+3.00");
        view.setArrows(java.util.Arrays.asList(first,later));
        RectF chip = view.labelBounds(first,new ArrayList<>());
        Bitmap image = Bitmap.createBitmap(360,360,Bitmap.Config.ARGB_8888);
        try {
            view.draw(new Canvas(image));
            int x = Math.round(chip.centerX()), y = Math.round(chip.top+4);
            // Both shafts cross this pixel. It must be the opaque chip background.
            assertEquals(0xFF101810,image.getPixel(x,y));
            int border = image.getPixel(x,Math.round(chip.top));
            assertTrue(Color.green(border)>150 && Color.blue(border)<140);
            java.io.File directory = new java.io.File("build/reports/ui-preview");
            directory.mkdirs();
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(directory,"score-layering.png"))) {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));
            }
        } finally { image.recycle(); }
    }

    @Test public void fiveScoreChipsAtTheBoardEdgeUseAdditionalRowsWhenSpaceAllows() {
        ArrowOverlayView view = new ArrowOverlayView(RuntimeEnvironment.getApplication());
        Rect board = new Rect(20,20,340,340);
        view.setBoardBounds(board);
        List<RectF> placed = new ArrayList<>();
        for (int i=0; i<5; i++) {
            RectF chip = view.labelBounds(new ArrowOverlayView.Arrow(160,20,160,40,0,12,"+2.00"),placed);
            assertTrue(new RectF(board).contains(chip));
            for (RectF other : placed) assertFalse(RectF.intersects(chip,other));
            placed.add(chip);
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
