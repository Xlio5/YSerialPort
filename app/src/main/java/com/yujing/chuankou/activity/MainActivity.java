package com.yujing.chuankou.activity;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.hehongdan.ch34xuartdriver.CH34xUARTDriver;
import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivityMainBinding;
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

public class MainActivity extends BaseActivity<ActivityMainBinding> {
    private static final String[] BAUD_RATES = {
            "9600", "19200", "38400", "57600", "115200", "230400", "460800", "921600"
    };
    private static final String[] DATA_BITS = {"5", "6", "7", "8"};
    private static final String[] PARITY = {"None", "Odd", "Even"};
    private static final String[] STOP_BITS = {"1", "2"};
    private static final int MAX_LOG_CHARS = 120_000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean reading = new AtomicBoolean(false);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private final SerialLogBuffer logBuffer = new SerialLogBuffer(MAX_LOG_CHARS);

    private SerialPort serialPort;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread readThread;
    private CH34xUARTDriver ch34xDriver;
    private UsbDevice ch34xDevice;
    private boolean closingPort;
    private long txBytes;
    private long rxBytes;

    private final Runnable timedSendTask = new Runnable() {
        @Override
        public void run() {
            if (!binding.cbTimedSend.isChecked()) return;
            sendCurrentText(false);
            mainHandler.postDelayed(this, readIntervalMs());
        }
    };

    public MainActivity() {
        super(R.layout.activity_main);
    }

