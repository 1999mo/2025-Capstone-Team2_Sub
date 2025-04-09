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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity  {

    //https://blog.naver.com/ori_kku/222383254872
    //여기서 많이 훔쳤습니다.
    private TextView mMessageTextView;
    private Handler mHandler;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_CODE_PERMISSIONS = 2;


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



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMessageTextView = findViewById(R.id.messageTextView);  // Initialize TextView

        // Create a Handler to post updates to the UI thread
        mHandler = new Handler(Looper.getMainLooper());




// 권한 확인 후 없을시 권한 요청

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
//        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//        if(!bluetoothAdapter.isEnabled()) {
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//        }
        bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();
//
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (!pairedDevices.isEmpty()) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                if(deviceName.equals("EOG_DEVICE")) bluetoothDevice = device;
            }
        }
//        ConnectThread btThread = new ConnectThread(EOG_DEVICE, mHandler, mMessageTextView);
//        btThread.run();


        Button btnExpand = findViewById(R.id.btn_expand_status_bar);
        btnExpand.setOnClickListener(v -> {
            // InputAccessibilityService를 통해 드래그 액션 요청
            Toast.makeText(getApplicationContext(), "Make Text", Toast.LENGTH_SHORT).show();
            connectDevice("EOG_DEVICE");

            InputAccessibilityService.requestStatusBarDrag();
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
