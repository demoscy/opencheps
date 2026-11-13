package com.opencheps.util;

/** Mutable editor geometry; a resize keeps the opposite corner fixed and the board square. */
public final class BoardSelection {
    public double left, top, side;
    private final double width, height, minSide;

    public BoardSelection(double left, double top, double side, double width, double height, double minSide) {
        this.width = width;
        this.height = height;
        this.minSide = Math.min(minSide, Math.min(width, height));
        this.side = clamp(side, this.minSide, Math.min(width, height));
        moveTo(left, top);
    }

    public void moveTo(double x, double y) {
        left = clamp(x, 0, width - side);
        top = clamp(y, 0, height - side);
    }

    // Corners clockwise from top left: 0, 1, 2, 3.
    public void resize(int corner, double anchorX, double anchorY, double x, double y) {
        int sx = corner == 0 || corner == 3 ? -1 : 1;
        int sy = corner < 2 ? -1 : 1;
        double max = Math.min(sx < 0 ? anchorX : width - anchorX,
                sy < 0 ? anchorY : height - anchorY);
        side = clamp((sx * (x - anchorX) + sy * (y - anchorY)) / 2,
                Math.min(minSide, max), max);
        left = sx < 0 ? anchorX - side : anchorX;
        top = sy < 0 ? anchorY - side : anchorY;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
