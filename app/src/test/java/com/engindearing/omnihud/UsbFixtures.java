package com.engindearing.omnihud;

import static com.engindearing.omnihud.UsbDescriptorInfo.*;

import java.util.Arrays;

/** Descriptor builders shaped like real devices, for the USB unit tests. */
final class UsbFixtures {

    private UsbFixtures() {
    }

    static Endpoint ep(int index, int address, int type, int maxPacketSize) {
        return new Endpoint(index, address, type, maxPacketSize, type == TYPE_INTERRUPT ? 1 : 0);
    }

    static Interface iface(int index, int id, int cls, int subclass, int protocol, Endpoint... endpoints) {
        return new Interface(index, id, 0, cls, subclass, protocol, Arrays.asList(endpoints));
    }

    static UsbDescriptorInfo device(int vid, int pid, int deviceClass, String product, Interface... interfaces) {
        return new UsbDescriptorInfo(vid, pid, deviceClass, 0, 0, null, product, "/dev/bus/usb/001/002",
                Arrays.asList(interfaces));
    }

    /** TI MSP430 HID data pipe at the IDs from the original ECOTI report. */
    static UsbDescriptorInfo tiHidDatapipe() {
        return device(0x2047, 0x0104, CLASS_PER_INTERFACE, null,
                iface(0, 0, CLASS_HID, 0, 0,
                        ep(0, 0x81, TYPE_INTERRUPT, 64),
                        ep(1, 0x01, TYPE_INTERRUPT, 64)));
    }

    /** Standard CDC ACM serial: control interface 0 (notification IN only), data interface 1 (bulk IN and OUT). */
    static UsbDescriptorInfo tiCdcSerial() {
        return device(0x2047, 0x0300, CLASS_MISC, "MSP430-USB Example",
                iface(0, 0, CLASS_COMM, 2, 1, ep(0, 0x82, TYPE_INTERRUPT, 8)),
                iface(1, 1, CLASS_CDC_DATA, 0, 0,
                        ep(0, 0x81, TYPE_BULK, 64),
                        ep(1, 0x01, TYPE_BULK, 64)));
    }

    /** Composite: a HID interface with no OUT endpoint first, then CDC serial. */
    static UsbDescriptorInfo hidPlusCdc() {
        return device(0x2047, 0x0104, CLASS_MISC, null,
                iface(0, 0, CLASS_HID, 0, 0, ep(0, 0x83, TYPE_INTERRUPT, 64)),
                iface(1, 1, CLASS_COMM, 2, 1, ep(0, 0x82, TYPE_INTERRUPT, 8)),
                iface(2, 2, CLASS_CDC_DATA, 0, 0,
                        ep(0, 0x81, TYPE_BULK, 64),
                        ep(1, 0x01, TYPE_BULK, 64)));
    }

    static UsbDescriptorInfo ftdi() {
        return device(0x0403, 0x6001, CLASS_PER_INTERFACE, "FT232R USB UART",
                iface(0, 0, CLASS_VENDOR_SPECIFIC, 0xFF, 0xFF,
                        ep(0, 0x81, TYPE_BULK, 64),
                        ep(1, 0x02, TYPE_BULK, 64)));
    }

    static UsbDescriptorInfo flashDrive() {
        return device(0x0781, 0x5581, CLASS_PER_INTERFACE, "Ultra",
                iface(0, 0, CLASS_MASS_STORAGE, 6, 0x50,
                        ep(0, 0x81, TYPE_BULK, 512),
                        ep(1, 0x02, TYPE_BULK, 512)));
    }

    static UsbDescriptorInfo storagePlusCdc() {
        return device(0x1234, 0x5678, CLASS_MISC, null,
                iface(0, 0, CLASS_MASS_STORAGE, 6, 0x50,
                        ep(0, 0x81, TYPE_BULK, 512),
                        ep(1, 0x02, TYPE_BULK, 512)),
                iface(1, 1, CLASS_COMM, 2, 1, ep(0, 0x84, TYPE_INTERRUPT, 8)),
                iface(2, 2, CLASS_CDC_DATA, 0, 0,
                        ep(0, 0x83, TYPE_BULK, 64),
                        ep(1, 0x03, TYPE_BULK, 64)));
    }

    static UsbDescriptorInfo mtpCamera() {
        return device(0x04A9, 0x32D1, CLASS_PER_INTERFACE, "Camera",
                iface(0, 0, CLASS_STILL_IMAGE, 1, 1,
                        ep(0, 0x81, TYPE_BULK, 512),
                        ep(1, 0x02, TYPE_BULK, 512),
                        ep(2, 0x83, TYPE_INTERRUPT, 8)));
    }

    static UsbDescriptorInfo keyboard() {
        return device(0x046D, 0xC31C, CLASS_PER_INTERFACE, "USB Keyboard",
                iface(0, 0, CLASS_HID, 1, 1, ep(0, 0x81, TYPE_INTERRUPT, 8)));
    }
}
