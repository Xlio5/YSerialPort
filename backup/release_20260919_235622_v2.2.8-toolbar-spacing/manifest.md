# Android Serial Tool Release

- Version: v2.2.8-toolbar-spacing
- Timestamp: 20260919_235622
- Branch: master
- Source commit: d85d6f44ed96a5bc8339ce370bae849ca16c7c60

## Artifacts

| File | Size | SHA256 | Purpose |
| --- | ---: | --- | --- |
| android_serial_tool_v2.2.8-toolbar-spacing_20260919_235622.apk | 8657038 | CC39D752FF93C1570E8AF5D92DA507AC2AD38290DA6DF4A2AF5889875008BEA1 | Debug APK with fixed spacing between serial parameter row and counter toolbar |
| device_toolbar_spacing_preview.png | 92424 | CFC96BA2F423725DAE8120BB8DC6309C52CD3400CDBC7BF35FABE0771EAC374D | Device screenshot verifying counter toolbar no longer interferes with baud/stop-bit row |

## Verification

- Built with `gradle :app:assembleDebug`
- Tested with `gradle :app:testDebugUnitTest`
- Installed with `adb install -r`
- Launched with `adb shell am start -n com.yujing.chuankou/.activity.MainActivity`
- Captured device screenshot to verify toolbar spacing
- Copied APK was read back with SHA256 after archive copy
