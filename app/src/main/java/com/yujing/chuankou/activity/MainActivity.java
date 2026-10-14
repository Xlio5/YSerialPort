package com.yujing.chuankou.activity;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivityModeBinding;
import com.yujing.chuankou.mcu.MavlinkSerialControl;
import com.yujing.chuankou.serial.SerialLogBuffer;
import com.yujing.chuankou.ymodem.YModemSender;
import com.yujing.serialport.SerialPort;
import com.yujing.serialport.SerialPortFinder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends BaseActivity<ActivityModeBinding> {
    private static final int REQUEST_BOOT_FILE = 1001;
    private static final int REQUEST_APP_FILE = 1002;
    private static final String DEFAULT_DEVICE = "/dev/ttyUSB1";
    private static final String DEFAULT_BAUD = "115200";
    private static final int MAX_LOG_CHARS = 8_000;
    private static final int RX_LOG_INTERVAL_MS = 120;
    private static final int RX_LOG_PREVIEW_BYTES = 32;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService workExecutor = Executors.newSingleThreadExecutor();
    private final LinkedBlockingQueue<Integer> incomingBytes = new LinkedBlockingQueue<>();
    private final AtomicBoolean reading = new AtomicBoolean(false);
    private final SerialLogBuffer logBuffer = new SerialLogBuffer(MAX_LOG_CHARS);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private final Object rxLogLock = new Object();
    private final byte[] rxLogPreview = new byte[RX_LOG_PREVIEW_BYTES];

    private SerialPort serialPort;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread readThread;
    private YModemSender activeSender;
    private FirmwareFile bootFile;
    private FirmwareFile appFile;
    private volatile boolean busy;
    private volatile boolean appSideOtaMode;
    private volatile boolean activityVisible = true;
    private volatile boolean destroyed;
    private int pendingRxBytes;
    private int pendingRxPreviewBytes;
    private boolean rxLogScheduled;

    public MainActivity() {
        super(R.layout.activity_mode);
    }

    @Override
    protected void init() {
        setupBaudRate();
        scanPorts();
        binding.btnScan.setOnClickListener(v -> scanPorts());
        binding.btnConnect.setOnClickListener(v -> toggleSerial());
        binding.btnBootFile.setOnClickListener(v -> chooseFile(REQUEST_BOOT_FILE));
        binding.btnAppFile.setOnClickListener(v -> chooseFile(REQUEST_APP_FILE));
        binding.btnBootPrepare.setOnClickListener(v -> startBootUpgrade());
        binding.btnBootStart.setVisibility(View.GONE);
        binding.btnAppCommand.setOnClickListener(v -> sendShellCommand("ota_upgrade"));
        binding.btnAppSend.setOnClickListener(v -> startAppUpgrade());
        binding.btnCancel.setOnClickListener(v -> cancelTransfer());
        binding.btnClear.setOnClickListener(v -> clearLog());
        refreshSerialStatus();
        refreshBusy();
        mainHandler.postDelayed(this::autoConnectDefaultSerial, 300);
    }

    private void setupBaudRate() {
        String[] baudRates = {"9600", "19200", "38400", "57600", "115200", "230400", "460800", "921600"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_dark, baudRates);
        adapter.setDropDownViewResource(R.layout.item_spinner_dark_dropdown);
        binding.spBaud.setAdapter(adapter);
        binding.spBaud.setSelection(Arrays.asList(baudRates).indexOf(DEFAULT_BAUD));
    }

    private void scanPorts() {
        String[] devices = new SerialPortFinder().getAllDevicesPath();
        if (devices.length == 0) {
            devices = new String[]{DEFAULT_DEVICE, "/dev/ttyUSB0", "/dev/ttyS0", "/dev/ttyS1"};
            appendLog("SYS", "未扫描到串口，已给出常见路径");
        } else {
            boolean hasDefault = false;
            for (String device : devices) {
                if (DEFAULT_DEVICE.equals(device)) {
                    hasDefault = true;
                    break;
                }
            }
            if (!hasDefault) {
                List<String> merged = new ArrayList<>();
                merged.add(DEFAULT_DEVICE);
                merged.addAll(Arrays.asList(devices));
                devices = merged.toArray(new String[0]);
                appendLog("SYS", "未扫描到 " + DEFAULT_DEVICE + "，已加入默认路径");
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_dark, devices);
        adapter.setDropDownViewResource(R.layout.item_spinner_dark_dropdown);
        binding.spDevice.setAdapter(adapter);
        int selected = 0;
        for (int i = 0; i < devices.length; i++) {
            if (DEFAULT_DEVICE.equals(devices[i])) {
                selected = i;
                break;
            }
        }
        binding.spDevice.setSelection(selected);
    }

    private void autoConnectDefaultSerial() {
        if (destroyed || isSerialOpen()) return;
        Object item = binding.spDevice.getSelectedItem();
        if (item == null || !DEFAULT_DEVICE.equals(item.toString())) {
            scanPorts();
        }
        appendLog("SYS", "自动连接 " + DEFAULT_DEVICE);
        openSerial();
    }

    private void toggleSerial() {
        if (isSerialOpen()) {
            closeSerial();
        } else {
            openSerial();
        }
    }

    private void openSerial() {
        try {
            Object item = binding.spDevice.getSelectedItem();
            if (item == null) throw new IllegalStateException("没有可打开的串口");
            int baud = Integer.parseInt(binding.spBaud.getSelectedItem().toString());
            serialPort = SerialPort.newBuilder(new File(item.toString()), baud).build();
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();
            startReader();
            appendLog("SYS", "已连接 " + item + " @ " + baud);
        } catch (Exception e) {
            closeSerial();
            toast("打开串口失败: " + e.getMessage());
            appendLog("ERR", "打开串口失败: " + e.getMessage());
        }
        refreshSerialStatus();
    }

    private void closeSerial() {
        reading.set(false);
        if (activeSender != null) activeSender.cancel();
        if (readThread != null) readThread.interrupt();
        readThread = null;
        try {
            if (serialPort != null) serialPort.tryClose();
        } catch (Exception ignored) {
        }
        serialPort = null;
        inputStream = null;
        outputStream = null;
        incomingBytes.clear();
        appendLog("SYS", "串口已断开");
        refreshSerialStatus();
    }

    private void startReader() {
        reading.set(true);
        readThread = new Thread(() -> {
            byte[] buffer = new byte[512];
            while (reading.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    int n = inputStream == null ? -1 : inputStream.read(buffer);
                    if (n <= 0) continue;
                    for (int i = 0; i < n; i++) incomingBytes.offer(buffer[i] & 0xFF);
                    queueRxLog(buffer, n);
                } catch (Exception e) {
                    if (reading.get()) mainHandler.post(() -> appendLog("ERR", "读取串口失败: " + e.getMessage()));
                    break;
                }
            }
        }, "McuUpgradeSerialReader");
        readThread.start();
    }

    private void startBootUpgrade() {
        if (bootFile == null) {
            toast("请先选择 BOOTLOADER 固件");
            appendLog("ERR", "未选择 BOOTLOADER 固件，不能一键升级");
            return;
        }
        if (!ensureSerialReady() || busy) return;
        busy = true;
        appSideOtaMode = false;
        refreshBusy();
        binding.progressUpgrade.setProgress(0);
        binding.tvProgress.setText("BOOTLOADER 一键升级准备中");
        appendLog("SYS", "BOOTLOADER 一键升级开始: " + bootFile.name + " (" + bootFile.data.length + "B)");
        workExecutor.execute(() -> {
            try {
                writeBytes(MavlinkSerialControl.shellCommand("bl_upgrade"));
                mainHandler.post(() -> appendLog("TX CMD", "bl_upgrade"));
                Thread.sleep(250);
                sendYModemBlocking("BOOTLOADER 第1次校验", bootFile, false, 0, 50);
                mainHandler.post(() -> appendLog("SYS", "第1次校验完成，自动等待设备第二次请求"));
                sendYModemBlocking("BOOTLOADER 第2次正式写入", bootFile, false, 50, 100);
                mainHandler.post(() -> {
                    appendLog("SYS", "BOOTLOADER 一键升级完成，请关机重启设备");
                    binding.tvProgress.setText("完成，请关机重启");
                    toast("BOOTLOADER升级完成，请关机重启");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    toast("BOOTLOADER 一键升级失败");
                    appendLog("ERR", "BOOTLOADER 一键升级失败: " + e.getMessage());
                });
            } finally {
                activeSender = null;
                busy = false;
                mainHandler.post(this::refreshBusy);
            }
        });
    }

    private void startAppUpgrade() {
        if (appFile == null) {
            toast("请先选择 APP 固件");
            appendLog("ERR", "未选择 APP 固件，不能发送 APP 固件");
            return;
        }
        boolean singleEotAck = appSideOtaMode;
        appSideOtaMode = false;
        runUpgrade("APP 升级", appFile, singleEotAck, null, null);
    }

    private void sendShellCommand(String command) {
        if (!ensureSerialReady() || busy) return;
        workExecutor.execute(() -> {
            try {
                writeBytes(MavlinkSerialControl.shellCommand(command));
                if ("ota_upgrade".equals(command)) appSideOtaMode = true;
                mainHandler.post(() -> appendLog("TX CMD", command));
            } catch (Exception e) {
                mainHandler.post(() -> appendLog("ERR", "命令发送失败: " + e.getMessage()));
            }
        });
    }

    private void runUpgrade(String title, FirmwareFile firmwareFile, boolean allowSingleEotAck,
                            BeforeTransfer beforeTransfer, Runnable afterSuccess) {
        if (!ensureSerialReady() || busy) return;
        busy = true;
        refreshBusy();
        binding.progressUpgrade.setProgress(0);
        binding.tvProgress.setText(title + " 准备中");
        appendLog("SYS", title + " 已启动: " + firmwareFile.name + " (" + firmwareFile.data.length + "B)");
        workExecutor.execute(() -> {
            try {
                if (beforeTransfer != null) {
                    beforeTransfer.run();
                    mainHandler.post(() -> appendLog("TX CMD", "bl_upgrade"));
                    Thread.sleep(250);
                }
                YModemSender sender = new YModemSender(incomingBytes);
                activeSender = sender;
                mainHandler.post(() -> appendLog("SYS", title + " YMODEM 开始: " + firmwareFile.name));
                sender.send(outputStream, firmwareFile.name, firmwareFile.data, allowSingleEotAck,
                        (sent, total, message) -> mainHandler.post(() -> {
                            int percent = total <= 0 ? 0 : (int) Math.min(100, sent * 100 / total);
                            binding.progressUpgrade.setProgress(percent);
                            binding.tvProgress.setText(percent + "%  " + message);
                        }));
                mainHandler.post(() -> {
                    appendLog("SYS", title + " 完成");
                    if (afterSuccess != null) afterSuccess.run();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    toast(title + "失败");
                    appendLog("ERR", title + "失败: " + e.getMessage());
                });
            } finally {
                activeSender = null;
                busy = false;
                mainHandler.post(this::refreshBusy);
            }
        });
    }

    private void sendYModemBlocking(String title, FirmwareFile firmwareFile, boolean allowSingleEotAck,
                                    int progressStart, int progressEnd)
            throws Exception {
        YModemSender sender = new YModemSender(incomingBytes);
        activeSender = sender;
        mainHandler.post(() -> appendLog("SYS", title + " YMODEM 开始: " + firmwareFile.name));
        sender.send(outputStream, firmwareFile.name, firmwareFile.data, allowSingleEotAck,
                (sent, total, message) -> mainHandler.post(() -> {
                    int localPercent = total <= 0 ? 0 : (int) Math.min(100, sent * 100 / total);
                    int percent = progressStart + (progressEnd - progressStart) * localPercent / 100;
                    binding.progressUpgrade.setProgress(percent);
                    binding.tvProgress.setText(percent + "%  " + title + " " + message);
                }));
        mainHandler.post(() -> appendLog("SYS", title + " 完成"));
    }

    private boolean ensureSerialReady() {
        if (!isSerialOpen() || outputStream == null) {
            toast("请先连接串口");
            appendLog("ERR", "串口未连接，操作已取消");
            return false;
        }
        return true;
    }

    private boolean isSerialOpen() {
        return serialPort != null && outputStream != null;
    }

    private void writeBytes(byte[] data) throws Exception {
        OutputStream out = outputStream;
        if (out == null) throw new IllegalStateException("串口未连接");
        synchronized (this) {
            out.write(data);
            out.flush();
        }
    }

    private void cancelTransfer() {
        if (activeSender != null) activeSender.cancel();
        appendLog("SYS", "已请求取消当前传输");
    }

    private void chooseFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, requestCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            FirmwareFile firmwareFile = readFirmware(data.getData());
            if (requestCode == REQUEST_BOOT_FILE) {
                bootFile = firmwareFile;
                binding.tvBootFile.setText(firmwareFile.name + "  " + firmwareFile.data.length + "B");
            } else if (requestCode == REQUEST_APP_FILE) {
                appFile = firmwareFile;
                binding.tvAppFile.setText(firmwareFile.name + "  " + firmwareFile.data.length + "B");
            }
            appendLog("SYS", "已选择 " + firmwareFile.name + " (" + firmwareFile.data.length + "B)");
            refreshBusy();
        } catch (Exception e) {
            toast("读取固件失败");
            appendLog("ERR", "读取固件失败: " + e.getMessage());
        }
    }

    private FirmwareFile readFirmware(Uri uri) throws Exception {
        String name = queryDisplayName(uri);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("无法打开文件");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) bos.write(buffer, 0, n);
        }
        return new FirmwareFile(name, bos.toByteArray());
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null || last.trim().isEmpty() ? "firmware.bin" : last;
    }

    private void refreshSerialStatus() {
        boolean open = isSerialOpen();
        binding.btnConnect.setText(open ? "断开串口" : "连接串口");
        binding.btnConnect.setBackgroundResource(open ? R.drawable.bg_button_success : R.drawable.bg_button_primary);
        binding.btnConnect.setTextColor(open ? 0xFF22C55E : 0xFF0F172A);
    }

    private void refreshBusy() {
        binding.btnBootPrepare.setEnabled(!busy);
        binding.btnBootPrepare.setAlpha(busy ? 0.45f : 1.0f);
        binding.btnBootStart.setVisibility(View.GONE);
        binding.btnAppCommand.setEnabled(!busy);
        binding.btnAppSend.setEnabled(!busy);
        binding.btnCancel.setEnabled(busy);
        if (!busy && binding.progressUpgrade.getProgress() == 100) {
            binding.tvProgress.setText("完成");
        }
    }

    private void appendLog(String tag, String message) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> appendLog(tag, message));
            return;
        }
        if (destroyed) return;
        String text = "[" + currentTimeText() + "] [" + tag + "]\n  "
                + (message == null ? "" : message.replace("\n", "\n  ")) + "\n\n";
        logBuffer.append(text);
        if (!activityVisible) return;
        updateLogView();
    }

    private void queueRxLog(byte[] data, int length) {
        synchronized (rxLogLock) {
            int previewRoom = RX_LOG_PREVIEW_BYTES - pendingRxPreviewBytes;
            if (previewRoom > 0) {
                int copy = Math.min(previewRoom, length);
                System.arraycopy(data, 0, rxLogPreview, pendingRxPreviewBytes, copy);
                pendingRxPreviewBytes += copy;
            }
            pendingRxBytes += length;
            if (rxLogScheduled) return;
            rxLogScheduled = true;
        }
        mainHandler.postDelayed(this::flushRxLog, RX_LOG_INTERVAL_MS);
    }

    private void flushRxLog() {
        int total;
        byte[] previewData;
        int previewLength;
        synchronized (rxLogLock) {
            total = pendingRxBytes;
            previewLength = pendingRxPreviewBytes;
            previewData = Arrays.copyOf(rxLogPreview, previewLength);
            pendingRxBytes = 0;
            pendingRxPreviewBytes = 0;
            rxLogScheduled = false;
        }
        if (total <= 0 || destroyed) return;
        String text = preview(previewData, previewLength);
        if (total > previewLength) text += " ...";
        appendLog("RX " + total + "B", text);
    }

    private void updateLogView() {
        binding.tvLog.setText(logBuffer.getText());
        binding.svLog.post(() -> binding.svLog.fullScroll(android.view.View.FOCUS_DOWN));
    }

    private String preview(byte[] data, int length) {
        int count = Math.min(length, 32);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format(Locale.US, "%02X", data[i] & 0xFF));
        }
        if (length > count) sb.append(" ...");
        return sb.toString();
    }

    private String currentTimeText() {
        synchronized (timeFormat) {
            return timeFormat.format(new Date());
        }
    }

    private void clearLog() {
        logBuffer.clear();
        binding.tvLog.setText("");
        binding.progressUpgrade.setProgress(0);
        binding.tvProgress.setText("待机");
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (activeSender != null) activeSender.cancel();
        closeSerial();
        workExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        activityVisible = false;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityVisible = true;
        if (!destroyed) {
            refreshSerialStatus();
            refreshBusy();
            updateLogView();
        }
    }

    private interface BeforeTransfer {
        void run() throws Exception;
    }

    private static final class FirmwareFile {
        final String name;
        final byte[] data;

        FirmwareFile(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }
}
