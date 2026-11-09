package com.opencheps.vision;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/** Offline 13-class tile CNN. No starting-position templates or frame-label reuse. */
public final class PieceClassifier implements AutoCloseable {
    private static final String MODEL = "piece-model/chess-tiles-v2.onnx";
    private static final char[] LABELS = ".KQRBNPkqrbnp".toCharArray();
    private static final float MIN_CONFIDENCE = 0.70f;
    private final OrtEnvironment environment;
    private final OrtSession session;

    public PieceClassifier(Context context) throws Exception {
        byte[] bytes;
        try (InputStream in = context.getAssets().open(MODEL);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            bytes = out.toByteArray();
        }
        environment = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            session = environment.createSession(bytes, options);
        }
    }

    public static final class Result {
        public final char[][] grid;
        public final float confidence;
        Result(char[][] grid, float confidence) { this.grid = grid; this.confidence = confidence; }
    }

    /** Returns screen-order rows; perspective is applied by FenBuilder/BoardMapping. */
    public char[][] classifyBoard(Bitmap bitmap, BoardDetector.Board board) throws OrtException {
        Result result = readBoard(bitmap, board);
        return result == null ? null : result.grid;
    }

    public Result readBoard(Bitmap bitmap, BoardDetector.Board board) throws OrtException {
        float[] tiles = extractTiles(bitmap, board);
        if (tiles == null) return null;
        try (OnnxTensor input = OnnxTensor.createTensor(environment,
                     FloatBuffer.wrap(tiles), new long[]{64, 1024});
             OrtSession.Result output = session.run(Collections.singletonMap("tiles", input))) {
            float[][] probabilities = (float[][]) output.get(0).getValue();
            char[][] grid = new char[8][8];
            float minimum = 1, total = 0;
            for (int i = 0; i < 64; i++) {
                int best = 0;
                for (int j = 1; j < LABELS.length; j++)
                    if (probabilities[i][j] > probabilities[i][best]) best = j;
                if (!Float.isFinite(probabilities[i][best]) || probabilities[i][best] < MIN_CONFIDENCE)
                    return null;
                minimum = Math.min(minimum, probabilities[i][best]);
                total += probabilities[i][best];
                grid[7 - i / 8][i % 8] = LABELS[best];
            }
            return new Result(grid, (minimum + total / 64) / 2);
        }
    }

    // Model preprocessing: Rec.601 grayscale; half-pixel bilinear 256x256 resize;
    // upright 32x32 tiles, bottom row first. See the bundled upstream notice/license.
    static float[] extractTiles(Bitmap bitmap, BoardDetector.Board board) {
        if (bitmap == null || board == null || board.side < 64 || board.left < 0 || board.top < 0
                || (long) board.left + board.side > bitmap.getWidth()
                || (long) board.top + board.side > bitmap.getHeight()) return null;
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        float[] result = new float[64 * 1024];
        for (int y = 0; y < 256; y++) {
            double sy = board.top + (y + 0.5) * board.side / 256 - 0.5;
            int fy = (int) Math.floor(sy);
            double wy = sy - fy;
            for (int x = 0; x < 256; x++) {
                double sx = board.left + (x + 0.5) * board.side / 256 - 0.5;
                int fx = (int) Math.floor(sx);
                double wx = sx - fx;
                double value = gray(pixels, width, height, fx, fy) * (1-wx) * (1-wy)
                        + gray(pixels, width, height, fx+1, fy) * wx * (1-wy)
                        + gray(pixels, width, height, fx, fy+1) * (1-wx) * wy
                        + gray(pixels, width, height, fx+1, fy+1) * wx * wy;
                int tile = (7 - y / 32) * 8 + x / 32;
                result[tile * 1024 + (y % 32) * 32 + x % 32] = (float) (value / 255);
            }
        }
        return result;
    }

    private static double gray(int[] pixels, int width, int height, int x, int y) {
        int color = pixels[Math.max(0, Math.min(height-1, y)) * width + Math.max(0, Math.min(width-1, x))];
        return .299 * ((color >> 16) & 255) + .587 * ((color >> 8) & 255) + .114 * (color & 255);
    }

    @Override public void close() throws OrtException { session.close(); }
}
