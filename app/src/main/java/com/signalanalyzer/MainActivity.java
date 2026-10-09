package com.signalanalyzer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView statusLabel;
    private View statusDot;
    private Button startButton;
    private boolean isActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusLabel = findViewById(R.id.statusLabel);
        statusDot = findViewById(R.id.statusDot);
        startButton = findViewById(R.id.startButton);

        // Initial state
        setStatus("READY", R.color.neon_green);

        // Start button click
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isActive) {
                    // In next phase, this will request permissions and start service
                    isActive = true;
                    startButton.setText(R.string.stop_signal);
                    startButton.setBackgroundColor(
                        getResources().getColor(R.color.neon_red, null));
                    setStatus("FLOATING ASSISTANT ACTIVE", R.color.neon_cyan);
                } else {
                    isActive = false;
                    startButton.setText(R.string.start_signal);
                    startButton.setBackgroundColor(
                        getResources().getColor(R.color.neon_cyan, null));
                    setStatus("READY", R.color.neon_green);
                }
            }
        });
    }

    private void setStatus(String text, int colorRes) {
        statusLabel.setText(text);
        int color = getResources().getColor(colorRes, null);
        statusDot.setBackgroundColor(color);
        statusLabel.setTextColor(color);
    }
}
