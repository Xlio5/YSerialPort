package com.yujing.chuankou.activity;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.hehongdan.ch34xuartdriver.CH34xUARTDriver;
import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivitySerialUdpBridgeBinding;
import com.yujing.chuankou.net.UdpController;
import com.yujing.chuankou.serial.SerialDataCodec;
import com.yujing.chuankou.serial.SerialLogBuffer;
import com.yujing.chuankou.serial.SerialPortOption;
import com.yujing.serialport.SerialPort;
import com.yujing.serialport.SerialPortFinder;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class SerialUdpBridgeActivity extends BaseActivity<ActivitySerialUdpBridgeBinding> {
    private static final String[] BAUD_RATES = {"9600", "19200", "38400", "57600", "115200", "230400", "460800", "921600"};
    private static final String[] DATA_BITS = {"5", "6", "7", "8"};
    private static final String[] PARITY = {"None", "Odd", "Even"};
    private static final String[] STOP_BITS = {"1", "2"};
    private static final int MAX_LOG_CHARS = 4_000;
    private static final long LOG_FLUSH_DELAY_MS = 500L;
    private static final int MAX_DISPLAY_BYTES = 64;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean reading = new AtomicBoolean(false);
    private final UdpController udpController = new UdpController();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private final SerialLogBuffer logBuffer = new SerialLogBuffer(MAX_LOG_CHARS);
    private final Object logLock = new Object();
    private final StringBuilder pendingLog = new StringBuilder();

    private SerialPort serialPort;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread readThread;
    private CH34xUARTDriver ch34xDriver;
    private UsbDevice ch34xDevice;
    private SerialPortOption pendingCh34xOption;
    private boolean closingPort;
    private boolean logFlushPosted;
    private volatile boolean serialToUdp;
    private volatile boolean udpToSerial;
    private volatile boolean hexDisplay;
    private volatile String targetHost = "";
    private volatile int targetPort = 8888;
    private long serialRxBytes;
    private long serialTxBytes;
    private long udpRxBytes;
    private long udpTxBytes;

    public SerialUdpBridgeActivity() {
        super(R.layout.activity_serial_udp_bridge);
    }

    @Override
    protected void init() {
        setupSpinners();
        setupCh34xDriver();
        scanPorts();
        serialToUdp = binding.cbSerialToUdp.isChecked();
        udpToSerial = binding.cbUdpToSerial.isChecked();
        hexDisplay = binding.cbHexDisplay.isChecked();
        updateTargetEndpoint();
        binding.cbSerialToUdp.setOnCheckedChangeListener((buttonView, checked) -> serialToUdp = checked);
        binding.cbUdpToSerial.setOnCheckedChangeListener((buttonView, checked) -> udpToSerial = checked);
        binding.cbHexDisplay.setOnCheckedChangeListener((buttonView, checked) -> hexDisplay = checked);
        TextWatcher targetWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateTargetEndpoint();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        binding.etTargetHost.addTextChangedListener(targetWatcher);
        binding.etTargetPort.addTextChangedListener(targetWatcher);
        udpController.setReceiveListener((data, address, port) -> {
            udpRxBytes += data.length;
            appendLog("UDP RX " + data.length + "B " + address.getHostAddress() + ":" + port, formatPreview(data));
            if (udpToSerial) {
                try {
                    writeSerial(data);
                    serialTxBytes += data.length;
                    appendLog("UDP -> 串口", data.length + "B");
                } catch (Exception e) {
                    appendLog("ERR", "UDP 写入串口失败: " + e.getMessage());
                }
            }
            mainHandler.post(this::refreshCount);
        });
        udpController.setErrorListener(error ->
                mainHandler.post(() -> appendLog("ERR", "UDP 接收失败: " + error.getMessage())));
        binding.btnScan.setOnClickListener(v -> scanPorts());
        binding.btnSerialOpen.setOnClickListener(v -> {
            if (isSerialOpen()) closeSerial();
            else openSerial();
        });
        binding.btnUdpOpen.setOnClickListener(v -> toggleUdp());
        binding.btnClear.setOnClickListener(v -> clearLog());
        refreshSerialStatus();
        refreshUdpStatus();
        refreshCount();
    }

    private void setupSpinners() {
        setAdapter(binding.spBaud, BAUD_RATES);
        setAdapter(binding.spDataBits, DATA_BITS);
        setAdapter(binding.spParity, PARITY);
        setAdapter(binding.spStopBits, STOP_BITS);
        binding.spBaud.setSelection(indexOf(BAUD_RATES, "115200"));
        binding.spDataBits.setSelection(indexOf(DATA_BITS, "8"));
    }

    private void setAdapter(android.widget.Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_dark, values);
        adapter.setDropDownViewResource(R.layout.item_spinner_dark_dropdown);
        spinner.setAdapter(adapter);
    }

    private void setPortAdapter(List<SerialPortOption> values) {
        ArrayAdapter<SerialPortOption> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_dark, values);
        adapter.setDropDownViewResource(R.layout.item_spinner_dark_dropdown);
        binding.spDevice.setAdapter(adapter);
    }

    private int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(target)) return i;
        }
        return 0;
    }

    private void scanPorts() {
        List<SerialPortOption> options = new ArrayList<>();
        int ch34xCount = 0;
        for (UsbDevice device : listCh34xDevices()) {
            options.add(SerialPortOption.ch34x(device.getVendorId(), device.getProductId(), device.getDeviceName()));
            ch34xCount++;
        }
        String[] devices = new SerialPortFinder().getAllDevicesPath();
        if (devices.length == 0) {
            devices = new String[]{"/dev/ttyS0", "/dev/ttyS1", "/dev/ttyS2", "/dev/ttyS3", "/dev/ttyUSB0"};
            appendLog("SYS", "未扫描到串口，已给出常见路径备选");
        }
        for (String device : devices) {
            options.add(SerialPortOption.nativePort(device));
        }
        if (ch34xCount > 0) appendLog("SYS", "发现 " + ch34xCount + " 个 CH34x USB免驱设备");
        setPortAdapter(options);
    }

    private void openSerial() {
        Object item = binding.spDevice.getSelectedItem();
        if (item == null) {
            toast("没有可打开的串口");
            return;
        }
        SerialPortOption option = item instanceof SerialPortOption
                ? (SerialPortOption) item
                : SerialPortOption.nativePort(item.toString());
        if (option.isCh34x()) openCh34xPort(option);
        else openNativePort(option.getNativePath());
    }

    private void openNativePort(String path) {
        try {
            int baud = Integer.parseInt(binding.spBaud.getSelectedItem().toString());
            int dataBits = Integer.parseInt(binding.spDataBits.getSelectedItem().toString());
            int parity = binding.spParity.getSelectedItemPosition();
            int stopBits = Integer.parseInt(binding.spStopBits.getSelectedItem().toString());
            serialPort = SerialPort.newBuilder(new File(path), baud)
                    .dataBits(dataBits)
                    .parity(parity)
                    .stopBits(stopBits)
                    .build();
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();
            startReader();
            appendLog("SYS", "串口已打开 " + path);
        } catch (Exception e) {
            closeSerial();
            appendLog("ERR", "打开串口失败: " + e.getMessage());
            toast("打开串口失败");
        }
        refreshSerialStatus();
    }

    private void openCh34xPort(SerialPortOption option) {
        setupCh34xDriver();
        UsbDevice device = findCh34xDevice(option);
        if (device == null) {
            appendLog("ERR", "CH34x 设备已不存在");
            scanPorts();
            return;
        }
        if (!ch34xDriver.getUsbManager().hasPermission(device)) {
            pendingCh34xOption = option;
            ch34xDriver.openDevice(device);
            appendLog("SYS", "已请求 USB 权限，授权后会自动继续打开");
            return;
        }
        try {
            pendingCh34xOption = null;
            int baud = Integer.parseInt(binding.spBaud.getSelectedItem().toString());
            byte dataBits = (byte) Integer.parseInt(binding.spDataBits.getSelectedItem().toString());
            byte parity = (byte) binding.spParity.getSelectedItemPosition();
            byte stopBits = (byte) Integer.parseInt(binding.spStopBits.getSelectedItem().toString());
            ch34xDriver.setReadListener(this::onSerialReceive);
            ch34xDriver.setCloseListener(() -> {
                if (closingPort) return;
                mainHandler.post(() -> {
                    ch34xDevice = null;
                    appendLog("SYS", "CH34x USB免驱设备已断开");
                    refreshSerialStatus();
                });
            });
            ch34xDriver.openDevice(device);
            if (ch34xDriver.getUsbDeviceConnection() == null) throw new IllegalStateException("USB 设备连接失败");
            if (!ch34xDriver.uartInit()) throw new IllegalStateException("CH34x 初始化失败");
            if (!ch34xDriver.setConfig(baud, dataBits, stopBits, parity, (byte) 0)) {
                throw new IllegalStateException("CH34x 串口参数配置失败");
            }
            ch34xDevice = device;
            appendLog("SYS", "CH34x 已打开 " + option);
        } catch (Exception e) {
            closeCh34xPort();
            appendLog("ERR", "打开 CH34x 失败: " + e.getMessage());
            toast("打开 CH34x 失败");
        }
        refreshSerialStatus();
    }

    private void startReader() {
        reading.set(true);
        readThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            while (reading.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    int available = inputStream == null ? 0 : inputStream.available();
                    if (available <= 0) {
                        Thread.sleep(8);
                        continue;
                    }
                    int n = inputStream.read(buffer, 0, Math.min(buffer.length, available));
                    if (n > 0) onSerialReceive(Arrays.copyOf(buffer, n));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    mainHandler.post(() -> appendLog("ERR", "读取串口失败: " + e.getMessage()));
                    break;
                }
            }
        }, "SerialUdpBridgeReader");
        readThread.start();
    }

    private void onSerialReceive(byte[] data) {
        if (data == null || data.length == 0) return;
        serialRxBytes += data.length;
        appendLog("串口 RX " + data.length + "B", formatPreview(data));
        if (serialToUdp) {
            try {
                String host = targetHost;
                int port = targetPort;
                if (host.isEmpty()) throw new IllegalArgumentException("目标地址为空");
                if (port < 1 || port > 65535) throw new IllegalArgumentException("目标端口无效");
                udpController.sendAsync(host, port, data, new UdpController.SendListener() {
                    @Override
                    public void onSent(int length) {
                        udpTxBytes += length;
                        appendLog("串口 -> UDP", length + "B " + host + ":" + port);
                        mainHandler.post(SerialUdpBridgeActivity.this::refreshCount);
                    }

                    @Override
                    public void onError(Exception error) {
                        appendLog("ERR", "串口转 UDP 失败: " + error.getMessage());
                    }
                });
            } catch (Exception e) {
                appendLog("ERR", "串口转 UDP 失败: " + e.getMessage());
            }
        }
        mainHandler.post(this::refreshCount);
    }

    private void writeSerial(byte[] data) throws Exception {
        if (ch34xDevice != null && ch34xDriver != null && ch34xDriver.isConnected()) {
            int written = ch34xDriver.writeData(data, data.length);
            if (written < 0) throw new IllegalStateException("写入返回 " + written);
            return;
        }
        if (outputStream == null) throw new IllegalStateException("串口未打开");
        outputStream.write(data);
        outputStream.flush();
    }

    private void toggleUdp() {
        if (udpController.isOpen()) {
            udpController.close();
            appendLog("SYS", "UDP 已关闭");
            refreshUdpStatus();
            return;
        }
        try {
            int port = parsePort(binding.etLocalPort.getText().toString());
            udpController.open(port);
            appendLog("SYS", "UDP 已监听 0.0.0.0:" + port);
        } catch (Exception e) {
            appendLog("ERR", "打开 UDP 失败: " + e.getMessage());
            toast("打开 UDP 失败");
        }
        refreshUdpStatus();
    }

    private int parsePort(String value) {
        int port = Integer.parseInt(value.trim());
        if (port < 1 || port > 65535) throw new IllegalArgumentException("端口应在 1-65535");
        return port;
    }

    private String formatPreview(byte[] data) {
        byte[] preview = data.length > MAX_DISPLAY_BYTES ? Arrays.copyOf(data, MAX_DISPLAY_BYTES) : data;
        String text = hexDisplay
                ? SerialDataCodec.formatHex(preview)
                : SerialDataCodec.formatAscii(preview);
        if (preview.length < data.length) text += " ... (+" + (data.length - preview.length) + " bytes)";
        return text;
    }

    private void setupCh34xDriver() {
        if (ch34xDriver != null) return;
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        ch34xDriver = new CH34xUARTDriver(usbManager, this, getPackageName() + ".USB_PERMISSION");
        ch34xDriver.setPermissionListener(() -> mainHandler.post(() -> {
            if (pendingCh34xOption != null && !isSerialOpen()) {
                appendLog("SYS", "USB 权限已通过，继续打开 CH34x");
                openCh34xPort(pendingCh34xOption);
            }
        }));
    }

    private void updateTargetEndpoint() {
        targetHost = binding.etTargetHost.getText().toString().trim();
        try {
            targetPort = parsePort(binding.etTargetPort.getText().toString());
        } catch (Exception ignored) {
            targetPort = 0;
        }
    }

    private List<UsbDevice> listCh34xDevices() {
        setupCh34xDriver();
        try {
            return ch34xDriver.enumerateDeviceList();
        } catch (Exception e) {
            appendLog("ERR", "扫描 CH34x 失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private UsbDevice findCh34xDevice(SerialPortOption option) {
        for (UsbDevice device : listCh34xDevices()) {
            if (option.matchesUsbDevice(device.getVendorId(), device.getProductId(), device.getDeviceName())) {
                return device;
            }
        }
        return null;
    }

    private boolean isSerialOpen() {
        return (serialPort != null && outputStream != null)
                || (ch34xDevice != null && ch34xDriver != null && ch34xDriver.isConnected());
    }

    private void closeSerial() {
        reading.set(false);
        if (readThread != null) readThread.interrupt();
        readThread = null;
        try {
            if (serialPort != null) serialPort.tryClose();
        } catch (Exception ignored) {
        }
        serialPort = null;
        inputStream = null;
        outputStream = null;
        closeCh34xPort();
        appendLog("SYS", "串口已关闭");
        refreshSerialStatus();
    }

    private void closeCh34xPort() {
        if (ch34xDriver == null) return;
        closingPort = true;
        try {
            ch34xDriver.closeDevice();
        } catch (Exception ignored) {
        } finally {
            closingPort = false;
            ch34xDevice = null;
        }
    }

    private void appendLog(String direction, String message) {
        String safeMessage = message == null ? "" : message;
        String line = "[" + currentTimeText() + "]  [" + direction + "]\n"
                + "  " + safeMessage.replace("\n", "\n  ") + "\n\n";
        synchronized (logLock) {
            pendingLog.append(line);
            if (logFlushPosted) return;
            logFlushPosted = true;
        }
        mainHandler.postDelayed(this::flushLog, LOG_FLUSH_DELAY_MS);
    }

    private String currentTimeText() {
        synchronized (timeFormat) {
            return timeFormat.format(new Date());
        }
    }

    private void flushLog() {
        String text;
        synchronized (logLock) {
            text = pendingLog.toString();
            pendingLog.setLength(0);
            logFlushPosted = false;
        }
        if (text.isEmpty()) return;
        logBuffer.append(text);
        binding.tvLog.setText(logBuffer.getText());
        binding.svLog.post(() -> binding.svLog.fullScroll(android.view.View.FOCUS_DOWN));
    }

    private void clearLog() {
        synchronized (logLock) {
            pendingLog.setLength(0);
            logFlushPosted = false;
        }
        logBuffer.clear();
        binding.tvLog.setText("");
    }

    private void refreshSerialStatus() {
        boolean open = isSerialOpen();
        binding.btnSerialOpen.setText(open ? "关闭串口" : "打开串口");
        binding.btnSerialOpen.setBackgroundResource(open ? R.drawable.bg_button_success : R.drawable.bg_button_primary);
        binding.btnSerialOpen.setTextColor(open ? 0xFF22C55E : 0xFF0F172A);
    }

    private void refreshUdpStatus() {
        boolean open = udpController.isOpen();
        binding.btnUdpOpen.setText(open ? "关闭 UDP" : "打开 UDP");
        binding.btnUdpOpen.setBackgroundResource(open ? R.drawable.bg_button_success : R.drawable.bg_button_primary);
        binding.btnUdpOpen.setTextColor(open ? 0xFF22C55E : 0xFF0F172A);
    }

    private void refreshCount() {
        binding.tvCount.setText("SER R:" + serialRxBytes + " S:" + serialTxBytes
                + "  UDP R:" + udpRxBytes + " S:" + udpTxBytes);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        udpController.destroy();
        closeSerial();
        super.onDestroy();
    }
}
