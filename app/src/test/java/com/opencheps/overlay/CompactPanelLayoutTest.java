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
    @Test public void evaluationStripKeepsOnlyLogoScoreSyncAndColorAndMateTextCanExpand() throws Exception {
        ShadowSettings.setCanDrawOverlays(true);
        com.opencheps.util.Prefs.setEvaluationOnly(org.robolectric.RuntimeEnvironment.getApplication(),true);
        com.opencheps.util.Prefs.setShowMateLine(org.robolectric.RuntimeEnvironment.getApplication(),true);
        org.robolectric.android.controller.ServiceController<OverlayService> service=Robolectric.buildService(OverlayService.class).create();
        try {
            FloatingPanel panel=OverlayService.panel();
            View root=ReflectionHelpers.getField(panel,"root");
            panel.setPositionScore("M4",true,"W a7→a8=Q   B h8→h7\nW a8→a7   B h7→h6\nW a7→a6   B h6→h5\nW a6→a5",true);
            panel.setMinimized(true);render(root,"evaluation-compact");
            assertEquals(232,root.getWidth());assertEquals(56,root.getHeight());
            Bitmap preview=android.graphics.BitmapFactory.decodeFile("build/reports/ui-preview/evaluation-compact.png");
            View scoreView=root.findViewById(R.id.panelCompactScore);
            int[] scoreOrigin=new int[2],rootOrigin=new int[2];
            scoreView.getLocationOnScreen(scoreOrigin);root.getLocationOnScreen(rootOrigin);
            int ink=0;
            for(int y=scoreOrigin[1]-rootOrigin[1];y<scoreOrigin[1]-rootOrigin[1]+scoreView.getHeight();y++)
                for(int x=scoreOrigin[0]-rootOrigin[0];x<scoreOrigin[0]-rootOrigin[0]+scoreView.getWidth();x++) {
                    int pixel=preview.getPixel(x,y);
                    if(android.graphics.Color.red(pixel)>200&&android.graphics.Color.green(pixel)>200&&android.graphics.Color.blue(pixel)>200)ink++;
                }
            preview.recycle();
            android.widget.TextView scoreText=(android.widget.TextView)scoreView;
            assertTrue("Compact score must draw visible white text: ink="+ink+" size="+scoreText.getTextSize()
                    +" bounds="+scoreView.getLeft()+","+scoreView.getTop()+","+scoreView.getWidth()+","+scoreView.getHeight()
                    +" scroll="+scoreView.getScrollX()+","+scoreView.getScrollY()+" color="+scoreText.getCurrentTextColor()
                    +" layout="+scoreText.getLayout()+" layoutWidth="+scoreText.getLayout().getWidth()+" baseline="+scoreText.getBaseline()+" visible="+scoreView.getVisibility(),ink>20);
            assertEquals("M4",((android.widget.TextView)root.findViewById(R.id.panelCompactScore)).getText().toString());
            assertEquals(View.GONE,root.findViewById(R.id.panelExpanded).getVisibility());
            assertEquals(View.VISIBLE,root.findViewById(R.id.panelColor).getVisibility());
            assertEquals(View.VISIBLE,root.findViewById(R.id.panelRecal).getVisibility());
            root.findViewById(R.id.panelIcon).performClick();render(root,"evaluation-mate-expanded");
            android.widget.TextView line=root.findViewById(R.id.panelContinuation);
            assertTrue(line.getText().toString().startsWith("Mate line (shortest found):"));
            assertTrue(line.getText().toString().endsWith("W a6→a5"));
            assertNull(line.getEllipsize());
            panel.setEval("");render(root,"evaluation-unavailable");
            assertEquals("—",((android.widget.TextView)root.findViewById(R.id.panelCompactScore)).getText().toString());
            assertEquals(View.GONE,root.findViewById(R.id.panelMateLine).getVisibility());
            assertEquals(View.GONE,line.getVisibility());
            panel.setPositionScore("-M4",true,"B h8→h7",false);
            assertTrue(line.getText().toString().startsWith("Mate line (partial):"));
        } finally {service.destroy();}
    }
}
