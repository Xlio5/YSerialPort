package com.yujing.chuankou.mcu;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class MavlinkSerialControl {
    private static final int STX_V1 = 0xFE;
    private static final int MSG_ID_SERIAL_CONTROL = 126;
    private static final int SERIAL_CONTROL_CRC_EXTRA = 220;
    private static final int SERIAL_CONTROL_DEV_SHELL = 10;
    private static final int SERIAL_CONTROL_FLAG_RESPOND = 2;
    private static final int SERIAL_CONTROL_PAYLOAD_LEN = 79;
    private static int sequence;

    private MavlinkSerialControl() {
    }

    public static synchronized byte[] shellCommand(String command) {
        byte[] text = command.endsWith("\n")
                ? command.getBytes(StandardCharsets.US_ASCII)
                : (command + "\n").getBytes(StandardCharsets.US_ASCII);
        if (text.length > 70) {
            text = Arrays.copyOf(text, 70);
        }

        byte[] payload = new byte[SERIAL_CONTROL_PAYLOAD_LEN];
        writeUInt32(payload, 0, 0);
        writeUInt16(payload, 4, 0);
        payload[6] = (byte) SERIAL_CONTROL_DEV_SHELL;
        payload[7] = (byte) SERIAL_CONTROL_FLAG_RESPOND;
        payload[8] = (byte) text.length;
        System.arraycopy(text, 0, payload, 9, text.length);

        byte[] frame = new byte[6 + payload.length + 2];
        frame[0] = (byte) STX_V1;
        frame[1] = (byte) payload.length;
        frame[2] = (byte) (sequence++ & 0xFF);
        frame[3] = 1;
        frame[4] = 1;
        frame[5] = (byte) MSG_ID_SERIAL_CONTROL;
        System.arraycopy(payload, 0, frame, 6, payload.length);

        int crc = 0xFFFF;
        for (int i = 1; i < 6 + payload.length; i++) {
            crc = crcAccumulate(frame[i] & 0xFF, crc);
        }
        crc = crcAccumulate(SERIAL_CONTROL_CRC_EXTRA, crc);
        frame[6 + payload.length] = (byte) (crc & 0xFF);
        frame[7 + payload.length] = (byte) ((crc >> 8) & 0xFF);
        return frame;
    }

    private static void writeUInt16(byte[] payload, int offset, int value) {
        payload[offset] = (byte) (value & 0xFF);
        payload[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static void writeUInt32(byte[] payload, int offset, long value) {
        payload[offset] = (byte) (value & 0xFF);
        payload[offset + 1] = (byte) ((value >> 8) & 0xFF);
        payload[offset + 2] = (byte) ((value >> 16) & 0xFF);
        payload[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static int crcAccumulate(int data, int crc) {
        int tmp = data ^ (crc & 0xFF);
        tmp ^= (tmp << 4) & 0xFF;
        return ((crc >> 8) ^ (tmp << 8) ^ (tmp << 3) ^ (tmp >> 4)) & 0xFFFF;
    }
}
