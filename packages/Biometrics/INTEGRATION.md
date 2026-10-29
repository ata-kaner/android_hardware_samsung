# SamsungBiometrics – Unified Optical + Ultrasonic UDFPS

Unified system application (`org.lineageos.samsung.biometrics`) providing TouchScreen Panel (TSP) FoD control and Optical UDFPS dimming/illumination on Samsung devices running LineageOS / AOSP.

The sensor type reported by the framework (`FingerprintSensorPropertiesInternal.sensorType`) determines the active components:

| Sensor Type | Active Components |
|---|---|
| `TYPE_UDFPS_ULTRASONIC` | `FodController` (`set_fod_rect`, `fod_icon_visible`, `fod_enable`, `ScrubPosWatcher`) |
| `TYPE_UDFPS_OPTICAL` | `FodController` + `SamsungOpticalUdfpsService` (`MaskView`, `LightSourceView`, `BlackMaskView`) |

---

## Architectural Overview

```
                      +-----------------------------+
                      |   SamsungBiometricService   |
                      +--------------+--------------+
                                     |
               +---------------------+---------------------+
               |                                           |
               v                                           v
      [FodController]                         [SamsungOpticalUdfpsService]
 - TSP command dispatch                   - Dim overlay (MaskView)
 - State machine (DISABLED /              - Illumination circle (LightSourceView)
   SCREEN_ON_FAST / AOD_STRICT /          - Anti-flash black mask (BlackMaskView)
   AOD_FAST)                              - Holds Auth CPU WakeLock
 - Touch event monitoring (scrub_pos)     - Wakes display on AoD/Screen-off touch
 - Holds AOD_FAST CPU WakeLock            - Syncs with DisplayStateListener
 - Dispatches to FodListener              - Implements FodController.FodListener
```

### 1. Unified TouchScreen (TSP) FoD Controller (`FodController`)
- **Single Source of Truth**: Owns all TSP FoD interactions, both outbound commands (`fod_enable`, `set_fod_rect`, `fod_icon_visible`) and inbound touch detection from `/sys/devices/virtual/sec/tsp/scrub_pos`.
- **Dynamic Icon Visibility (`fod_icon_visible`)**: Synchronized dynamically for both optical and ultrasonic sensors matching Samsung TSP driver requirements:
  - Enabled (`fod_icon_visible,1`) whenever biometric authentication is running and the icon is visually rendered (`STATE_ON`, `STATE_DOZE`, or during `AOD_FAST`).
  - Disabled (`fod_icon_visible,0`) when the screen is truly off (`STATE_OFF`), biometric state is idle, or immediately when authentication succeeds (`onAuthenticationSucceeded`).
- **Integrated Touch Event Detection**:
  - `0x0F` (`SCRUB_FOD`): Finger down. If in `AOD_STRICT`, automatically enters `AOD_FAST` (`fod_enable,1,1,0`) and dispatches `onFingerDown(x, y)` to registered listeners.
  - `0x10` (`SCRUB_FOD_RELEASE`) / `0x11` (`SCRUB_FOD_OUT`): Finger up. Dispatches `onFingerUp()` to registered listeners.
- **Resource Efficiency**: A single `ScrubPosWatcher` background thread is active only when FoD is armed (`SCREEN_ON_FAST`, `AOD_STRICT`, `AOD_FAST`), terminating immediately when `DISABLED`.

### 2. Display State & Power Management
- **DisplayStateListener**: Tracks real hardware states (`Display.STATE_ON`, `Display.STATE_DOZE`, `Display.STATE_DOZE_SUSPEND`, `Display.STATE_OFF`) via `DisplayManager.DisplayListener`, `PowerManager.isInteractive()`, and system screen broadcasts (`ACTION_SCREEN_ON`, `ACTION_SCREEN_OFF`, `ACTION_USER_PRESENT`).
- **AoD / Screen-Off Touch Handling**:
  1. When screen is in AoD (`STATE_DOZE` / `STATE_DOZE_SUSPEND`) or OFF with Screen-Off UDFPS enabled, TSP is kept armed in `AOD_STRICT` (`fod_enable,1,0,0`).
  2. `FodController` watches `scrub_pos`.
  3. When touch is detected (`0x0F` / `SCRUB_FOD`):
     - `FodController` automatically enters `AOD_FAST` with a 5s wake-lock window.
     - `SamsungOpticalUdfpsService.onFingerDown(x, y)` runs:
       - Acquires `WAKE_LOCK` (up to 5s) to prevent device sleep.
       - Calls `PowerManager.wakeUp(uptime, WAKE_REASON_BIOMETRIC, "SamsungBiometrics:AoD")` to transition the AMOLED panel to `STATE_ON`.
       - Mounts `MaskView` (dim layer) and triggers `LightSourceView.showCircle()` before HBM engages, preventing blinding brightness flash.
  4. On finger release (`0x10` / `0x11`), `LightSourceView.hideCircle()` is called immediately, and the wake lock is scheduled for delayed release.

### 3. Dim Layer & Light Source Circle (`MaskView` & `LightSourceView`)
- SurfaceFlinger / HWComposer relies on window titles for z-ordering and HBM activation:
  - `DIM_WINDOW_TITLE`: `"Dim Layer for UDFPS"`
  - `CIRCLE_WINDOW_TITLE`: `"UdfpsControllerOverlay"`
