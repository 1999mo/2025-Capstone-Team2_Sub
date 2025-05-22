package com.example.eogmodule;

import android.content.Context;
import android.widget.Toast;

import java.util.UUID;

public class EOGManager {

    private BluetoothHelper bluetoothHelper;
    private Context context;
    private EOGEventListener eogEventListener;
    private static long y_prevTime = 0;
    private static long x_prevTime = 0;
    private static boolean eyeBlinkDetector = false;
    private static boolean eyeHorizontalMovementDetector = false;
    private static boolean selector = false;
    private static long selectedTime = 0;
    private static String prevDirection = null;




    public interface EOGEventListener {
        void onEyeMovement(String direction);
        void onRawData(String rawData);
    }

    public EOGManager(Context context) {
        this.context = context;
        bluetoothHelper = new BluetoothHelper(context);
        bluetoothHelper.setConnectionListener(new BluetoothHelper.ConnectionListener() {
            @Override
            public void onConnected() {}

            @Override
            public void onConnectionFailed(String reason) {
            }

            @Override
            public void onDataReceived(String data) {
                if (eogEventListener != null) eogEventListener.onRawData(data);
                processSensorData(data);
            }
        });
    }

    public void setEOGEventListener(EOGEventListener listener) {
        this.eogEventListener = listener;
    }

    public void connect(String deviceName, UUID uuid) {
        bluetoothHelper.connect(deviceName, uuid);
    }

    public void disconnect() {
        bluetoothHelper.disconnect();
    }

    private void processSensorData(String data) {
        String[] temp = data.split(",");
        String[] temp2 = temp[0].split(":");
        float x = Float.parseFloat(temp2[1]);
        temp2 = temp[1].split(":");
        float y = Float.parseFloat(temp2[1]);

        String direction = null;

        /*
        *
        * x는 액션이 발생하는 순간 방향을 감지할수 있음. (대신 더 나누는건 분류기가 나와야 함.
        * y는 깜빡임인지는 알수 있음. 상하 움직임은 분류기가 나와야 함.
        *1이 취소 2가 오른쪽 3이 왼쪽
        */
        long currTime = System.currentTimeMillis();
        if( currTime - selectedTime > 1500) {
            if (Math.abs(x) > 0.5 && !eyeHorizontalMovementDetector) {
                eyeHorizontalMovementDetector = true;
                selector = true;
                direction = x < 0 ? "3" : "2";
                prevDirection = direction;
                x_prevTime = System.currentTimeMillis();
            }
            if (eyeHorizontalMovementDetector) { //단순 타임아웃
                long x_currentTime = System.currentTimeMillis();
                if (x_currentTime - x_prevTime > 1000) {
                    eyeHorizontalMovementDetector = false;
                }
            }
            if (selector) {
                if (Math.abs(x) > 0.5) {
                    //취소됨
                    direction = "1";
                    selector = false;
                    eyeHorizontalMovementDetector = false;
                }
                long selector_time = System.currentTimeMillis();
                if (selector_time - x_prevTime > 3000) {
                    //선택됨
                    selector = false;
                    eyeHorizontalMovementDetector = false;
                    selectedTime = System.currentTimeMillis();
                }
            }
        }

        if (y > 0.7 && !eyeBlinkDetector) {
            eyeBlinkDetector = true;
            y_prevTime = System.currentTimeMillis();
        }
        if (eyeBlinkDetector) {
            long y_currentTime = System.currentTimeMillis();
            if (y_currentTime - y_prevTime > 500) {
                if (y > 0.5) {
                    direction = "vertical movement"; //should be classified
                }
                else {
                    direction = "blink";
                }
                eyeBlinkDetector = false;
            }
        }



        if (direction != null && eogEventListener != null) {
            eogEventListener.onEyeMovement(direction);
        }
    }
    /*
        private static final int THRESHOLD = 200;
    private long lastLoggedTime = 0; //
    public void processSensorData(float x, float y) {
        long currentTime = System.currentTimeMillis();

        // 마지막 기록 이후 1500ms가 지났는지 체크
        if (currentTime - lastLoggedTime < 1500) {
            // 아직 쿨타임
            return;
        }

        String direction = null;

        if (x > THRESHOLD) {
            direction = "LEFT";
        } else if (x < -THRESHOLD) {
            direction = "RIGHT";
        }

        if (direction != null) {
            // 로그 작성
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("mm:ss:SSS");
            String formattedTime = sdf.format(new java.util.Date(currentTime));

            String logLine = "[" + formattedTime + "]          EOG SIGNAL: " + direction + "\n\n";
            writeLog(logLine);

            // 마지막 기록 시간 갱신 (쿨타임 시작)
            lastLoggedTime = currentTime;
        }
    }
    */
}