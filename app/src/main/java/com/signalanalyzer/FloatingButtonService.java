 package com.signalanalyzer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class FloatingButtonService extends Service {

    private static final String CHANNEL_ID = "signal_analyzer_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_SCREENSHOT_CAPTURED =
            "com.signalanalyzer.SCREENSHOT_CAPTURED";
    public static final String EXTRA_BASE64 = "base64_image";

    private WindowManager windowManager;
    private TextView floatingButton;
    private WindowManager.LayoutParams params;
    private NotificationManager notificationManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;

    private MediaProjectionManager projectionManager;

    // ═══════════════════════════════════════════════
    // STATIC — persists across service restarts
    // ═══════════════════════════════════════════════
    private static MediaProjection mediaProjection;
    private static VirtualDisplay virtualDisplay;
    private static ImageReader imageReader;
    private static int screenWidth, screenHeight, screenDensity;
    private static boolean projectionReady = false;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();

        // If projection already set up from previous service instance, keep it
        String initialText = projectionReady
                ? "Ready — tap to capture"
                : "Starting...";
        startForeground(NOTIFICATION_ID, buildNotification(initialText));

        projectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        addFloatingButton();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent resultData = intent.getParcelableExtra("resultData");

            if (resultData != null && resultCode != -1) {
                // Fresh data from MainActivity
                setupMediaProjection(resultCode, resultData);
            } else if (projectionReady && mediaProjection != null) {
                // Service restarted without data — but projection is alive
                updateNotification("Ready — tap to capture");
            } else {
                // No data, no projection — user must restart
                updateNotification("Capture not ready — restart app");
            }
        } else if (projectionReady && mediaProjection != null) {
            updateNotification("Ready — tap to capture");
        } else {
            updateNotification("Capture not ready — restart app");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null && floatingButton != null) {
            try { windowManager.removeView(floatingButton); } catch (Exception ignored) {}
        }
        // ⚠ DO NOT release projection here — keep it alive for restart
        // Only release if user explicitly stops (via MainActivity.stopService)
        // That will be handled when service fully stops
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void setupMediaProjection(int resultCode, Intent resultData) {
        // If already set up, skip
        if (projectionReady && mediaProjection != null) {
            updateNotification("Ready — tap to capture");
            return;
        }

        try {
            // Clean up old
            releaseProjection();

            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) {
                updateNotification("Projection null");
                return;
            }

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            screenDensity = metrics.densityDpi;

            imageReader = ImageReader.newInstance(
                    screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "SignalAnalyzerCapture",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);

            projectionReady = true;
            updateNotification("Ready — tap to capture");
        } catch (Exception e) {
            projectionReady = false;
            updateNotification("Setup error: " + e.getClass().getSimpleName());
        }
    }

    private void releaseProjection() {
        try {
            if (virtualDisplay != null) virtualDisplay.release();
        } catch (Exception ignored) {}
        try {
            if (imageReader != null) imageReader.close();
        } catch (Exception ignored) {}
        try {
            if (mediaProjection != null) mediaProjection.stop();
        } catch (Exception ignored) {}
        virtualDisplay = null;
        imageReader = null;
        mediaProjection = null;
        projectionReady = false;
    }

    // Public method for MainActivity to call on STOP
    public static void resetProjection() {
        try {
            if (virtualDisplay != null) virtualDisplay.release();
        } catch (Exception ignored) {}
        try {
            if (imageReader != null) imageReader.close();
        } catch (Exception ignored) {}
        try {
            if (mediaProjection != null) mediaProjection.stop();
        } catch (Exception ignored) {}
        virtualDisplay = null;
        imageReader = null;
        mediaProjection = null;
        projectionReady = false;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Signal Analyzer", NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) notificationManager.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent openApp = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp, flags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setContentTitle("Signal Analyzer")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        try {
            if (notificationManager != null)
                notificationManager.notify(NOTIFICATION_ID, buildNotification(text));
        } catch (Exception ignored) {}
    }

    private void addFloatingButton() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingButton = new TextView(this);
        floatingButton.setText("SA");
        floatingButton.setTextColor(Color.parseColor("#0A0E1A"));
        floatingButton.setTextSize(18);
        floatingButton.setTypeface(null, android.graphics.Typeface.BOLD);
        floatingButton.setGravity(Gravity.CENTER);
        setButtonBackground("#00E5FF");

        int size = dpToPx(56);
        params = new WindowManager.LayoutParams(
                size, size,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 300;

        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 20 || Math.abs(dy) > 20) isDragging = true;
                        if (isDragging) {
                            params.x = initialX + dx;
                            params.y = initialY + dy;
                            try { windowManager.updateViewLayout(floatingButton, params); }
                            catch (Exception ignored) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) onButtonTapped();
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        isDragging = false;
                        return true;
                }
                return false;
            }
        });
        windowManager.addView(floatingButton, params);
    }

    private void setButtonBackground(String hex) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.parseColor(hex));
        g.setStroke(4, Color.parseColor("#0A0E1A"));
        floatingButton.setBackground(g);
    }

    private void onButtonTapped() {
        setButtonBackground("#00E676");
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(60);
        } catch (Exception ignored) {}

        if (!projectionReady || mediaProjection == null || imageReader == null) {
            updateNotification("Capture not ready — restart app");
        } else {
            updateNotification("Capturing...");
            captureScreenshot();
        }

        handler.postDelayed(new Runnable() {
            @Override public void run() { setButtonBackground("#00E5FF"); }
        }, 400);
    }

    private void captureScreenshot() {
        try {
            if (imageReader == null) {
                updateNotification("ImageReader missing");
                return;
            }
            Image image = imageReader.acquireLatestImage();
            if (image == null) {
                // Retry a few times
                retryCount++;
                if (retryCount < 5) {
                    handler.postDelayed(new Runnable() {
                        @Override public void run() { captureScreenshot(); }
                    }, 200);
                } else {
                    retryCount = 0;
                    updateNotification("Capture failed — try again");
                }
                return;
            }
            retryCount = 0;
            processImage(image);
        } catch (Exception e) {
            updateNotification("Capture error");
        }
    }

    private int retryCount = 0;

    private void processImage(Image image) {
        Bitmap bitmap = null;
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
        } catch (Exception e) {
            updateNotification("Bitmap error");
            return;
        } finally {
            image.close();
        }
        if (bitmap == null) return;

        Bitmap cropped;
        try {
            cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
        } catch (Exception e) {
            cropped = bitmap;
        }

        updateNotification("Screenshot captured ✓");

        String base64 = bitmapToBase64(cropped);

        Intent broadcast = new Intent(ACTION_SCREENSHOT_CAPTURED);
        broadcast.setPackage(getPackageName());
        broadcast.putExtra(EXTRA_BASE64, base64);
        sendBroadcast(broadcast);

        handler.postDelayed(new Runnable() {
            @Override public void run() { updateNotification("Ready — tap to capture"); }
        }, 2500);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
