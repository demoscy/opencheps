package com.opencheps.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class ArrowOverlayView extends View {

    public static class Arrow {
        public final float sx, sy, ex, ey;
        public final int color;
        public final float widthPx;
        public final String label;
        public Arrow(float sx, float sy, float ex, float ey,
                     int color, float widthPx, String label) {
            this.sx = sx; this.sy = sy; this.ex = ex; this.ey = ey;
            this.color = color; this.widthPx = widthPx; this.label = label;
        }
    }

    private final List<Arrow> arrows = new ArrayList<>();
    private Rect boardBounds;
    private int boardFrameColor = 0x6600C853;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBg= new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelAccent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  headPath = new Path();

    public ArrowOverlayView(Context ctx) {
        super(ctx);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        shadow.setStyle(Paint.Style.STROKE);
        shadow.setStrokeCap(Paint.Cap.ROUND);
        shadow.setColor(0x99000000);
        head.setStyle(Paint.Style.FILL);
        headOutline.setStyle(Paint.Style.STROKE);
        headOutline.setStrokeJoin(Paint.Join.ROUND);
        headOutline.setColor(0xFF07120D);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(5f);
        label.setColor(0xFFFFFFFF);
        label.setTextSize(30f);
        label.setFakeBoldText(true);
        labelBg.setStyle(Paint.Style.FILL);
        labelBg.setColor(0xFF101810);
        labelBorder.setStyle(Paint.Style.STROKE);
        labelBorder.setStrokeWidth(5f);
    }

    public void setArrows(List<Arrow> a) {
        arrows.clear();
        if (a != null) arrows.addAll(a);
        postInvalidate();
    }

    public void setBoardBounds(Rect r) {
        boardBounds = r;
        postInvalidate();
    }

    public void setBoardFrameColor(int c) {
        boardFrameColor = c;
        postInvalidate();
    }

    public void clearArrowsOnly() {
        arrows.clear();
        postInvalidate();
    }

    public void clearAll() {
        arrows.clear();
        boardBounds = null;
        postInvalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int[] origin = new int[2];
        getLocationOnScreen(origin);
        c.save();
        c.translate(-origin[0], -origin[1]);
        if (boardBounds != null) {
            frame.setColor(boardFrameColor);
            c.drawRect(boardBounds, frame);
        }
        for (Arrow a : arrows) {
            shadow.setStrokeWidth(a.widthPx + 6f);
            stroke.setStrokeWidth(a.widthPx);
            stroke.setColor(a.color);

            float dx = a.ex - a.sx, dy = a.ey - a.sy;
            float distance = (float) Math.hypot(dx, dy);
            if (distance < 1f) continue;
            // Keep the rounded shaft cap inside the head, not beyond its pointed tip.
            float scale = 1f - Math.min(distance * 0.5f, a.widthPx * 1.2f) / distance;
            float endX = a.sx + dx * scale, endY = a.sy + dy * scale;
            c.drawLine(a.sx, a.sy, endX, endY, shadow);
            c.drawLine(a.sx, a.sy, endX, endY, stroke);
        }
        // Endpoints stay above every shaft, including later, collinear candidates.
        float cell = boardBounds == null ? 0 : Math.min(boardBounds.width(), boardBounds.height()) / 8f;
        for (Arrow a : arrows) {
            float distance = (float) Math.hypot(a.ey - a.sy, a.ex - a.sx);
            if (distance < 1f) continue;
            head.setColor(a.color);
            double ang = Math.atan2(a.ey - a.sy, a.ex - a.sx);
            float headLen = Math.min(distance * 0.85f, Math.max(a.widthPx * 3.8f, cell * 0.5f));
            float wing = (float) (Math.PI / 6);
            float lx = a.ex - (float) (Math.cos(ang - wing) * headLen);
            float ly = a.ey - (float) (Math.sin(ang - wing) * headLen);
            float rx = a.ex - (float) (Math.cos(ang + wing) * headLen);
            float ry = a.ey - (float) (Math.sin(ang + wing) * headLen);
            headPath.reset();
            headPath.moveTo(a.ex, a.ey);
            headPath.lineTo(lx, ly);
            headPath.lineTo(rx, ry);
            headPath.close();
            c.drawPath(headPath, head);
            headOutline.setStrokeWidth(Math.max(3f, a.widthPx * 0.2f));
            c.drawPath(headPath, headOutline);
        }

        // Every shaft/head must be below every score chip, including later candidates.
        List<RectF> labels = new ArrayList<>();
        for (Arrow a : arrows) {
            if (a.label != null && !a.label.isEmpty()) {
                RectF box = labelBounds(a, labels);
                labels.add(box);
                c.drawRoundRect(box, 10f, 10f, labelBg);
                c.save();
                c.clipRect(box);
                labelBorder.setColor(a.color);
                c.drawRoundRect(box.left+2.5f, box.top+2.5f, box.right-2.5f, box.bottom-2.5f,
                        8f, 8f, labelBorder);
                labelAccent.setColor(a.color);
                c.drawRoundRect(box.left+5f, box.top+7f, box.left+12f, box.bottom-7f,
                        3f, 3f, labelAccent);
                c.drawText(a.label, box.left + 19f, box.bottom - 6f, label);
                c.restore();
            }
        }
        c.restore();
    }

    /** Keep short score chips readable when candidates share the same starting square. */
    RectF labelBounds(Arrow arrow, List<RectF> placed) {
        Rect limits = boardBounds != null && !boardBounds.isEmpty() ? boardBounds
                : new Rect(0, 0, getWidth(), getHeight());
        float width = Math.min(label.measureText(arrow.label) + 28f, Math.max(1, limits.width()));
        float height = Math.min(38f, Math.max(1, limits.height()));
        float x = Math.max(limits.left, Math.min((arrow.sx + arrow.ex - width) / 2, limits.right - width));
        RectF fallback = null;
        for (int step = 0; step <= 2*(placed.size()+1); step++) {
            int offset = step % 2 == 0 ? step/2 : -(step+1)/2;
            float y = Math.max(limits.top, Math.min((arrow.sy + arrow.ey) / 2 - 24 + offset*(height+4), limits.bottom-height));
            RectF box = new RectF(x, y, x+width, y+height);
            if (fallback == null) fallback = box;
            boolean intersects = false;
            for (RectF other : placed) if (RectF.intersects(box, other)) intersects = true;
            if (!intersects) return box;
        }
        return fallback;
    }
}
