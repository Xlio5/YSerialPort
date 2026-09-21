package com.yujing.chuankou.net;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UdpController {
    public interface ReceiveListener {
        void onReceive(byte[] data, InetAddress address, int port);
    }

    public interface ErrorListener {
        void onError(Exception error);
    }

    public interface SendListener {
        void onSent(int length);

        void onError(Exception error);
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "UdpSender");
        thread.setDaemon(true);
        return thread;
    });
    private DatagramSocket socket;
    private Thread receiveThread;
    private ReceiveListener receiveListener;
    private ErrorListener errorListener;

    public void setReceiveListener(ReceiveListener receiveListener) {
        this.receiveListener = receiveListener;
    }

    public void setErrorListener(ErrorListener errorListener) {
        this.errorListener = errorListener;
    }

    public synchronized void open(int localPort) throws Exception {
        close();
        socket = new DatagramSocket(localPort);
        socket.setReuseAddress(true);
        running.set(true);
        receiveThread = new Thread(this::receiveLoop, "UdpReceiver");
        receiveThread.start();
    }

    private void receiveLoop() {
        byte[] buffer = new byte[8192];
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                DatagramSocket current = socket;
                if (current == null) break;
                current.receive(packet);
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                ReceiveListener listener = receiveListener;
                if (listener != null) {
                    listener.onReceive(data, packet.getAddress(), packet.getPort());
                }
            } catch (Exception e) {
                if (running.get() && errorListener != null) {
                    errorListener.onError(e);
                }
                break;
            }
        }
    }

    public void send(String host, int port, byte[] data) throws Exception {
        DatagramSocket current = socket;
        boolean temporary = false;
        if (current == null || current.isClosed()) {
            current = new DatagramSocket();
            temporary = true;
        }
        try {
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(host), port);
            current.send(packet);
        } finally {
            if (temporary) current.close();
        }
    }

    public void sendAsync(String host, int port, byte[] data, SendListener listener) {
        byte[] copy = Arrays.copyOf(data, data.length);
        sendExecutor.execute(() -> {
            try {
                send(host, port, copy);
                if (listener != null) listener.onSent(copy.length);
            } catch (Exception e) {
                if (listener != null) listener.onError(e);
            }
        });
    }

    public synchronized void close() {
        running.set(false);
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }
    }

    public synchronized void destroy() {
        close();
        sendExecutor.shutdownNow();
    }

    public boolean isOpen() {
        DatagramSocket current = socket;
        return current != null && !current.isClosed();
    }
}
