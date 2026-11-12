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
            assertEquals(4,choices.getMax());
            assertEquals(2, choices.getProgress()); // Three choices by default.
            choices.setProgress(0);
            assertEquals(1, Prefs.multiPv(activity));
            choices.setProgress(2);
            assertEquals(3, Prefs.multiPv(activity));
            choices.setProgress(4);
            assertEquals(5,Prefs.multiPv(activity));
            assertEquals("Move choices: 5",((TextView)activity.findViewById(R.id.multiPvLabel)).getText().toString());
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
    @Test public void fiveChoiceSettingSurvivesReopeningSettings() {
        Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().commit();
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            ((SeekBar)controller.get().findViewById(R.id.multiPvBar)).setProgress(4);
            controller.recreate();
            assertEquals(4,((SeekBar)controller.get().findViewById(R.id.multiPvBar)).getProgress());
            assertEquals(5,Prefs.multiPv(controller.get()));
        }
    }
    @Test public void fastestThinkTimeIsSelectableAndSurvivesReopeningSettings() {
        Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().commit();
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            SeekBar think = controller.get().findViewById(R.id.thinkBar);
            assertEquals(100, think.getMin());
            think.setProgress(0);
            assertEquals(100, think.getProgress());
            assertEquals(100, Prefs.thinkMs(controller.get()));
            assertEquals("Think time: 100 ms", ((TextView) controller.get().findViewById(R.id.thinkLabel)).getText().toString());
            controller.recreate();
            assertEquals(100, ((SeekBar) controller.get().findViewById(R.id.thinkBar)).getProgress());
        }
    }
    @Test public void activeCaptureSettingsKeepTheGrantAndDisableStartingASecondProjection() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true);
        org.robolectric.android.controller.ServiceController<com.opencheps.capture.ScreenCaptureService> capture =
                Robolectric.buildService(com.opencheps.capture.ScreenCaptureService.class).create();
        // A session token stand-in: Robolectric cannot supply a real capture consent grant.
        android.media.projection.MediaProjection projection = org.robolectric.shadow.api.Shadow.newInstanceOf(
                android.media.projection.MediaProjection.class);
        assertNotNull(projection);
        org.robolectric.util.ReflectionHelpers.setField(capture.get(), "projection", projection);
        org.robolectric.util.ReflectionHelpers.setField(capture.get(), "worker", new android.os.Handler(android.os.Looper.getMainLooper()));
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertFalse(activity.findViewById(R.id.btnStart).isEnabled());
            assertTrue(activity.findViewById(R.id.btnStop).isEnabled());
            assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.btnReturnToBoard).getVisibility());
            assertTrue(org.robolectric.util.ReflectionHelpers.getField(capture.get(), "settingsVisible"));
            ((SeekBar) activity.findViewById(R.id.thinkBar)).setProgress(100);
            controller.recreate();
            assertTrue(org.robolectric.util.ReflectionHelpers.getField(capture.get(), "settingsVisible"));
            controller.pause().stop();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
            assertFalse(org.robolectric.util.ReflectionHelpers.getField(capture.get(), "settingsVisible"));
            assertSame(projection, org.robolectric.util.ReflectionHelpers.getField(capture.get(), "projection"));
            assertEquals(100, Prefs.thinkMs(capture.get()));
            controller.start().resume();
            org.robolectric.util.ReflectionHelpers.setField(capture.get(), "projection", null);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
            assertTrue(controller.get().findViewById(R.id.btnStart).isEnabled());
            assertFalse(controller.get().findViewById(R.id.btnStop).isEnabled());
            assertEquals(android.view.View.GONE, controller.get().findViewById(R.id.btnReturnToBoard).getVisibility());
        } finally {
            // This test injects the main looper, not a real capture worker/thread.
            org.robolectric.util.ReflectionHelpers.setField(capture.get(), "worker", null);
            org.robolectric.util.ReflectionHelpers.setField(capture.get(), "projection", null);
            capture.destroy();
        }
        assertFalse(com.opencheps.capture.ScreenCaptureService.isRunning());
    }
    @Test @Config(sdk = {26,33,34})
    public void openingAndReopeningSettingsNeverRequestsNotificationPermission() throws Exception {
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            for (int i=0;i<2;i++) {
                MainActivity activity=controller.get();
                assertNull(org.robolectric.Shadows.shadowOf(activity).getLastRequestedPermission());
                String[] permissions=activity.getPackageManager().getPackageInfo(activity.getPackageName(),
                        android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
                java.util.List<String> requested=java.util.Arrays.asList(permissions);
                assertFalse(requested.contains("android.permission.POST_NOTIFICATIONS"));
                assertTrue(requested.contains("android.permission.FOREGROUND_SERVICE"));
                assertTrue(requested.contains("android.permission.SYSTEM_ALERT_WINDOW"));
                controller.recreate();
            }
        }
    }
    @Test public void evaluationOnlyControlsSurviveRecreationWithoutChangingArrowPreferences() {
        Prefs.get(RuntimeEnvironment.getApplication()).edit().clear().commit();
        Prefs.setLimitStrength(RuntimeEnvironment.getApplication(),true);
        Prefs.setPracticeElo(RuntimeEnvironment.getApplication(),1600);Prefs.setMultiPv(RuntimeEnvironment.getApplication(),5);
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            ((CheckBox)activity.findViewById(R.id.evaluationOnly)).performClick();
            ((CheckBox)activity.findViewById(R.id.showMateLine)).performClick();
            assertFalse(activity.findViewById(R.id.multiPvBar).isEnabled());
            assertFalse(activity.findViewById(R.id.eloBar).isEnabled());
            assertFalse(activity.findViewById(R.id.showStudyLine).isEnabled());
            controller.recreate();activity=controller.get();
            assertTrue(((CheckBox)activity.findViewById(R.id.evaluationOnly)).isChecked());
            assertTrue(((CheckBox)activity.findViewById(R.id.showMateLine)).isChecked());
            controller.pause().stop();
            Prefs.setShowMateLine(activity,false); // Changed through the floating panel while settings were closed.
            controller.start().resume();
            assertFalse(((CheckBox)activity.findViewById(R.id.showMateLine)).isChecked());
            ((CheckBox)activity.findViewById(R.id.evaluationOnly)).performClick();
            assertTrue(activity.findViewById(R.id.eloBar).isEnabled());assertEquals(1600,Prefs.strengthElo(activity));
            assertEquals(5,Prefs.multiPv(activity));
        }
    }
}
