package com.yujing.chuankou.serial;

import java.nio.charset.StandardCharsets;

public final class SerialDataCodec {
    private SerialDataCodec() {
    }

    public static byte[] encodeAscii(String text) {
        String safe = text == null ? "" : text;
        return safe.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] parseHex(String text) {
        String compact = text == null ? "" : text.replaceAll("\\s+", "");
        if ((compact.length() & 1) != 0) {
            throw new IllegalArgumentException("HEX 位数必须为偶数");
        }
        byte[] out = new byte[compact.length() / 2];
        for (int i = 0; i < compact.length(); i += 2) {
            int hi = Character.digit(compact.charAt(i), 16);
            int lo = Character.digit(compact.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("HEX 只能包含 0-9 A-F");
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static String formatHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    public static String formatAscii(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length);
        for (byte value : bytes) {
            int c = value & 0xFF;
            if (c == '\r' || c == '\n' || c == '\t') {
                sb.append((char) c);
            } else if (c >= 32 && c <= 126) {
                sb.append((char) c);
            } else {
                sb.append('.');
            }
        }
        return sb.toString();
    }
}
