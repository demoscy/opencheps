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
    @Test public void puzzleAndBotLayoutsBothSurviveRestartAndAutoClearsBoth() {
        BoardArea bot = BoardArea.fromPixels(20, 400, 1000, 1080, 2340, 0);
        BoardArea puzzle = BoardArea.fromPixels(20, 620, 1000, 1080, 2340, 0);
        Prefs.setBoardArea(context, bot);
        Prefs.setBoardArea(context, puzzle);
        Context fresh = context.createConfigurationContext(context.getResources().getConfiguration());
        assertEquals(2, Prefs.boardAreas(fresh).size());
        assertEquals(puzzle.encode(), Prefs.boardAreas(fresh).get(0).encode());
        assertEquals(bot.encode(), Prefs.boardAreas(fresh).get(1).encode());
        Prefs.setBoardArea(fresh, puzzle);
        assertEquals(2, Prefs.boardAreas(context).size());
        Prefs.setBoardArea(fresh, null);
        assertTrue(Prefs.boardAreas(context).isEmpty());
    }
    @Test public void migratesOriginalSingleAreaAndBoundsListSize() {
        BoardArea legacy = BoardArea.fromPixels(20, 400, 1000, 1080, 2340, 0);
        Prefs.get(context).edit().putString("manual_board_area_v1", legacy.encode()).commit();
        assertEquals(legacy.encode(), Prefs.boardAreas(context).get(0).encode());
        for (int i = 0; i < 12; i++)
            Prefs.setBoardArea(context, BoardArea.fromPixels(20, 500+i*50, 1000, 1080, 2340, 0));
        assertEquals(8, Prefs.boardAreas(context).size());
    }

    @Test public void studySettingsPersistWithFullStrengthAsTheDefault() {
        assertEquals(0, Prefs.strengthElo(context));
        assertFalse(Prefs.showStudyLine(context));
        Prefs.setPracticeElo(context, 1700);
        Prefs.setLimitStrength(context, true);
        Prefs.setShowStudyLine(context, true);
        Context fresh = context.createConfigurationContext(context.getResources().getConfiguration());
        assertEquals(1700, Prefs.strengthElo(fresh));
        assertTrue(Prefs.showStudyLine(fresh));
        Prefs.setLimitStrength(fresh, false);
        assertEquals(0, Prefs.strengthElo(context));
        assertEquals(1700, Prefs.practiceElo(context));
    }
    @Test public void strengthClampsToTheActualStockfish18RangeAndRejectsCorruptSettings() {
        Prefs.setPracticeElo(context, 500);
        assertEquals(1320, Prefs.practiceElo(context));
        Prefs.setPracticeElo(context, 9000);
        assertEquals(3190, Prefs.practiceElo(context));
        Prefs.get(context).edit().putString("practice_elo", "bad").putString("limit_strength", "bad")
                .putInt("show_study_line", 1).commit();
        assertEquals(1800, Prefs.practiceElo(context));
        assertEquals(0, Prefs.strengthElo(context));
        assertFalse(Prefs.showStudyLine(context));
    }

    @Test public void choiceCountDefaultsToThreePersistsAndClampsCorruptValues() {
        assertEquals(3, Prefs.multiPv(context));
        Prefs.setMultiPv(context, 2);
        Context fresh = context.createConfigurationContext(context.getResources().getConfiguration());
        assertEquals(2, Prefs.multiPv(fresh));
        Prefs.setMultiPv(fresh, 20);
        assertEquals(3, Prefs.multiPv(context));
        Prefs.setMultiPv(fresh, 0);
        assertEquals(1, Prefs.multiPv(context));
        Prefs.get(context).edit().putString("multipv", "bad").commit();
        assertEquals(3, Prefs.multiPv(context));
    }

    @Test public void fastThinkTimePersistsAndInvalidTimesAreClampedOrDefaulted() {
        assertEquals(1500, Prefs.thinkMs(context));
        Prefs.setThinkMs(context, 100);
        Context fresh = context.createConfigurationContext(context.getResources().getConfiguration());
        assertEquals(100, Prefs.thinkMs(fresh));
        Prefs.setThinkMs(fresh, -50);
        assertEquals(100, Prefs.thinkMs(context));
        Prefs.get(context).edit().putInt(Prefs.KEY_THINK_MS, 10000).commit();
        assertEquals(3000, Prefs.thinkMs(context));
        Prefs.get(context).edit().putString(Prefs.KEY_THINK_MS, "bad").commit();
        assertEquals(1500, Prefs.thinkMs(context));
    }
}
