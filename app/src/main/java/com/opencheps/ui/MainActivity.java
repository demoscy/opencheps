package com.opencheps.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.opencheps.R;
import com.opencheps.capture.ScreenCaptureService;
import com.opencheps.util.Prefs;

public class MainActivity extends AppCompatActivity {

    private Button btnOverlay, btnStart, btnStop;
    private TextView statusTv, thinkTv, eloTv;
    private SeekBar thinkBar, eloBar;
    private RadioGroup sideGroup;
    private RadioButton sideWhite, sideBlack;
    private MediaProjectionManager mpm;

    private final ActivityResultLauncher<Intent> projectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == Activity.RESULT_OK && r.getData() != null) {
                    String side = sideBlack.isChecked() ? "b" : "w";
                    Prefs.setUserSide(this, side);
                    Intent svc = new Intent(this, ScreenCaptureService.class);
                    svc.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, r.getResultCode());
                    svc.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, r.getData());
                    svc.putExtra(ScreenCaptureService.EXTRA_USER_SIDE, side);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                    else startService(svc);
                    statusTv.setText(R.string.status_running);
                    btnStop.setEnabled(true);
                    btnStart.setEnabled(false);
                    moveTaskToBack(true);
                } else {
                    toast("Screen capture not granted");
                }
            });

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        btnOverlay = findViewById(R.id.btnOverlay);
        btnStart   = findViewById(R.id.btnStart);
        btnStop    = findViewById(R.id.btnStop);
        statusTv   = findViewById(R.id.status);
        thinkTv    = findViewById(R.id.thinkLabel);
        eloTv = findViewById(R.id.eloLabel);
        thinkBar   = findViewById(R.id.thinkBar);
        eloBar = findViewById(R.id.eloBar);
        sideGroup  = findViewById(R.id.sideGroup);
        sideWhite  = findViewById(R.id.sideWhite);
        sideBlack  = findViewById(R.id.sideBlack);

        SharedPreferences sp = Prefs.get(this);
        int think = Prefs.thinkMs(this);
        String side = sp.getString(Prefs.KEY_USER_SIDE, "w");

        thinkBar.setMax(Prefs.MAX_THINK_MS);
        thinkBar.setMin(Prefs.MIN_THINK_MS);
        thinkBar.setProgress(think);
        eloBar.setMax((Prefs.MAX_ELO - Prefs.MIN_ELO) / 10);
        eloBar.setProgress((Prefs.practiceElo(this) - Prefs.MIN_ELO) / 10);
        eloTv.setText(getString(R.string.practice_elo, Prefs.practiceElo(this)));
        CheckBox limit = findViewById(R.id.limitStrength);
        CheckBox line = findViewById(R.id.showStudyLine);
        limit.setChecked(Prefs.limitStrength(this));
        eloBar.setEnabled(limit.isChecked());
        line.setChecked(Prefs.showStudyLine(this));
        limit.setOnCheckedChangeListener((button, checked) -> {
            Prefs.setLimitStrength(this, checked);
            eloBar.setEnabled(checked);
        });
        line.setOnCheckedChangeListener((button, checked) -> Prefs.setShowStudyLine(this, checked));
        thinkTv.setText(thinkLabelText(think));
        if ("b".equals(side)) sideBlack.setChecked(true); else sideWhite.setChecked(true);

        thinkBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int v, boolean u) {
                int t = Prefs.clampThinkMs(v);
                thinkTv.setText(thinkLabelText(t));
                Prefs.setThinkMs(MainActivity.this, t);
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        eloBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                int elo = Prefs.MIN_ELO + value * 10;
                Prefs.setPracticeElo(MainActivity.this, elo);
                eloTv.setText(getString(R.string.practice_elo, elo));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        SeekBar choices = findViewById(R.id.multiPvBar);
        TextView choicesLabel = findViewById(R.id.multiPvLabel);
        choices.setMax(2);
        choices.setProgress(Prefs.multiPv(this) - 1);
        choicesLabel.setText(getString(R.string.move_choices, Prefs.multiPv(this)));
        choices.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                Prefs.setMultiPv(MainActivity.this, value + 1);
                choicesLabel.setText(getString(R.string.move_choices, value + 1));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        sideGroup.setOnCheckedChangeListener((g, id) -> {
            Prefs.setUserSide(this, id == R.id.sideBlack ? "b" : "w");
        });

        btnOverlay.setOnClickListener(v -> requestOverlay());
        btnStart.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) { toast("Grant overlay permission first"); return; }
            // Android offers either a single app or the entire display. Capture callbacks map
            // full-screen app content (excluding system bars) back to the overlay's screen origin.
            projectionLauncher.launch(mpm.createScreenCaptureIntent());
        });
        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, ScreenCaptureService.class));
            statusTv.setText(R.string.status_idle);
            btnStop.setEnabled(false);
            btnStart.setEnabled(true);
        });

        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ android.Manifest.permission.POST_NOTIFICATIONS }, 1);
        }
    }

    private String thinkLabelText(int ms) { return getString(R.string.think_time, ms); }

    @Override protected void onResume() {
        super.onResume();
        boolean overlay = Settings.canDrawOverlays(this);
        btnOverlay.setEnabled(!overlay);
        btnOverlay.setText(overlay
                ? getString(R.string.overlay_granted)
                : getString(R.string.grant_overlay));
        btnStart.setEnabled(overlay);
    }

    private void requestOverlay() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
