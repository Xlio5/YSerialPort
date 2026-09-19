# YSerialPort Neutral Clear Count Release

- Version: v2.2.8-neutral-clear-count
- App versionName: 2.2.8
- App versionCode: 28
- Timestamp: 20260920_001948
- Source branch: master
- Source commit before release commit: 9a7891701dd41ce11ec7b9248d54331aaee7d72d
- Build command: `gradle.bat :app:assembleDebug`
- Test command: `gradle.bat :app:testDebugUnitTest`
- Install verification: installed by ADB from this build before screenshot capture

## Artifacts

| File | Size | SHA256 | Purpose |
| --- | ---: | --- | --- |
| android_serial_tool_v2.2.8-neutral-clear-count_20260920_001948.apk | 8658972 | D4639F634FD90F6D426DA54811A41FD74DDB06EA84D65001751F05BCCB8157E2 | Android debug APK with neutral clear-count button styling |
| android_serial_tool_v2.2.8-neutral-clear-count_20260920_001948.png | 93299 | 24729D0C020FAB4B9A67C214E2D7B8B22A460E85DD5FB5C6E1561E3C4E24E6F4 | Installed-device screenshot verifying neutral clear-count button |

## Notes

- The clear-count button now uses the same secondary slate treatment as the clear-data button.
- Serial communication behavior is unchanged.
