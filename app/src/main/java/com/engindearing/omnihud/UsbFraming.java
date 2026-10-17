package com.engindearing.omnihud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Byte-level helpers for the USB link: CDC line setup, HID report framing and hex dumps. */
public final class UsbFraming {

    /** Report ID used by the HID data pipe in TI's MSP430 USB stack. */
    public static final int TI_DATAPIPE_REPORT_ID = 0x3F;

    public enum Mode {
        /** Payload written as is, split into chunks of at most {@code size} bytes. */
        RAW,
        /** TI MSP430 HID data pipe: [0x3F, length, data..., zero padding], {@code size} bytes per report. */
        TI_HID_DATAPIPE,
        /** HID output report with an ID: [id, data..., zero padding], {@code size} bytes per report. */
        HID_REPORT,
        /** HID output report without an ID: [data..., zero padding], {@code size} bytes per report. */
        HID_PADDED
    }

    /** How to frame writes to a HID interface. */
    public static final class HidFraming {
        public final Mode mode;
        public final int size;
        public final int reportId;

        public HidFraming(Mode mode, int size, int reportId) {
            this.mode = mode;
            this.size = size;
            this.reportId = reportId;
        }

        public String describe() {
            switch (mode) {
                case TI_HID_DATAPIPE:
                    return String.format(Locale.US, "TI HID data pipe, report 0x%02X, %d-byte reports", reportId, size);
                case HID_REPORT:
                    return String.format(Locale.US, "HID output report 0x%02X, %d-byte reports", reportId, size);
                case HID_PADDED:
                    return String.format(Locale.US, "HID output report without ID, %d-byte reports", size);
                default:
                    return "raw";
            }
        }
    }

    private UsbFraming() {
    }

    /** CDC SET_LINE_CODING payload: baud rate (little endian), 1 stop bit, no parity, 8 data bits. */
    public static byte[] cdcLineCoding(int baud) {
        return new byte[] {
            (byte) baud, (byte) (baud >> 8), (byte) (baud >> 16), (byte) (baud >> 24),
            0, 0, 8
        };
    }

    /**
     * Split a payload into the transfers to write.
     *
     * @param size     largest chunk for {@link Mode#RAW}; full report length, ID byte included, for the HID modes
     * @param reportId report ID for {@link Mode#HID_REPORT}
     */
    public static List<byte[]> frame(byte[] payload, Mode mode, int size, int reportId) {
        List<byte[]> out = new ArrayList<>();
        if (payload == null || payload.length == 0) {
            return out;
        }
        int header;
        switch (mode) {
            case TI_HID_DATAPIPE:
                header = 2;
                break;
            case HID_REPORT:
                header = 1;
                break;
            default:
                header = 0;
        }
        if (size <= header) {
            throw new IllegalArgumentException("frame size " + size + " too small for " + mode);
        }
        int perFrame = size - header;
        if (mode == Mode.TI_HID_DATAPIPE) {
            perFrame = Math.min(perFrame, 255); // the length field is one byte
        }
        for (int offset = 0; offset < payload.length; offset += perFrame) {
            int n = Math.min(perFrame, payload.length - offset);
            byte[] frame;
            switch (mode) {
                case TI_HID_DATAPIPE:
                    frame = new byte[size];
                    frame[0] = (byte) TI_DATAPIPE_REPORT_ID;
                    frame[1] = (byte) n;
                    break;
                case HID_REPORT:
                    frame = new byte[size];
                    frame[0] = (byte) reportId;
                    break;
                case HID_PADDED:
                    frame = new byte[size];
                    break;
                default:
                    frame = new byte[n];
            }
            System.arraycopy(payload, offset, frame, header, n);
            out.add(frame);
        }
        return out;
    }

    /**
     * Pick the framing for a HID interface from the output reports in its report descriptor.
     *
     * @param outputs       output reports parsed from the report descriptor, possibly empty
     * @param maxPacketSize max packet size of the interrupt OUT endpoint, used when the descriptor says nothing
     * @param vendorId      device vendor ID; TI devices default to the data pipe format
     */
    public static HidFraming chooseHidFraming(List<HidReportDescriptor.Report> outputs, int maxPacketSize, int vendorId) {
        for (HidReportDescriptor.Report r : outputs) {
            if (r.id == TI_DATAPIPE_REPORT_ID && r.bytes() > 1) {
                return new HidFraming(Mode.TI_HID_DATAPIPE, r.bytes() + 1, r.id);
            }
        }
        for (HidReportDescriptor.Report r : outputs) {
            if (r.id != 0 && r.bytes() > 0) {
                return new HidFraming(Mode.HID_REPORT, r.bytes() + 1, r.id);
            }
        }
        for (HidReportDescriptor.Report r : outputs) {
            if (r.id == 0 && r.bytes() > 0) {
                return new HidFraming(Mode.HID_PADDED, r.bytes(), 0);
            }
        }
        int size = maxPacketSize > 2 ? maxPacketSize : 64;
        if (vendorId == UsbDeviceClassifier.VID_TI_MSP430 || vendorId == UsbDeviceClassifier.VID_TI) {
            return new HidFraming(Mode.TI_HID_DATAPIPE, size, TI_DATAPIPE_REPORT_ID);
        }
        return new HidFraming(Mode.HID_PADDED, size, 0);
    }

    /** Space-separated hex, e.g. "3C 3F 78". */
    public static String hex(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder(length * 3);
        for (int i = offset; i < offset + length && i < data.length; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(String.format(Locale.US, "%02X", data[i] & 0xFF));
        }
        return sb.toString();
    }

    /** Classic 16-bytes-per-line dump with offsets and printable ASCII. */
    public static String hexDump(byte[] data, int length) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(length, data.length);
        for (int line = 0; line < n; line += 16) {
            int count = Math.min(16, n - line);
            sb.append(String.format(Locale.US, "%04X  ", line));
            for (int i = 0; i < 16; i++) {
                if (i < count) {
                    sb.append(String.format(Locale.US, "%02X ", data[line + i] & 0xFF));
                } else {
                    sb.append("   ");
                }
            }
            sb.append(' ');
            for (int i = 0; i < count; i++) {
                int b = data[line + i] & 0xFF;
                sb.append(b >= 0x20 && b < 0x7F ? (char) b : '.');
            }
            if (line + 16 < n) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
