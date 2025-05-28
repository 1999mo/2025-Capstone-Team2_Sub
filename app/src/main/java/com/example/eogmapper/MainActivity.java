package com.example.eogmapper;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.eogmodule.EOGManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;


public class MainActivity extends AppCompatActivity  {
    private static final String DEVICE_NAME = "EOG_DEVICE";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // UUID로 교체
    private static final int REQUEST_CODE_PERMISSIONS = 20;
    private static final int CENTER_INDEX = 4;

    private EOGManager eogManager;
    private Handler handler = new Handler();
    private Random random = new Random();

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;

    private List<View> leftSections = new ArrayList<>();
    private List<View> rightSections = new ArrayList<>();
    private int prevIndex = -1;
    private int iteration = 0;
    private static final int TOTAL_ITER = 10;

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 화면 회전으로 인한 재생성 방지.
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // NEW 모듈 버전

        super.onCreate(savedInstanceState);
        // 액션바(타이틀바) 숨기기
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setContentView(R.layout.activity_main);

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
        // 0~8 사이 인덱스 랜덤 선택 (이전과 다르게)
        int idx;
        if (iteration % 2 == 0) {
            // 짝수 번째(iteration 0,2,4…)에는 항상 가운데
            idx = CENTER_INDEX;
        } else {
            // 홀수 번째(iteration 1,3,5…)에는 외곽 중 랜덤 (이전과 중복 방지)
            do {
                idx = random.nextInt(leftSections.size());
            } while (idx == prevIndex || idx == CENTER_INDEX);
        }
        prevIndex = idx;

        View leftSec  = leftSections.get(idx);
        View rightSec = rightSections.get(idx);

        // 기존/하이라이트 색
        int originalColor = 0xFFCCCCCC;
        int highlightColor = 0xFFB5D692;

        // 좌우 동시에 변경
        leftSec.setBackgroundColor(highlightColor);
        rightSec.setBackgroundColor(highlightColor);

        eogManager.setCustomLabel(idx);

        // 1.5초 후 원상복귀 및 다음 반복
        handler.postDelayed(() -> {
            leftSec.setBackgroundColor(originalColor);
            rightSec.setBackgroundColor(originalColor);
            iteration++;
            changeSection();
        }, 1500);
    }

    private void initSections() {
        leftSections.clear();
        rightSections.clear();

        // IDs가 section_left_1 ~ section_left_9, section_right_1 ~ section_right_9 로 정의되어 있어야 함
        for (int i = 1; i <= 9; i++) {
            int leftId  = getResources().getIdentifier("section_left_"  + i, "id", getPackageName());
            int rightId = getResources().getIdentifier("section_right_" + i, "id", getPackageName());
            leftSections.add(findViewById(leftId));
            rightSections.add(findViewById(rightId));
        }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (eogManager != null) {
            eogManager.disconnect();
        }
    }
}