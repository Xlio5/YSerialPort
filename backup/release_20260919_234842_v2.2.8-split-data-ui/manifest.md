# Android Serial Tool Release

- Version: v2.2.8-split-data-ui
- Timestamp: 20260919_234842
- Branch: master
- Source commit: 68b1dfcf09b6e0f13ce3ac9d3da9398b8e9e94b3

## Artifacts

| File | Size | SHA256 | Purpose |
| --- | ---: | --- | --- |
| android_serial_tool_v2.2.8-split-data-ui_20260919_234842.apk | 8657724 | D4A17279512E671C083BDF1C5AE101F9C683481C5DD2FB4D30B2D6BAC6F63CDC | Debug APK with split communication/write-data layout |
| device_split_data_preview.png | 103160 | EFE12BA164A11A3C270A4CA3BAF4A50FFF51F33C3D4D4CDF14445FB3CB33AF8A | Device screenshot verifying communication log and write-data input are both visible |

## Verification

- Built with `gradle :app:assembleDebug`
- Tested with `gradle :app:testDebugUnitTest`
- Installed with `adb install -r`
- Launched with `adb shell am start -n com.yujing.chuankou/.activity.MainActivity`
- Captured device screenshot to verify split data layout
- Copied APK was read back with SHA256 after archive copy
