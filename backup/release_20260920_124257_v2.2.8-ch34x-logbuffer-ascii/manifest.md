# Android 串口助手发布清单

## 基本信息
- 版本: v2.2.8-ch34x-logbuffer-ascii
- 时间: 2026-09-20 12:42:57
- 来源分支: master
- 来源 commit: f4c171cbc0eaf42d2306ae4208f1e4105a743b93
- APK 包名: com.yujing.chuankou
- versionName: 2.2.8
- versionCode: 28

## 产物
- android_serial_tool_v2.2.8-ch34x-logbuffer-ascii_20260920_124257.apk
  - 大小: 8673964 bytes
  - SHA256: 0B7147ECEECD5597CCE7A6F00225605DC06ED94D24CC01246156B214CC6DB078
  - 用途: 默认 ASCII、支持系统串口和 CH34x USB 免驱串口的 debug APK
- android_serial_tool_v2.2.8-ch34x-logbuffer-ascii_20260920_124257.png
  - 大小: 109565 bytes
  - SHA256: AA5E73E50E6B2BF36CEE99E5063A9799F89EC2062A6E4F29BFCCA7C324C6312B
  - 用途: 真机界面截图，确认 HEX 显示和 HEX 发送默认关闭

## 关键变更
- 串口下拉框同时展示系统串口和 CH34x USB 免驱设备。
- CH34x 设备使用 `[USB免驱] CH340G 1A86:7523` 等标签，避免和原 `/dev/tty*` 逻辑混淆。
- 发送路径兼容原生串口输出流和 CH34x USB 写入。
- 通讯数据区改为增量追加缓存，超过上限才裁剪刷新，降低数据满屏后闪屏。
- HEX 显示和 HEX 发送默认关闭，即默认 ASCII。

## 验证
- 单元测试: `:app:testDebugUnitTest` 通过。
- 构建: `:app:clean :app:assembleDebug --rerun-tasks` 通过。
- 安装: `adb install -r` 成功。
- 设备读回: `dumpsys package com.yujing.chuankou` 显示 versionName=2.2.8, versionCode=28, lastUpdateTime=2026-09-20 12:42:40。
- 复制校验: APK 归档前后 SHA256 一致。
