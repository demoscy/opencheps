package com.opencheps.overlay;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import com.opencheps.R;
import com.opencheps.util.Side;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="w360dp-h640dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class SpecialMovesOverlayTest {
    private char[][] position() {
        return new char[][] {"r...k..r".toCharArray(),"........".toCharArray(),"........".toCharArray(),"...pP...".toCharArray(),
                "........".toCharArray(),"........".toCharArray(),"........".toCharArray(),"R...K...".toCharArray()};
    }
    private Button button(ViewGroup view,int string) {
        String text=view.getContext().getString(string);
        for (int i=0;i<view.getChildCount();i++) {
            View child=view.getChildAt(i);
            if (child instanceof Button && ((Button)child).getText().toString().equals(text)) return (Button)child;
            if (child instanceof ViewGroup) { Button found=button((ViewGroup)child,string); if(found!=null)return found; }
        }
        return null;
    }
    @Test public void onlyPossibleRightsAreSelectableAndNothingChangesBeforeSave() throws Exception {
        String[] saved={null,null}; int[] cancelled={0};
        SpecialMovesOverlay editor=new SpecialMovesOverlay(RuntimeEnvironment.getApplication(),position(),Side.WHITE,"q","d6",
                (rights,ep)->{saved[0]=rights;saved[1]=ep;},()->cancelled[0]++);
        editor.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));
        editor.layout(0,0,360,640);
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(360,640,android.graphics.Bitmap.Config.ARGB_8888);
        editor.draw(new android.graphics.Canvas(image));
        java.io.File directory=new java.io.File("build/reports/ui-preview"); directory.mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,"special-moves.png"))) {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        } finally {image.recycle();}
        CheckBox[] rights=ReflectionHelpers.getField(editor,"rights");
        assertFalse(rights[0].isEnabled()); // Missing h1 rook cannot grant White O-O.
        rights[1].setChecked(true);
        assertNull(saved[0]);
        Spinner target=ReflectionHelpers.getField(editor,"target");
        assertEquals("d6",target.getSelectedItem());
        button(editor,R.string.special_save).performClick();
        assertEquals("Qq",saved[0]); assertEquals("d6",saved[1]); assertEquals(0,cancelled[0]);
    }
    @Test public void cancelDiscardsEditsAndActionsStayVisibleOnShortScreens() {
        int[] callbacks={0,0};
        SpecialMovesOverlay editor=new SpecialMovesOverlay(RuntimeEnvironment.getApplication(),position(),Side.WHITE,"-","-",
                (rights,ep)->callbacks[0]++,()->callbacks[1]++);
        CheckBox[] rights=ReflectionHelpers.getField(editor,"rights");
        rights[1].setChecked(true);
        editor.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY));
        editor.layout(0,0,360,320);
        Button save=button(editor,R.string.special_save),cancel=button(editor,R.string.special_cancel);
        android.graphics.Rect visible=new android.graphics.Rect();
        assertTrue(save.getLocalVisibleRect(visible)); assertEquals(48,visible.height());
        assertTrue(cancel.getLocalVisibleRect(visible));
        cancel.performClick();
        assertEquals(0,callbacks[0]);assertEquals(1,callbacks[1]);
    }
}
