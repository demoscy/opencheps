package com.opencheps.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

public class Prefs {

    private static final String FILE = "opencheps";
    public  static final String KEY_USER_SIDE = "user_side";
    public  static final String KEY_THINK_MS  = "think_ms";
    public static final String KEY_MULTIPV = "multipv";
    public static final int MIN_ELO = 1320, MAX_ELO = 3190, DEFAULT_ELO = 1800;
    private static final String KEY_LIMIT_STRENGTH = "limit_strength";
    private static final String KEY_PRACTICE_ELO = "practice_elo";
    private static final String KEY_STUDY_LINE = "show_study_line";

    private static final String KEY_BOARD_AREA = "manual_board_area_v1";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String userSide(Context c) {
        return get(c).getString(KEY_USER_SIDE, "w");
    }

    public static void setUserSide(Context c, String side) {
        get(c).edit().putString(KEY_USER_SIDE, side).apply();
    }

    private static final String KEY_BOARD_AREAS = "manual_board_areas_v2";
    private static final int MAX_AREAS = 8;

    public static BoardArea boardArea(Context c) {
        List<BoardArea> areas = boardAreas(c);
        return areas.isEmpty() ? null : areas.get(0);
    }

    /** Most recently saved first; migrate the original single rectangle without losing it. */
    public static List<BoardArea> boardAreas(Context c) {
        List<BoardArea> result = new ArrayList<>();
        try {
            String encoded = get(c).getString(KEY_BOARD_AREAS, null);
            if (encoded == null) encoded = get(c).getString(KEY_BOARD_AREA, null);
            if (encoded == null || encoded.length() > 8192) return result;
            for (String value : encoded.split(";", -1)) {
                BoardArea area = BoardArea.decode(value);
                if (area != null) result.add(area);
                if (result.size() == MAX_AREAS) break;
            }
        } catch (ClassCastException ignored) { /* Corrupt preferences are never trusted. */ }
        return result;
    }

    /** SAVE remembers this layout alongside others. AUTO atomically clears every manual layout. */
    public static void setBoardArea(Context c, BoardArea area) {
        SharedPreferences.Editor editor = get(c).edit();
        if (area == null) editor.remove(KEY_BOARD_AREA).remove(KEY_BOARD_AREAS);
        else {
            StringBuilder value = new StringBuilder(area.encode());
            int count = 1;
            for (BoardArea previous : boardAreas(c)) {
                boolean same = previous.rotation == area.rotation
                        && Math.abs(previous.displayAspect / area.displayAspect - 1) < .01
                        && Math.abs(previous.left - area.left) < .002
                        && Math.abs(previous.top - area.top) < .002
                        && Math.abs(previous.right - area.right) < .002
                        && Math.abs(previous.bottom - area.bottom) < .002;
                if (same) continue;
                value.append(';').append(previous.encode());
                if (++count == MAX_AREAS) break;
            }
            editor.putString(KEY_BOARD_AREA, area.encode()).putString(KEY_BOARD_AREAS, value.toString());
        }
        editor.apply();
    }

    public static int thinkMs(Context c) { return get(c).getInt(KEY_THINK_MS, 1500); }
    public static int multiPv(Context c) {
        try { return Math.max(1, Math.min(3, get(c).getInt(KEY_MULTIPV, 3))); }
        catch (ClassCastException e) { return 3; }
    }
    public static void setMultiPv(Context c, int choices) {
        get(c).edit().putInt(KEY_MULTIPV, Math.max(1, Math.min(3, choices))).apply();
    }
    public static int clampElo(int elo) { return Math.max(MIN_ELO, Math.min(MAX_ELO, elo)); }
    public static int practiceElo(Context c) {
        try { return clampElo(get(c).getInt(KEY_PRACTICE_ELO, DEFAULT_ELO)); }
        catch (ClassCastException e) { return DEFAULT_ELO; }
    }
    public static void setPracticeElo(Context c, int elo) {
        get(c).edit().putInt(KEY_PRACTICE_ELO, clampElo(elo)).apply();
    }
    public static boolean limitStrength(Context c) {
        try { return get(c).getBoolean(KEY_LIMIT_STRENGTH, false); }
        catch (ClassCastException e) { return false; }
    }
    public static void setLimitStrength(Context c, boolean limit) {
        get(c).edit().putBoolean(KEY_LIMIT_STRENGTH, limit).apply();
    }
    public static int strengthElo(Context c) { return limitStrength(c) ? practiceElo(c) : 0; }
    public static boolean showStudyLine(Context c) {
        try { return get(c).getBoolean(KEY_STUDY_LINE, false); }
        catch (ClassCastException e) { return false; }
    }
    public static void setShowStudyLine(Context c, boolean show) {
        get(c).edit().putBoolean(KEY_STUDY_LINE, show).apply();
    }
}
