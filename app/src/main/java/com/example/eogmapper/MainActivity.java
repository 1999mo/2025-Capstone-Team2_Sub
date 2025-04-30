package com.example.eogmapper;

import static com.example.eogmapper.ForegroundService.MY_UUID;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;

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

        // 먼저 WRITE_SETTINGS 체크
        if (!Settings.System.canWrite(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_WRITE_SETTINGS);
            return;
        }

        // OVERLAY 권한 체크
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_DRAW_OVERLAY);
            return;
        }

        // 모든 권한 확인 후 오버레이 서비스 실행
        startService(new Intent(this, OverlayButtonService.class));
        finish(); // 액티비티 종료

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

        Button btnExpand = findViewById(R.id.btn_expand_status_bar);
        btnExpand.setOnClickListener(v -> {
            // InputAccessibilityService를 통해 드래그 액션 요청
            Toast.makeText(getApplicationContext(), "Make Text", Toast.LENGTH_SHORT).show();
            connectDevice("EOG_DEVICE");

        // InputAccessibilityService.requestStatusBarDrag();
        });

        observerAddButton = findViewById(R.id.obserber_Button);
        observerAddButton.setOnClickListener(v -> {
            observer_EyeMovement();
            tracking = true;
        });
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
                    InputAccessibilityService.requestStatusBarDrag();
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
                                            //센서값 여기에
                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mMessageTextView.setText(text);
                                                    if(tracking ==true) {
                                                        String[] temp = text.split(",");
                                                        String[] temp2 = temp[0].split(":");
                                                        float x = Float.parseFloat(temp2[1]);
                                                        temp2 = temp[1].split(":");
                                                        float y = Float.parseFloat(temp2[1]);
                                                        liveDataButton.setSensorValue(Math.abs(x-y));
                                                    }
//
                                                    //센서값을 읽고 만약에 절대값이 일정 값보다 클경우.
                                                }
                                            });
                                        }
                                    });
                                }
                                else {
                                    readBuffer[readBufferPosition++] = tempByte;
                                }

                            }
                        }

                    }catch (IOException e) {
                        e.printStackTrace();
                    }
                }try{
                    Thread.sleep(1000/8);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        });
        workerThread.start();
    }

}
