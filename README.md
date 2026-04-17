# KernelForge Tweak — Android Root System Optimizer

> ⚠️ **ROOT REQUIRED** — Android 8.0 (API 26) minimum

## Features

### 🏠 Dashboard (Home)
- Real-time CPU usage gauge dengan animasi
- Battery temperature monitor
- Thermal status (Cool/Warm/Hot/Critical)
- SELinux status
- RAM & GPU frequency stats
- Performance profiles: Balanced / Gaming / Battery / Powersave / Performance
- **Boost Now** — instant cache drop + CPU boost
- Apply on Boot toggle
- FPS Overlay shortcut

### ⚙️ Kernel Tweaks
- CPU Governor selector (schedutil, performance, ondemand, conservative, powersave)
- I/O Scheduler selector (cfq, deadline, noop, bfq, kyber)
- VM Swappiness slider (0-100)
- TCP Congestion algorithm (cubic, bbr, reno, westwood)
- Enable TCP BBR
- ZRAM 512MB toggle
- Aggressive Doze toggle
- Disable Auto-Sync toggle

### 📊 System Info
- Device, Android version, Kernel version, Build fingerprint
- Root & SELinux status
- CPU arch, cores, max frequency
- Real-time RAM usage
- Battery level, temp, status
- GPU frequency
- Live line charts: CPU%, RAM%, Battery Temp (last 60 seconds)

### 🛠️ Tools
- **Root Terminal** — run any root command live
- Drop Caches
- Kill Background Apps
- Logcat viewer
- Wakelock Detector
- Top Processes
- Network Info
- Build.prop viewer
- Battery Calibrate
- Reboot / Recovery / Bootloader

### 🎮 FPS Overlay (Draggable!)
- Custom overlay window rendered via Canvas
- FPS tracked via Choreographer (accurate frame timing)
- **Color coded**: Green (≥55fps) / Yellow (≥30fps) / Red (<30fps)
- **Double-tap** to expand/collapse
- **Drag anywhere** on screen
- Expanded view shows: FPS graph, CPU%, RAM%, Temp
- Configurable: opacity, lock position, show/hide stats
- Persists position across sessions

### 🔄 Apply on Boot
- Saves your profile and re-applies all tweaks on every boot

## Project Structure
```
app/src/main/
├── java/com/kernelforge/tweak/
│   ├── KernelForgeApp.java          # Application class, notification channels
│   ├── activities/
│   │   ├── SplashActivity.java      # Root check + animated splash
│   │   └── MainActivity.java        # Bottom nav + fragment host
│   ├── services/
│   │   ├── FpsOverlayService.java   # Draggable FPS overlay
│   │   └── SystemMonitorService.java # Background CPU/RAM/temp polling
│   ├── ui/
│   │   ├── fragments/
│   │   │   ├── HomeFragment.java    # Dashboard
│   │   │   ├── KernelFragment.java  # Kernel tweaks
│   │   │   ├── SystemFragment.java  # System info + charts
│   │   │   ├── ToolsFragment.java   # Terminal + tools
│   │   │   └── ProfileFragment.java # FPS settings + about
│   │   └── widgets/
│   │       └── GaugeView.java       # Custom animated gauge
│   └── utils/
│       ├── RootUtils.java           # All root commands
│       ├── PrefsManager.java        # SharedPreferences wrapper
│       └── BootReceiver.java        # Auto-apply on boot
└── res/
    ├── layout/                      # All XML layouts
    ├── drawable/                    # Vector icons + backgrounds
    ├── values/                      # Colors, strings, styles, dimens
    └── ...
```

## How to Build

1. Open project in **Android Studio Hedgehog** or newer
2. Sync Gradle (auto downloads dependencies)
3. Build → Generate Signed APK, or run directly on device
4. **Grant root** when prompted on first launch
5. **Grant overlay permission** for FPS meter

## Dependencies
- MPAndroidChart 3.1.0 — live system charts
- Material Components 1.11.0 — UI components
- Lottie 6.3.0 — animations

## Permissions Required
- `SYSTEM_ALERT_WINDOW` — FPS overlay
- `FOREGROUND_SERVICE` — background monitor
- `RECEIVE_BOOT_COMPLETED` — auto-apply on boot
- Root (su) — all kernel/system tweaks

