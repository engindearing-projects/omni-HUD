package com.engindearing.omnihud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UsbLinkPlanTest {

    @Test
    public void cdcSerialWritesToTheDataInterfaceNotInterfaceZero() {
        UsbLinkPlan p = UsbLinkPlan.plan(UsbFixtures.tiCdcSerial());
        assertEquals(UsbLinkPlan.Kind.CDC_ACM, p.kind);
        assertEquals(1, p.dataInterface);
        assertEquals(1, p.outEndpoint);
        assertEquals(0, p.inEndpoint);
        assertEquals(0, p.controlInterface);
    }

    @Test
    public void tiHidDatapipeUsesTheInterruptEndpoints() {
        UsbLinkPlan p = UsbLinkPlan.plan(UsbFixtures.tiHidDatapipe());
        assertEquals(UsbLinkPlan.Kind.HID_INTERRUPT, p.kind);
        assertEquals(0, p.dataInterface);
        assertEquals(1, p.outEndpoint);
        assertEquals(0, p.inEndpoint);
        assertEquals(-1, p.controlInterface);
    }

    @Test
    public void compositeDevicePrefersCdcOverAnInputOnlyHidInterface() {
        UsbLinkPlan p = UsbLinkPlan.plan(UsbFixtures.hidPlusCdc());
        assertEquals(UsbLinkPlan.Kind.CDC_ACM, p.kind);
        assertEquals(2, p.dataInterface);
        assertEquals(1, p.controlInterface);
    }

    @Test
    public void vendorSpecificSerialChipUsesBulk() {
        UsbLinkPlan p = UsbLinkPlan.plan(UsbFixtures.ftdi());
        assertEquals(UsbLinkPlan.Kind.BULK, p.kind);
        assertEquals(0, p.dataInterface);
        assertEquals(1, p.outEndpoint);
    }

    @Test
    public void storageIsNeverClaimedAndSaysWhy() {
        UsbLinkPlan p = UsbLinkPlan.plan(UsbFixtures.flashDrive());
        assertFalse(p.isUsable());
        assertTrue(p.reason, p.reason.contains("storage"));
    }

    @Test
    public void storagePlusCdcCompositeUsesOnlyTheSerialPart() {
        UsbLinkPlan p = UsbLinkPlan.plan(UsbFixtures.storagePlusCdc());
        assertEquals(UsbLinkPlan.Kind.CDC_ACM, p.kind);
        assertEquals(2, p.dataInterface);
    }

    @Test
    public void cameraTransferIsNotTreatedAsADataLink() {
        UsbLinkPlan p = UsbLinkPlan.plan(UsbFixtures.mtpCamera());
        assertFalse(p.isUsable());
        assertTrue(p.reason, p.reason.contains("PTP/MTP"));
    }

    @Test
    public void inputOnlyHidHasNothingToWriteTo() {
        UsbLinkPlan p = UsbLinkPlan.plan(UsbFixtures.keyboard());
        assertFalse(p.isUsable());
        assertTrue(p.reason, p.reason.contains("no interrupt OUT"));
    }

    @Test
    public void nonHidInterruptOutIsAFallback() {
        UsbDescriptorInfo d = UsbFixtures.device(0xABCD, 0x0001, 0, null,
                UsbFixtures.iface(0, 0, UsbDescriptorInfo.CLASS_APP_SPECIFIC, 0, 0,
                        UsbFixtures.ep(0, 0x01, UsbDescriptorInfo.TYPE_INTERRUPT, 32)));
        UsbLinkPlan p = UsbLinkPlan.plan(d);
        assertEquals(UsbLinkPlan.Kind.INTERRUPT, p.kind);
        assertEquals(-1, p.inEndpoint);
    }

    @Test
    public void deviceWithoutInterfacesIsUnsupported() {
        UsbDescriptorInfo d = UsbFixtures.device(0xABCD, 0x0002, 0, null);
        assertFalse(UsbLinkPlan.plan(d).isUsable());
    }
}
