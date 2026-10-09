private void startFloatingServiceWithProjection(final int resultCode, final Intent resultData) {
    // 1. Stop existing service first
    try {
        Intent stopIntent = new Intent(this, FloatingButtonService.class);
        stopService(stopIntent);
    } catch (Exception ignored) {}

    // 2. Wait 500ms for cleanup, then start fresh
    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
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
            isActive = true;
            updateUi();
        }
    }, 500);
}
