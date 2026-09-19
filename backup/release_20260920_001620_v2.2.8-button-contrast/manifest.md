# YSerialPort Button Contrast Release

- Version: v2.2.8-button-contrast
- App versionName: 2.2.8
- App versionCode: 28
- Timestamp: 20260920_001620
- Source branch: master
- Source commit before release commit: 8c40a85b43d1984589ec2554702fe8b695f6b339
- Build command: `gradle.bat :app:assembleDebug`
- Test command: `gradle.bat :app:testDebugUnitTest`
- Install verification: installed by ADB from this build before screenshot capture

## Artifacts

| File | Size | SHA256 | Purpose |
| --- | ---: | --- | --- |
| android_serial_tool_v2.2.8-button-contrast_20260920_001620.apk | 8658981 | C89ECBEC6E28D165C7B113BE658EEAD88A4CC5E6E7838140831F4AAD484135BA | Android debug APK with Material button tint disabled for readable secondary/danger buttons |
| android_serial_tool_v2.2.8-button-contrast_20260920_001620.png | 96088 | C294C865701348BDD33A3BD3FB6E8ECAFB4B0B458F88AF94A381A943652CF976 | Installed-device screenshot verifying readable scan/clear buttons |

## Notes

- Root cause: MaterialComponents applied default button background tint over custom drawable backgrounds.
- Fix: custom-background buttons now set both `android:backgroundTint` and `app:backgroundTint` to `@null`.
