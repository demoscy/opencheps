package com.opencheps.util;

/** One capture-to-display transform for both board frame and move-square centers. */
public final class BoardMapping {
    public final float left, top, width, height;

    public BoardMapping(int captureLeft, int captureTop, int captureSide, float scaleX, float scaleY) {
        this(captureLeft, captureTop, captureSide, scaleX, scaleY, 0, 0);
    }

    public BoardMapping(int captureLeft, int captureTop, int captureSide, float scaleX, float scaleY,
                        int screenOffsetX, int screenOffsetY) {
        left = screenOffsetX + captureLeft * scaleX;
        top = screenOffsetY + captureTop * scaleY;
        width = captureSide * scaleX;
        height = captureSide * scaleY;
    }

    public float[] squareCenter(int file, int rankFromTop, boolean blackAtBottom) {
        int x = blackAtBottom ? 7 - file : file;
        int y = blackAtBottom ? 7 - rankFromTop : rankFromTop;
        return new float[] { left + (x + 0.5f) * width / 8, top + (y + 0.5f) * height / 8 };
    }
}
