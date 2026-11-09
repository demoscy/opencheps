package com.opencheps.util;

import android.content.Context;
import org.robolectric.RuntimeEnvironment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 34})
public class PrefsTest {
    private Context context;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        Prefs.get(context).edit().clear().commit();
    }

    @Test public void saveSurvivesReloadAndAutoRemovesOnlyBoardArea() {
        Prefs.setUserSide(context, "b");
        BoardArea area = BoardArea.fromPixels(20, 400, 1000, 1080, 2340, 0);
        Prefs.setBoardArea(context, area);
        Context freshContext = context.createConfigurationContext(context.getResources().getConfiguration());
        BoardArea loaded = Prefs.boardArea(freshContext);
        assertNotNull(loaded);
        assertEquals(area.encode(), loaded.encode());
        Prefs.setBoardArea(freshContext, null);
        assertNull(Prefs.boardArea(context));
        assertEquals("b", Prefs.userSide(context));
    }

    @Test public void missingCorruptAndWrongTypeValuesFallBackSafely() {
        assertNull(Prefs.boardArea(context));
        Prefs.get(context).edit().putString("manual_board_area_v1", "bad").commit();
        assertNull(Prefs.boardArea(context));
        Prefs.get(context).edit().putInt("manual_board_area_v1", 7).commit();
        assertNull(Prefs.boardArea(context));
    }
}
