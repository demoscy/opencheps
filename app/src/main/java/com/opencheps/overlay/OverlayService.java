package com.opencheps.overlay;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.opencheps.util.BoardArea;

import java.util.List;

public class OverlayService extends Service {
    private static final String TAG = "OC/Overlay";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile OverlayService instance;

    private WindowManager wm;
    private ArrowOverlayView arrows;
    private FloatingPanel panel;
    private CalibrationOverlay calibration;
    private CalibrationListener calibrationListener;
    private boolean suspended;

    public interface CalibrationListener {
        // changed=false means CANCEL, changed=true and area=null means AUTO.
        void onFinished(boolean changed, BoardArea area);
    }

    @Nullable @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        arrows = new ArrowOverlayView(this);
        try { wm.addView(arrows, OverlayWindow.fullScreen(false)); }
        catch (Exception e) { Log.e(TAG, "addView arrows failed", e); stopSelf(); return; }
        panel = new FloatingPanel(this);
        if (!panel.attach()) { stopSelf(); return; }
        instance = this;
    }

    /** Must be called on the main thread, before detaching the capture surface. */
    public static void suspendForCalibration() {
        if (instance == null) return;
        instance.suspended = true;
        instance.arrows.clearAll();
        instance.arrows.setVisibility(View.INVISIBLE);
        instance.panel.setVisible(false);
    }

    /** The caller has stopped capture before attaching the editor. */
    public static void showCalibration(int width, int height, int rotation, Rect initial,
                                       CalibrationListener listener) {
        OverlayService s = instance;
        if (s == null || !Settings.canDrawOverlays(s) || s.calibration != null) {
            listener.onFinished(false, null);
            return;
        }
        s.calibrationListener = listener;
        s.calibration = new CalibrationOverlay(s, width, height, rotation, initial,
                new CalibrationOverlay.Listener() {
                    @Override public void onSave(BoardArea area) { s.finishCalibration(true, area); }
                    @Override public void onAuto() { s.finishCalibration(true, null); }
                    @Override public void onCancel() { s.finishCalibration(false, null); }
                });
        try { s.wm.addView(s.calibration, OverlayWindow.fullScreen(true)); }
        catch (Exception e) {
            Log.e(TAG, "addView calibration failed", e);
            s.finishCalibration(false, null);
        }
    }

    private void finishCalibration(boolean changed, BoardArea area) {
        if (calibration != null) {
            try { wm.removeViewImmediate(calibration); } catch (Exception ignored) {}
            calibration = null;
        }
        CalibrationListener listener = calibrationListener;
        calibrationListener = null;
        if (listener != null) listener.onFinished(changed, area);
    }

    public static void resumeAfterCalibration() {
        if (instance == null) return;
        instance.suspended = false;
        instance.arrows.setVisibility(View.VISIBLE);
        instance.panel.setVisible(true);
    }

    public static void cancelCalibration() {
        if (instance != null) instance.finishCalibration(false, null);
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        finishCalibration(false, null);
    }

    @Override public void onDestroy() {
        instance = null;
        finishCalibration(false, null);
        try { if (arrows != null) wm.removeView(arrows); } catch (Exception ignored) {}
        if (panel != null) panel.detach();
        stopService(new Intent(this, com.opencheps.capture.ScreenCaptureService.class));
        super.onDestroy();
    }

    public static FloatingPanel panel() { return instance == null ? null : instance.panel; }

    // Android View state is owned by the main thread. Also reject results queued before editor entry.
    private static void render(java.util.function.Consumer<ArrowOverlayView> update) {
        Runnable task = () -> {
            OverlayService s = instance;
            if (s != null && !s.suspended) update.accept(s.arrows);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else MAIN.post(task);
    }

    public static void pushArrows(List<ArrowOverlayView.Arrow> arrows, Rect bounds) {
        render(v -> { v.setBoardBounds(bounds); v.setArrows(arrows); });
    }
    public static void setBoardBounds(Rect bounds) { render(v -> v.setBoardBounds(bounds)); }
    public static void setBoardFrameColor(int color) { render(v -> v.setBoardFrameColor(color)); }
    public static void clearArrowsOnly() { render(ArrowOverlayView::clearArrowsOnly); }
    public static void clearAll() { render(ArrowOverlayView::clearAll); }
}
