package com.yujing.chuankou.serial;

import java.util.Locale;

public final class SerialPortOption {
    public enum Kind {
        NATIVE,
        CH34X
    }

    private final Kind kind;
    private final String nativePath;
    private final int vendorId;
    private final int productId;
    private final String usbDeviceName;

    private SerialPortOption(Kind kind, String nativePath, int vendorId, int productId, String usbDeviceName) {
        this.kind = kind;
        this.nativePath = nativePath;
        this.vendorId = vendorId;
        this.productId = productId;
        this.usbDeviceName = usbDeviceName;
    }

    public static SerialPortOption nativePort(String path) {
        return new SerialPortOption(Kind.NATIVE, path, 0, 0, "");
    }

    public static SerialPortOption ch34x(int vendorId, int productId, String usbDeviceName) {
        return new SerialPortOption(Kind.CH34X, "", vendorId, productId, usbDeviceName);
    }

    public boolean isCh34x() {
        return kind == Kind.CH34X;
    }

    public String getNativePath() {
        return nativePath;
    }

    public int getVendorId() {
        return vendorId;
    }

    public int getProductId() {
        return productId;
    }

    public String getUsbDeviceName() {
        return usbDeviceName;
    }

    public boolean matchesUsbDevice(int vendorId, int productId, String usbDeviceName) {
        return isCh34x()
                && this.vendorId == vendorId
                && this.productId == productId
                && safe(this.usbDeviceName).equals(safe(usbDeviceName));
    }

    @Override
    public String toString() {
        if (isCh34x()) {
            return String.format(Locale.US, "[USB免驱] %s %04X:%04X",
                    ch34xName(vendorId, productId), vendorId, productId);
        }
        return "[系统串口] " + safe(nativePath);
    }

    public static String ch34xName(int vendorId, int productId) {
        String id = String.format(Locale.US, "%04x:%04x", vendorId, productId);
        switch (id) {
            case "1a86:7523":
                return "CH340G";
            case "1a86:5523":
                return "CH340";
            case "1a86:5512":
                return "CH341";
            case "1a86:55e3":
                return "CH343";
            default:
                return "CH34x";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
