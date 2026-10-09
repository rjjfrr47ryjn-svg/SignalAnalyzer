package com.signalanalyzer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {

    private static final int OVERLAY_PERMISSION_REQUEST = 2001;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 2002;
    private static final int MEDIA_PROJECTION_REQUEST = 2003;

    private WebView webView;
    private MediaProjectionManager projectionManager;

    private BroadcastReceiver screenshotReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String base64 = intent.getStringExtra(FloatingButtonService.EXTRA_BASE64);
            if (base64 != null && webView != null) {
                final String b = base64;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript(
                                "javascript:onScreenshotCaptured('" + b + "')", null);
                    }
                });
            }
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        webView.setBackgroundColor(0xFF0A0E1A);
        webView.setWebViewClient(new WebViewClient());

        try {
            InputStream is = getAssets().open("index.html");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            is.close();
            String html = baos.toString("UTF-8");
            webView.loadDataWithBaseURL("file:///android_asset/", html,
                    "text/html", "UTF-8", null);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load UI: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");
        setContentView(webView);

        projectionManager = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

        // Register receiver for screenshots
        IntentFilter filter = new IntentFilter(FloatingButtonService.ACTION_SCREENSHOT_CAPTURED);
        if (Build.VERSION.SDK_INT >= 34) {
            registerReceiver(screenshotReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenshotReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(screenshotReceiver, filter);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(screenshotReceiver); } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    private class WebAppInterface {
        @JavascriptInterface
        public void requestStart() {
            runOnUiThread(new Runnable() {
                @Override public void run() { startPermissionFlow(); }
            });
        }

        @JavascriptInterface
        public void requestStop() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent svc = new Intent(MainActivity.this, FloatingButtonService.class);
                    stopService(svc);
                    sendJs("onServiceStopped()");
                }
            });
        }
    }

    private void startPermissionFlow() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{"android.permission.POST_NOTIFICATIONS"},
                        NOTIFICATION_PERMISSION_REQUEST);
                return;
            }
        }
        requestOverlayThenProjection();
    }

    private void requestOverlayThenProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                    "Please allow \"Display over other apps\"",
                    Toast.LENGTH_LONG).show();
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
            return;
        }
        requestMediaProjection();
    }

    private void requestMediaProjection() {
        if (projectionManager != null) {
            Intent captureIntent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, MEDIA_PROJECTION_REQUEST);
        } else {
            sendJs("onPermissionDenied('MediaProjection unavailable')");
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && Settings.canDrawOverlays(this)) {
                requestMediaProjection();
            } else {
                sendJs("onPermissionDenied('Overlay permission denied')");
            }
        } else if (requestCode == MEDIA_PROJECTION_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                startFloatingService(resultCode, data);
            } else {
                sendJs("onPermissionDenied('Screen capture denied')");
            }
        }
    }

    private void startFloatingService(final int resultCode, final Intent resultData) {
        try {
            Intent stopIntent = new Intent(this, FloatingButtonService.class);
            stopService(stopIntent);
        } catch (Exception ignored) {}

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent svc = new Intent(MainActivity.this, FloatingButtonService.class);
                svc.putExtra("resultCode", resultCode);
                svc.putExtra("resultData", resultData);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }

                sendJs("onServiceStarted()");
            }
        }, 400);
    }

    private void sendJs(final String jsCall) {
        if (webView != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("javascript:" + jsCall, null);
                }
            });
        }
    }
}
