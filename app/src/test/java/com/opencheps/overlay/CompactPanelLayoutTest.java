package com.opencheps.overlay;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import com.opencheps.R;
import com.opencheps.util.Side;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CompactPanelLayoutTest {
    private void render(View root, String name) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.AT_MOST));
        root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());
        File directory = new File("build/reports/ui-preview");
        directory.mkdirs();
        Bitmap image = Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(image));
        try (FileOutputStream output = new FileOutputStream(new File(directory,name+".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output));
        } finally { image.recycle(); }
    }
    @Test public void actualPanelFitsSmallPhoneAndEveryHeaderIconHasAnAccessibleTouchTarget() throws Exception {
        ShadowSettings.setCanDrawOverlays(true);
        org.robolectric.android.controller.ServiceController<OverlayService> service =
                Robolectric.buildService(OverlayService.class).create();
        try {
            FloatingPanel panel = OverlayService.panel();
            panel.setStatus("Practice · 1530 Elo (approx.)");
            panel.setEval("g4→g5  +7.00");
            View root = ReflectionHelpers.getField(panel,"root");
            render(root,"expanded");
            assertTrue(root.getWidth() <= 320);
            assertTrue(root.getHeight() <= 240);
            for (int id : new int[]{R.id.panelIcon,R.id.panelColor,R.id.panelRecal,R.id.panelSettings,R.id.panelToggle,R.id.panelStop}) {
                View button = root.findViewById(id);
                assertEquals(48,button.getWidth());
                assertEquals(48,button.getHeight());
                assertNotNull(button.getContentDescription());
            }
            root.findViewById(R.id.panelIcon).performClick();
            render(root,"compact-white");
            assertEquals(152,root.getWidth());
            assertEquals(56,root.getHeight());
            panel.setUserSide(Side.BLACK);
            ShadowLooper.idleMainLooper();
            render(root,"compact-black");
            root.findViewById(R.id.panelIcon).performClick();
            assertFalse(panel.isMinimized());
        } finally { service.destroy(); }
    }
}
