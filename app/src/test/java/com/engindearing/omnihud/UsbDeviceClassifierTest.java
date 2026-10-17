package com.engindearing.omnihud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UsbDeviceClassifierTest {

    @Test
    public void reportedEcotiIdsMatchAsHex() {
        assertEquals(UsbDeviceClassifier.Match.ECOTI, UsbDeviceClassifier.classify(UsbFixtures.tiHidDatapipe()));
    }

    @Test
    public void theDecimalReadingShippedIn01StillMatches() {
        UsbDescriptorInfo d = UsbFixtures.device(0x07FF, 0x0104, 0, null,
                UsbFixtures.iface(0, 0, UsbDescriptorInfo.CLASS_HID, 0, 0));
        assertEquals(UsbDeviceClassifier.Match.ECOTI, UsbDeviceClassifier.classify(d));
    }

    @Test
    public void anyTexasInstrumentsDeviceIsLikely() {
        assertEquals(UsbDeviceClassifier.Match.LIKELY, UsbDeviceClassifier.classify(UsbFixtures.tiCdcSerial()));
        UsbDescriptorInfo storageOnTiVid = UsbFixtures.device(0x0451, 0x1234, 0, null,
                UsbFixtures.iface(0, 0, UsbDescriptorInfo.CLASS_MASS_STORAGE, 6, 0x50));
        assertEquals(UsbDeviceClassifier.Match.LIKELY, UsbDeviceClassifier.classify(storageOnTiVid));
    }

    @Test
    public void serialChipsAndWritableDevicesAreLikely() {
        assertEquals(UsbDeviceClassifier.Match.LIKELY, UsbDeviceClassifier.classify(UsbFixtures.ftdi()));
        assertEquals(UsbDeviceClassifier.Match.LIKELY, UsbDeviceClassifier.classify(UsbFixtures.storagePlusCdc()));
    }

    @Test
    public void storageCamerasAndKeyboardsAreListedButRankedLast() {
        assertEquals(UsbDeviceClassifier.Match.OTHER, UsbDeviceClassifier.classify(UsbFixtures.flashDrive()));
        assertEquals(UsbDeviceClassifier.Match.OTHER, UsbDeviceClassifier.classify(UsbFixtures.mtpCamera()));
        assertEquals(UsbDeviceClassifier.Match.OTHER, UsbDeviceClassifier.classify(UsbFixtures.keyboard()));
        assertTrue(UsbDeviceClassifier.rank(UsbFixtures.tiHidDatapipe()) < UsbDeviceClassifier.rank(UsbFixtures.ftdi()));
        assertTrue(UsbDeviceClassifier.rank(UsbFixtures.ftdi()) < UsbDeviceClassifier.rank(UsbFixtures.flashDrive()));
    }

    @Test
    public void labelsCarryIdsAndClassesSoAScreenshotIsEnough() {
        assertEquals("ECOTI ID match: Texas Instruments device (2047:0104, HID)",
                UsbDeviceClassifier.label(UsbFixtures.tiHidDatapipe()));
        assertEquals("Possible HUD: FT232R USB UART (0403:6001, Vendor specific)",
                UsbDeviceClassifier.label(UsbFixtures.ftdi()));
        assertEquals("Ultra (0781:5581, Mass storage)", UsbDeviceClassifier.label(UsbFixtures.flashDrive()));
        assertEquals("MSP430-USB Example (2047:0300, CDC + CDC data)",
                UsbDeviceClassifier.label(UsbFixtures.tiCdcSerial()).replace("Possible HUD: ", ""));
    }

    @Test
    public void describeListsEveryInterfaceAndEndpoint() {
        String text = UsbFixtures.tiCdcSerial().describe();
        assertTrue(text, text.startsWith("2047:0300  MSP430-USB Example"));
        assertTrue(text, text.contains("vendor ID owner: Texas Instruments"));
        assertTrue(text, text.contains("interface 1 alt 0: CDC data (class 0x0A, subclass 0x00, protocol 0x00)"));
        assertTrue(text, text.contains("endpoint 0x01 OUT bulk, max packet 64"));
    }
}
