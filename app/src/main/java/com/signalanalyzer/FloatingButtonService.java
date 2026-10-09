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
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.nio.ByteBuffer;

public class FloatingButtonService extends Service {

    private static final String CHANNEL_ID = "signal_analyzer_channel";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private TextView floatingButton;
    private WindowManager.LayoutParams params;
    private NotificationManager notificationManager;

    private Handler handler = new Handler(Looper.getMainLooper());

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight, screenDensity;

    private ImageView previewView;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Starting..."));

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
                setupMediaProjection(resultCode, resultData);
            } else {
                updateNotification("Ready — tap the button");
            }
        } else {
            updateNotification("Ready — tap the button");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null) {
            if (floatingButton != null) {
                try { windowManager.removeView(floatingButton); } catch (Exception ignored) {}
            }
            if (previewView != null) {
                try { windowManager.removeView(previewView); } catch (Exception ignored) {}
            }
        }
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (imageReader != null) {
            try { imageReader.close(); } catch (Exception ignored) {}
            imageReader = null;
        }
        if (mediaProjection != null) {
            try { mediaProjection.stop(); } catch (Exception ignored) {}
            mediaProjection = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void setupMediaProjection(int resultCode, Intent resultData) {
        try {
            if (mediaProjection != null) {
                try { mediaProjection.stop(); } catch (Exception ignored) {}
                mediaProjection = null;
            }
            if (virtualDisplay != null) {
                try { virtualDisplay.release(); } catch (Exception ignored) {}
                virtualDisplay = null;
            }
            if (imageReader != null) {
                try { imageReader.close(); } catch (Exception ignored) {}
                imageReader = null;
            }

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
                    screenWidth, screenHeight,
                    PixelFormat.RGBA_8888, 2);

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "SignalAnalyzerCapture",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null, null);

            updateNotification("Ready — tap the button to capture");
        } catch (Exception e) {
            updateNotification("Setup error: " + e.getClass().getSimpleName());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Signal Analyzer", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Floating assistant status");
            if (notificationManager != null) notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String statusText) {
        Intent openApp = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("Signal Analyzer")
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
    }

    private void updateNotification(String statusText) {
        try {
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, buildNotification(statusText));
            }
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
                PixelFormat.TRANSLUCENT
        );
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
                            try {
                                windowManager.updateViewLayout(floatingButton, params);
                            } catch (Exception ignored) {}
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

    private void setButtonBackground(String colorHex) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor(colorHex));
        circle.setStroke(4, Color.parseColor("#0A0E1A"));
        floatingButton.setBackground(circle);
    }

    private void onButtonTapped() {
        setButtonBackground("#00E676");

        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(60);
        } catch (Exception ignored) {}

        if (mediaProjection == null) {
            updateNotification("Capture not ready — restart app");
        } else {
            updateNotification("Capturing...");
            captureScreenshot();
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (floatingButton != null) setButtonBackground("#00E5FF");
            }
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
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Image retry = imageReader.acquireLatestImage();
                        if (retry != null) {
                            processImage(retry);
                        } else {
                            updateNotification("Capture failed — try again");
                        }
                    }
                }, 200);
                return;
            }
            processImage(image);
        } catch (Exception e) {
            updateNotification("Error: " + e.getClass().getSimpleName());
        }
    }

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
                    screenHeight,
                    Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
        } catch (Exception e) {
            updateNotification("Bitmap error");
            return;
        } finally {
            image.close();
        }

        if (bitmap == null) {
            updateNotification("Null bitmap");
            return;
        }

        Bitmap cropped;
        try {
            cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
        } catch (Exception e) {
            cropped = bitmap;
        }

        updateNotification("Captured " + screenWidth + "x" + screenHeight);

        showPreview(cropped);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateNotification("Ready — tap the button to capture");
            }
        }, 3000);
    }

    private void showPreview(Bitmap bitmap) {
        try {
            if (windowManager == null) return;

            if (previewView != null) {
                try { windowManager.removeView(previewView); } catch (Exception ignored) {}
                previewView = null;
            }

            previewView = new ImageView(this);
            previewView.setImageBitmap(bitmap);
            previewView.setAlpha(0.9f);

            int previewWidth = dpToPx(200);
            int previewHeight = (int) ((float) previewWidth * bitmap.getHeight() / bitmap.getWidth());

            WindowManager.LayoutParams previewParams = new WindowManager.LayoutParams(
                    previewWidth, previewHeight,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            previewParams.gravity = Gravity.BOTTOM | Gravity.END;
            previewParams.x = 20;
            previewParams.y = 100;

            windowManager.addView(previewView, previewParams);

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (previewView != null && windowManager != null) {
                        try { windowManager.removeView(previewView); } catch (Exception ignored) {}
                        previewView = null;
                    }
                }
            }, 2500);
        } catch (Exception ignored) {}
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
