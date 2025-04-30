package com.example.eogmapper;


import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

public class OverlayButtonService extends Service {

    private WindowManager windowManager;
    private View overlayView;
    private Handler handler = new Handler();
    private final int HOLD_DURATION = 1000; // 1초 꾹 누르기

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_buttons, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                400,
                400,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        windowManager.addView(overlayView, params);

        Button btnUp = overlayView.findViewById(R.id.btn_up);
        Button btnDown = overlayView.findViewById(R.id.btn_down);
        Button btnLeft = overlayView.findViewById(R.id.btn_left);
        Button btnRight = overlayView.findViewById(R.id.btn_right);

        // 꾹 눌렀을 때 동작 연결
        setHoldButtonBehavior(btnUp, () -> adjustBrightness(50));
        setHoldButtonBehavior(btnDown, () -> adjustBrightness(-50));
        setHoldButtonBehavior(btnLeft, () ->
                handler.postDelayed(() ->
                        InputAccessibilityService.getInstance().seekYouTubeBySeconds(-10), 500)
        );

        setHoldButtonBehavior(btnRight, () ->
                handler.postDelayed(() ->
                        InputAccessibilityService.getInstance().seekYouTubeBySeconds(+10), 500)
        );
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null) windowManager.removeView(overlayView);
    }

    private void setHoldButtonBehavior(Button button, Runnable action) {
        final int[] holdTime = {0};
        final int interval = 50; // 밀리초 단위
        final int maxTime = HOLD_DURATION;
        final Handler colorHandler = new Handler();

        Runnable[] colorRunnable = new Runnable[1];
        Runnable[] holdRunnable = new Runnable[1];

        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    holdTime[0] = 0;

                    colorRunnable[0] = new Runnable() {
                        @Override
                        public void run() {
                            holdTime[0] += interval;
                            float ratio = Math.min(1f, (float) holdTime[0] / maxTime);
                            int color = interpolateColor(Color.WHITE, Color.BLACK, ratio);
                            button.setBackgroundColor(color);
                            colorHandler.postDelayed(this, interval);
                        }
                    };

                    holdRunnable[0] = () -> {
                        action.run();
                        resetButton(button, colorHandler, colorRunnable[0]);
                    };

                    colorHandler.post(colorRunnable[0]);
                    handler.postDelayed(holdRunnable[0], HOLD_DURATION);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(holdRunnable[0]);
                    resetButton(button, colorHandler, colorRunnable[0]);
                    return true;
            }
            return false;
        });
    }
    private void adjustBrightness(int delta) {
        try {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

            int current = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);

            int newVal = Math.max(10, Math.min(255, current + delta));

            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    newVal);

            Toast.makeText(this,
                    delta > 0 ? "밝기 증가됨" : "밝기 감소됨",
                    Toast.LENGTH_SHORT).show();

        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
    }

    private int interpolateColor(int startColor, int endColor, float ratio) {
        int a = (int) (Color.alpha(startColor) + ratio * (Color.alpha(endColor) - Color.alpha(startColor)));
        int r = (int) (Color.red(startColor) + ratio * (Color.red(endColor) - Color.red(startColor)));
        int g = (int) (Color.green(startColor) + ratio * (Color.green(endColor) - Color.green(startColor)));
        int b = (int) (Color.blue(startColor) + ratio * (Color.blue(endColor) - Color.blue(startColor)));
        return Color.argb(a, r, g, b);
    }

    private void resetButton(Button button, Handler colorHandler, Runnable colorRunnable) {
        colorHandler.removeCallbacks(colorRunnable);
        button.setBackgroundColor(Color.WHITE);
    }
}