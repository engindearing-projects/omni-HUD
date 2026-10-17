package com.engindearing.omnihud;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class UsbFramingTest {

    private static byte[] ramp(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) (i + 1);
        }
        return b;
    }

    @Test
    public void lineCodingIs115200EightNoneOne() {
        assertArrayEquals(new byte[] {0x00, (byte) 0xC2, 0x01, 0x00, 0, 0, 8}, UsbFraming.cdcLineCoding(115200));
    }

    @Test
    public void tiDatapipeReportsCarryIdLengthAndPadding() {
        List<byte[]> frames = UsbFraming.frame(ramp(100), UsbFraming.Mode.TI_HID_DATAPIPE, 64, 0);
        assertEquals(2, frames.size());
        byte[] first = frames.get(0);
        byte[] second = frames.get(1);
        assertEquals(64, first.length);
        assertEquals(64, second.length);
        assertEquals(0x3F, first[0] & 0xFF);
        assertEquals(62, first[1]);
        assertEquals(1, first[2]);
        assertEquals(62, first[63]);
        assertEquals(0x3F, second[0] & 0xFF);
        assertEquals(38, second[1]);
        assertEquals(63, second[2]);
        assertEquals(100, second[39]);
        assertEquals(0, second[40]);
        assertEquals(0, second[63]);
    }

    @Test
    public void hidReportWithIdIsPadded() {
        List<byte[]> frames = UsbFraming.frame(ramp(10), UsbFraming.Mode.HID_REPORT, 9, 5);
        assertEquals(2, frames.size());
        assertArrayEquals(new byte[] {5, 1, 2, 3, 4, 5, 6, 7, 8}, frames.get(0));
        assertArrayEquals(new byte[] {5, 9, 10, 0, 0, 0, 0, 0, 0}, frames.get(1));
    }

    @Test
    public void hidReportWithoutIdIsPadded() {
        List<byte[]> frames = UsbFraming.frame(ramp(10), UsbFraming.Mode.HID_PADDED, 8, 0);
        assertEquals(2, frames.size());
        assertArrayEquals(new byte[] {9, 10, 0, 0, 0, 0, 0, 0}, frames.get(1));
    }

    @Test
    public void rawChunksAreNotPadded() {
        List<byte[]> frames = UsbFraming.frame(ramp(10), UsbFraming.Mode.RAW, 4, 0);
        assertEquals(3, frames.size());
        assertArrayEquals(new byte[] {9, 10}, frames.get(2));
    }

    @Test
    public void emptyPayloadWritesNothing() {
        assertEquals(0, UsbFraming.frame(new byte[0], UsbFraming.Mode.TI_HID_DATAPIPE, 64, 0).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void frameTooSmallForItsHeaderIsRejected() {
        UsbFraming.frame(ramp(4), UsbFraming.Mode.TI_HID_DATAPIPE, 2, 0);
    }

    @Test
    public void hidFramingFollowsTheReportDescriptor() {
        List<HidReportDescriptor.Report> ti = Collections.singletonList(new HidReportDescriptor.Report(0x3F, 63 * 8));
        UsbFraming.HidFraming f = UsbFraming.chooseHidFraming(ti, 64, 0x2047);
        assertEquals(UsbFraming.Mode.TI_HID_DATAPIPE, f.mode);
        assertEquals(64, f.size);

        List<HidReportDescriptor.Report> withId = Collections.singletonList(new HidReportDescriptor.Report(2, 32 * 8));
        f = UsbFraming.chooseHidFraming(withId, 64, 0x1234);
        assertEquals(UsbFraming.Mode.HID_REPORT, f.mode);
        assertEquals(33, f.size);
        assertEquals(2, f.reportId);

        List<HidReportDescriptor.Report> noId = Collections.singletonList(new HidReportDescriptor.Report(0, 64 * 8));
        f = UsbFraming.chooseHidFraming(noId, 64, 0x1234);
        assertEquals(UsbFraming.Mode.HID_PADDED, f.mode);
        assertEquals(64, f.size);
    }

    @Test
    public void withoutADescriptorTiDefaultsToTheDatapipe() {
        List<HidReportDescriptor.Report> none = new ArrayList<>();
        assertEquals(UsbFraming.Mode.TI_HID_DATAPIPE, UsbFraming.chooseHidFraming(none, 64, 0x2047).mode);
        assertEquals(UsbFraming.Mode.HID_PADDED, UsbFraming.chooseHidFraming(none, 64, 0x1234).mode);
    }

    @Test
    public void hexHelpers() {
        byte[] xml = "<?xml version".getBytes();
        assertEquals("3C 3F 78", UsbFraming.hex(xml, 0, 3));
        assertEquals("0000  3C 3F 78 6D 6C 20 76 65 72 73 69 6F 6E           <?xml version",
                UsbFraming.hexDump(xml, xml.length));
        byte[] twoLines = Arrays.copyOf(xml, 20);
        assertEquals(2, UsbFraming.hexDump(twoLines, 20).split("\n").length);
    }
}
