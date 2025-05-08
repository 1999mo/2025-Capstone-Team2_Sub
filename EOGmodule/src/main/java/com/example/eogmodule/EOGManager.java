package com.example.eogmodule;

import android.content.Context;
import java.util.UUID;

public class EOGManager {

    private BluetoothHelper bluetoothHelper;
    private Context context;
    private EOGEventListener eogEventListener;

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
            public void onConnectionFailed(String reason) {}

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
        if (x > 200) direction = "LEFT";
        else if (x < -200) direction = "RIGHT";

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