package com.yujing.chuankou;

import com.yujing.chuankou.serial.SerialPortOption;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SerialPortOptionTest {
    @Test
    public void ch34xDisplayLabel_marksUsbDriverlessDevice() {
        SerialPortOption option = SerialPortOption.ch34x(0x1A86, 0x7523, "/dev/bus/usb/001/002");

        assertEquals("[USB免驱] CH340G 1A86:7523", option.toString());
        assertTrue(option.isCh34x());
    }

    @Test
    public void nativeDisplayLabel_keepsOriginalPathVisible() {
        SerialPortOption option = SerialPortOption.nativePort("/dev/ttyS3");

        assertEquals("[系统串口] /dev/ttyS3", option.toString());
    }
}
