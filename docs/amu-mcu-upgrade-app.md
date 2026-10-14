# 阿木升级 MCU App 说明

## 背景

阿木升级 MCU App 是基于 YSerialPort 工程裁剪出来的 Android 升级工具，面向 RQ10 设备现场升级场景。应用只保留 MCU 升级相关能力，启动后直接进入升级界面，减少串口助手、网络助手等调试功能对生产升级流程的干扰。

当前应用名为“阿木升级MCU”，应用图标使用中文“木”作为主视觉元素。

## 当前版本

| 项目 | 内容 |
| --- | --- |
| Android 包名 | `com.yujing.chuankou` |
| 应用名称 | `阿木升级MCU` |
| 版本号 | `2.2.8` |
| versionCode | `28` |
| 默认串口 | `/dev/ttyUSB1` |
| 默认波特率 | `115200` |
| 屏幕方向 | 横屏 |
| 串口实现 | 工程内 `yserialport` 模块 + `ch34x` 模块 |
| 升级协议 | YMODEM |

## 内置固件

升级固件已固定内置到 App 内部，不需要用户手动选择文件。

| 类型 | 内置文件 | 大小 |
| --- | --- | --- |
| BOOTLOADER | `BOOT_RQ10_v2.11_260708.bin` | `17562B` |
| APP | `APP_RQ10_20260710.bin` | `91972B` |

资源位置：

```text
app/src/main/res/raw/boot_rq10_v2_11_260708.bin
app/src/main/res/raw/app_rq10_20260710.bin
```

应用启动后会读取以上 raw 资源，并在界面上显示固件名和大小。原来的“选择 BOOT 固件”和“选择 APP 固件”按钮已经隐藏，避免现场误选文件。

## 主界面功能

主界面分为四部分：

1. 串口连接区域
2. BOOTLOADER 升级区域
3. APP 升级区域
4. 升级进度和日志区域

串口区域默认选择 `/dev/ttyUSB1`，默认波特率 `115200`。应用启动后会自动扫描串口并尝试连接 `/dev/ttyUSB1`。如果系统扫描不到该串口，也会把 `/dev/ttyUSB1` 加入候选列表，方便 CH34x 免驱设备场景直接使用。

## BOOTLOADER 升级流程

BOOTLOADER 升级设计为一键操作，按钮为“一键升级BOOTLOADER”。

点击后 App 自动完成以下流程：

1. 确认串口已连接。
2. 通过 MAVLink `SERIAL_CONTROL` 向设备 shell 发送 `bl_upgrade` 命令。
3. 等待设备进入 BOOTLOADER 升级接收状态。
4. 使用 YMODEM 发送内置 BOOTLOADER 固件，作为第 1 次校验。
5. 第 1 次完成后自动等待设备第 2 次请求。
6. 使用 YMODEM 再次发送同一个 BOOTLOADER 固件，作为第 2 次正式写入。
7. 两次发送完成后提示“BOOTLOADER升级完成，请关机重启”。

注意事项：

- BOOTLOADER 必须升级两次，App 已经把两次 YMODEM 发送做到同一个按钮里。
- 用户不需要再单独点击“发送校验包”或手动选择 BOOT 固件。
- 完成后需要按提示关机重启设备，等待状态灯流程结束后再进行后续操作。

## APP 升级流程

APP 升级按钮为“升级APP固件”。

APP 侧进入升级模式不是由 Android App 下发命令触发，而是由设备开机时按住 `R1 + R3` 进入升级模式。因此 APP 升级前需要先手动让 MCU 进入 APP 升级接收状态。

推荐操作步骤：

1. 设备关机。
2. 按住 `R1 + R3`。
3. 设备开机，进入 APP 升级模式。
4. 确认 App 已连接 `/dev/ttyUSB1 @ 115200`。
5. 点击“升级APP固件”。
6. App 使用 YMODEM 发送内置 APP 固件。
7. 发送完成后提示“APP升级完成，请关机重启”。

## YMODEM 实现要点

当前 YMODEM 发送逻辑位于：

```text
app/src/main/java/com/yujing/chuankou/ymodem/YModemSender.java
```

