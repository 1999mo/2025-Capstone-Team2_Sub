package com.example.eogmapper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.view.accessibility.AccessibilityEvent;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class InputAccessibilityService extends AccessibilityService {

    private static InputAccessibilityService instance;
    final int ACTION_SET_PROGRESS = 0x00002000;
    final String ARG_PROGRESS_VALUE = "android.view.accessibility.action.ARGUMENT_PROGRESS_VALUE";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 필요시 처리
    }

    @Override
    public void onInterrupt() {
        // 인터럽트 처리
    }

    public static InputAccessibilityService getInstance() {
        return instance;
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

    // 더블 클릭 발동
    public static void triggerDoubleTap(float xRatio) {
        if (instance == null) return;

        DisplayMetrics metrics = instance.getResources().getDisplayMetrics();
        int x = (int) (metrics.widthPixels * xRatio);
        int y = metrics.heightPixels / 2;

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription tapGesture1 = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 50))
                .build();

        GestureDescription tapGesture2 = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 50))
                .build();

        instance.dispatchGesture(tapGesture1, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);

                // 두 번째 탭은 첫 번째 완료 후 70ms 정도 지연
                new Handler().postDelayed(() -> {
                    instance.dispatchGesture(tapGesture2, null, null);
                }, 70);
            }
        }, null);
    }

    public void seekYouTubeBySeconds(int seconds) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        AccessibilityNodeInfo seekBarNode = findSeekBarNode(rootNode);
        if (seekBarNode == null) {
            Toast.makeText(this, "SeekBar 찾을 수 없음", Toast.LENGTH_SHORT).show();
            return;
        }

        AccessibilityNodeInfo.RangeInfo rangeInfo = seekBarNode.getRangeInfo();
        if (rangeInfo == null) {
            Toast.makeText(this, "재생 정보 없음", Toast.LENGTH_SHORT).show();
            return;
        }

        float current = rangeInfo.getCurrent();
        float max = rangeInfo.getMax();
        float newVal = current + seconds;
        newVal = Math.max(rangeInfo.getMin(), Math.min(max, newVal));

        final int ACTION_SET_PROGRESS = 0x00002000;
        final String ARG_PROGRESS_VALUE = "android.view.accessibility.action.ARGUMENT_PROGRESS_VALUE";

        Bundle setProgressArgs = new Bundle();
        setProgressArgs.putFloat(ARG_PROGRESS_VALUE, newVal);
        seekBarNode.performAction(ACTION_SET_PROGRESS, setProgressArgs);

        Toast.makeText(this, (seconds > 0 ? "+10초" : "-10초") + " 이동", Toast.LENGTH_SHORT).show();
    }
    private AccessibilityNodeInfo findSeekBarNode(AccessibilityNodeInfo node) {
        if (node == null) return null;

        if ("android.widget.SeekBar".equals(node.getClassName())) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findSeekBarNode(child);
            if (result != null) return result;
        }

        return null;
    }
}
