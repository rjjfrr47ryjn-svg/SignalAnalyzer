package com.signalanalyzer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int OVERLAY_PERMISSION_REQUEST = 2001;

    private TextView statusLabel;
    private View statusDot;
    private Button startButton;
    private boolean isActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusLabel = findViewById(R.id.statusLabel);
        statusDot = findViewById(R.id.statusDot);
        startButton = findViewById(R.id.startButton);

        updateUi();

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isActive) {
                    requestOverlayAndStart();
                } else {
                    stopService();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isActive && !canDrawOverlays()) {
            isActive = false;
            updateUi();
        }
    }

    private void requestOverlayAndStart() {
        if (!canDrawOverlays()) {
            Toast.makeText(this,
                    "Please allow \"Display over other apps\"",
                    Toast.LENGTH_LONG).show();

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
            return;
        }
        startFloatingService();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (canDrawOverlays()) {
                startFloatingService();
            } else {
                Toast.makeText(this,
                        "Overlay permission denied",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void startFloatingService() {
        Intent svc = new Intent(this, FloatingButtonService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        isActive = true;
        updateUi();
    }

    private void stopService() {
        Intent svc = new Intent(this, FloatingButtonService.class);
        stopService(svc);
        isActive = false;
        updateUi();
    }

    private void updateUi() {
        if (isActive) {
            startButton.setText(R.string.stop_signal);
            startButton.setBackgroundColor(
                    getResources().getColor(R.color.neon_red, null));
            statusLabel.setText(R.string.floating_active);
            int cyan = getResources().getColor(R.color.neon_cyan, null);
            statusDot.setBackgroundColor(cyan);
            statusLabel.setTextColor(cyan);
        } else {
            startButton.setText(R.string.start_signal);
            startButton.setBackgroundColor(
                    getResources().getColor(R.color.neon_cyan, null));
            statusLabel.setText(R.string.status_ready);
            int green = getResources().getColor(R.color.neon_green, null);
            statusDot.setBackgroundColor(green);
            statusLabel.setTextColor(green);
        }
    }
}
