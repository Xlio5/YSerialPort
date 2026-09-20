package com.yujing.chuankou;

import com.yujing.chuankou.serial.SerialLogBuffer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SerialLogBufferTest {
    @Test
    public void append_keepsIncrementalTextBeforeLimit() {
        SerialLogBuffer buffer = new SerialLogBuffer(20);

        SerialLogBuffer.Update first = buffer.append("abc\n");
        SerialLogBuffer.Update second = buffer.append("def\n");

        assertFalse(first.requiresFullRefresh());
        assertFalse(second.requiresFullRefresh());
        assertEquals("def\n", second.getAppendedText());
        assertEquals("abc\ndef\n", buffer.getText());
    }

    @Test
    public void append_trimsOldTextWhenLimitExceeded() {
        SerialLogBuffer buffer = new SerialLogBuffer(10);

        buffer.append("123456\n");
        SerialLogBuffer.Update update = buffer.append("abcdef\n");

        assertTrue(update.requiresFullRefresh());
        assertEquals("cdef\n", update.getFullText());
        assertEquals("cdef\n", buffer.getText());
    }

    @Test
    public void clear_emptiesBuffer() {
        SerialLogBuffer buffer = new SerialLogBuffer(10);

        buffer.append("abc");
        buffer.clear();

        assertEquals("", buffer.getText());
    }
}
