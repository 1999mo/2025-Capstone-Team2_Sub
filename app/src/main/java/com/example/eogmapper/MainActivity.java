package com.example.eogmapper;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.eogmodule.EOGManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;


public class MainActivity extends AppCompatActivity  {
    private static final String DEVICE_NAME = "EOG_DEVICE";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // UUID로 교체

    private static final int REQUEST_CODE_PERMISSIONS = 10;

    private EOGManager eogManager;
    private Handler handler = new Handler();
    private Random random = new Random();

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;

    private boolean isDataMode = false;

    private List<View> sections = new ArrayList<>();
    private int prevIndex = -1;
    private int iteration = 0;
    private static final int TOTAL_ITER = 20;
    private View layoutNormal, layoutDataCollect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // NEW 모듈 버전

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        layoutNormal      = findViewById(R.id.layout_normal);
        layoutDataCollect = findViewById(R.id.layout_datacollect);
        Button btnConnect = findViewById(R.id.buttonConnect);
        Button btnData    = findViewById(R.id.data_collection_button);

        // 로그 기록용 권한 요청
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_PERMISSIONS);
        }

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

        // 버튼 클릭 -> 블루투스 연결
        Button buttonConnect = findViewById(R.id.buttonConnect);
        buttonConnect.setOnClickListener(v -> {
            //Toast.makeText(this, "블루투스 연결 시도중...", Toast.LENGTH_SHORT).show();
            checkBluetoothPermissions();
            eogManager.connect(DEVICE_NAME, MY_UUID);
        });


        // 데이터 수집 시작 버튼
        Button dataCollectBtn = findViewById(R.id.data_collection_button);
        dataCollectBtn.setOnClickListener(v -> {
            // layout 전환
            findViewById(R.id.layout_normal).setVisibility(View.GONE);
            findViewById(R.id.layout_datacollect).setVisibility(View.VISIBLE);

            // 화면 가로 고정
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            initSections();

            // 3초 뒤 시작
            handler.postDelayed(this::startSequence, 3000);
        });
    }

    private void startSequence() {
        iteration = 0;
        prevIndex = -1;
        handler.post(this::changeSection);
    }

    private void changeSection() {
        if (iteration >= TOTAL_ITER) {
            // 종료
            return;
        }
        // 랜덤 섹션 선택 (이전과 다르게)
        int idx;
        do {
            idx = random.nextInt(sections.size());
        } while (idx == prevIndex);
        prevIndex = idx;

        View sec = sections.get(idx);

        // 색 변경
        int originalColor = 0xFFCCCCCC;
        int highlightColor = 0xffb5d692;
        sec.setBackgroundColor(highlightColor);

        writeLog("SECTION " + (idx+1) + "\n");

        // 1초 후 색 복원 & 다음 반복
        handler.postDelayed(() -> {
            sec.setBackgroundColor(originalColor);
            iteration++;
            changeSection();
        }, 1500);
    }

    private void initSections() {
        sections.clear();
        sections.add(findViewById(R.id.section1));
        sections.add(findViewById(R.id.section2));
        sections.add(findViewById(R.id.section3));
        sections.add(findViewById(R.id.section4));
        sections.add(findViewById(R.id.section5));
        sections.add(findViewById(R.id.section6));
        sections.add(findViewById(R.id.section7));
        sections.add(findViewById(R.id.section8));

        eogManager.setEOGEventListener(rawData -> writeLog(rawData + "\n"));
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

    // 로그 파일에 문자열을 추가
    private void writeLog(String log) {
        try {
            // Downloads 폴더 경로
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File logFile = new File(downloadsDir, "EOG_log.txt");

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
