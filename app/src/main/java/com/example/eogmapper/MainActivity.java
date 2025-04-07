package com.example.eogmapper;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnExpand = findViewById(R.id.btn_expand_status_bar);
        btnExpand.setOnClickListener(v -> {
            // InputAccessibilityService를 통해 드래그 액션 요청
            InputAccessibilityService.requestStatusBarDrag();
        });
    }
}
