package com.example.eogmapper;

import static com.example.eogmapper.ForegroundService.MY_UUID;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConnectThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final BluetoothDevice mmDevice;
    private InputStream mmInStream;
    private OutputStream mmOutStream;
    private byte[] mmBuffer;  // Buffer for incoming data
    private final Handler mHandler;  // Handler to update UI
    private final TextView mTextView;

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public ConnectThread(BluetoothDevice device, Handler handler, TextView textView) {
        InputStream tmpIn = null;
        OutputStream tmpOut = null;
        BluetoothSocket tmp = null;
        mmDevice = device;
        mHandler = handler;
        mTextView = textView;

        try {
            // Create an RFCOMM socket to the device
            tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            Log.e("ConnectThread", "Socket creation failed", e);
        }
        mmSocket = tmp;
        mmInStream = null;
        mmOutStream = null;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void run() {
        mmBuffer = new byte[1024];  // Buffer for incoming data
        int numBytes;

       try{
            mmSocket.connect();
            mmInStream = mmSocket.getInputStream();
            mmOutStream = mmSocket.getOutputStream();

            // Read data continuously
            while (true) {
                // Read bytes from the InputStream
                numBytes = mmInStream.read(mmBuffer);
                // Here you can handle the data in mmBuffer. For example, process it or convert it to a string.
                String readMessage = new String(mmBuffer, 0, numBytes);
                Log.d("ConnectThread", "Received data: " + readMessage);

                final String message = readMessage;
//                mHandler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        // Set the received message to the TextView
                        mTextView.setText(message);
//                    }
//                });
            }
        } catch (IOException e) {
            Log.e("ConnectThread", "Error while reading from InputStream", e);
            try {
                mmSocket.close();
            } catch (IOException closeException) {
                Log.e("ConnectThread", "Could not close socket", closeException);
            }
        }
    }

    // Closes the client socket and causes the thread to finish.
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
            Log.e("ConnectThread", "Could not close the client socket", e);
        }
    }
}
