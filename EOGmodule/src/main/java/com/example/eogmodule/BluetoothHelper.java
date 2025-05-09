package com.example.eogmodule;

import static androidx.core.app.ActivityCompat.requestPermissions;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BluetoothHelper {
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;

    private Context context;

    private ConnectionListener listener;


    public interface ConnectionListener {
        void onConnected();
        void onConnectionFailed(String reason);
        void onDataReceived(String data);
    }

    public BluetoothHelper(Context context) {
        this.context = context;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    public void connect(String deviceName, UUID uuid) {

        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions((Activity) context,new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_CODE_PERMISSIONS);
        }
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CODE_PERMISSIONS);
        }
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions((Activity) context, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_PERMISSIONS);
        }
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions((Activity) context, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_PERMISSIONS);
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().equals(deviceName)) {
                bluetoothDevice = device;
                break;
            }
        }

        if (bluetoothDevice == null) {
            if (listener != null) listener.onConnectionFailed("Device not found");
            return;
        }

        new Thread(() -> {
            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
                bluetoothSocket.connect();

                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();

                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onConnected());
                }

                readData();
            } catch (IOException e) {
                e.printStackTrace();
                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onConnectionFailed("Connection failed: " + e.getMessage()));
                }
            }
        }).start();
        Toast.makeText(context.getApplicationContext(), "Start Connection", Toast.LENGTH_SHORT).show();
    }

    private void readData() {
        byte[] buffer = new byte[1024];
        int bufferPosition = 0;

        while (true) {
            try {
                int bytesAvailable = inputStream.available();
                if (bytesAvailable > 0) {
                    byte[] bytes = new byte[bytesAvailable];
                    inputStream.read(bytes);

                    for (int i = 0; i < bytesAvailable; i++) {
                        byte b = bytes[i];
                        if (b == '\n') {
                            byte[] encoded = new byte[bufferPosition];
                            System.arraycopy(buffer, 0, encoded, 0, encoded.length);
                            String data = new String(encoded, "UTF-8");

                            bufferPosition = 0;

                            if (listener != null) {
                                new Handler(Looper.getMainLooper()).post(() -> listener.onDataReceived(data));
                            }
                        } else {
                            buffer[bufferPosition++] = b;
                        }
                    }
                }

                Thread.sleep(125);
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }

    public void disconnect() {
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}