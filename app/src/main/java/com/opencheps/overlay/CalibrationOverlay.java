package com.opencheps.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.opencheps.R;
import com.opencheps.util.BoardArea;
import com.opencheps.util.BoardSelection;

/** Editing consumes touches, but does not capture, classify, or store any screen content. */
@SuppressLint("ViewConstructor")
final class CalibrationOverlay extends FrameLayout {
    interface Listener {
        void onSave(BoardArea area);
        void onAuto();
        void onCancel();
    }

    private final int displayW, displayH, rotation;
    private final BoardSelection selection;
    private final LinearLayout controls;
    private final SelectionView grid;
    private int insetTop, insetBottom, insetLeft, insetRight;

    CalibrationOverlay(Context context, int width, int height, int rotation, Rect initial, Listener listener) {
        super(context);
        this.displayW = width;
        this.displayH = height;
        this.rotation = rotation;
        selection = new BoardSelection(initial.left, initial.top, initial.width(), width, height, dp(96));
        grid = new SelectionView(context);
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(8), dp(4), dp(8), dp(4));
        controls.setBackgroundColor(0xE6222222);
        TextView help = new TextView(context);
        help.setText(R.string.board_area_help);
        help.setTextColor(Color.WHITE);
        help.setTextSize(13);
        controls.addView(help);
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(width > height ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        addButton(buttons, R.string.board_area_save, () -> {
            BoardArea area = BoardArea.fromPixels((int) Math.round(selection.left),
                    (int) Math.round(selection.top), (int) Math.round(selection.side),
                    displayW, displayH, rotation);
            if (area != null) listener.onSave(area);
        });
        addButton(buttons, R.string.board_area_auto, listener::onAuto);
        addButton(buttons, R.string.board_area_cancel, listener::onCancel);
        controls.addView(buttons);
        addView(controls, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setOnApplyWindowInsetsListener((v, insets) -> {
            insetTop = insets.getSystemWindowInsetTop();
            insetBottom = insets.getSystemWindowInsetBottom();
            insetLeft = insets.getSystemWindowInsetLeft();
            insetRight = insets.getSystemWindowInsetRight();
            if (android.os.Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                insetTop = Math.max(insetTop, insets.getDisplayCutout().getSafeInsetTop());
                insetBottom = Math.max(insetBottom, insets.getDisplayCutout().getSafeInsetBottom());
                insetLeft = Math.max(insetLeft, insets.getDisplayCutout().getSafeInsetLeft());
                insetRight = Math.max(insetRight, insets.getDisplayCutout().getSafeInsetRight());
            }
            positionControls();
            return insets;
        });
        post(this::positionControls);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void addButton(LinearLayout row, int label, Runnable action) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setTextSize(13);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setOnClickListener(v -> action.run());
        row.addView(button, displayW > displayH
                ? new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    }

    private void positionControls() {
        LayoutParams lp = (LayoutParams) controls.getLayoutParams();
        // Dock in the larger free region, using a narrow side strip in landscape.
        if (displayW > displayH) {
            lp.width = Math.min(dp(180), displayW / 3);
            lp.gravity = Gravity.CENTER_VERTICAL | (selection.left > displayW - selection.left - selection.side
                    ? Gravity.LEFT : Gravity.RIGHT);
        } else {
            lp.width = LayoutParams.MATCH_PARENT;
            lp.gravity = selection.top > displayH - selection.top - selection.side ? Gravity.TOP : Gravity.BOTTOM;
        }
        lp.topMargin = insetTop + dp(8);
        lp.bottomMargin = insetBottom + dp(8);
        lp.leftMargin = insetLeft + dp(8);
        lp.rightMargin = insetRight + dp(8);
        controls.setLayoutParams(lp);
    }

    private final class SelectionView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] origin = new int[2];
        private int corner = -1;
        private boolean dragging;
        private double downX, downY, startX, startY, anchorX, anchorY;

        SelectionView(Context context) {
            super(context);
            setContentDescription(getContext().getString(R.string.board_area_help));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            getLocationOnScreen(origin);
            canvas.save();
            canvas.translate(-origin[0], -origin[1]);
            float l = (float) selection.left, t = (float) selection.top, s = (float) selection.side;
            // No fill inside the selected board.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(0x9900E676);
            for (int i = 1; i < 8; i++) {
                canvas.drawLine(l + s * i / 8, t, l + s * i / 8, t + s, paint);
                canvas.drawLine(l, t + s * i / 8, l + s, t + s * i / 8, paint);
            }
            paint.setColor(0xFF00E676);
            paint.setStrokeWidth(dp(3));
            canvas.drawRect(l, t, l + s, t + s, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 4; i++) {
                float x = l + (i == 1 || i == 2 ? s : 0);
                float y = t + (i >= 2 ? s : 0);
                paint.setColor(0xFF002A12);
                canvas.drawCircle(x, y, dp(9), paint);
                paint.setColor(0xFF00E676);
                canvas.drawCircle(x, y, dp(6), paint);
            }
            canvas.restore();
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent event) {
            double x = event.getRawX(), y = event.getRawY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x; downY = y;
                    startX = selection.left; startY = selection.top;
                    initialSize = selection.side;
                    corner = -1;
                    double nearest = dp(28);
                    for (int i = 0; i < 4; i++) {
                        double cx = startX + (i == 1 || i == 2 ? selection.side : 0);
                        double cy = startY + (i >= 2 ? selection.side : 0);
                        double distance = Math.hypot(x - cx, y - cy);
                        if (distance < nearest) { nearest = distance; corner = i; }
                    }
                    dragging = corner >= 0 || (x >= startX && x <= startX + selection.side
                            && y >= startY && y <= startY + selection.side);
                    if (corner >= 0) {
                        anchorX = startX + (corner == 0 || corner == 3 ? selection.side : 0);
                        anchorY = startY + (corner < 2 ? selection.side : 0);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging) return true;
                    if (corner >= 0) {
                        // Preserve the finger-to-handle offset to avoid a jump on first movement.
                        double initialX = startX + (corner == 1 || corner == 2 ? initialSize : 0);
                        double initialY = startY + (corner >= 2 ? initialSize : 0);
                        selection.resize(corner, anchorX, anchorY, initialX + x - downX, initialY + y - downY);
                    } else selection.moveTo(startX + x - downX, startY + y - downY);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    positionControls();
                    return true;
                default:
                    return true;
            }
        }
        private double initialSize;
    }
}
