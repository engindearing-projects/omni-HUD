package com.engindearing.omnihud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Minimal HID report descriptor parser: just enough to size the device's output reports. */
public final class HidReportDescriptor {

    /** An output report. {@code id} is 0 when the device does not use report IDs. */
    public static final class Report {
        public final int id;
        public final int bits;

        public Report(int id, int bits) {
            this.id = id;
            this.bits = bits;
        }

        /** Report body size in bytes, without the report ID byte. */
        public int bytes() {
            return (bits + 7) / 8;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "id 0x%02X %d bytes", id, bytes());
        }
    }

    private static final int TYPE_MAIN = 0;
    private static final int TYPE_GLOBAL = 1;
    private static final int TAG_OUTPUT = 0x9;
    private static final int TAG_REPORT_SIZE = 0x7;
    private static final int TAG_REPORT_ID = 0x8;
    private static final int TAG_REPORT_COUNT = 0x9;
    private static final int TAG_PUSH = 0xA;
    private static final int TAG_POP = 0xB;

    private HidReportDescriptor() {
    }

    /** Output reports declared by the descriptor, in order of first appearance. */
    public static List<Report> outputReports(byte[] desc, int length) {
        Map<Integer, Integer> bitsById = new LinkedHashMap<>();
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        int reportSize = 0;
        int reportCount = 0;
        int reportId = 0;
        int end = Math.min(length, desc.length);
        int i = 0;
        while (i < end) {
            int prefix = desc[i] & 0xFF;
            if (prefix == 0xFE) {
                // Long item: prefix, data size, long tag, data. No output-sizing meaning; skip it.
                if (i + 1 >= end) {
                    break;
                }
                i += 3 + (desc[i + 1] & 0xFF);
                continue;
            }
            int size = prefix & 0x03;
            if (size == 3) {
                size = 4;
            }
            int type = (prefix >> 2) & 0x03;
            int tag = (prefix >> 4) & 0x0F;
            if (i + 1 + size > end) {
                break;
            }
            long value = 0;
            for (int b = 0; b < size; b++) {
                value |= (long) (desc[i + 1 + b] & 0xFF) << (8 * b);
            }
            if (type == TYPE_GLOBAL) {
                switch (tag) {
                    case TAG_REPORT_SIZE:
                        reportSize = (int) value;
                        break;
                    case TAG_REPORT_ID:
                        reportId = (int) value;
                        break;
                    case TAG_REPORT_COUNT:
                        reportCount = (int) value;
                        break;
                    case TAG_PUSH:
                        stack.push(new int[] {reportSize, reportCount, reportId});
                        break;
                    case TAG_POP:
                        if (!stack.isEmpty()) {
                            int[] s = stack.pop();
                            reportSize = s[0];
                            reportCount = s[1];
                            reportId = s[2];
                        }
                        break;
                    default:
                        break;
                }
            } else if (type == TYPE_MAIN && tag == TAG_OUTPUT) {
                Integer previous = bitsById.get(reportId);
                bitsById.put(reportId, (previous == null ? 0 : previous) + reportSize * reportCount);
            }
            i += 1 + size;
        }
        List<Report> reports = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : bitsById.entrySet()) {
            reports.add(new Report(e.getKey(), e.getValue()));
        }
        return reports;
    }
}
