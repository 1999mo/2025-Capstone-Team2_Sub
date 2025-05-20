package com.example.eogmodule;

import android.content.Context;
import java.util.UUID;

public class EOGManager {

    private BluetoothHelper bluetoothHelper;
    private Context context;
    private EOGEventListener eogEventListener;
    private HorizontalListener horizontalListener;
    private VerticalListener verticalListener;
    private SectionListener sectionListener;

    public interface EOGEventListener {
        void onRawData(String rawData);
    }

    public interface HorizontalListener {
        void onHorizontal(String direction); // "LEFT" or "RIGHT"
    }

    public interface VerticalListener {
        void onVertical(String direction);   // "UP" or "DOWN"
    }

    public interface SectionListener {
        void onSection(int section);         // 1 ~ 8
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

    public void setHorizontalListener(HorizontalListener listener) {
        this.horizontalListener = listener;
    }

    public void setVerticalListener(VerticalListener listener) {
        this.verticalListener = listener;
    }

    public void setSectionListener(SectionListener listener) {
        this.sectionListener = listener;
    }

    public void connect(String deviceName, UUID uuid) {
        bluetoothHelper.connect(deviceName, uuid);
    }

    public void disconnect() {
        bluetoothHelper.disconnect();
    }

    private void processSensorData(String data) {
        String[] parts = data.split(",");
        float x = Float.parseFloat(parts[0].split(":")[1]);
        float y = Float.parseFloat(parts[1].split(":")[1]);

        // x 방향 액션 감지
        String hor = null;
        if (x > 200) hor = "LEFT";
        else if (x < -200) hor = "RIGHT";

        if (hor != null) {
            if (horizontalListener != null) {
                horizontalListener.onHorizontal(hor);
            }
        }

        // y 방향 액션 감지
        String ver = null;
        if (y > 200) {
            ver = "DOWN";
        } else if (y < -200) {
            ver = "UP";
        }
        if (ver != null) {
            if (verticalListener != null) {
                verticalListener.onVertical(ver);
            }
        }

        // Section 처리
        int section = calculateSection(x, y);
        if (sectionListener != null) {
            sectionListener.onSection(section);
        }
    }

    private int calculateSection(float x, float y) {
        // 이곳에서 section을 판단하고 반환

        int sec = 1;
        return sec;
    }


    /*
    private long lastLoggedTime = 0; //
    public void processSensorData(float x, float y) {
        long currentTime = System.currentTimeMillis();

        // 마지막 기록 이후 1500ms가 지났는 지 체크
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