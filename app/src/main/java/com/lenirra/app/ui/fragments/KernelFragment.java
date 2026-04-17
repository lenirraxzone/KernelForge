package com.lenirra.app.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.google.android.material.slider.Slider;
import com.lenirra.app.R;
import com.lenirra.app.utils.PrefsManager;
import com.lenirra.app.utils.RootUtils;

public class KernelFragment extends Fragment {

    private Spinner  spinnerGovernor;
    private Spinner  spinnerIoSched;
    private Spinner  spinnerTcpCong;
    private Spinner  spinnerCpuMaxFreq;   // CPU max freq dari device
    private Spinner  spinnerCpuMinFreq;   // CPU min freq dari device
    private Slider   sliderSwappiness;
    private TextView swappinessValue;
    private TextView tvCpuMaxCurrent, tvCpuMinCurrent;
    private Switch   switchTcpBbr, switchZram, switchDoze, switchSync;
    private Button   btnApplyKernel;
    private TextView tvKernelVer, tvCpuCores;
    private TextView tvCpuGovernorCurrent, tvIoSchedulerCurrent;

    // Frekuensi yang tersedia dari device (dalam MHz, sorted descending)
    private String[] cpuFreqListMhz = {};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_kernel, container, false);
        initViews(v);
        setupListeners();
        loadCurrentValues();
        return v;
    }

    private void initViews(View v) {
        spinnerGovernor      = v.findViewById(R.id.spinner_governor);
        spinnerIoSched       = v.findViewById(R.id.spinner_io_sched);
        spinnerTcpCong       = v.findViewById(R.id.spinner_tcp_cong);
        spinnerCpuMaxFreq    = v.findViewById(R.id.spinner_cpu_max_freq);
        spinnerCpuMinFreq    = v.findViewById(R.id.spinner_cpu_min_freq);
        sliderSwappiness     = v.findViewById(R.id.slider_swappiness);
        swappinessValue      = v.findViewById(R.id.swappiness_value);
        tvCpuMaxCurrent      = v.findViewById(R.id.tv_cpu_max_current);
        tvCpuMinCurrent      = v.findViewById(R.id.tv_cpu_min_current);
        switchTcpBbr         = v.findViewById(R.id.switch_tcp_bbr);
        switchZram           = v.findViewById(R.id.switch_zram);
        switchDoze           = v.findViewById(R.id.switch_doze);
        switchSync           = v.findViewById(R.id.switch_sync);
        btnApplyKernel       = v.findViewById(R.id.btn_apply_kernel);
        tvKernelVer          = v.findViewById(R.id.tv_kernel_version);
        tvCpuCores           = v.findViewById(R.id.tv_cpu_cores);
        tvCpuGovernorCurrent = v.findViewById(R.id.tv_governor_current);
        tvIoSchedulerCurrent = v.findViewById(R.id.tv_io_current);
    }

    private ArrayAdapter<String> makeAdapter(String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                R.layout.spinner_item_white, items);
        a.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        return a;
    }

    private void loadCurrentValues() {
        new Thread(() -> {
            String kernelVer  = RootUtils.getKernelVersion();
            int    cores      = RootUtils.getNumCpuCores();
            String gov        = RootUtils.getCpuGovernor();
            String[] availGov = RootUtils.getAvailableGovernors();
            String ioSched    = RootUtils.getIoScheduler();
            int swappiness    = RootUtils.getSwappiness();
            String tcpCong    = RootUtils.getTcpCongestion();

            // Baca available frequencies CPU dari sysfs
            String availFreqRaw = RootUtils.runCommand(
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies 2>/dev/null");
            String curMaxRaw = RootUtils.runCommand(
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq 2>/dev/null");
            String curMinRaw = RootUtils.runCommand(
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq 2>/dev/null");

            String[] freqList = parseAvailFreqs(availFreqRaw);
            int curMaxMhz = parseKhzToMhz(curMaxRaw);
            int curMinMhz = parseKhzToMhz(curMinRaw);

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                tvKernelVer.setText(kernelVer.isEmpty() ? "N/A" : kernelVer);
                tvCpuCores.setText(cores + " Cores");
                tvCpuGovernorCurrent.setText("Current: " + gov);
                tvIoSchedulerCurrent.setText("Current: " + ioSched);

                // CPU Governor
                spinnerGovernor.setAdapter(makeAdapter(availGov));
                for (int i = 0; i < availGov.length; i++) {
                    if (availGov[i].equals(gov)) { spinnerGovernor.setSelection(i); break; }
                }

                // I/O Scheduler
                String[] schedulers = {"cfq","deadline","noop","bfq","kyber","mq-deadline"};
                spinnerIoSched.setAdapter(makeAdapter(schedulers));
                for (int i = 0; i < schedulers.length; i++) {
                    if (schedulers[i].equals(ioSched)) { spinnerIoSched.setSelection(i); break; }
                }

                // Swappiness slider
                sliderSwappiness.setValue(Math.min(swappiness, 100));
                swappinessValue.setText(String.valueOf(swappiness));

                // TCP Congestion
                String[] tcpAlgos = {"cubic","bbr","reno","westwood","vegas","htcp"};
                spinnerTcpCong.setAdapter(makeAdapter(tcpAlgos));
                for (int i = 0; i < tcpAlgos.length; i++) {
                    if (tcpAlgos[i].equals(tcpCong)) { spinnerTcpCong.setSelection(i); break; }
                }

                // CPU Frequency spinners — dari available_frequencies device
                cpuFreqListMhz = freqList;
                if (freqList.length > 0) {
                    String[] labels = new String[freqList.length];
                    for (int i = 0; i < freqList.length; i++)
                        labels[i] = freqList[i] + " MHz";

                    spinnerCpuMaxFreq.setAdapter(makeAdapter(labels));
                    spinnerCpuMinFreq.setAdapter(makeAdapter(labels));

                    // Set ke nilai current dari device
                    selectFreqInSpinner(spinnerCpuMaxFreq, freqList, curMaxMhz);
                    selectFreqInSpinner(spinnerCpuMinFreq, freqList, curMinMhz);

                    if (tvCpuMaxCurrent != null)
                        tvCpuMaxCurrent.setText(curMaxMhz > 0 ? curMaxMhz + " MHz" : "—");
                    if (tvCpuMinCurrent != null)
                        tvCpuMinCurrent.setText(curMinMhz > 0 ? curMinMhz + " MHz" : "—");
                } else {
                    // Fallback jika device tidak expose available_frequencies
                    String[] fallback = {"3200 MHz","3000 MHz","2800 MHz","2600 MHz","2400 MHz",
                                         "2200 MHz","2000 MHz","1800 MHz","1600 MHz","1200 MHz","800 MHz"};
                    spinnerCpuMaxFreq.setAdapter(makeAdapter(fallback));
                    spinnerCpuMinFreq.setAdapter(makeAdapter(fallback));
                    spinnerCpuMinFreq.setSelection(fallback.length - 1);
                }

                switchTcpBbr.setChecked(PrefsManager.isTcpBbrEnabled());
                switchZram.setChecked(PrefsManager.isZramEnabled());
                switchDoze.setChecked(PrefsManager.isAggressiveDoze());
                switchSync.setChecked(PrefsManager.isSyncDisabled());
            });
        }).start();
    }

    /** Parse "kHz kHz kHz..." → MHz string[], sorted descending */
    private String[] parseAvailFreqs(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new String[0];
        String[] tokens = raw.trim().split("\\s+");
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        for (String t : tokens) {
            try {
                long val = Long.parseLong(t.trim());
                // CPU freq biasanya dalam kHz
                int mhz = val > 100_000 ? (int)(val / 1_000) : (int)val;
                if (mhz > 0) set.add(mhz);
            } catch (NumberFormatException ignored) {}
        }
        String[] result = new String[set.size()];
        int i = 0;
        for (int mhz : set) result[i++] = String.valueOf(mhz);
        return result;
    }

    /** Parse kHz string → MHz int */
    private int parseKhzToMhz(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        try {
            long kHz = Long.parseLong(raw.trim().split("\\s+")[0]);
            return (int)(kHz / 1_000);
        } catch (Exception e) { return 0; }
    }

    /** Pilih item di spinner yang paling mendekati targetMhz */
    private void selectFreqInSpinner(Spinner spinner, String[] freqList, int targetMhz) {
        if (targetMhz <= 0 || freqList.length == 0) return;
        int best = 0;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < freqList.length; i++) {
            try {
                int diff = Math.abs(Integer.parseInt(freqList[i]) - targetMhz);
                if (diff < bestDiff) { bestDiff = diff; best = i; }
            } catch (NumberFormatException ignored) {}
        }
        spinner.setSelection(best);
    }

    private void setupListeners() {
        sliderSwappiness.addOnChangeListener((s, v, f) ->
                swappinessValue.setText(String.valueOf((int)v)));
        switchTcpBbr.setOnCheckedChangeListener((b, c) -> PrefsManager.setTcpBbrEnabled(c));
        switchZram.setOnCheckedChangeListener((b, c)   -> PrefsManager.setZramEnabled(c));
        switchDoze.setOnCheckedChangeListener((b, c)   -> PrefsManager.setAggressiveDoze(c));
        switchSync.setOnCheckedChangeListener((b, c)   -> PrefsManager.setSyncDisabled(c));
        btnApplyKernel.setOnClickListener(vv -> applyKernelTweaks());
    }

    private void applyKernelTweaks() {
        btnApplyKernel.setEnabled(false);
        btnApplyKernel.setText("Applying…");

        new Thread(() -> {
            // Governor
            if (spinnerGovernor.getSelectedItem() != null) {
                String gov = spinnerGovernor.getSelectedItem().toString();
                RootUtils.setCpuGovernor(gov);
                PrefsManager.setSavedGovernor(gov);
            }

            // CPU Max Freq
            int maxMhz = getSelectedMhz(spinnerCpuMaxFreq);
            if (maxMhz > 0) {
                long maxKhz = (long) maxMhz * 1_000L;
                int cores = RootUtils.getNumCpuCores();
                for (int c = 0; c < cores; c++)
                    RootUtils.runCommand("echo " + maxKhz + " > /sys/devices/system/cpu/cpu" + c
                            + "/cpufreq/scaling_max_freq 2>/dev/null");
            }

            // CPU Min Freq
            int minMhz = getSelectedMhz(spinnerCpuMinFreq);
            if (minMhz > 0) {
                long minKhz = (long) minMhz * 1_000L;
                int cores = RootUtils.getNumCpuCores();
                for (int c = 0; c < cores; c++)
                    RootUtils.runCommand("echo " + minKhz + " > /sys/devices/system/cpu/cpu" + c
                            + "/cpufreq/scaling_min_freq 2>/dev/null");
            }

            // I/O Scheduler
            if (spinnerIoSched.getSelectedItem() != null) {
                String sched = spinnerIoSched.getSelectedItem().toString();
                RootUtils.setIoScheduler(sched);
                PrefsManager.setSavedIoScheduler(sched);
            }

            // Swappiness
            int swappiness = (int) sliderSwappiness.getValue();
            RootUtils.setSwappiness(swappiness);
            PrefsManager.setSavedSwappiness(swappiness);

            // TCP Congestion
            if (spinnerTcpCong.getSelectedItem() != null) {
                String tcp = spinnerTcpCong.getSelectedItem().toString();
                RootUtils.setTcpCongestion(tcp);
            }

            // ZRAM
            if (switchZram.isChecked()) {
                RootUtils.runCommand("swapoff /dev/block/zram0 2>/dev/null; " +
                        "echo 536870912 > /sys/block/zram0/disksize; " +
                        "mkswap /dev/block/zram0; swapon /dev/block/zram0");
            }

            if (switchDoze.isChecked()) RootUtils.runCommand("dumpsys deviceidle force-idle");

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                btnApplyKernel.setEnabled(true);
                btnApplyKernel.setText("Apply Tweaks");
                Toast.makeText(requireContext(), "✓ Kernel tweaks applied!", Toast.LENGTH_SHORT).show();
                loadCurrentValues();
            });
        }).start();
    }

    /** Ambil MHz dari spinner item ("xxx MHz") */
    private int getSelectedMhz(Spinner spinner) {
        if (spinner.getSelectedItem() == null) return 0;
        try {
            return Integer.parseInt(spinner.getSelectedItem().toString().replace(" MHz", "").trim());
        } catch (Exception e) { return 0; }
    }
}
