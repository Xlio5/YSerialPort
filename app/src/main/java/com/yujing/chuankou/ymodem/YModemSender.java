package com.yujing.chuankou.ymodem;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class YModemSender {
    public interface ProgressListener {
        void onProgress(long sent, long total, String message);
    }

    private static final int SOH = 0x01;
    private static final int STX = 0x02;
    private static final int EOT = 0x04;
    private static final int ACK = 0x06;
    private static final int NAK = 0x15;
    private static final int CAN = 0x18;
    private static final int CRC_REQUEST = 'C';
    private static final int PACKET_SIZE = 128;
    private static final int PACKET_1K_SIZE = 1024;
    private static final long HANDSHAKE_TIMEOUT_MS = 60_000L;
    private static final long ACK_TIMEOUT_MS = 12_000L;

    private final LinkedBlockingQueue<Integer> incoming;
    private volatile boolean cancelled;

    public YModemSender(LinkedBlockingQueue<Integer> incoming) {
        this.incoming = incoming;
    }

    public void cancel() {
        cancelled = true;
    }

    public void send(OutputStream outputStream, String fileName, byte[] fileData, ProgressListener listener)
            throws IOException, InterruptedException {
        send(outputStream, fileName, fileData, false, listener);
    }

    public void send(OutputStream outputStream, String fileName, byte[] fileData, boolean allowSingleEotAck,
                     ProgressListener listener)
            throws IOException, InterruptedException {
        cancelled = false;
        incoming.clear();
        progress(listener, 0, fileData.length, "等待设备发送 C");
        waitFor(CRC_REQUEST, HANDSHAKE_TIMEOUT_MS);

        progress(listener, 0, fileData.length, "发送文件头");
        writePacket(outputStream, 0, headerBlock(fileName, fileData.length), PACKET_SIZE);
        waitFor(ACK, ACK_TIMEOUT_MS);
        waitFor(CRC_REQUEST, ACK_TIMEOUT_MS);

        int seq = 1;
        long sent = 0;
        while (sent < fileData.length) {
            checkCancelled();
            int chunk = (int) Math.min(PACKET_1K_SIZE, fileData.length - sent);
            byte[] block = new byte[PACKET_1K_SIZE];
            Arrays.fill(block, (byte) 0x1A);
            System.arraycopy(fileData, (int) sent, block, 0, chunk);
            writePacket(outputStream, seq, block, PACKET_1K_SIZE);
            waitFor(ACK, ACK_TIMEOUT_MS);
            sent += chunk;
            progress(listener, Math.min(sent, fileData.length), fileData.length,
                    "发送数据 " + Math.min(sent, fileData.length) + "/" + fileData.length);
            seq = (seq + 1) & 0xFF;
        }

        progress(listener, sent, fileData.length, "发送结束标记");
        writeByte(outputStream, EOT);
        int firstEotResponse = waitForAny(new int[]{NAK, ACK}, ACK_TIMEOUT_MS);
        if (firstEotResponse == ACK && allowSingleEotAck) {
            progress(listener, fileData.length, fileData.length, "YMODEM 完成");
            return;
        }
        if (firstEotResponse != NAK) {
            throw new IOException("设备过早 ACK EOT");
        }
        writeByte(outputStream, EOT);
        waitFor(ACK, ACK_TIMEOUT_MS);
        waitFor(CRC_REQUEST, ACK_TIMEOUT_MS);

        progress(listener, sent, fileData.length, "发送空文件头");
        writePacket(outputStream, 0, new byte[PACKET_SIZE], PACKET_SIZE);
        waitFor(ACK, ACK_TIMEOUT_MS);
        progress(listener, fileData.length, fileData.length, "YMODEM 完成");
    }

    private byte[] headerBlock(String fileName, long fileSize) {
        byte[] block = new byte[PACKET_SIZE];
        byte[] name = safeName(fileName).getBytes(StandardCharsets.US_ASCII);
        byte[] size = Long.toString(fileSize).getBytes(StandardCharsets.US_ASCII);
        int nameLength = Math.min(name.length, 80);
        System.arraycopy(name, 0, block, 0, nameLength);
        int sizeOffset = nameLength + 1;
        System.arraycopy(size, 0, block, sizeOffset, Math.min(size.length, block.length - sizeOffset));
        return block;
    }

    private String safeName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return "firmware.bin";
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void writePacket(OutputStream outputStream, int sequence, byte[] data, int size) throws IOException {
        outputStream.write(size == PACKET_1K_SIZE ? STX : SOH);
        outputStream.write(sequence & 0xFF);
        outputStream.write((~sequence) & 0xFF);
        outputStream.write(data, 0, size);
        int crc = crc16(data, size);
        outputStream.write((crc >> 8) & 0xFF);
        outputStream.write(crc & 0xFF);
        outputStream.flush();
    }

    private void writeByte(OutputStream outputStream, int value) throws IOException {
        outputStream.write(value);
        outputStream.flush();
    }

    private void waitFor(int expected, long timeoutMs) throws InterruptedException, IOException {
        waitForAny(new int[]{expected}, timeoutMs);
    }

    private int waitForAny(int[] expectedValues, long timeoutMs) throws InterruptedException, IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            checkCancelled();
            Integer value = incoming.poll(150, TimeUnit.MILLISECONDS);
            if (value == null) continue;
            int b = value & 0xFF;
            for (int expected : expectedValues) {
                if (b == expected) return b;
            }
            if (b == CAN) throw new IOException("设备取消传输");
        }
        throw new IOException("等待设备响应超时");
    }

    private void checkCancelled() throws IOException {
        if (cancelled) throw new IOException("传输已取消");
    }

    private int crc16(byte[] data, int size) {
        int crc = 0;
        for (int i = 0; i < size; i++) {
            crc = updateCrc16(crc, data[i] & 0xFF);
        }
        crc = updateCrc16(crc, 0);
        crc = updateCrc16(crc, 0);
        return crc & 0xFFFF;
    }

    private int updateCrc16(int crcIn, int value) {
        int crc = crcIn;
        int in = value | 0x100;
        do {
            crc <<= 1;
            in <<= 1;
            if ((in & 0x100) != 0) crc++;
            if ((crc & 0x10000) != 0) crc ^= 0x1021;
        } while ((in & 0x10000) == 0);
        return crc & 0xFFFF;
    }

    private void progress(ProgressListener listener, long sent, long total, String message) {
        if (listener != null) listener.onProgress(sent, total, message);
    }
}
