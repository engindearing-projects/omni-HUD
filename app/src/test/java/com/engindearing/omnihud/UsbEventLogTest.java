package com.engindearing.omnihud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class UsbEventLogTest {

    @Test
    public void keepsOnlyTheNewestLines() {
        UsbEventLog log = new UsbEventLog(2);
        log.add("one");
        log.add("two");
        log.add("three");
        List<String> lines = log.snapshot();
        assertEquals(2, lines.size());
        assertTrue(lines.get(0), lines.get(0).endsWith("two"));
        assertTrue(lines.get(1), lines.get(1).endsWith("three"));
    }
}
