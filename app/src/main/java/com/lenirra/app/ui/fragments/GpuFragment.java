package com.lenirra.app.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.lenirra.app.R;
import com.lenirra.app.utils.PrefsManager;
import com.lenirra.app.utils.RootUtils;

public class GpuFragment extends Fragment {

    private TextView     tvGpuFreqCurrent, tvGpuGovernorCurrent;
    private TextView     tvGpuMaxFreq, tvGpuMinFreq;
    private TextView     tvGpuStatus;
    private ChipGroup    chipGroupGpuGov;
    private Spinner      spinnerGpuGov;

    // Spinner frekuensi — dari available_frequencies device
    private Spinner      spinnerGpuMaxFreq;
    private Spinner      spinnerGpuMinFreq;

    private Switch       switchAdrenoBoost, switchGpuThrottle, switchGpuOc;
    private MaterialButton btnApplyGpu;

    private String   selectedGov  = "";
    private String[] freqListMhz  = {};   // list MHz sebagai string, diisi dari device

    private static final String[] GPU_GOV_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
        "/sys/class/devfreq/gpufreq/governor",
        "/sys/kernel/gpu/gpu_governor"
    };
    private static final String[] GPU_MAX_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
        "/sys/class/devfreq/gpufreq/max_freq",
        "/sys/kernel/gpu/gpu_max_clock"
    };
    private static final String[] GPU_MIN_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
        "/sys/class/devfreq/gpufreq/min_freq",
        "/sys/kernel/gpu/gpu_min_clock"
    };
    private static final String[] GPU_AVAIL_FREQ_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
        "/sys/class/devfreq/gpufreq/available_frequencies",
        "/sys/kernel/gpu/gpu_available_frequencies"
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_gpu, container, false);
        initViews(v);
        setupListeners();
        loadGpuInfo();
        return v;
    }

    private void initViews(View v) {
        tvGpuFreqCurrent     = v.findViewById(R.id.tv_gpu_freq_current);
        tvGpuGovernorCurrent = v.findViewById(R.id.tv_gpu_gov_current);
        tvGpuMaxFreq         = v.findViewById(R.id.tv_gpu_max_freq);
        tvGpuMinFreq         = v.findViewById(R.id.tv_gpu_min_freq);
        chipGroupGpuGov      = v.findViewById(R.id.chipgroup_gpu_governor);
        spinnerGpuGov        = v.findViewById(R.id.spinner_gpu_governor);
        spinnerGpuMaxFreq    = v.findViewById(R.id.spinner_gpu_max_freq);
        spinnerGpuMinFreq    = v.findViewById(R.id.spinner_gpu_min_freq);
        switchAdrenoBoost    = v.findViewById(R.id.switch_adreno_boost);
        switchGpuThrottle    = v.findViewById(R.id.switch_gpu_throttle);
        switchGpuOc          = v.findViewById(R.id.switch_gpu_oc);
        btnApplyGpu          = v.findViewById(R.id.btn_apply_gpu);
        tvGpuStatus          = v.findViewById(R.id.tv_gpu_status);

        switchAdrenoBoost.setChecked(PrefsManager.isAdrenoBoostEnabled());
        switchGpuThrottle.setChecked(PrefsManager.isGpuThrottleEnabled());
        switchGpuOc.setChecked(PrefsManager.isGpuOcEnabled());
    }

    private void loadGpuInfo() {
        tvGpuStatus.setText("Loading…");
        new Thread(() -> {
            int    curFreqMhz = RootUtils.getGpuFreqMhz();
            String curGov     = readFirstValid(GPU_GOV_PATHS);
            String maxRaw     = readFirstValid(GPU_MAX_PATHS);
            String minRaw     = readFirstValid(GPU_MIN_PATHS);

            // Baca available frequencies dari device
            String availRaw = readFirstValid(GPU_AVAIL_FREQ_PATHS);

            // Parse available frequencies → konversi ke MHz, sort descending
            String[] parsedMhz = parseAvailFreqs(availRaw, maxRaw, minRaw);

            int maxMhz = parsedMhz.length > 0 ? Integer.parseInt(parsedMhz[0]) : 800;
            int minMhz = parsedMhz.length > 0 ? Integer.parseInt(parsedMhz[parsedMhz.length - 1]) : 100;

            // Governor list
            String govListRaw = RootUtils.runCommand(
                "cat /sys/class/kgsl/kgsl-3d0/devfreq/available_governors 2>/dev/null || " +
                "cat /sys/class/devfreq/gpufreq/available_governors 2>/dev/null || " +
                "echo 'simple_ondemand msm-adreno-tz powersave performance'");
            String[] govList = govListRaw.trim().isEmpty()
                ? new String[]{"simple_ondemand", "msm-adreno-tz", "powersave", "performance", "userspace"}
                : govListRaw.trim().split("\\s+");

            final String[] fFreqList = parsedMhz;
            final String[] fGovList  = govList;
            final String   fCurGov   = curGov.trim();
            final int fCurFreq = curFreqMhz;
            final int fMax = maxMhz, fMin = minMhz;

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;

                freqListMhz = fFreqList;

                tvGpuFreqCurrent.setText(fCurFreq > 0 ? fCurFreq + "" : "—");
                tvGpuGovernorCurrent.setText(fCurGov.isEmpty() ? "—" : fCurGov);
                tvGpuMaxFreq.setText(fMax + "");
                tvGpuMinFreq.setText(fMin + "");
                selectedGov = fCurGov;

                // Governor UI
                if (fGovList.length <= 8) {
                    chipGroupGpuGov.setVisibility(View.VISIBLE);
                    spinnerGpuGov.setVisibility(View.GONE);
                    buildGovChips(fGovList, fCurGov);
                } else {
                    chipGroupGpuGov.setVisibility(View.GONE);
                    spinnerGpuGov.setVisibility(View.VISIBLE);
                    buildGovSpinner(fGovList, fCurGov);
                }

                // Populate frequency spinners dari list device
                if (fFreqList.length > 0) {
                    // Label: "xxx MHz"
                    String[] labels = new String[fFreqList.length];
                    for (int i = 0; i < fFreqList.length; i++)
                        labels[i] = fFreqList[i] + " MHz";

                    ArrayAdapter<String> adapterMax = makeFreqAdapter(labels);
                    spinnerGpuMaxFreq.setAdapter(adapterMax);
                    // Set default ke max (index 0 = highest)
                    spinnerGpuMaxFreq.setSelection(0);

                    ArrayAdapter<String> adapterMin = makeFreqAdapter(labels);
                    spinnerGpuMinFreq.setAdapter(adapterMin);
                    // Set default ke min (index terakhir = lowest)
                    spinnerGpuMinFreq.setSelection(Math.max(0, labels.length - 1));
                } else {
                    // Fallback jika device tidak expose available_frequencies
                    String[] fallback = {"800 MHz", "700 MHz", "600 MHz", "500 MHz",
                                         "400 MHz", "300 MHz", "200 MHz", "100 MHz"};
                    spinnerGpuMaxFreq.setAdapter(makeFreqAdapter(fallback));
                    spinnerGpuMinFreq.setAdapter(makeFreqAdapter(fallback));
                    spinnerGpuMinFreq.setSelection(fallback.length - 1);
                }

                tvGpuStatus.setText("Loaded ✓");
            });
        }).start();
    }

    // Buat ArrayAdapter dengan style putih / konsisten
    private ArrayAdapter<String> makeFreqAdapter(String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                R.layout.spinner_item_white, items);
        a.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        return a;
    }

    /**
     * Parse raw available_frequencies string dari sysfs.
     * Bisa berupa Hz (ratusan juta) atau MHz (ratusan).
     * Return: sorted descending array of MHz strings.
     */
    private String[] parseAvailFreqs(String raw, String maxRaw, String minRaw) {
        if (raw == null || raw.trim().isEmpty()) {
            // Tidak ada data dari sysfs, buat dari max & min saja
            int max = parseToMhz(maxRaw);
            int min = parseToMhz(minRaw);
            if (max <= 0) return new String[0];
            if (min <= 0) min = 100;
            java.util.List<String> gen = new java.util.ArrayList<>();
            for (int f = max; f >= min; f -= 50)
                gen.add(String.valueOf(f));
            return gen.toArray(new String[0]);
        }

        String[] tokens = raw.trim().split("\\s+");
        java.util.TreeSet<Integer> freqs = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        for (String t : tokens) {
            try {
                long hz = Long.parseLong(t.trim());
                int mhz = hz > 1_000_000 ? (int)(hz / 1_000_000) : hz > 1_000 ? (int)(hz / 1_000) : (int)hz;
                if (mhz > 0) freqs.add(mhz);
            } catch (NumberFormatException ignored) {}
        }

        String[] result = new String[freqs.size()];
        int i = 0;
        for (int mhz : freqs) result[i++] = String.valueOf(mhz);
        return result;
    }

    private int parseToMhz(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        try {
            long hz = Long.parseLong(raw.trim().split("\\s+")[0]);
            return hz > 1_000_000 ? (int)(hz / 1_000_000) : hz > 1_000 ? (int)(hz / 1_000) : (int)hz;
        } catch (Exception e) { return 0; }
    }

    private void buildGovChips(String[] govList, String current) {
        chipGroupGpuGov.removeAllViews();
        for (String gov : govList) {
            Chip chip = new Chip(requireContext());
            chip.setText(gov);
            chip.setCheckable(true);
            chip.setChecked(gov.equals(current));
            chip.setTag(gov);
            chip.setChipBackgroundColorResource(R.color.chip_bg_color);
            chip.setChipStrokeColorResource(R.color.chip_stroke_color);
            chip.setChipStrokeWidth(1.5f);
            chip.setTextColor(getResources().getColorStateList(R.color.chip_text_color));
            chip.setTextSize(12f);
            chip.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) selectedGov = gov;
            });
            chipGroupGpuGov.addView(chip);
        }
    }

    private void buildGovSpinner(String[] govList, String current) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                R.layout.spinner_item_white, govList);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white);
        spinnerGpuGov.setAdapter(adapter);
        for (int i = 0; i < govList.length; i++) {
            if (govList[i].equals(current)) { spinnerGpuGov.setSelection(i); break; }
        }
        spinnerGpuGov.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedGov = govList[pos];
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void setupListeners() {
        btnApplyGpu.setOnClickListener(vv -> applyGpuTweaks());
    }

    private void applyGpuTweaks() {
        btnApplyGpu.setEnabled(false);
        btnApplyGpu.setText("Applying…");
        tvGpuStatus.setText("Applying tweaks…");

        new Thread(() -> {
            // Governor
            if (!selectedGov.isEmpty()) {
                for (String p : GPU_GOV_PATHS)
                    RootUtils.runCommand("echo " + selectedGov + " > " + p + " 2>/dev/null");
            }

            // Max freq dari spinner — ambil nilai MHz, konversi ke Hz
            int maxMhz = getSelectedFreqMhz(spinnerGpuMaxFreq);
            if (maxMhz > 0) {
                long maxHz = (long) maxMhz * 1_000_000L;
                for (String p : GPU_MAX_PATHS)
                    RootUtils.runCommand("echo " + maxHz + " > " + p + " 2>/dev/null");
                // Update stat card
                final int fm = maxMhz;
                requireActivity().runOnUiThread(() -> tvGpuMaxFreq.setText(fm + ""));
            }

            // Min freq dari spinner
            int minMhz = getSelectedFreqMhz(spinnerGpuMinFreq);
            if (minMhz > 0) {
                long minHz = (long) minMhz * 1_000_000L;
                for (String p : GPU_MIN_PATHS)
                    RootUtils.runCommand("echo " + minHz + " > " + p + " 2>/dev/null");
                final int fm = minMhz;
                requireActivity().runOnUiThread(() -> tvGpuMinFreq.setText(fm + ""));
            }

            // Adreno boost
            boolean boost = switchAdrenoBoost.isChecked();
            PrefsManager.setAdrenoBoostEnabled(boost);
            RootUtils.runCommand("echo " + (boost?"1":"0") + " > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null");

            // Throttling
            boolean throttle = switchGpuThrottle.isChecked();
            PrefsManager.setGpuThrottleEnabled(throttle);
            RootUtils.runCommand("echo " + (throttle?"1":"0") + " > /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null");

            // OC mode
            boolean oc = switchGpuOc.isChecked();
            PrefsManager.setGpuOcEnabled(oc);
            if (oc) RootUtils.runCommand("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null");

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                btnApplyGpu.setEnabled(true);
                btnApplyGpu.setText("Apply GPU Config");
                tvGpuStatus.setText("Done ✓");
                Toast.makeText(requireContext(), "GPU config applied!", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    /** Ambil nilai MHz dari item yang dipilih di spinner ("xxx MHz") */
    private int getSelectedFreqMhz(Spinner spinner) {
        if (spinner.getSelectedItem() == null) return 0;
        try {
            String s = spinner.getSelectedItem().toString().replace(" MHz", "").trim();
            return Integer.parseInt(s);
        } catch (Exception e) { return 0; }
    }

    private String readFirstValid(String[] paths) {
        for (String p : paths) {
            String val = RootUtils.runCommand("cat " + p + " 2>/dev/null");
            if (!val.isEmpty()) return val.trim();
        }
        return "";
    }
}
