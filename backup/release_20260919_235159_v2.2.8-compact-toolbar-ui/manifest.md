# Android Serial Tool Release

- Version: v2.2.8-compact-toolbar-ui
- Timestamp: 20260919_235159
- Branch: master
- Source commit: f0f9b6400e6e0a5bce11d47d1c71dccb2539d0f5

## Artifacts

| File | Size | SHA256 | Purpose |
| --- | ---: | --- | --- |
| android_serial_tool_v2.2.8-compact-toolbar-ui_20260919_235159.apk | 8657010 | 5DB2279FA4AD8A962B8C7F11016716E42FAA543D0BDDE0E04B91970A685DDAB5 | Debug APK with compact toolbar, merged counters, and button-based open status |
| device_compact_toolbar_preview.png | 92523 | 8917D004F22CE04F5559E8BAE7B4168FCB902DF96ED9D476CE5524DECC172B3A | Device screenshot verifying compact toolbar layout |

## Verification

- Built with `gradle :app:assembleDebug`
- Tested with `gradle :app:testDebugUnitTest`
- Installed with `adb install -r`
- Launched with `adb shell am start -n com.yujing.chuankou/.activity.MainActivity`
- Captured device screenshot to verify title/status removal and merged toolbar
- Copied APK was read back with SHA256 after archive copy
