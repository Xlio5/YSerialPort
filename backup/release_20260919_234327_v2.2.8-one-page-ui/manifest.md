# Android Serial Tool Release

- Version: v2.2.8-one-page-ui
- Timestamp: 20260919_234327
- Branch: master
- Source commit: 4c6e2ddec50be9f43dea2ffc2fda312abeb7f9ed

## Artifacts

| File | Size | SHA256 | Purpose |
| --- | ---: | --- | --- |
| android_serial_tool_v2.2.8-one-page-ui_20260919_234327.apk | 8657746 | 54A7CB9C906EF4F9CEA588966EEB07CA6FFF2BFD32D244B657C5E1503D61657B | Debug APK with one-page serial assistant UI |
| device_one_page_preview.png | 241760 | C185BD0D54055B2110DB473AAA1F11FE2B5F8479B9AD81A045EA5EEEDA491C3F | Device screenshot verifying all main controls are visible without page scrolling |

## Verification

- Built with `gradle :app:assembleDebug`
- Tested with `gradle :app:testDebugUnitTest`
- Installed with `adb install -r`
- Launched with `adb shell am start -n com.yujing.chuankou/.activity.MainActivity`
- Captured device screenshot to verify one-page layout
- Copied APK was read back with SHA256 after archive copy
