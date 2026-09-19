# Android Serial Tool Release

- Version: v2.2.8-polished-ui-scroll
- Timestamp: 20260919_233734
- Branch: master
- Source commit: 1fa3538e3f563f13b8691f0d4859916adbd88853

## Artifacts

| File | Size | SHA256 | Purpose |
| --- | ---: | --- | --- |
| android_serial_tool_v2.2.8-polished-ui-scroll_20260919_233734.apk | 8657875 | 02E8D3BC7E9EA2C9ADF9E5B91085946B1197F01654D2614128187D29C871408B | Debug APK with polished dark UI, readable controls, fixed log height, and scrollable main surface |

## Verification

- Built with `gradle :app:assembleDebug`
- Tested with `gradle :app:testDebugUnitTest`
- Installed with `adb install -r`
- Launched with `adb shell am start -n com.yujing.chuankou/.activity.MainActivity`
- Captured device screenshot to verify readability and layout
- Copied APK was read back with SHA256 after archive copy
