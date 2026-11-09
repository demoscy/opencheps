package com.opencheps.vision;

/** Change trigger only: masked/overlay-contaminated pixels are never piece-classifier input. */
public final class BoardFrameChange {
    private BoardFrameChange() {}

    public static boolean changed(int[] clean, int[] current, int[] mask, int width, int height,
                                  BoardDetector.Board board) {
        if (clean == null || current == null || mask == null || board == null
                || clean.length != width * height || current.length != clean.length
                || mask.length != clean.length) return true;
        int changed = 0;
        for (int y = Math.max(0, board.top + 3); y < Math.min(height, board.top + board.side - 3); y += 2) {
            for (int x = Math.max(0, board.left + 3); x < Math.min(width, board.left + board.side - 3); x += 2) {
                boolean covered = false;
                // Include a small compositor/antialiasing margin around our rendered pixels.
                for (int dy = -2; dy <= 2 && !covered; dy++)
                    for (int dx = -2; dx <= 2; dx++)
                        if ((mask[(y+dy)*width+x+dx] >>> 24) != 0) { covered = true; break; }
                if (covered) continue;
                int a = clean[y*width+x], b = current[y*width+x];
                int delta = Math.max(Math.abs((a & 255) - (b & 255)),
                        Math.max(Math.abs(((a>>8)&255) - ((b>>8)&255)),
                                 Math.abs(((a>>16)&255) - ((b>>16)&255))));
                if (delta > 32 && ++changed >= 8) return true;
            }
        }
        return false;
    }
}
