package com.opencheps.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import com.opencheps.R;
import com.opencheps.util.Side;
import com.opencheps.vision.PositionRights;
import java.util.List;
import java.util.function.BiConsumer;

/** Current-position metadata only. The capture surface is detached before this window is shown. */
@SuppressLint("ViewConstructor")
final class SpecialMovesOverlay extends LinearLayout {
    private final CheckBox[] rights = new CheckBox[4];
    private final Spinner target;

    SpecialMovesOverlay(Context context, char[][] board, Side turn, String castling, String ep,
                        BiConsumer<String, String> save, Runnable cancel) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(0xFF101810);
        setPadding(dp(12), dp(12), dp(12), dp(12));
        ScrollView scroll = new ScrollView(context);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        text(body, context.getString(R.string.special_moves), 20);
        text(body, context.getString(R.string.special_moves_help), 14);
        text(body, context.getString(turn == Side.WHITE ? R.string.position_turn_white : R.string.position_turn_black), 16);
        int[] labels = {R.string.castle_white_king, R.string.castle_white_queen,
                R.string.castle_black_king, R.string.castle_black_queen};
        String possible = PositionRights.allowedCastling(board, "KQkq");
        for (int i = 0; i < rights.length; i++) {
            rights[i] = new CheckBox(context);
            rights[i].setText(labels[i]);
            rights[i].setTextColor(Color.WHITE);
            rights[i].setMinHeight(dp(48));
            rights[i].setEnabled(possible.indexOf("KQkq".charAt(i)) >= 0);
            rights[i].setChecked(castling.indexOf("KQkq".charAt(i)) >= 0 && rights[i].isEnabled());
            body.addView(rights[i]);
        }
        text(body, context.getString(R.string.en_passant_help), 14);
        List<String> targets = PositionRights.enPassantTargets(board, turn);
        // A dialog-style spinner needs a focusable application token; this overlay uses dropdown mode.
        target = createTarget(context, targets, ep);
        body.addView(target, new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
        scroll.addView(body);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout actions = new LinearLayout(context);
        Button cancelButton = new Button(context);
        cancelButton.setText(R.string.special_cancel);
        cancelButton.setOnClickListener(v -> cancel.run());
        actions.addView(cancelButton, new LayoutParams(0, dp(48), 1));
        Button saveButton = new Button(context);
        saveButton.setText(R.string.special_save);
        saveButton.setOnClickListener(v -> {
            StringBuilder selected = new StringBuilder();
            for (int i = 0; i < rights.length; i++) if (rights[i].isChecked()) selected.append("KQkq".charAt(i));
            save.accept(selected.toString(), targets.get(target.getSelectedItemPosition()));
        });
        actions.addView(saveButton, new LayoutParams(0, dp(48), 1));
        addView(actions);
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

    private Spinner createTarget(Context context, List<String> targets, String ep) {
        Spinner spinner = new Spinner(context, Spinner.MODE_DROPDOWN);
        spinner.setContentDescription(context.getString(R.string.en_passant_target));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, targets) {
            @Override public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, targets.indexOf(ep)));
        return spinner;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void text(LinearLayout body, String value, int size) {
        TextView label = new TextView(getContext());
        label.setText(value);
        label.setTextColor(Color.WHITE);
        label.setTextSize(size);
        label.setPadding(0, dp(6), 0, dp(6));
        body.addView(label);
    }
}
