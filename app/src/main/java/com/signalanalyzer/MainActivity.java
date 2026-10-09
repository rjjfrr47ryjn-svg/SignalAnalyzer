package com.signalanalyzer;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
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
    private static final int NOTIFICATION_PERMISSION_REQUEST = 2002;
    private static final int MEDIA_PROJECTION_REQUEST = 2003;

    private TextView statusLabel;
    private View statusDot;
    private Button startButton;
    private boolean isActive = false;

    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusLabel = findViewById(R.id.statusLabel);
        statusDot = findViewById(R.id.statusDot);
        startButton = findViewById(R.id.startButton);

        projectionManager = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

        updateUi();

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isActive) {
                    startPermissionFlow();
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

    // ---------- Permission flow ----------
    private void startPermissionFlow() {
        // Step 1: Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{"android.permission.POST_NOTIFICATIONS"},
                        NOTIFICATION_PERMISSION_REQUEST);
                return;
            }
        }
        // Step 2: Overlay permission
        requestOverlayThenProjection();
    }

    private void requestOverlayThenProjection() {
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
        // Step 3: MediaProjection permission
        requestMediaProjection();
    }

    private void requestMediaProjection() {
        if (projectionManager != null) {
            Intent captureIntent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, MEDIA_PROJECTION_REQUEST);
        } else {
            Toast.makeText(this,
                    "MediaProjection not available on this device",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            requestOverlayThenProjection();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (canDrawOverlays()) {
                requestMediaProjection();
            } else {
                Toast.makeText(this,
                        "Overlay permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == MEDIA_PROJECTION_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                startFloatingServiceWithProjection(resultCode, data);
            } else {
                Toast.makeText(this,
                        "Screen capture permission denied",
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

    // ---------- Service control ----------
    private void startFloatingServiceWithProjection(int resultCode, Intent data) {
        Intent svc = new Intent(this, FloatingButtonService.class);
        svc.setAction("START");
        svc.putExtra("resultCode", resultCode);
        svc.putExtra("resultData", data);
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

    // ---------- UI ----------
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
