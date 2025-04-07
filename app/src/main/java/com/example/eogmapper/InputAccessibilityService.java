package com.example.eogmapper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;
import android.util.DisplayMetrics;

public class InputAccessibilityService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 필요시 처리
    }

    @Override
    public void onInterrupt() {
        // 인터럽트 처리
    }

    public void performClick(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription strokeDescription =
                new GestureDescription.StrokeDescription(path, 0, 100);
        GestureDescription gestureDescription =
                new GestureDescription.Builder()
                        .addStroke(strokeDescription)
                        .build();

        dispatchGesture(gestureDescription, null, null);
    }


    private static InputAccessibilityService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static void requestStatusBarDrag() {
        if (instance != null) {
            instance.performDragDownGesture();
        }
    }

    // 제스처로 드래그 다운 수행 (위에서 아래로 슬라이드)
    public void performDragDownGesture() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float width = metrics.widthPixels;
        float height = metrics.heightPixels;

        float startX = width / 2;
        float startY = 0;
        float endY = height / 3f; // 하단까지 내릴 필요 없이 일정 거리만

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(startX, endY);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 300);
        GestureDescription gesture =
                new GestureDescription.Builder().addStroke(stroke).build();

        dispatchGesture(gesture, null, null);
    }
}
