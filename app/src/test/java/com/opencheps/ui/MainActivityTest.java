package com.opencheps.ui;

import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import com.opencheps.R;
import com.opencheps.util.Prefs;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTest {
    @Test public void strengthAndContinuationControlsPersistWithoutASpuriousThinkTimeRating() {
        Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().commit();
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            SeekBar elo = activity.findViewById(R.id.eloBar);
            CheckBox limit = activity.findViewById(R.id.limitStrength);
            CheckBox line = activity.findViewById(R.id.showStudyLine);
            SeekBar choices = activity.findViewById(R.id.multiPvBar);
            assertEquals(2, choices.getProgress()); // Three choices by default.
            choices.setProgress(0);
            assertEquals(1, Prefs.multiPv(activity));
            choices.setProgress(2);
            assertEquals(3, Prefs.multiPv(activity));
            assertFalse(elo.isEnabled());
            limit.performClick();
            assertTrue(elo.isEnabled());
            elo.setProgress(100);
            line.performClick();
            assertEquals(2320, Prefs.strengthElo(activity));
            assertTrue(Prefs.showStudyLine(activity));
            assertTrue(((TextView) activity.findViewById(R.id.eloLabel)).getText().toString().contains("2320"));
            assertFalse(((TextView) activity.findViewById(R.id.thinkLabel)).getText().toString().toLowerCase().contains("elo"));
        }
    }
}
