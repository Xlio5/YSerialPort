package com.yujing.chuankou;

import com.yujing.chuankou.serial.SerialDataCodec;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SerialDataCodecTest {
    @Test
    public void parseHex_ignoresWhitespaceAndReturnsBytes() {
        assertArrayEquals(
                new byte[]{0x01, 0x23, (byte) 0xAB, (byte) 0xCD},
                SerialDataCodec.parseHex("01 23\nAB\tcd")
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseHex_rejectsOddNumberOfDigits() {
        SerialDataCodec.parseHex("0A 1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseHex_rejectsNonHexCharacters() {
        SerialDataCodec.parseHex("AA GG");
    }

    @Test
    public void encodeAscii_usesUtf8Bytes() {
        assertArrayEquals("AT+PING".getBytes(StandardCharsets.UTF_8),
                SerialDataCodec.encodeAscii("AT+PING"));
    }

    @Test
    public void formatHex_usesUppercasePairsWithSpaces() {
        assertEquals("00 0A FF", SerialDataCodec.formatHex(new byte[]{0, 10, (byte) 255}));
    }

    @Test
    public void formatAscii_replacesControlCharactersExceptLineBreaks() {
        assertEquals("OK\n.!", SerialDataCodec.formatAscii(new byte[]{'O', 'K', '\n', 0x02, '!'}));
    }
}
