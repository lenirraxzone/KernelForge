package com.lenirra.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import com.lenirra.app.utils.PrefsManager;

public class KernelForgeApp extends Application {

    public static final String CHANNEL_MONITOR = "channel_monitor";

    @Override
    public void onCreate() {
        super.onCreate();
        PrefsManager.init(this);
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            // System Monitor channel
            NotificationChannel monitor = new NotificationChannel(
                    CHANNEL_MONITOR,
                    "System Monitor",
                    NotificationManager.IMPORTANCE_LOW
            );
            monitor.setDescription("Background system monitoring");
            monitor.setShowBadge(false);
            nm.createNotificationChannel(monitor);
        }
    }
}
