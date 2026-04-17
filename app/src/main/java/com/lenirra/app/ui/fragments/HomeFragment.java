package com.lenirra.app.ui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.lenirra.app.R;
import com.lenirra.app.services.SystemMonitorService;
import com.lenirra.app.ui.widgets.GaugeView;
import com.lenirra.app.utils.PrefsManager;
import com.lenirra.app.utils.RootUtils;

public class HomeFragment extends Fragment {

    private Handler uiHandler;
    private boolean updating = false;

    private GaugeView cpuGauge;
    private TextView cpuValueText, cpuFreqText, cpuFreqBottom;
    private TextView battTempText, battLevelText;
    private TextView thermalText, thermalIcon;
    private TextView selinuxText;
    private TextView ramText, gpuFreqText;
    private TextView uptimeText, tvProfileStatus;
    private SwitchMaterial switchApplyBoot;
    private ChipGroup profileChipGroup;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);
        initViews(v);
        setupListeners();
        startLiveUpdate();
        return v;
    }

    private void initViews(View v) {
        cpuGauge        = v.findViewById(R.id.cpu_gauge);
        cpuValueText    = v.findViewById(R.id.cpu_value_text);
        cpuFreqText     = v.findViewById(R.id.cpu_freq_text);
        cpuFreqBottom   = v.findViewById(R.id.cpu_freq_bottom);
        battTempText    = v.findViewById(R.id.batt_temp_value);
        battLevelText   = v.findViewById(R.id.batt_level_text);
        thermalText     = v.findViewById(R.id.thermal_value);
        thermalIcon     = v.findViewById(R.id.thermal_icon);
        selinuxText     = v.findViewById(R.id.selinux_value);
        ramText         = v.findViewById(R.id.ram_text);
        gpuFreqText     = v.findViewById(R.id.gpu_freq_text);
        uptimeText      = v.findViewById(R.id.uptime_text);
        tvProfileStatus = v.findViewById(R.id.tv_profile_status);
        switchApplyBoot = v.findViewById(R.id.switch_apply_boot);
        profileChipGroup = v.findViewById(R.id.profile_chip_group);

        // Restore saved profile chip
        switchApplyBoot.setChecked(PrefsManager.isApplyOnBoot());
        String profile = PrefsManager.getCurrentProfile();
        int chipId;
        switch (profile) {
            case "performance": chipId = R.id.chip_performance; break;
            case "gaming":      chipId = R.id.chip_gaming;      break;
            case "battery":     chipId = R.id.chip_battery;     break;
            case "powersave":   chipId = R.id.chip_powersave;   break;
            default:            chipId = R.id.chip_balanced;    break;
        }
        profileChipGroup.check(chipId);
    }

    private void setupListeners() {
        switchApplyBoot.setOnCheckedChangeListener((btn, checked) ->
                PrefsManager.setApplyOnBoot(checked));

        // Chip tap → langsung apply profile, tanpa tombol boost
        profileChipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            String profile = "balanced";
            if (id == R.id.chip_performance) profile = "performance";
            else if (id == R.id.chip_gaming)    profile = "gaming";
            else if (id == R.id.chip_battery)   profile = "battery";
            else if (id == R.id.chip_powersave) profile = "powersave";

            final String finalProfile = profile;
            PrefsManager.setCurrentProfile(finalProfile);

            // Feedback langsung di status text
            if (tvProfileStatus != null) tvProfileStatus.setText("Applying…");

            new Thread(() -> {
                RootUtils.applyProfile(finalProfile);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (tvProfileStatus != null)
                        tvProfileStatus.setText("Applied: " + finalProfile);
                    Toast.makeText(requireContext(),
                            "Profile: " + finalProfile, Toast.LENGTH_SHORT).show();
                    // Reset status setelah 2 detik
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (tvProfileStatus != null && isAdded())
                            tvProfileStatus.setText("tap to apply instantly");
                    }, 2000);
                });
            }).start();
        });
    }

    private void startLiveUpdate() {
        updating = true;
        uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(updateRunnable);
    }

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!updating || !isAdded()) return;
            updateUI();
            uiHandler.postDelayed(this, 1000);
        }
    };

    private void updateUI() {
        int cpu        = SystemMonitorService.cpuUsage;
        int freq       = SystemMonitorService.cpuFreq;
        int temp       = SystemMonitorService.batteryTemp;
        int bat        = SystemMonitorService.batteryLevel;
        int thermal    = SystemMonitorService.thermalStatus;
        long ramUsed   = SystemMonitorService.ramUsed;
        long ramTotal  = SystemMonitorService.ramTotal;
        int gpuFreq    = SystemMonitorService.gpuFreq;
        String selinux = SystemMonitorService.selinuxStatus;

        if (cpuGauge     != null) cpuGauge.setValue(cpu);
        if (cpuValueText != null) cpuValueText.setText(cpu + "%");
        if (cpuFreqText  != null) cpuFreqText.setText(freq + " MHz");
        if (cpuFreqBottom!= null) cpuFreqBottom.setText(freq > 0 ? freq + " MHz" : "—");

        if (battTempText  != null) battTempText.setText(temp + "°C");
        if (battLevelText != null) battLevelText.setText(bat + "%");

        if (thermalText != null) {
            String[] labels = {"Cool", "Warm", "Hot", "Critical"};
            thermalText.setText(thermal < labels.length ? labels[thermal] : "—");
            int[] colors = {0xFF2ED573, 0xFFFFB800, 0xFFFF6B35, 0xFFFF4757};
            if (thermal < colors.length) thermalText.setTextColor(colors[thermal]);
        }

        if (selinuxText != null) {
            selinuxText.setText(selinux.isEmpty() ? "—" : selinux);
            selinuxText.setTextColor(
                    selinux.equalsIgnoreCase("Enforcing") ? 0xFF6C63FF : 0xFFFF6B35);
        }

        if (ramText != null && ramTotal > 0) {
            ramText.setText(ramUsed + "/" + ramTotal + " MB");
        }

        if (gpuFreqText != null) gpuFreqText.setText(gpuFreq > 0 ? gpuFreq + " MHz" : "—");

        if (uptimeText != null) {
            new Thread(() -> {
                String up = RootUtils.getUptime();
                if (isAdded()) requireActivity().runOnUiThread(() -> uptimeText.setText(up));
            }).start();
        }
    }

    @Override
    public void onDestroyView() {
        updating = false;
        if (uiHandler != null) uiHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
