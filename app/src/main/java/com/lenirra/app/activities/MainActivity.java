package com.lenirra.app.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.lenirra.app.R;
import com.lenirra.app.services.SystemMonitorService;
import com.lenirra.app.ui.fragments.GpuFragment;
import com.lenirra.app.ui.fragments.HomeFragment;
import com.lenirra.app.ui.fragments.KernelFragment;
import com.lenirra.app.ui.fragments.ProfileFragment;
import com.lenirra.app.ui.fragments.ToolsFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private boolean rootGranted = false;
    private SystemMonitorService monitorService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            SystemMonitorService.LocalBinder lb = (SystemMonitorService.LocalBinder) binder;
            monitorService = lb.getService();
            serviceBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootGranted = getIntent().getBooleanExtra("root_granted", false);

        // Start system monitor service
        Intent svcIntent = new Intent(this, SystemMonitorService.class);
        startForegroundService(svcIntent);
        bindService(svcIntent, serviceConnection, BIND_AUTO_CREATE);

        bottomNav = findViewById(R.id.bottom_nav);
        setupBottomNav();

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }
    }

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (id == R.id.nav_kernel) {
                fragment = new KernelFragment();
            } else if (id == R.id.nav_gpu) {
                fragment = new GpuFragment();
            } else if (id == R.id.nav_tools) {
                fragment = new ToolsFragment();
            } else if (id == R.id.nav_profile) {
                fragment = new ProfileFragment();
            } else {
                return false;
            }

            loadFragment(fragment);
            return true;
        });
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.setCustomAnimations(
                R.anim.fragment_fade_enter,
                R.anim.fragment_fade_exit,
                R.anim.fragment_fade_enter,
                R.anim.fragment_fade_exit
        );
        tx.replace(R.id.fragment_container, fragment);
        tx.commit();
    }

    public boolean isRootGranted() { return rootGranted; }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        super.onDestroy();
    }
}
