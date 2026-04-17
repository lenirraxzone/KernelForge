package com.lenirra.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.lenirra.app.R;
import com.lenirra.app.utils.RootUtils;

public class SplashActivity extends AppCompatActivity {

    private TextView statusText;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        statusText = findViewById(R.id.status_text);
        progressBar = findViewById(R.id.progress_bar);
        ImageView logo = findViewById(R.id.logo);

        // Animate logo
        logo.setAlpha(0f);
        logo.setScaleX(0.5f);
        logo.setScaleY(0.5f);
        logo.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(800)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // Check root in background
        new Thread(() -> {
            runOnUiThread(() -> updateStatus("Checking root access…", 20));
            boolean rootAvail = RootUtils.isRootAvailable();

            if (!rootAvail) {
                runOnUiThread(() -> {
                    updateStatus("Root not found!", 100);
                    new Handler(Looper.getMainLooper()).postDelayed(() ->
                        proceed(false), 1500);
                });
                return;
            }

            runOnUiThread(() -> updateStatus("Requesting root permission…", 50));
            boolean granted = RootUtils.requestRoot();

            runOnUiThread(() -> {
                if (granted) {
                    updateStatus("Root granted ✓", 70);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        updateStatus("Loading KernelForge…", 90);
                        new Handler(Looper.getMainLooper()).postDelayed(() ->
                            proceed(true), 600);
                    }, 500);
                } else {
                    updateStatus("Root denied — limited mode", 100);
                    new Handler(Looper.getMainLooper()).postDelayed(() ->
                        proceed(false), 1500);
                }
            });
        }).start();
    }

    private void updateStatus(String msg, int progress) {
        statusText.setText(msg);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            progressBar.setProgress(progress, true);
        } else {
            progressBar.setProgress(progress);
        }
    }

    private void proceed(boolean rootOk) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("root_granted", rootOk);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
