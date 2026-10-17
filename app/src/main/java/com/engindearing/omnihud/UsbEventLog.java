package com.engindearing.omnihud;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Bounded, timestamped log of USB events, kept in memory for the USB report. */
public final class UsbEventLog {

    private final int capacity;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss'Z'", Locale.US);

    public UsbEventLog(int capacity) {
        this.capacity = Math.max(1, capacity);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public synchronized void add(String line) {
        if (lines.size() == capacity) {
            lines.removeFirst();
        }
        lines.addLast(format.format(new Date()) + "  " + line);
    }

    public synchronized List<String> snapshot() {
        return new ArrayList<>(lines);
    }
}
