package com.example.eogmapper;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

public class FloatingButtonService extends Service {
    private WindowManager windowManager;
    private LinearLayout floatingLayout;
    private WindowManager.LayoutParams params;

    @Override
    public IBinder onBind(Intent intent) {
        return null; // 바인드 안함
    }

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatingLayout = new LinearLayout(this);
        floatingLayout.setOrientation(LinearLayout.HORIZONTAL);

        Button increaseBtn = new Button(this);
        increaseBtn.setText("+");

        Button decreaseBtn = new Button(this);
        decreaseBtn.setText("-");

        floatingLayout.addView(increaseBtn);
        floatingLayout.addView(decreaseBtn);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 100;

        windowManager.addView(floatingLayout, params);

        increaseBtn.setOnClickListener(v -> adjustBrightness(50));
        decreaseBtn.setOnClickListener(v -> adjustBrightness(-50));
    }

    private void adjustBrightness(int delta) {
        try {
            // 자동 밝기 모드를 수동으로 변경
            Settings.System.putInt(
                    getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

            // 현재 밝기 가져오기
            int brightness = Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);

            // 밝기 값 조정
            brightness += delta;
            if (brightness > 255) brightness = 255;
            if (brightness < 10) brightness = 10;

            // 시스템 설정 업데이트
            Settings.System.putInt(
                    getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness);

            // 여기서 params를 직접 수정
            params.screenBrightness = brightness / 255.0f;
            windowManager.updateViewLayout(floatingLayout, params);

        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingLayout != null) windowManager.removeView(floatingLayout);
    }
}