package com.opencheps.util;

/** A square in physical, full-display coordinates. No status-bar offsets or device constants. */
public final class BoardArea {
    public final double left, top, right, bottom;
    public final double displayAspect;
    public final int rotation;

    private BoardArea(double left, double top, double right, double bottom,
                      double displayAspect, int rotation) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.displayAspect = displayAspect;
        this.rotation = rotation;
    }

    public static BoardArea fromPixels(int x, int y, int side, int width, int height, int rotation) {
        if (width <= 0 || height <= 0 || side < 8 || x < 0 || y < 0
                || (long) x + side > width || (long) y + side > height) return null;
        return validated((double) x / width, (double) y / height,
                ((double) x + side) / width, ((double) y + side) / height,
                (double) width / height, rotation);
    }

    private static BoardArea validated(double l, double t, double r, double b, double aspect, int rotation) {
        if (!Double.isFinite(l) || !Double.isFinite(t) || !Double.isFinite(r)
                || !Double.isFinite(b) || !Double.isFinite(aspect)
                || aspect <= 0 || rotation < 0 || rotation > 3
                || l < 0 || t < 0 || r > 1 || b > 1 || r <= l || b <= t) return null;
        double w = (r - l) * aspect, h = b - t;
        if (Math.abs(w - h) > Math.max(w, h) * 0.001) return null;
        return new BoardArea(l, t, r, b, aspect, rotation);
    }

    /** Reject changed layouts instead of silently applying a portrait area to a landscape board. */
    public Pixels toPixels(int width, int height, int currentRotation) {
        if (width <= 0 || height <= 0 || rotation != currentRotation
                || Math.abs(((double) width / height) / displayAspect - 1) > 0.01) return null;
        int side = (int) Math.round(Math.min((right - left) * width, (bottom - top) * height));
        if (side < 8 || side > Math.min(width, height)) return null;
        int x = Math.min(width - side, (int) Math.round(left * width));
        int y = Math.min(height - side, (int) Math.round(top * height));
        return new Pixels(x, y, side);
    }

    // One versioned value makes SAVE/AUTO atomic in SharedPreferences.
    public String encode() {
        return "1," + left + "," + top + "," + right + "," + bottom + "," + displayAspect + "," + rotation;
    }

    public static BoardArea decode(String value) {
        if (value == null) return null;
        try {
            String[] p = value.split(",", -1);
            if (p.length != 7 || !"1".equals(p[0])) return null;
            return validated(Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]), Double.parseDouble(p[4]),
                    Double.parseDouble(p[5]), Integer.parseInt(p[6]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static final class Pixels {
        public final int left, top, side;
        public Pixels(int left, int top, int side) {
            this.left = left;
            this.top = top;
            this.side = side;
        }
    }
}
