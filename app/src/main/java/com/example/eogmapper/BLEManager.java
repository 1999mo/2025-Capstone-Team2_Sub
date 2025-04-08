package com.example.eogmapper;

import android.content.Context;

import androidx.annotation.NonNull;

import no.nordicsemi.android.ble.BleManager;

public class BLEManager extends BleManager {
    private static final String TAG = "MyBleManager";

    public BLEManager(@NonNull final Context context) {
        super(context);
    }
}
