package com.yujing.chuankou.activity;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.yujing.chuankou.R;
import com.yujing.chuankou.base.BaseActivity;
import com.yujing.chuankou.databinding.ActivityUdpAssistantBinding;
import com.yujing.chuankou.net.UdpController;
import com.yujing.chuankou.serial.SerialDataCodec;
import com.yujing.chuankou.serial.SerialLogBuffer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UdpAssistantActivity extends BaseActivity<ActivityUdpAssistantBinding> {
    private static final int MAX_LOG_CHARS = 4_000;
    private static final long LOG_FLUSH_DELAY_MS = 500L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final UdpController udpController = new UdpController();
    private final SerialLogBuffer logBuffer = new SerialLogBuffer(MAX_LOG_CHARS);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private final Object logLock = new Object();
    private final StringBuilder pendingLog = new StringBuilder();
    private boolean logFlushPosted;
    private volatile boolean hexDisplay;
    private long txBytes;
    private long rxBytes;

    public UdpAssistantActivity() {
        super(R.layout.activity_udp_assistant);
    }

    @Override
    protected void init() {
        hexDisplay = binding.cbHexDisplay.isChecked();
        binding.cbHexDisplay.setOnCheckedChangeListener((buttonView, checked) -> hexDisplay = checked);
        udpController.setReceiveListener((data, address, port) -> {
            rxBytes += data.length;
            appendLog("RX " + data.length + "B " + address.getHostAddress() + ":" + port, formatData(data));
            mainHandler.post(this::refreshCount);
        });
        udpController.setErrorListener(error ->
                mainHandler.post(() -> appendLog("ERR", "UDP 接收失败: " + error.getMessage())));
        binding.btnUdpOpen.setOnClickListener(v -> toggleUdp());
        binding.btnSend.setOnClickListener(v -> sendUdp());
        binding.btnClear.setOnClickListener(v -> clearLog());
        refreshStatus();
        refreshCount();
    }

    private void toggleUdp() {
        if (udpController.isOpen()) {
            udpController.close();
            appendLog("SYS", "UDP 已关闭");
            refreshStatus();
            return;
        }
        try {
            int port = parsePort(binding.etLocalPort.getText().toString());
            udpController.open(port);
            appendLog("SYS", "UDP 已监听 0.0.0.0:" + port);
        } catch (Exception e) {
            toast("打开 UDP 失败: " + e.getMessage());
            appendLog("ERR", "打开 UDP 失败: " + e.getMessage());
        }
        refreshStatus();
    }

    private void sendUdp() {
        try {
            String host = binding.etTargetHost.getText().toString().trim();
            if (host.isEmpty()) throw new IllegalArgumentException("目标地址为空");
            int port = parsePort(binding.etTargetPort.getText().toString());
            String text = binding.etSend.getText().toString();
            if (text.trim().isEmpty()) throw new IllegalArgumentException("发送内容为空");
            byte[] data = binding.cbHexSend.isChecked()
                    ? SerialDataCodec.parseHex(text)
                    : SerialDataCodec.encodeAscii(text);
            String display = formatData(data);
            udpController.sendAsync(host, port, data, new UdpController.SendListener() {
                @Override
                public void onSent(int length) {
                    mainHandler.post(() -> {
                        txBytes += length;
                        appendLog("TX " + length + "B " + host + ":" + port, display);
                        refreshCount();
                    });
                }

                @Override
                public void onError(Exception error) {
                    mainHandler.post(() -> {
                        toast(error.getMessage());
                        appendLog("ERR", "发送 UDP 失败: " + error.getMessage());
                    });
                }
            });
        } catch (Exception e) {
            toast(e.getMessage());
            appendLog("ERR", "发送 UDP 失败: " + e.getMessage());
        }
    }

    private int parsePort(String value) {
        int port = Integer.parseInt(value.trim());
        if (port < 1 || port > 65535) throw new IllegalArgumentException("端口应在 1-65535");
        return port;
    }

    private String formatData(byte[] data) {
        return hexDisplay
                ? SerialDataCodec.formatHex(data)
                : SerialDataCodec.formatAscii(data);
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

    private void refreshStatus() {
        boolean open = udpController.isOpen();
        binding.btnUdpOpen.setText(open ? "关闭 UDP" : "打开 UDP");
        binding.btnUdpOpen.setBackgroundResource(open ? R.drawable.bg_button_success : R.drawable.bg_button_primary);
        binding.btnUdpOpen.setTextColor(open ? 0xFF22C55E : 0xFF0F172A);
    }

    private void refreshCount() {
        binding.tvCount.setText("S:" + txBytes + "  R:" + rxBytes);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        udpController.destroy();
        super.onDestroy();
    }
}
