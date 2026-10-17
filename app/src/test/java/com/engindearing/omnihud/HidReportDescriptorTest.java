package com.engindearing.omnihud;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class HidReportDescriptorTest {

    /** The report descriptor TI's MSP430 USB stack generates for a HID data pipe with 64-byte packets. */
    static final byte[] TI_DATAPIPE = bytes(
            0x06, 0x00, 0xFF,   // Usage Page (vendor defined)
            0x09, 0x01,         // Usage
            0xA1, 0x01,         // Collection (application)
            0x85, 0x3F,         //   Report ID 0x3F
            0x95, 0x3F,         //   Report Count 63
            0x75, 0x08,         //   Report Size 8
            0x25, 0x01,         //   Logical Maximum
            0x15, 0x01,         //   Logical Minimum
            0x09, 0x01,         //   Usage
            0x81, 0x02,         //   Input
            0x85, 0x3F,         //   Report ID 0x3F
            0x95, 0x3F,         //   Report Count 63
            0x75, 0x08,         //   Report Size 8
            0x25, 0x01,
            0x15, 0x01,
            0x09, 0x01,
            0x91, 0x02,         //   Output
            0xC0);              // End Collection

    /** Boot keyboard LED output: five 1-bit LEDs plus three bits of padding, no report IDs. */
    static final byte[] KEYBOARD_LEDS = bytes(
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01,
            0x05, 0x08, 0x19, 0x01, 0x29, 0x05,
            0x95, 0x05, 0x75, 0x01, 0x91, 0x02,
            0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
            0xC0);

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    @Test
    public void tiDatapipeHasOne63ByteOutputReport() {
        List<HidReportDescriptor.Report> out = HidReportDescriptor.outputReports(TI_DATAPIPE, TI_DATAPIPE.length);
        assertEquals(1, out.size());
        assertEquals(0x3F, out.get(0).id);
        assertEquals(63, out.get(0).bytes());
        assertEquals(UsbFraming.Mode.TI_HID_DATAPIPE, UsbFraming.chooseHidFraming(out, 64, 0x2047).mode);
        assertEquals(64, UsbFraming.chooseHidFraming(out, 64, 0x2047).size);
    }

    @Test
    public void reportWithoutIdsSumsItsOutputFields() {
        List<HidReportDescriptor.Report> out = HidReportDescriptor.outputReports(KEYBOARD_LEDS, KEYBOARD_LEDS.length);
        assertEquals(1, out.size());
        assertEquals(0, out.get(0).id);
        assertEquals(1, out.get(0).bytes());
    }

    @Test
    public void pushAndPopRestoreGlobalState() {
        byte[] d = bytes(
                0x75, 0x08, 0x95, 0x04, // size 8, count 4
                0xA4,                   // push
                0x75, 0x01, 0x95, 0x08, // size 1, count 8
                0x91, 0x02,             // output: 8 bits
                0xB4,                   // pop back to size 8, count 4
                0x91, 0x02);            // output: 32 bits
        List<HidReportDescriptor.Report> out = HidReportDescriptor.outputReports(d, d.length);
        assertEquals(1, out.size());
        assertEquals(5, out.get(0).bytes());
    }

    @Test
    public void truncatedOrLongItemsDoNotThrow() {
        byte[] truncated = bytes(0x85, 0x3F, 0x95);
        assertEquals(0, HidReportDescriptor.outputReports(truncated, truncated.length).size());
        byte[] longItem = bytes(0xFE, 0x02, 0x10, 0xAA, 0xBB, 0x75, 0x08, 0x95, 0x02, 0x91, 0x02);
        List<HidReportDescriptor.Report> out = HidReportDescriptor.outputReports(longItem, longItem.length);
        assertEquals(1, out.size());
        assertEquals(2, out.get(0).bytes());
    }
}
