package com.lenirra.app.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import com.lenirra.app.KernelForgeApp;
import com.lenirra.app.R;
import com.lenirra.app.activities.MainActivity;
import com.lenirra.app.utils.RootUtils;
import java.util.ArrayList;
import java.util.List;

public class SystemMonitorService extends Service {

    public static final int UPDATE_INTERVAL_MS = 2000;

    // Live data
    public static volatile int cpuUsage = 0;
    public static volatile int cpuFreq = 0;
    public static volatile int gpuFreq = 0;
    public static volatile long ramTotal = 0;
    public static volatile long ramUsed = 0;
    public static volatile long ramFree = 0;
    public static volatile int batteryTemp = 0;
    public static volatile int batteryLevel = 0;
    public static volatile int thermalStatus = 0;
    public static volatile String cpuGovernor = "";
    public static volatile String ioScheduler = "";
    public static volatile String selinuxStatus = "";

    // History (last 60 seconds)
    public static final int HISTORY_SIZE = 60;
    public static List<Integer> cpuHistory = new ArrayList<>();
    public static List<Integer> ramHistory = new ArrayList<>();
    public static List<Integer> tempHistory = new ArrayList<>();

    private final IBinder binder = new LocalBinder();
    private Handler handler;
    private boolean running = false;

    public class LocalBinder extends Binder {
        public SystemMonitorService getService() { return SystemMonitorService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotification(0, 0, 0));
        startMonitoring();
        return START_STICKY;
    }

    private void startMonitoring() {
        running = true;
        handler = new Handler(Looper.getMainLooper());
        handler.post(updateTask);
    }

    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            new Thread(() -> {
                // CPU
                cpuUsage = RootUtils.getCpuUsagePercent();
                cpuFreq = RootUtils.getCpuFreqMhz(0);
                gpuFreq = RootUtils.getGpuFreqMhz();

                // RAM
                long[] ram = RootUtils.getRamInfo();
                ramTotal = ram[0]; ramUsed = ram[1]; ramFree = ram[2];

                // Battery
                batteryTemp = RootUtils.getBatteryTemp();
                batteryLevel = RootUtils.getBatteryLevel();

                // Thermal
                thermalStatus = RootUtils.getThermalStatus();

                // Kernel
                cpuGovernor = RootUtils.getCpuGovernor();
                ioScheduler = RootUtils.getIoScheduler();
                selinuxStatus = RootUtils.getSelinuxStatus();

                // Update history
                addToHistory(cpuHistory, cpuUsage);
                int ramPercent = ramTotal > 0 ? (int)(ramUsed * 100 / ramTotal) : 0;
                addToHistory(ramHistory, ramPercent);
                addToHistory(tempHistory, batteryTemp);

                // Update notification
                updateNotification();

                handler.postDelayed(updateTask, UPDATE_INTERVAL_MS);
            }).start();
        }
    };

    private void addToHistory(List<Integer> list, int val) {
        list.add(val);
        if (list.size() > HISTORY_SIZE) list.remove(0);
    }

    private Notification buildNotification(int cpu, int temp, int bat) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, KernelForgeApp.CHANNEL_MONITOR)
                .setSmallIcon(R.drawable.ic_monitor)
                .setContentTitle("KF Monitor")
                .setContentText("CPU: " + cpu + "% | Temp: " + temp + "°C | Bat: " + bat + "%")
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification() {
        Notification n = buildNotification(cpuUsage, batteryTemp, batteryLevel);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(1, n);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (handler != null) handler.removeCallbacks(updateTask);
        super.onDestroy();
    }
}
