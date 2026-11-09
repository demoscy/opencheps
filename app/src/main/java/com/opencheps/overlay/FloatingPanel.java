package com.opencheps.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.widget.Button;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.util.DisplayMetrics;

import com.opencheps.R;
import com.opencheps.util.Side;
import com.opencheps.ui.MainActivity;

public class FloatingPanel {

    private final Context ctx;
    private final WindowManager wm;
    private View root;
    private WindowManager.LayoutParams lp;
    private TextView statusTv, evalTv, sideWhiteTv, sideBlackTv, continuationTv;
    private ImageButton toggleBtn;
    private Button turnBtn, flipBtn;
    private Side turn = Side.WHITE;
    private boolean paused = false;
    private Side userSide = Side.WHITE;
    private int screenW, screenH;
    private boolean minimized, positioned;
    private View expanded, bubble;

    public interface Listener {
        void onPauseToggle(boolean paused);
        void onStop();
        void onRecalibrate();
        void onSetBoardArea();
        void onCheckBoard();
        void onSideChange(Side newSide);
        void onTurnChange(Side side);
        void onFlipBoard();
    }
    private Listener listener;

    public FloatingPanel(Context ctx) {
        this.ctx = ctx;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public void setListener(Listener l) { this.listener = l; }

    @SuppressLint("ClickableViewAccessibility")
    public boolean attach() {
        if (root != null) return true;
        root = LayoutInflater.from(ctx).inflate(R.layout.floating_panel, null);
        expanded = root.findViewById(R.id.panelExpanded);
        bubble = root.findViewById(R.id.panelBubble);
        root.findViewById(R.id.panelMinimize).setOnClickListener(v -> setMinimized(true));
        bubble.setOnClickListener(v -> setMinimized(false));
        root.findViewById(R.id.panelSettings).setOnClickListener(v -> {
            try {
                ctx.startActivity(new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            } catch (RuntimeException e) { Toast.makeText(ctx, R.string.settings_open_failed, Toast.LENGTH_SHORT).show(); }
        });
        statusTv    = root.findViewById(R.id.panelStatus);
        evalTv      = root.findViewById(R.id.panelEval);
        continuationTv = root.findViewById(R.id.panelContinuation);
        sideWhiteTv = root.findViewById(R.id.sideWhite);
        sideBlackTv = root.findViewById(R.id.sideBlack);
        toggleBtn   = root.findViewById(R.id.panelToggle);
        turnBtn = root.findViewById(R.id.panelTurn);
        flipBtn = root.findViewById(R.id.panelFlip);
        turnBtn.setOnClickListener(v -> {
            if (listener != null) listener.onTurnChange(turn == Side.WHITE ? Side.BLACK : Side.WHITE);
        });
        flipBtn.setOnClickListener(v -> { if (listener != null) listener.onFlipBoard(); });
        ImageButton stopBtn  = root.findViewById(R.id.panelStop);
        ImageButton recalBtn = root.findViewById(R.id.panelRecal);

        toggleBtn.setOnClickListener(v -> {
            paused = !paused;
            toggleBtn.setImageResource(paused
                    ? android.R.drawable.ic_media_play
                    : android.R.drawable.ic_media_pause);
            bubble.setContentDescription(ctx.getString(paused ? R.string.panel_expand_paused : R.string.panel_expand));
            if (listener != null) listener.onPauseToggle(paused);
        });
        stopBtn.setOnClickListener(v -> {
            if (listener != null) listener.onStop();
        });
        recalBtn.setOnClickListener(v -> {
            if (listener != null) listener.onRecalibrate();
        });
        root.findViewById(R.id.panelCheckBoard).setOnClickListener(v -> {
            if (listener != null) listener.onCheckBoard();
        });
        root.findViewById(R.id.panelBoardArea).setOnClickListener(v -> {
            if (listener != null) listener.onSetBoardArea();
        });
        sideWhiteTv.setOnClickListener(v -> chooseSide(Side.WHITE));
        sideBlackTv.setOnClickListener(v -> chooseSide(Side.BLACK));

        // Match the arrow/editor origin; clamp the smaller window within the safe screen.
        lp = OverlayWindow.fullScreen(true);
        lp.width = lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        DisplayMetrics display = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(display);
        screenW = display.widthPixels;
        screenH = display.heightPixels;
        lp.x = dp(8);
        lp.y = dp(40);

        View.OnTouchListener drag = new View.OnTouchListener() {
            float dx, dy; int sx, sy; boolean dragging;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                        dx = e.getRawX(); dy = e.getRawY();
                        sx = lp.x; sy = lp.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dist = (float) Math.hypot(e.getRawX() - dx, e.getRawY() - dy);
                        if (dragging || dist > ViewConfiguration.get(ctx).getScaledTouchSlop()) {
                            dragging = true;
                            lp.x = sx + (int) (e.getRawX() - dx);
                            lp.y = sy + (int) (e.getRawY() - dy);
                            clampPosition();
                            try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
                            return true;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                        if (!dragging) v.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                        return true;
                    default:
                        return true;
                }
            }
        };
        root.setOnTouchListener(drag);
        root.findViewById(R.id.panelDragHandle).setOnTouchListener(drag);
        bubble.setOnTouchListener(drag);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> clampPosition());

        try { wm.addView(root, lp); }
        catch (Exception e) { root = null; return false; }
        renderSideToggle();
        return true;
    }

