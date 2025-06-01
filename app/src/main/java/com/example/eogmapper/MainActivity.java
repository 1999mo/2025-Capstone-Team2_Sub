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

import java.io.DataInput;
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

    private TextView textDirectionResult;

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

        textDirectionResult = findViewById(R.id.textDirectionResult);

        // EOGManager 초기화
        eogManager = new EOGManager(this);
        eogManager.setEOGEventListener(new EOGManager.EOGEventListener() {
            @Override
            public void onEyeMovement(EOGManager.Direction direction) {
                String dirText;
                switch (direction) {
                    case LEFT:  dirText = "왼쪽";   break;
                    case RIGHT: dirText = "오른쪽"; break;
                    case UP:    dirText = "위";     break;
                    case DOWN:  dirText = "아래";   break;
                    default:    dirText = "알 수 없음"; break;
                }

                textDirectionResult.setText(dirText);
                String message = "눈 움직임 감지: " + dirText;
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
