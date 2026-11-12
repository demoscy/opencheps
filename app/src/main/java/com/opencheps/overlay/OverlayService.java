package com.opencheps.overlay;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import com.opencheps.util.CaptureViewport;
import android.os.Handler;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;

import androidx.annotation.Nullable;

import com.opencheps.util.BoardArea;
import com.opencheps.util.Side;
import java.util.function.BiConsumer;

import java.util.List;

public class OverlayService extends Service {
    private static final String TAG = "OC/Overlay";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile OverlayService instance;

    private WindowManager wm;
    private ArrowOverlayView arrows;
    private FloatingPanel panel;
    private CalibrationOverlay calibration;
    private BoardPreviewOverlay preview;
    private Runnable previewClosed;
    private SpecialMovesOverlay specialMoves;
    private BiConsumer<String, String> specialMovesClosed;
    private CalibrationListener calibrationListener;
    private boolean suspended;
    private boolean capturing;
    private boolean settingsVisible;

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
        instance.updateVisibility();
    }

    /** The caller has stopped capture before attaching the editor. */
    public static void showCalibration(int width, int height, int rotation, Rect initial,
                                       CalibrationListener listener) {
        OverlayService s = instance;
        if (s == null || !Settings.canDrawOverlays(s) || s.calibration != null || s.preview != null || s.specialMoves != null) {
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

    /** Takes ownership of a memory-only copy; the caller has detached capture first. */
    public static void showBoardPreview(Bitmap bitmap, String details, Runnable closed) {
        OverlayService service = instance;
        if (service == null || !Settings.canDrawOverlays(service) || service.preview != null || service.calibration != null || service.specialMoves != null) {
            if (bitmap != null) bitmap.recycle();
            closed.run();
            return;
        }
        service.previewClosed = closed;
        service.preview = new BoardPreviewOverlay(service, bitmap, details, service::finishPreview);
        try { service.wm.addView(service.preview, OverlayWindow.fullScreen(true)); }
        catch (RuntimeException e) {
            Log.e(TAG, "Could not show captured-board preview", e);
            service.finishPreview();
        }
    }

    /** Null metadata on close means CANCEL; capture must already be detached. */
    public static void showSpecialMoves(char[][] board, Side turn, String rights, String target,
                                       BiConsumer<String, String> closed) {
        OverlayService service = instance;
        if (service == null || !Settings.canDrawOverlays(service) || service.specialMoves != null
                || service.preview != null || service.calibration != null) {
            closed.accept(null, null);
            return;
        }
        service.specialMovesClosed = closed;
        service.specialMoves = new SpecialMovesOverlay(service, board, turn, rights, target,
                service::finishSpecialMoves, () -> service.finishSpecialMoves(null, null));
        try { service.wm.addView(service.specialMoves, OverlayWindow.fullScreen(true)); }
        catch (RuntimeException e) {
            Log.e(TAG, "Could not show special-move controls", e);
            service.finishSpecialMoves(null, null);
        }
    }

    private void finishSpecialMoves(String rights, String target) {
        if (specialMoves != null) {
            try { wm.removeViewImmediate(specialMoves); } catch (RuntimeException ignored) {}
            specialMoves = null;
        }
        BiConsumer<String, String> closed = specialMovesClosed;
        specialMovesClosed = null;
        if (closed != null) closed.accept(rights, target);
    }

    private void finishPreview() {
        if (preview != null) {
            try { wm.removeViewImmediate(preview); } catch (RuntimeException ignored) {}
            preview.releaseImage();
            preview = null;
        }
        Runnable closed = previewClosed;
        previewClosed = null;
        if (closed != null) closed.run();
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
        instance.updateVisibility();
    }

    /** Main thread only: hide our own pixels briefly while a fresh capture frame is acquired. */
    public static void setCapturing(boolean capturing) {
        if (instance == null) return;
        instance.capturing = capturing;
        instance.updateVisibility();
    }

    private void updateVisibility() {
        boolean visible = !suspended && !capturing && !settingsVisible;
        arrows.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        panel.setVisible(visible);
    }

    public static void setSettingsVisible(boolean visible) {
        OverlayService service = instance;
        if (service == null) return;
        service.settingsVisible = visible;
        if (visible) service.arrows.clearAll();
        service.updateVisibility();
    }

    /** Visible system bars describe the on-screen origin of a normal full-screen shared app. */
    public static Rect displayInsets() {
        if (instance == null) return new Rect();
        WindowInsets insets = instance.arrows.getRootWindowInsets();
        if (insets == null) return new Rect();
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new Rect(safe.left, safe.top, safe.right, safe.bottom);
        }
        return new Rect(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
    }

    /** Main-thread snapshot of our own pixels for change probes; never used for recognition. */
    public static int[] captureMask(int width, int height, CaptureViewport viewport) {
        OverlayService service = instance;
        if (service == null || viewport == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.scale((float) width / viewport.width, (float) height / viewport.height);
            canvas.translate(-viewport.left, -viewport.top);
            int[] origin = new int[2];
            service.arrows.getLocationOnScreen(origin);
            canvas.save();
            canvas.translate(origin[0], origin[1]);
            service.arrows.draw(canvas);
            canvas.restore();
            Rect panelBounds = service.panel.boundsOnScreen();
            Paint paint = new Paint();
            paint.setColor(0xFF000000);
            canvas.drawRect(panelBounds, paint);
            int[] result = new int[width * height];
            bitmap.getPixels(result, 0, width, 0, 0, width, height);
            return result;
        } finally { bitmap.recycle(); }
    }

    public static void cancelCalibration() {
        if (instance != null) {
            instance.finishSpecialMoves(null, null);
            instance.finishPreview();
            instance.finishCalibration(false, null);
        }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        finishSpecialMoves(null, null);
        finishPreview();
        finishCalibration(false, null);
        android.util.DisplayMetrics display = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(display);
        if (panel != null) panel.placeBottomLeft(display.widthPixels, display.heightPixels);
    }

    @Override public void onDestroy() {
        instance = null;
        finishSpecialMoves(null, null);
        finishPreview();
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
            if (s != null && !s.suspended && !s.settingsVisible) update.accept(s.arrows);
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
