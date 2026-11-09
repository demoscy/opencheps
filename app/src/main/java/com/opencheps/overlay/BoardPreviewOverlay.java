package com.opencheps.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.opencheps.R;

/** A memory-only copy of the last clean board crop. Capture is detached while visible. */
@SuppressLint("ViewConstructor")
final class BoardPreviewOverlay extends LinearLayout {
    private final ImageView image;

    BoardPreviewOverlay(Context context, Bitmap bitmap, String details, Runnable close) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(0xFF101810);
        setPadding(dp(12), dp(12), dp(12), dp(12));
        ScrollView scroll = new ScrollView(context);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        addText(body, context.getString(R.string.board_check_title), 18);
        addText(body, details, 14);
        image = new ImageView(context);
        image.setContentDescription(context.getString(R.string.board_check_image));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(bitmap);
        int height = Math.min(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels / 2);
        body.addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, height));
        addText(body, context.getString(R.string.board_check_help), 13);
        scroll.addView(body);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        Button button = new Button(context);
        button.setText(R.string.board_check_close);
        button.setMinHeight(dp(48));
        button.setOnClickListener(v -> close.run());
        addView(button, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setOnApplyWindowInsetsListener((view, insets) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                l = safe.left; t = safe.top; r = safe.right; b = safe.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft(); t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight(); b = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    l = Math.max(l, insets.getDisplayCutout().getSafeInsetLeft());
                    t = Math.max(t, insets.getDisplayCutout().getSafeInsetTop());
                    r = Math.max(r, insets.getDisplayCutout().getSafeInsetRight());
                    b = Math.max(b, insets.getDisplayCutout().getSafeInsetBottom());
                }
            }
            setPadding(dp(12)+l, dp(12)+t, dp(12)+r, dp(12)+b);
            return insets;
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void addText(LinearLayout parent, String text, int size) {
        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setTextSize(size);
        label.setPadding(0, dp(6), 0, dp(6));
        parent.addView(label, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
    void releaseImage() { image.setImageDrawable(null); }
}
