package com.lenirra.app.ui.fragments;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.*;
import com.lenirra.app.R;
import com.lenirra.app.services.SystemMonitorService;
import com.lenirra.app.utils.RootUtils;
import java.util.ArrayList;
import java.util.List;

public class SystemFragment extends Fragment {

    private TextView tvDevice, tvAndroid, tvKernel, tvBuild;
    private TextView tvRootStatus, tvSelinux, tvUptime;
    private TextView tvCpuArch, tvCpuCores, tvCpuMaxFreq;
    private TextView tvRamTotal, tvRamUsed, tvRamFree;
    private TextView tvGpuFreq, tvBattLevel, tvBattTemp, tvBattStatus;

    private LineChart cpuChart, ramChart, tempChart;
    private Handler handler;
    private boolean running = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_system, container, false);
        initViews(v);
        loadStaticInfo();
        setupCharts();
        startLiveUpdate();
        return v;
    }

    private void initViews(View v) {
        tvDevice = v.findViewById(R.id.tv_device);
        tvAndroid = v.findViewById(R.id.tv_android);
        tvKernel = v.findViewById(R.id.tv_kernel);
        tvBuild = v.findViewById(R.id.tv_build);
        tvRootStatus = v.findViewById(R.id.tv_root_status);
        tvSelinux = v.findViewById(R.id.tv_selinux);
        tvUptime = v.findViewById(R.id.tv_uptime);
        tvCpuArch = v.findViewById(R.id.tv_cpu_arch);
        tvCpuCores = v.findViewById(R.id.tv_cpu_cores);
        tvCpuMaxFreq = v.findViewById(R.id.tv_cpu_max_freq);
        tvRamTotal = v.findViewById(R.id.tv_ram_total);
        tvRamUsed = v.findViewById(R.id.tv_ram_used);
        tvRamFree = v.findViewById(R.id.tv_ram_free);
        tvGpuFreq = v.findViewById(R.id.tv_gpu_freq);
        tvBattLevel = v.findViewById(R.id.tv_batt_level);
        tvBattTemp = v.findViewById(R.id.tv_batt_temp);
        tvBattStatus = v.findViewById(R.id.tv_batt_status);
        cpuChart = v.findViewById(R.id.chart_cpu);
        ramChart = v.findViewById(R.id.chart_ram);
        tempChart = v.findViewById(R.id.chart_temp);
    }

    private void loadStaticInfo() {
        tvDevice.setText(Build.MANUFACTURER + " " + Build.MODEL);
        tvAndroid.setText("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        tvBuild.setText(Build.DISPLAY);
        tvRootStatus.setText(RootUtils.isRootGranted() ? "✓ Granted" : "✗ Denied");
        tvRootStatus.setTextColor(RootUtils.isRootGranted() ? 0xFF00FF94 : 0xFFFF3D57);

        new Thread(() -> {
            String kern = RootUtils.getKernelVersion();
            int cores = RootUtils.getNumCpuCores();
            int maxFreq = RootUtils.getMaxCpuFreqMhz(0);
            String arch = RootUtils.runCommand("uname -m");
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                tvKernel.setText(kern.isEmpty() ? Build.VERSION.BASE_OS : kern);
                tvCpuCores.setText(cores + " Cores");
                tvCpuMaxFreq.setText(maxFreq + " MHz");
                tvCpuArch.setText(arch.isEmpty() ? Build.SUPPORTED_ABIS[0] : arch);
            });
        }).start();
    }

    private void setupCharts() {
        setupChart(cpuChart, 0xFF00FF94);
        setupChart(ramChart, 0xFFFF6B35);
        setupChart(tempChart, 0xFFFF3D57);
    }

    private void setupChart(LineChart chart, int color) {
        if (chart == null) return;
        chart.setDrawGridBackground(false);
        chart.setBackgroundColor(0x00000000);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);

        XAxis x = chart.getXAxis();
        x.setEnabled(false);

        YAxis left = chart.getAxisLeft();
        left.setTextColor(0xFF8B9DB5);
        left.setTextSize(9f);
        left.setAxisMinimum(0f);
        left.setAxisMaximum(100f);
        left.setGridColor(0x221E2D3D);
        left.setDrawAxisLine(false);

        chart.getAxisRight().setEnabled(false);
    }

    private void updateChart(LineChart chart, List<Integer> history, int color) {
        if (chart == null || history.isEmpty()) return;
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            entries.add(new Entry(i, history.get(i)));
        }
        LineDataSet ds = new LineDataSet(entries, "");
        ds.setColor(color);
        ds.setLineWidth(2f);
        ds.setDrawCircles(false);
        ds.setDrawValues(false);
        ds.setFillColor(color);
        ds.setFillAlpha(30);
        ds.setDrawFilled(true);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        chart.setData(new LineData(ds));
        chart.invalidate();
    }

    private void startLiveUpdate() {
        running = true;
        handler = new Handler(Looper.getMainLooper());
        handler.post(updateTask);
    }

    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (!running || !isAdded()) return;

            long ramTotal = SystemMonitorService.ramTotal;
            long ramUsed = SystemMonitorService.ramUsed;
            long ramFree = SystemMonitorService.ramFree;
            int battTemp = SystemMonitorService.batteryTemp;
            int battLevel = SystemMonitorService.batteryLevel;
            int gpuFreq = SystemMonitorService.gpuFreq;
            String selinux = SystemMonitorService.selinuxStatus;

            tvRamTotal.setText(ramTotal + " MB");
            tvRamUsed.setText(ramUsed + " MB");
            tvRamFree.setText(ramFree + " MB");
            tvBattTemp.setText(battTemp + "°C");
            tvBattLevel.setText(battLevel + "%");
            tvGpuFreq.setText(gpuFreq > 0 ? gpuFreq + " MHz" : "N/A");
            tvSelinux.setText(selinux.isEmpty() ? "N/A" : selinux);

            // Charts
            updateChart(cpuChart, new ArrayList<>(SystemMonitorService.cpuHistory), 0xFF00FF94);
            updateChart(ramChart, new ArrayList<>(SystemMonitorService.ramHistory), 0xFFFF6B35);
            updateChart(tempChart, new ArrayList<>(SystemMonitorService.tempHistory), 0xFFFF3D57);

            new Thread(() -> {
                String uptime = RootUtils.getUptime();
                String battStatus = RootUtils.getBatteryStatus();
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        tvUptime.setText(uptime);
                        tvBattStatus.setText(battStatus);
                    });
                }
            }).start();

            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onDestroyView() {
        running = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
