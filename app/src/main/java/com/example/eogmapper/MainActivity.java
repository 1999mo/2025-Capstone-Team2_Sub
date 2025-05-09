package com.example.eogmapper;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eogmodule.EOGManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;


public class MainActivity extends AppCompatActivity  {

    private Handler mHandler;

    private static final String DEVICE_NAME = "EOG_DEVICE";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // UUID로 교체

    private static final int REQUEST_CODE_PERMISSIONS = 10;

    private EOGManager eogManager;

    private TextView textViewStatus;
    private Button buttonConnect;

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // NEW 모듈 버전

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewStatus = findViewById(R.id.textViewStatus);
        buttonConnect = findViewById(R.id.buttonConnect);

        // Bluetooth 권한 체크 및 요청
        checkBluetoothPermissions();

        // Bluetooth 어댑터 준비
        bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth 지원 안됨", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // EOGManager 초기화
        eogManager = new EOGManager(this);
        eogManager.setEOGEventListener(new EOGManager.EOGEventListener() {
            @Override
            public void onEyeMovement(String direction) {
                String message = "눈 움직임 감지됨: " + direction;
                textViewStatus.setText(message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRawData(String rawData) {
                // 수신한 원본 데이터 처리
            }
        });

        // 버튼 클릭 → 연결 시작
        buttonConnect.setOnClickListener(v -> {
            //Toast.makeText(this, "블루투스 연결 시도중...", Toast.LENGTH_SHORT).show();
            checkBluetoothPermissions();
            eogManager.connect(DEVICE_NAME, MY_UUID);
        });

        // 방향 측정
        TextView directionText = findViewById(R.id.direction_text);
        Button directionCheckStartButton = findViewById(R.id.direction_check_start_button);
        setupDirectionCheck(directionText, directionCheckStartButton);
    }

    private void checkBluetoothPermissions() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_CODE_PERMISSIONS);
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CODE_PERMISSIONS);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_PERMISSIONS);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void setupDirectionCheck(TextView directionText, Button directionCheckStartButton) {

        final int IDLE_TIME = 2000;     // . 상태로 대기 시간
        final int WAIT_TIME = 2000;     // 방향 보여 주고 유지 시간
        final int MEASURE_COUNT = 10;   // 측정 횟수

        directionCheckStartButton.setOnClickListener(v -> {
            Thread directionThread = new Thread(() -> {
                for (int i = 0; i < MEASURE_COUNT; i++) {
                    mHandler.post(() -> directionText.setText("방향\n."));

                    try { Thread.sleep(IDLE_TIME); } catch (Exception ignored) {}

                    mHandler.post(() -> {
                        String direction;
                        if (Math.random() > 0.5) {
                            direction = "RIGHT";
                            directionText.setText("방향\n→");
                        } else {
                            direction = "LEFT";
                            directionText.setText("방향\n←");
                        }

                        // 로그 작성
                        // 파일명 direction_log.txt
                        // Download 폴더에 저장됨
                        long now = System.currentTimeMillis();
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("mm:ss:SSS");
                        String currentTime = sdf.format(new java.util.Date(now));
                        String logLine = "[" + currentTime + "] " + direction + "\n";

                        writeLog(logLine);
                    });

                    try { Thread.sleep(WAIT_TIME); } catch (Exception ignored) {}
                }

                mHandler.post(() -> directionText.setText("종료"));
            });

            directionThread.start();
        });
    }

    // 로그 파일에 문자열을 추가
    private void writeLog(String log) {
        try {
            // Downloads 폴더 경로
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File logFile = new File(downloadsDir, "direction_log.txt");

            FileWriter writer = new FileWriter(logFile, true); // true로 하면 append(추가) 모드
            writer.append(log);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (eogManager != null) {
            eogManager.disconnect();
        }
    }
}
