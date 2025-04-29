package com.example.eogmapper;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class ForegroundService extends Service {

    private static final String CHANNEL_ID = "channelId";
    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // 일반 SPP UUID

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("앱 실행 중")
                .setContentText("Bluetooth 입력 대기 중...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);

        startBluetoothListening();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Foreground Service Channel", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void startBluetoothListening() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice("00:11:22:33:AA:BB"); // 예시 MAC 주소

        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                socket.connect();
                InputStream inputStream = socket.getInputStream();

                byte[] buffer = new byte[1024];
                int bytes;
                while ((bytes = inputStream.read(buffer)) != -1) {
                    String input = new String(buffer, 0, bytes);
                    if (input.trim().equals("CLICK")) {
                        // 예시: AccessibilityService에 접근해 입력 발생
                        // 서비스 인스턴스 접근은 별도로 구현 필요
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