    @Override
    protected void init() {
        setupSpinners();
        setupCh34xDriver();
        scanPorts();
        binding.btnScan.setOnClickListener(v -> scanPorts());
        binding.btnOpen.setOnClickListener(v -> {
            if (isOpen()) closePort();
            else openPort();
        });
        binding.btnSend.setOnClickListener(v -> sendCurrentText(true));
        binding.btnClearData.setOnClickListener(v -> clearLog());
        binding.btnClearCount.setOnClickListener(v -> {
            txBytes = 0;
            rxBytes = 0;
            refreshCount();
        });
        binding.cbTimedSend.setOnCheckedChangeListener((buttonView, checked) -> {
            mainHandler.removeCallbacks(timedSendTask);
            if (checked) mainHandler.postDelayed(timedSendTask, readIntervalMs());
        });
        refreshStatus();
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.item_spinner_dark, values);
        adapter.setDropDownViewResource(R.layout.item_spinner_dark_dropdown);
        spinner.setAdapter(adapter);
    }

    private void setPortAdapter(List<SerialPortOption> values) {
        ArrayAdapter<SerialPortOption> adapter = new ArrayAdapter<>(this,
                R.layout.item_spinner_dark, values);
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
        String[] devices = new SerialPortFinder().getAllDevicesPath();
        if (devices.length == 0) {
            devices = new String[]{"/dev/ttyS0", "/dev/ttyS1", "/dev/ttyS2", "/dev/ttyS3", "/dev/ttyUSB0"};
            appendLog("SYS", "未扫描到串口，已给出常见路径备选");
        }
        for (String device : devices) {
            options.add(SerialPortOption.nativePort(device));
        }
        int ch34xCount = 0;
        for (UsbDevice device : listCh34xDevices()) {
            options.add(SerialPortOption.ch34x(device.getVendorId(), device.getProductId(), device.getDeviceName()));
            ch34xCount++;
        }
        if (ch34xCount > 0) {
            appendLog("SYS", "发现 " + ch34xCount + " 个 CH34x USB免驱设备");
        }
        setPortAdapter(options);
    }

    private void openPort() {
        Object item = binding.spDevice.getSelectedItem();
        if (item == null) {
            toast("没有可打开的串口");
            return;
        }
        SerialPortOption option = item instanceof SerialPortOption
                ? (SerialPortOption) item
                : SerialPortOption.nativePort(item.toString());
        if (option.isCh34x()) {
            openCh34xPort(option);
        } else {
            openNativePort(option.getNativePath());
        }
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
            appendLog("SYS", "已打开 " + path + " @ " + baud + "," + dataBits + "," + PARITY[parity] + "," + stopBits);
        } catch (Exception e) {
            closePort();
            toast("打开失败: " + e.getMessage());
            appendLog("ERR", "打开串口失败: " + e);
        }
        refreshStatus();
    }

    private void openCh34xPort(SerialPortOption option) {
        setupCh34xDriver();
        if (!ch34xDriver.usbFeatureSupported()) {
            toast("当前设备不支持 USB Host");
            appendLog("ERR", "当前设备不支持 USB Host，无法使用 CH34x 免驱串口");
            return;
        }
        UsbDevice device = findCh34xDevice(option);
        if (device == null) {
            toast("未找到该 CH34x 设备");
            appendLog("ERR", "CH34x 设备已不存在，请重新扫描");
            scanPorts();
            return;
        }
        if (!ch34xDriver.getUsbManager().hasPermission(device)) {
            ch34xDriver.openDevice(device);
            toast("请授权 USB 设备后再次打开");
            appendLog("SYS", "已请求 USB 权限，授权后再次点击打开串口");
            refreshStatus();
            return;
        }
        try {
            int baud = Integer.parseInt(binding.spBaud.getSelectedItem().toString());
            byte dataBits = (byte) Integer.parseInt(binding.spDataBits.getSelectedItem().toString());
            byte parity = (byte) binding.spParity.getSelectedItemPosition();
            byte stopBits = (byte) Integer.parseInt(binding.spStopBits.getSelectedItem().toString());
            ch34xDriver.setReadListener(bytes -> mainHandler.post(() -> onReceive(bytes)));
            ch34xDriver.setCloseListener(() -> {
                if (closingPort) return;
                mainHandler.post(() -> {
                    ch34xDevice = null;
                    appendLog("SYS", "CH34x USB免驱设备已断开");
                    refreshStatus();
                });
            });
            ch34xDriver.openDevice(device);
            if (ch34xDriver.getUsbDeviceConnection() == null) {
                throw new IllegalStateException("USB 设备连接失败");
            }
            if (!ch34xDriver.uartInit()) {
                throw new IllegalStateException("CH34x 初始化失败");
            }
            if (!ch34xDriver.setConfig(baud, dataBits, stopBits, parity, (byte) 0)) {
                throw new IllegalStateException("CH34x 串口参数配置失败");
            }
            ch34xDevice = device;
            appendLog("SYS", "已打开 " + option + " @ " + baud + "," + dataBits + "," + PARITY[parity] + "," + stopBits);
        } catch (Exception e) {
            closeCh34xPort();
            toast("打开失败: " + e.getMessage());
            appendLog("ERR", "打开 CH34x 免驱串口失败: " + e);
        }
        refreshStatus();
    }

    private void closePort() {
        mainHandler.removeCallbacks(timedSendTask);
        binding.cbTimedSend.setChecked(false);
        reading.set(false);
        if (readThread != null) readThread.interrupt();
        readThread = null;
        closeNativePort();
        closeCh34xPort();
        appendLog("SYS", "串口已关闭");
        refreshStatus();
    }

    private void closeNativePort() {
        try {
            if (serialPort != null) serialPort.tryClose();
        } catch (Exception ignored) {
        }
        serialPort = null;
        inputStream = null;
        outputStream = null;
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

    private boolean isOpen() {
        return (serialPort != null && outputStream != null)
                || (ch34xDevice != null && ch34xDriver != null && ch34xDriver.isConnected());
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
                    if (n > 0) {
                        byte[] data = Arrays.copyOf(buffer, n);
                        mainHandler.post(() -> onReceive(data));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        appendLog("ERR", "读取失败: " + e.getMessage());
                        closePort();
                    });
                    break;
                }
            }
        }, "SerialPortReader");
        readThread.start();
    }

    private void onReceive(byte[] data) {
        rxBytes += data.length;
        appendLog("RX", binding.cbHexDisplay.isChecked()
                ? SerialDataCodec.formatHex(data)
                : SerialDataCodec.formatAscii(data));
        refreshCount();
    }

    private void sendCurrentText(boolean reportEmpty) {
        if (!isOpen()) {
            if (reportEmpty) toast("请先打开串口");
            return;
        }
        String text = binding.etSend.getText().toString();
        if (text.trim().isEmpty()) {
            if (reportEmpty) toast("发送内容为空");
            return;
        }
        try {
            byte[] data = binding.cbHexSend.isChecked()
                    ? SerialDataCodec.parseHex(text)
                    : SerialDataCodec.encodeAscii(text);
            int written = writeData(data);
            if (written < 0) {
                throw new IllegalStateException("写入返回 " + written);
            }
            txBytes += written;
            appendLog("TX", binding.cbHexDisplay.isChecked()
                    ? SerialDataCodec.formatHex(data)
                    : SerialDataCodec.formatAscii(data));
            refreshCount();
        } catch (IllegalArgumentException e) {
            toast(e.getMessage());
        } catch (Exception e) {
            appendLog("ERR", "发送失败: " + e.getMessage());
            toast("发送失败");
        }
    }

    private int writeData(byte[] data) throws Exception {
        if (ch34xDevice != null && ch34xDriver != null && ch34xDriver.isConnected()) {
            return ch34xDriver.writeData(data, data.length);
        }
        outputStream.write(data);
        outputStream.flush();
        return data.length;
    }

    private void setupCh34xDriver() {
        if (ch34xDriver != null) return;
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        ch34xDriver = new CH34xUARTDriver(usbManager, this, getPackageName() + ".USB_PERMISSION");
    }

    private List<UsbDevice> listCh34xDevices() {
        setupCh34xDriver();
        try {
            return ch34xDriver.enumerateDeviceList();
        } catch (Exception e) {
            appendLog("ERR", "扫描 CH34x USB免驱设备失败: " + e.getMessage());
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

    private int readIntervalMs() {
        try {
            return Math.max(10, Integer.parseInt(binding.etInterval.getText().toString().trim()));
        } catch (Exception e) {
            return 1000;
        }
    }

    private void appendLog(String direction, String message) {
        String line = "[" + timeFormat.format(new Date()) + "] " + direction + "  " + message + "\n";
        SerialLogBuffer.Update update = logBuffer.append(line);
        if (update.requiresFullRefresh()) {
            binding.tvLog.setText(update.getFullText());
        } else {
            binding.tvLog.append(update.getAppendedText());
        }
        binding.svLog.post(() -> binding.svLog.fullScroll(android.view.View.FOCUS_DOWN));
    }

    private void clearLog() {
        logBuffer.clear();
        binding.tvLog.setText("");
    }

    private void refreshStatus() {
        boolean open = isOpen();
        binding.btnOpen.setText(open ? "关闭串口" : "打开串口");
        binding.btnOpen.setBackgroundResource(open ? R.drawable.bg_button_success : R.drawable.bg_button_primary);
        binding.btnOpen.setTextColor(open ? 0xFF22C55E : 0xFF0F172A);
    }

    private void refreshCount() {
        binding.tvCount.setText("S:" + txBytes + "  R:" + rxBytes);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        closePort();
        super.onDestroy();
    }
}
