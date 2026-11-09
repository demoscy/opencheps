package com.opencheps.util;

/** Maps a captured full-screen app (without system bars) or entire display to screen pixels. */
public final class CaptureViewport {
    public final int left, top, width, height;

    private CaptureViewport(int left, int top, int width, int height) {
        this.left = left; this.top = top; this.width = width; this.height = height;
    }

    public static CaptureViewport resolve(int screenW, int screenH, int contentW, int contentH,
                                          int insetLeft, int insetTop, int insetRight, int insetBottom) {
        if (screenW <= 0 || screenH <= 0) return null;
        // Before the Android 14 callback, and on older versions, capture covers the display.
        if (contentW == 0 && contentH == 0 || contentW == screenW && contentH == screenH) {
            return new CaptureViewport(0, 0, screenW, screenH);
        }
        if (insetLeft < 0 || insetTop < 0 || insetRight < 0 || insetBottom < 0) return null;
        int appW = screenW - insetLeft - insetRight, appH = screenH - insetTop - insetBottom;
        // Size alone cannot locate an arbitrary floating/split-screen app. Only accept an exact
        // full-screen content-area match; never guess a vertical offset from an aspect ratio.
        if (appW > 0 && appH > 0 && contentW == appW && contentH == appH) {
            return new CaptureViewport(insetLeft, insetTop, appW, appH);
        }
        return null;
    }

    public BoardArea.Pixels crop(BoardArea.Pixels screenArea, int captureW, int captureH) {
        if (screenArea == null || captureW <= 0 || captureH <= 0
                || screenArea.left < left || screenArea.top < top
                || (long) screenArea.left + screenArea.side > (long) left + width
                || (long) screenArea.top + screenArea.side > (long) top + height) return null;
        int x = Math.round((screenArea.left - left) * (float) captureW / width);
        int y = Math.round((screenArea.top - top) * (float) captureH / height);
        int side = Math.round(screenArea.side * Math.min((float) captureW / width, (float) captureH / height));
        if (side < 8 || side > Math.min(captureW, captureH)) return null;
        return new BoardArea.Pixels(Math.min(x, captureW - side), Math.min(y, captureH - side), side);
    }

    public boolean sameAs(CaptureViewport other) {
        return other != null && left == other.left && top == other.top
                && width == other.width && height == other.height;
    }
}
