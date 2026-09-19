package com.yujing.chuankou.activity;

import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivityMainBinding;
import com.yujing.chuankou.serial.SerialDataCodec;
import com.yujing.serialport.SerialPort;
import com.yujing.serialport.SerialPortFinder;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
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

    private SerialPort serialPort;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread readThread;
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
        scanPorts();
        binding.btnScan.setOnClickListener(v -> scanPorts());
        binding.btnOpen.setOnClickListener(v -> {
            if (isOpen()) closePort();
            else openPort();
        });
        binding.btnSend.setOnClickListener(v -> sendCurrentText(true));
        binding.btnClearData.setOnClickListener(v -> binding.tvLog.setText(""));
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

    private int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(target)) return i;
        }
        return 0;
    }

    private void scanPorts() {
        String[] devices = new SerialPortFinder().getAllDevicesPath();
        if (devices.length == 0) {
            devices = new String[]{"/dev/ttyS0", "/dev/ttyS1", "/dev/ttyS2", "/dev/ttyS3", "/dev/ttyUSB0"};
            appendLog("SYS", "未扫描到串口，已给出常见路径备选");
        }
        setAdapter(binding.spDevice, devices);
    }

    private void openPort() {
        Object item = binding.spDevice.getSelectedItem();
        if (item == null) {
            toast("没有可打开的串口");
            return;
        }
        try {
            String path = item.toString();
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

    private void closePort() {
        mainHandler.removeCallbacks(timedSendTask);
        binding.cbTimedSend.setChecked(false);
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
        appendLog("SYS", "串口已关闭");
        refreshStatus();
    }

    private boolean isOpen() {
        return serialPort != null && outputStream != null;
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
            outputStream.write(data);
            outputStream.flush();
            txBytes += data.length;
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

    private int readIntervalMs() {
        try {
            return Math.max(10, Integer.parseInt(binding.etInterval.getText().toString().trim()));
        } catch (Exception e) {
            return 1000;
        }
    }

    private void appendLog(String direction, String message) {
        String line = "[" + timeFormat.format(new Date()) + "] " + direction + "  " + message + "\n";
        String old = binding.tvLog.getText().toString();
        if (old.length() > MAX_LOG_CHARS) {
            old = old.substring(old.length() - MAX_LOG_CHARS / 2);
        }
        binding.tvLog.setText(old + line);
        binding.svLog.post(() -> binding.svLog.fullScroll(android.view.View.FOCUS_DOWN));
    }

    private void refreshStatus() {
        boolean open = isOpen();
        binding.btnOpen.setText(open ? "关闭串口" : "打开串口");
        binding.btnOpen.setBackgroundResource(open ? R.drawable.bg_button_danger : R.drawable.bg_button_primary);
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