    private void chooseSide(Side s) {
        if (s == userSide) return;
        userSide = s;
        renderSideToggle();
        if (listener != null) listener.onSideChange(s);
    }

    private void renderSideToggle() {
        if (sideWhiteTv == null || sideBlackTv == null) return;
        sideWhiteTv.post(() -> {
            if (userSide == Side.WHITE) {
                sideWhiteTv.setBackgroundResource(R.drawable.pill_white);
                sideWhiteTv.setTextColor(0xFF000000);
                sideBlackTv.setBackgroundResource(R.drawable.pill_unselected);
                sideBlackTv.setTextColor(0xFFCCCCCC);
            } else {
                sideBlackTv.setBackgroundResource(R.drawable.pill_black);
                sideBlackTv.setTextColor(0xFFFFFFFF);
                sideWhiteTv.setBackgroundResource(R.drawable.pill_unselected);
                sideWhiteTv.setTextColor(0xFFCCCCCC);
            }
        });
    }

    public void setUserSide(Side s) {
        userSide = s;
        renderSideToggle();
    }

    public void placeBottomLeft(int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) return;
        if (positioned && lp != null && screenW > 0 && screenH > 0) {
            lp.x = Math.round(lp.x * (float) screenWidth / screenW);
            lp.y = Math.round(lp.y * (float) screenHeight / screenH);
        }
        this.screenW = screenWidth;
        this.screenH = screenHeight;
        if (root == null || lp == null) return;
        root.post(() -> {
            if (root == null) return;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            int h = root.getHeight();
            if (!positioned) {
                lp.x = dp(8);
                lp.y = screenH - h - dp(48);
                positioned = true;
            }
            try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
            clampPosition();
        });
    }

    private int dp(int value) { return Math.round(value * ctx.getResources().getDisplayMetrics().density); }

    private void clampPosition() {
        if (root == null || lp == null) return;
        Rect safe = OverlayService.displayInsets();
        int heightLimit = Math.max(dp(48), screenH - safe.top - safe.bottom);
        if (!minimized && root.getHeight() > heightLimit && lp.height != heightLimit) {
            lp.height = heightLimit; // Expanded controls scroll on short/landscape displays.
            try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
        }
        int x = Math.max(safe.left, Math.min(lp.x, screenW - safe.right - root.getWidth()));
        int y = Math.max(safe.top, Math.min(lp.y, screenH - safe.bottom - root.getHeight()));
        if (x == lp.x && y == lp.y) return;
        lp.x = x; lp.y = y;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
    }

    public void setMinimized(boolean value) {
        if (root == null) return;
        minimized = value;
        expanded.setVisibility(value ? View.GONE : View.VISIBLE);
        bubble.setVisibility(value ? View.VISIBLE : View.GONE);
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
        root.requestLayout();
        root.post(this::clampPosition);
    }

    public boolean isMinimized() { return minimized; }

    public void setPosition(Side sideToMove, Side bottom) {
        turn = sideToMove;
        if (turnBtn != null) turnBtn.setText(sideToMove == Side.WHITE ? R.string.position_turn_white : R.string.position_turn_black);
        if (flipBtn != null) flipBtn.setText(bottom == Side.WHITE ? R.string.position_bottom_white : R.string.position_bottom_black);
    }

    public Rect boundsOnScreen() {
        if (root == null) return new Rect();
        int[] origin = new int[2];
        root.getLocationOnScreen(origin);
        return new Rect(origin[0], origin[1], origin[0] + root.getWidth(), origin[1] + root.getHeight());
    }

    public void setVisible(boolean visible) {
        if (root != null) root.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    public boolean isPaused() { return paused; }

    public void setStatus(String s) {
        if (statusTv == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) statusTv.setText(s);
        else statusTv.post(() -> statusTv.setText(s));
    }

    public void setEval(String s) {
        if (evalTv == null) return;
        Runnable update = () -> {
            evalTv.setText(s == null ? "" : s);
            if (s == null || s.isEmpty()) setContinuation("");
        };
        if (Looper.myLooper() == Looper.getMainLooper()) update.run();
        else evalTv.post(update);
    }

    public void setContinuation(String line) {
        if (continuationTv == null) return;
        continuationTv.setText(line.isEmpty() ? "" : ctx.getString(R.string.possible_line, line));
        continuationTv.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void detach() {
        if (root != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
            root = null;
        }
    }
}