实现特性：

- 使用 128 字节 SOH 数据包。
- 文件头包含文件名和文件大小。
- 使用 CRC16 校验。
- 等待设备发送字符 `C` 后开始传输。
- 每个数据包等待 ACK，失败后最多重试 10 次。
- 支持取消当前传输。
- 结束阶段兼容部分设备对最终空文件头响应不完整的情况，避免固件已经写入成功但 App 误报失败。

## MAVLink 命令实现

BOOTLOADER 升级前的 `bl_upgrade` 命令通过 MAVLink v1 `SERIAL_CONTROL` 消息发送。

实现位置：

```text
app/src/main/java/com/yujing/chuankou/mcu/MavlinkSerialControl.java
```

关键参数：

| 参数 | 值 |
| --- | --- |
| Message ID | `126` |
| CRC Extra | `220` |
| Device | `SERIAL_CONTROL_DEV_SHELL = 10` |
| Flag | `SERIAL_CONTROL_FLAG_RESPOND = 2` |
| GCS System ID | `255` |
| GCS Component ID | `190` |
| Target System ID | `1` |
| Target Component ID | `1` |
| Reply Timeout | `2000ms` |

当前只在 BOOTLOADER 升级流程中主动发送 `bl_upgrade`。APP 升级流程不发送 `ota_upgrade`，因为 APP 升级入口依赖设备开机时按住 `R1 + R3`。

## 日志和性能优化

升级界面底部保留通讯日志，方便现场判断串口状态和 YMODEM 进度。

为避免接收数据量较大时 UI 卡顿，串口接收日志做了节流和裁剪：

- 日志最大保留约 `8000` 字符。
- RX 数据按约 `120ms` 合并刷新一次。
- 每次 RX 日志只展示前 `32` 字节预览。
- 日志标签显示时间、方向和字节数，例如 `[21:04:49.454] [RX 33B]`。

这样可以保留关键排查信息，同时避免大量串口数据持续刷新导致整个 App 卡死。

## 异常处理

常见异常和处理建议：

| 现象 | 可能原因 | 处理方式 |
| --- | --- | --- |
| 打开串口失败 | `/dev/ttyUSB1` 权限不足或设备未枚举 | 检查 Android 设备 USB/串口权限，确认 CH34x 设备识别 |
| 一键升级 BOOTLOADER 超时 | MCU 未响应 `bl_upgrade` 或未进入 YMODEM 接收状态 | 重启设备后重试，观察日志是否出现等待设备发送 `C` |
| APP 升级超时 | MCU 未通过 `R1 + R3` 进入 APP 升级模式 | 重新按住 `R1 + R3` 开机后再点击升级 |
| 传输中断 | 串口断开、设备重启或用户取消 | 重新连接串口并重新开始升级 |

## 构建和安装

调试包构建命令：

```powershell
.\gradlew.bat :app:assembleDebug
```

单元测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

ADB 安装示例：

```powershell
adb -s 192.168.1.124:5555 install -r app/build/outputs/apk/debug/AmuMcuUpgrade_v2.2.8_28_*.apk
```

当前已验证安装设备：

```text
192.168.1.124:5555
```

## 今日变更记录

1. 从原串口调试工具分支切出阿木 MCU 升级专用 App。
2. 主入口改为“阿木升级MCU”升级界面。
3. 默认横屏显示，适配现场横屏操作。
4. 默认扫描并自动连接 `/dev/ttyUSB1 @ 115200`。
5. 新增 MAVLink `SERIAL_CONTROL` 发送 `bl_upgrade`。
6. 新增 YMODEM 固件发送能力。
7. BOOTLOADER 一键升级改为自动执行两次 YMODEM。
8. APP 升级改为只发送固件，不再尝试下发 APP 侧升级命令。
9. BOOTLOADER 和 APP 升级完成后均提示关机重启。
10. 优化 RX 日志刷新，降低大量接收数据时的 UI 卡顿。
11. 修复息屏恢复后因生命周期刷新导致的闪退问题。
12. App 图标改为中文“木”元素。
13. 固件固定内置到 App 内部，隐藏手动选择固件入口。