- `MaskView` layout params include `FLAG_SHOW_WHEN_LOCKED`, `FLAG_NOT_FOCUSABLE`, `FLAG_NOT_TOUCHABLE`, `FLAG_WATCH_OUTSIDE_TOUCH`.
- Dim alpha calculation respects Samsung OneUI formula clamped to `MAX_ALPHA = 0.93f`, and queries AoD backlight index (`getFingerPrintBacklightValue(2)`) during `STATE_DOZE`.
- `LightSourceView` uses `setZOrderOnTop(true)` and `PixelFormat.TRANSPARENT`, drawing the illumination circle on a hardware surface matching sensor coordinates.
- **Illumination Control & Scan Failure Handling**:
  - The illumination circle turns off immediately when:
    1. **Scan Fails** (`onAuthenticationFailed`): Fingerprint not recognized.
    2. **Scan Errors** (`onAuthenticationError`): Timeout, lockout, or cancellation.
    3. **Image Acquired** (`onAuthenticationAcquired` with `shouldDisableUdfpsDisplayMode`): Sensor exposure has finished.
    4. **Safety Timeout**: Failsafe maximum illumination window (1500ms).
    5. **Finger Lifted** (`onFingerUp`).

### 4. Anti-Flash Transition (`BlackMaskView`)
- Matches Samsung OneUI's `UdfpsOpticalBlackMaskWindow` / `BlackMaskView`.
- When optical fingerprint authentication succeeds from **Screen-Off** or **AOD** (`Display.STATE_DOZE` / `STATE_OFF`), `BlackMaskView` displays a full-screen black overlay (`TYPE_DISPLAY_OVERLAY`, title `FP BlackMaskView`).
- This masks the ~50–100ms hardware panel transition where the refresh rate switches to interactive mode and the backlight ramps down from HBM (525/319 nits) to user backlight level, eliminating panel flicker, AOD clock artifacts, and bright flashes.
- The mask is dismissed immediately when `DisplayBrightnessMonitor` notifies that interactive brightness has settled, or after a 100ms safety timeout.
- For Screen-ON authentication, `BlackMaskView` is bypassed as the `MaskView` dim layer already smoothly manages alpha-blending.

---

## Privileged Permissions

Declared in `AndroidManifest.xml` and allowlisted in `privapp-permissions-samsung-biometrics.xml`:

```xml
<permissions>
    <privapp-permissions package="org.lineageos.samsung.biometrics">
        <permission name="android.permission.DEVICE_POWER"/>
        <permission name="android.permission.INTERACT_ACROSS_USERS"/>
        <permission name="android.permission.MANAGE_BIOMETRIC"/>
        <permission name="android.permission.MONITOR_INPUT"/>
        <permission name="android.permission.SET_PROCESS_LIMIT"/>
        <permission name="android.permission.USE_BIOMETRIC_INTERNAL"/>
        <permission name="android.permission.WAKE_LOCK"/>
    </privapp-permissions>
</permissions>
```

---

## Sysfs & HAL Interfaces

- **Biometrics Fingerprint Vendor HAL**: `vendor.samsung.hardware.biometrics.fingerprint.ISehFingerprint/default`
  - Implemented inside `android.hardware.biometrics.fingerprint-service.samsung`.
  - Exposes `updateDisplayInfo(displayWidth, displayHeight, xdpi)`, `getSensorInfo`, `getFodRect`, `setFodEnable`, `setFodRect`, `setFodIconVisible`, `setMaskBrightness`, `getSensorTargetNits`, `getSensorLightColor`, and `setScreenState`.
  - Computes sensor geometry and the FoD touch rectangle natively: reads `/sys/class/fingerprint/fingerprint/position` directly for **both Optical and Ultrasonic sensors** (with fallback to `ro.vendor.fingerprint.sensor_location` or device defaults), eliminating framework-side reflection entirely.
  - Automatically programs `/sys/class/sec/tsp/cmd` with the computed `set_fod_rect`.
  - Internal native `poll()` thread monitors `/sys/devices/virtual/sec/tsp/scrub_pos` and dispatches `onFingerDown(x, y)` and `onFingerUp()` to `ISehFingerprintCallback`.
  - Directly signals optical exposure/capture transitions (`onOpticalCaptureStarted`, `onOpticalCaptureFinished`) to extinguish the illumination circle immediately upon authentication completion or failure.
  - Reads optical sensor target nits and illumination circle color dynamically from `/sys/class/fingerprint/fingerprint/rb` (or vendor request 32) via `getSensorTargetNits()` and `getSensorLightColor()`, completely eliminating sysfs reads in the Java application.
- **Sysfs Paths (Native HAL)**:
  - `/sys/class/sec/tsp/cmd` (TSP FOD commands)
  - `/sys/devices/virtual/sec/tsp/scrub_pos` (TSP FOD touch coordinates & events)
  - `/sys/class/lcd/panel/mask_brightness` (UDFPS HBM target brightness)
  - `/sys/class/fingerprint/fingerprint/position` (Optical and Ultrasonic physical sensor geometry)
  - `/sys/class/fingerprint/fingerprint/rb` (Optical sensor target nits and calibration)
- **Device Overlay Arrays**:
  - `config_Screen_Brightness_Nits_Value`: Array of panel candela (nits) values corresponding to each backlight index.
  - `config_Screen_Brightness_Backlight_Value`: Array of panel backlight levels (0..max).
  - Target optical UDFPS brightness is resolved dynamically by matching the target nits returned by `getSensorTargetNits()` against `config_Screen_Brightness_Nits_Value` and obtaining the matching index in `config_Screen_Brightness_Backlight_Value`.

---

## Debugging & Verification

Filter logcat tags:
```bash
adb logcat -s SB_Service SB_FodController SB_OpticalUdfpsService SB_MaskView SB_DisplayStateListener
```

Simulate fingerprint gesture for debugging:
```bash
adb shell am broadcast -a org.lineageos.biometrics.action.FINGERPRINT_GESTURE
```
