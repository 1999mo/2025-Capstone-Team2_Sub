package com.example.eogmapper;

import static com.example.eogmapper.ForegroundService.MY_UUID;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.provider.Settings;

public class MainActivity extends AppCompatActivity  {

    //https://blog.naver.com/ori_kku/222383254872
    //여기서 많이 훔쳤습니다.
    private TextView mMessageTextView;
    private Handler mHandler;
    private static Boolean tracking = false;
    private static final int REQUEST_CODE_PERMISSIONS = 2;
    LiveDataButton liveDataButton;

    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice bluetoothDevice;
    BluetoothSocket bluetoothSocket = null;
    OutputStream outputStream = null;
    InputStream inputStream = null;
    Thread workerThread = null;
    byte[] readBuffer;
    int readBufferPosition;
    String[] array = {"0"};

    private Button observerAddButton;
    private static final int REQUEST_CODE_WRITE_SETTINGS = 1001;
    private static final int REQUEST_CODE_DRAW_OVERLAY = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMessageTextView = findViewById(R.id.messageTextView);  // Initialize TextView

        // Create a Handler to post updates to the UI thread
        mHandler = new Handler(Looper.getMainLooper());

        // Bluetooth 관련 권한
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, 10);
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 10);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
        }
        // Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        // if(!bluetoothAdapter.isEnabled()) {
        //     startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        // }
        bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();

        liveDataButton = new ViewModelProvider(this).get(LiveDataButton.class);
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (!pairedDevices.isEmpty()) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                if(deviceName.equals("EOG_DEVICE")) bluetoothDevice = device;
            }
        }
        // ConnectThread btThread = new ConnectThread(EOG_DEVICE, mHandler, mMessageTextView);
        // btThread.run();

        // 블루투스 연결 액션 버튼에 연결
        Button btnExpand = findViewById(R.id.btn_expand_status_bar);
        btnExpand.setOnClickListener(v -> {
            // InputAccessibilityService를 통해 드래그 액션 요청
            Toast.makeText(getApplicationContext(), "Make Text", Toast.LENGTH_SHORT).show();
            connectDevice("EOG_DEVICE");
        });

        observerAddButton = findViewById(R.id.obserber_Button);
        observerAddButton.setOnClickListener(v -> {
            observer_EyeMovement();
            tracking = true;
        });


        // 방향 측정
        int IDLE_TIME = 2000; // . 상태로 기다리는 시간
        int WAIT_TIME = 2000; // 방향 보여 주고 유지하는 시간

        TextView directionText = findViewById(R.id.direction_text);
        Button direction_check_start_button = findViewById(R.id.direction_check_start_button);
        // 버튼 클릭 시 방향 측정 시작
        direction_check_start_button.setOnClickListener(v -> {
            Thread directionThread = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
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
                        // Download 폴더에 저장됨
                        // 파일명 direction_log.txt
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

    // 로그 파일에 문자열을 추가하는 메서드
    private void writeLog(String log) {
        try {
            // Downloads 폴더 경로 가져오기
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            // 로그 파일 준비 (파일명이 없으면 새로 생성됨)
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_WRITE_SETTINGS &&
                Settings.System.canWrite(this)) {
            recreate(); // 다시 onCreate 실행
        }

        if (requestCode == REQUEST_CODE_DRAW_OVERLAY &&
                Settings.canDrawOverlays(this)) {
            recreate(); // 다시 onCreate 실행
        }
    }

    private void observer_EyeMovement() {
        liveDataButton.getSensorValue().observe(this, new Observer<Float>() {
            @Override
            public void onChanged(Float value) {
                if (value != null && value > 200.0f) {
                    Toast.makeText(getApplicationContext(), "눈을 움직였군요(방향은 아직 몰라요)",Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void connectDevice(String deviceName) {
        Toast.makeText(getApplicationContext(),deviceName+"에 연결!",Toast.LENGTH_SHORT).show();
        UUID uuid = MY_UUID;

        try{
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, 10);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 10);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
            }

            if (bluetoothDevice == null) {
                Toast.makeText(getApplicationContext(), "Bluetooth device not found", Toast.LENGTH_SHORT).show();
                return;
            }

            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();

            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            receiveData();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void receiveData() {
        final Handler handler = new Handler();

        readBufferPosition = 0;
        readBuffer = new byte[1024];

        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(!Thread.currentThread().isInterrupted()) {
                    try {
                        int byteAvailable = inputStream.available();
                        if (byteAvailable > 0) {
                            byte[] bytes = new byte[byteAvailable];
                            inputStream.read(bytes);
                            for (int i = 0; i < byteAvailable; i++) {
                                byte tempByte = bytes[i];
                                if (tempByte == '\n') {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);

                                    final String text = new String(encodedBytes, "UTF-8");
                                    readBufferPosition = 0;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            mMessageTextView.setText(text);
                                            if(tracking) {
                                                String[] temp = text.split(",");
                                                String[] temp2 = temp[0].split(":");
                                                float x = Float.parseFloat(temp2[1]);
                                                temp2 = temp[1].split(":");
                                                float y = Float.parseFloat(temp2[1]);
                                                liveDataButton.setSensorValue(Math.abs(x-y));
                                                processSensorData(x, y);
                                            }
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = tempByte;
                                }
                            }
                        }
                        try{ Thread.sleep(1000/8);}
                        catch (InterruptedException ignored) {}
                    }catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        workerThread.start();
    }
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
}
