package com.lenirra.app.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.lenirra.app.services.SystemMonitorService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            // Start monitor service
            Intent svc = new Intent(context, SystemMonitorService.class);
            context.startForegroundService(svc);

            // Apply tweaks on boot if enabled
            if (PrefsManager.isApplyOnBoot()) {
                new Thread(() -> {
                    RootUtils.requestRoot();
                    String profile = PrefsManager.getCurrentProfile();
                    RootUtils.applyProfile(profile);
                    if (PrefsManager.isTcpBbrEnabled()) RootUtils.enableTcpBbr();
                    if (PrefsManager.isDropCachesOnBoot()) RootUtils.dropCaches();
                }).start();
            }
        }
    }
}
