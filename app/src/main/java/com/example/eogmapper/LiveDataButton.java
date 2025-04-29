package com.example.eogmapper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class LiveDataButton extends ViewModel {
    private final MutableLiveData<Float> sensorValue = new MutableLiveData<>();

    public LiveData<Float> getSensorValue() {
        return sensorValue;
    }
    public void setSensorValue(float value) {
        sensorValue.setValue(value);
    }
}
