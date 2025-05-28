package com.example.eogmodule;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EOGManager {

    private final SimpleDateFormat timeFormatter = new SimpleDateFormat("mm:ss.SSS", Locale.getDefault());
    private final SimpleDateFormat titleFormatter = new SimpleDateFormat("hh_mm", Locale.getDefault());

    private BluetoothHelper bluetoothHelper;
    private Context context;

    // 이벤트 리스너들
    private EOGEventListener eogEventListener;
    private HorizontalListener horizontalListener;
    private VerticalListener verticalListener;
    private SectionListener sectionListener;

    // 외부에서 설정 가능한 변수
    private int customLabel = -1;

    // 데이터를 임시로 저장할 버퍼 (스레드 안전하게)
    private final List<String> dataBuffer =
            Collections.synchronizedList(new ArrayList<String>());

    // 5초마다 파일로 플러시해줄 타이머
    private Timer writeTimer;

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
        this.context = context.getApplicationContext(); // 애플리케이션 컨텍스트 권장
        bluetoothHelper = new BluetoothHelper(context);

        // 5초 주기로 버퍼를 파일에 쓰도록 타이머 설정
        startWriteTimer();

        bluetoothHelper.setConnectionListener(new BluetoothHelper.ConnectionListener() {
            @Override
            public void onConnected() {}

            @Override
            public void onConnectionFailed(String reason) {}

            @Override
            public void onDataReceived(String data) {
                // 1) 기존 리스너 콜백
                if (eogEventListener != null) {
                    eogEventListener.onRawData(data);
                }
                // 2) 센서 처리
                processSensorData(data);
                // 3) 파일 기록용 버퍼에 추가
                bufferData(data);
            }
        });
    }

    /** 메서드로 분리한 타이머 초기화/재시작 로직 **/
    private void startWriteTimer() {
        // 기존 타이머가 있으면 취소
        if (writeTimer != null) {
            writeTimer.cancel();
        }
        writeTimer = new Timer();
        writeTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                flushBufferToFile();
            }
        }, 5000, 5000);
    }

    /** 사용자 콜백 설정 메서드들 **/
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
        // 타이머 취소
        if (writeTimer != null) {
            writeTimer.cancel();
            writeTimer = null;
        }
        // 남은 버퍼 즉시 플러시
        flushBufferToFile();
    }

    private void processSensorData(String data) {
        String[] parts = data.split(",");
        float x = Float.parseFloat(parts[0].split(":")[1]);
        float y = Float.parseFloat(parts[1].split(":")[1]);

        // x 방향 액션 감지
        String hor = null;
        if (x > 200) hor = "LEFT";
        else if (x < -200) hor = "RIGHT";

        if (hor != null && horizontalListener != null) {
            horizontalListener.onHorizontal(hor);
        }

        // y 방향 액션 감지
        String ver = null;
        if (y > 200) ver = "DOWN";
        else if (y < -200) ver = "UP";

        if (ver != null && verticalListener != null) {
            verticalListener.onVertical(ver);
        }

        // Section 계산
        int section = calculateSection(x, y);
        if (sectionListener != null) {
            sectionListener.onSection(section);
        }
    }

    private int calculateSection(float x, float y) {
        // TODO: 실제 로직 구현
        return 1;
    }

    /** 외부에서 customLabel 값을 설정하는 메서드 **/
    public void setCustomLabel(int label) {
        this.customLabel = label;
        startWriteTimer();
    }

    /** 현재 customLabel 값을 반환하는 메서드 **/
    public int getCustomLabel() {
        return this.customLabel;
    }

    /** 데이터를 파일에 바로 쓰지 않고 버퍼에 저장 **/
    private void bufferData(String data) {
        String timestamp = timeFormatter.format(new Date());
        String line = "[" + customLabel + "] " + "[" + timestamp + "] " + "," + data;
        dataBuffer.add(line);
    }

    /** 5초마다 호출되어 버퍼의 내용을 파일로 append **/
    private void flushBufferToFile() {
        List<String> copy;
        synchronized (dataBuffer) {
            if (dataBuffer.isEmpty()) return;
            copy = new ArrayList<>(dataBuffer);
            dataBuffer.clear();
        }

        File downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir != null && !downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        File outFile = new File(downloadDir, "direct_log_" + titleFormatter.format(new Date()) + ".txt");
        FileOutputStream fos = null;
        OutputStreamWriter writer = null;
        int writtenCount = 0;

        try {
            fos = new FileOutputStream(outFile, true); // append 모드
            writer = new OutputStreamWriter(fos);
            for (String line : copy) {
                writer.write(line);
                writer.write("\n");
                writtenCount++;
            }
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignored) {}
            }
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
            }
        }

        // 메인 스레드에서 Toast 띄우기
        if (writtenCount > 0) {
            int finalWrittenCount = writtenCount;
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(context,
                                finalWrittenCount + "개의 EOG 시그널이 파일에 저장되었습니다",
                                Toast.LENGTH_SHORT)
                        .show();
            });
        }
    }
}
