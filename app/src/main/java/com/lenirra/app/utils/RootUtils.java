package com.lenirra.app.utils;

import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class RootUtils {

    private static final String TAG = "KF-Root";
    private static volatile boolean rootGranted = false;

    // Persistent su shell
    private static Process suProcess = null;
    private static DataOutputStream suStdin = null;
    private static BufferedReader suStdout = null;

    // Single worker thread — all commands run here sequentially, no race condition
    private static final LinkedBlockingQueue<Runnable> cmdQueue = new LinkedBlockingQueue<>();
    private static Thread workerThread = null;
    private static volatile boolean workerRunning = false;

    private static final String MARKER = "---LEVIOSA_DONE---";

    // ── Init ──────────────────────────────────────────────────────────────────

    public static boolean isRootAvailable() {
        try {
            Process p = Runtime.getRuntime().exec("which su");
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            p.destroy();
            return line != null && !line.isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Open persistent su shell + start worker thread.
     * Call ONCE from SplashActivity. Shows ONE su popup.
     */
    public static boolean requestRoot() {
        if (rootGranted) return true;
        try {
            suProcess = Runtime.getRuntime().exec("su");
            suStdin  = new DataOutputStream(suProcess.getOutputStream());
            suStdout = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));

            // Test access
            String result = sendRaw("id");
            rootGranted = result.contains("uid=0");

            if (rootGranted) {
                startWorker();
            }
            return rootGranted;
        } catch (Exception e) {
            Log.e(TAG, "requestRoot failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean isRootGranted() { return rootGranted; }

    public static void closeRoot() {
        workerRunning = false;
        try {
            if (suStdin != null) {
                suStdin.writeBytes("exit\n");
                suStdin.flush();
                suStdin.close();
            }
            if (suStdout != null) suStdout.close();
            if (suProcess != null) suProcess.destroy();
        } catch (Exception ignored) {}
        suProcess = null; suStdin = null; suStdout = null;
        rootGranted = false;
    }

    // ── Worker thread — serializes all shell I/O ──────────────────────────────

    private static void startWorker() {
        workerRunning = true;
        workerThread = new Thread(() -> {
            while (workerRunning) {
                try {
                    Runnable task = cmdQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (task != null) task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        workerThread.setName("kf-root-worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    // ── Raw I/O — only called from worker thread ──────────────────────────────

    private static synchronized String sendRaw(String command) {
        StringBuilder sb = new StringBuilder();
        try {
            suStdin.writeBytes(command + "\n");
            suStdin.writeBytes("echo " + MARKER + "\n");
            suStdin.flush();
            String line;
            while ((line = suStdout.readLine()) != null) {
                if (line.equals(MARKER)) break;
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            Log.e(TAG, "sendRaw failed: " + e.getMessage());
            rootGranted = false;
        }
        return sb.toString().trim();
    }

    // ── Public API — submits to worker queue, blocks caller until done ────────

    public static String runCommand(String command) {
        if (!rootGranted) return "";
        // If already on worker thread, run directly
        if (Thread.currentThread().getName().equals("kf-root-worker")) {
            return sendRaw(command);
        }
        // Otherwise submit to queue and wait for result
        final String[] result = {""};
        final Object lock = new Object();
        final boolean[] done = {false};
        cmdQueue.offer(() -> {
            result[0] = sendRaw(command);
            synchronized (lock) { done[0] = true; lock.notifyAll(); }
        });
        synchronized (lock) {
            long deadline = System.currentTimeMillis() + 3000;
            while (!done[0]) {
                try {
                    long wait = deadline - System.currentTimeMillis();
                    if (wait <= 0) break;
                    lock.wait(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return result[0];
    }

    public static List<String> runCommandLines(String command) {
        List<String> lines = new ArrayList<>();
        String output = runCommand(command);
        if (!output.isEmpty()) {
            for (String line : output.split("\n")) lines.add(line);
        }
        return lines;
    }

    public static boolean writeFile(String path, String value) {
        runCommand("echo " + value + " > " + path);
        return true;
    }

    public static String readFile(String path) {
        return runCommand("cat " + path);
    }

    // ── CPU ───────────────────────────────────────────────────────────────────

    public static int getCpuUsagePercent() {
        try {
            String line1 = runCommand("cat /proc/stat | grep '^cpu '");
            Thread.sleep(200);
            String line2 = runCommand("cat /proc/stat | grep '^cpu '");
            return parseCpuUsage(line1, line2);
        } catch (Exception e) { return 0; }
    }

    private static int parseCpuUsage(String s1, String s2) {
        try {
            long[] t1 = parseCpuLine(s1), t2 = parseCpuLine(s2);
            long totalDiff = sum(t2) - sum(t1);
            long idleDiff = t2[3] - t1[3];
            if (totalDiff == 0) return 0;
            return (int)((totalDiff - idleDiff) * 100L / totalDiff);
        } catch (Exception e) { return 0; }
    }

    private static long[] parseCpuLine(String line) {
        String[] p = line.trim().split("\\s+");
        long[] v = new long[p.length - 1];
        for (int i = 1; i < p.length; i++) v[i-1] = Long.parseLong(p[i]);
        return v;
    }

    private static long sum(long[] a) { long s=0; for(long v:a) s+=v; return s; }

    public static int getCpuFreqMhz(int core) {
        try { return Integer.parseInt(runCommand("cat /sys/devices/system/cpu/cpu"+core+"/cpufreq/scaling_cur_freq").trim()) / 1000; }
        catch (Exception e) { return 0; }
    }

    public static int getMaxCpuFreqMhz(int core) {
        try { return Integer.parseInt(runCommand("cat /sys/devices/system/cpu/cpu"+core+"/cpufreq/cpuinfo_max_freq").trim()) / 1000; }
        catch (Exception e) { return 0; }
    }

    public static String getCpuGovernor() {
        return runCommand("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
    }

    public static boolean setCpuGovernor(String gov) {
        int cores = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < cores; i++)
            runCommand("echo "+gov+" > /sys/devices/system/cpu/cpu"+i+"/cpufreq/scaling_governor");
        return true;
    }

    public static String[] getAvailableGovernors() {
        String raw = runCommand("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors");
        return raw.isEmpty() ? new String[]{"performance","schedutil","ondemand","conservative","powersave"} : raw.trim().split("\\s+");
    }

    // ── I/O ───────────────────────────────────────────────────────────────────

    public static String getIoScheduler() {
        String raw = runCommand("cat /sys/block/sda/queue/scheduler");
        if (raw.isEmpty()) raw = runCommand("cat /sys/block/mmcblk0/queue/scheduler");
        int s = raw.indexOf('['), e = raw.indexOf(']');
        return (s >= 0 && e > s) ? raw.substring(s+1, e) : raw.trim();
    }

    public static boolean setIoScheduler(String sched) {
        runCommand("echo "+sched+" > /sys/block/sda/queue/scheduler");
        runCommand("echo "+sched+" > /sys/block/mmcblk0/queue/scheduler");
        return true;
    }

    // ── RAM ───────────────────────────────────────────────────────────────────

    public static long[] getRamInfo() {
        try {
            List<String> lines = runCommandLines("cat /proc/meminfo");
            long total=0,free=0,buffers=0,cached=0,sreclaimable=0;
            for (String l : lines) {
                if (l.startsWith("MemTotal:")) total=parseMemLine(l);
                else if (l.startsWith("MemFree:")) free=parseMemLine(l);
                else if (l.startsWith("Buffers:")) buffers=parseMemLine(l);
                else if (l.startsWith("Cached:")) cached=parseMemLine(l);
                else if (l.startsWith("SReclaimable:")) sreclaimable=parseMemLine(l);
            }
            long avail = free+buffers+cached+sreclaimable;
            return new long[]{total/1024, (total-avail)/1024, avail/1024};
        } catch (Exception e) { return new long[]{0,0,0}; }
    }

    private static long parseMemLine(String l) { return Long.parseLong(l.replaceAll("[^0-9]","")); }

    public static int getSwappiness() {
        try { return Integer.parseInt(runCommand("cat /proc/sys/vm/swappiness").trim()); }
        catch (Exception e) { return 60; }
    }

    public static boolean setSwappiness(int val) { return writeFile("/proc/sys/vm/swappiness", String.valueOf(val)); }

    public static boolean dropCaches() {
        runCommand("sync");
        runCommand("echo 3 > /proc/sys/vm/drop_caches");
        return true;
    }

    // ── Battery ───────────────────────────────────────────────────────────────

    public static int getBatteryTemp() {
        try {
            String raw = runCommand("cat /sys/class/power_supply/battery/temp");
            if (raw.isEmpty()) raw = runCommand("cat /sys/class/power_supply/Battery/temp");
            return Integer.parseInt(raw.trim()) / 10;
        } catch (Exception e) { return 0; }
    }

    public static int getBatteryLevel() {
        try { return Integer.parseInt(runCommand("cat /sys/class/power_supply/battery/capacity").trim()); }
        catch (Exception e) { return 0; }
    }

    public static String getBatteryStatus() {
        return runCommand("cat /sys/class/power_supply/battery/status");
    }

    // ── Thermal ───────────────────────────────────────────────────────────────

    public static int getThermalZoneTemp(int zone) {
        try {
            int t = Integer.parseInt(runCommand("cat /sys/class/thermal/thermal_zone"+zone+"/temp").trim());
            return t > 1000 ? t/1000 : t;
        } catch (Exception e) { return 0; }
    }

    public static int getThermalStatus() {
        int t = getThermalZoneTemp(0);
        return t < 35 ? 0 : t < 45 ? 1 : t < 55 ? 2 : 3;
    }

    // ── SELinux ───────────────────────────────────────────────────────────────

    public static String getSelinuxStatus() {
        String r = runCommand("getenforce");
        if (r.isEmpty()) r = runCommand("cat /sys/fs/selinux/enforce");
        return r.trim();
    }

    public static boolean setSelinuxPermissive() { runCommand("setenforce 0"); return true; }
    public static boolean setSelinuxEnforcing()  { runCommand("setenforce 1"); return true; }

    // ── GPU ───────────────────────────────────────────────────────────────────

    public static int getGpuFreqMhz() {
        String[] paths = {
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/kernel/gpu/gpu_clock",
            "/sys/class/devfreq/gpufreq/cur_freq",
            "/sys/class/misc/mali0/device/clock"
        };
        for (String p : paths) {
            String raw = runCommand("cat " + p);
            if (!raw.isEmpty()) {
                try {
                    long hz = Long.parseLong(raw.trim());
                    return hz > 1000000 ? (int)(hz/1000000) : (int)(hz/1000);
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    // ── Kernel ────────────────────────────────────────────────────────────────

    public static String getKernelVersion() { return runCommand("uname -r"); }

    public static String getUptime() {
        try {
            String raw = runCommand("cat /proc/uptime");
            long sec = (long) Double.parseDouble(raw.trim().split("\\s+")[0]);
            return String.format("%02dh %02dm %02ds", sec/3600, (sec%3600)/60, sec%60);
        } catch (Exception e) { return "N/A"; }
    }

    public static int getNumCpuCores() {
        try { return Integer.parseInt(runCommand("nproc --all").trim()); }
        catch (Exception e) { return Runtime.getRuntime().availableProcessors(); }
    }

    // ── Network ───────────────────────────────────────────────────────────────

    public static String getTcpCongestion() {
        return runCommand("cat /proc/sys/net/ipv4/tcp_congestion_control").trim();
    }

    public static boolean setTcpCongestion(String algo) {
        return writeFile("/proc/sys/net/ipv4/tcp_congestion_control", algo);
    }

    public static boolean enableTcpBbr() { return setTcpCongestion("bbr"); }

    // ── Performance Profiles ──────────────────────────────────────────────────

    public static void applyProfile(String profile) {
        switch (profile) {
            case "performance": setCpuGovernor("performance"); setSwappiness(10); setIoScheduler("deadline"); setTcpCongestion("bbr"); break;
            case "gaming":      setCpuGovernor("schedutil"); setSwappiness(20); setIoScheduler("cfq"); runCommand("echo 0 > /sys/devices/system/cpu/cpufreq/boost"); break;
            case "balanced":    setCpuGovernor("schedutil"); setSwappiness(60); setIoScheduler("cfq"); break;
            case "battery":     setCpuGovernor("conservative"); setSwappiness(80); setIoScheduler("noop"); break;
            case "powersave":   setCpuGovernor("powersave"); setSwappiness(100); setIoScheduler("noop"); break;
        }
    }

    public static void boostNow() {
        runCommand("echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
        runCommand("sync");
        runCommand("echo 3 > /proc/sys/vm/drop_caches");
        runCommand("for pid in $(ls /proc | grep -E '^[0-9]+$'); do if [ -f /proc/$pid/oom_adj ]; then echo -17 > /proc/$pid/oom_adj; fi; done");
    }
}
