package com.engindearing.omnihud;

import java.util.Locale;

/**
 * Which interface and endpoints to use when talking to a USB device, decided from its descriptors.
 *
 * Version 0.1 always claimed interface 0 and wrote to its first OUT endpoint. A standard CDC
 * serial device keeps its bulk OUT endpoint on the second (data) interface, and HID devices need
 * their own framing, so the plan looks at every interface instead.
 */
public final class UsbLinkPlan {

    public enum Kind {
        /** CDC ACM serial: bulk endpoints on the CDC data interface, line setup on the control interface. */
        CDC_ACM,
        /** Bulk OUT endpoint on a vendor-specific or other plain data interface. */
        BULK,
        /** HID interface with an interrupt OUT endpoint. Writes are framed as HID reports. */
        HID_INTERRUPT,
        /** Interrupt OUT endpoint on a non-HID interface. */
        INTERRUPT,
        /** Nothing the plugin can write to. {@link #reason} says why. */
        UNSUPPORTED
    }

    public final Kind kind;
    /** Index in the device's interface list of the interface holding the OUT endpoint, or -1. */
    public final int dataInterface;
    /** Index of the OUT endpoint within the data interface, or -1. */
    public final int outEndpoint;
    /** Index of an IN endpoint within the data interface, or -1 when the device cannot answer. */
    public final int inEndpoint;
    /** Index of the CDC control interface, or -1. */
    public final int controlInterface;
    /** Human-readable explanation, shown on the dashboard and in the USB report. */
    public final String reason;

    private UsbLinkPlan(Kind kind, int dataInterface, int outEndpoint, int inEndpoint,
                        int controlInterface, String reason) {
        this.kind = kind;
        this.dataInterface = dataInterface;
        this.outEndpoint = outEndpoint;
        this.inEndpoint = inEndpoint;
        this.controlInterface = controlInterface;
        this.reason = reason;
    }

    public boolean isUsable() {
        return kind != Kind.UNSUPPORTED;
    }

    public String describe() {
        return isUsable() ? reason : "cannot connect: " + reason;
    }

    public static UsbLinkPlan plan(UsbDescriptorInfo d) {
        // 1. CDC serial: bulk OUT on the CDC data interface.
        for (UsbDescriptorInfo.Interface i : d.interfaces) {
            if (i.interfaceClass != UsbDescriptorInfo.CLASS_CDC_DATA) {
                continue;
            }
            UsbDescriptorInfo.Endpoint out = i.find(false, UsbDescriptorInfo.TYPE_BULK);
            if (out != null) {
                UsbDescriptorInfo.Endpoint in = i.find(true, UsbDescriptorInfo.TYPE_BULK);
                int control = firstInterfaceOfClass(d, UsbDescriptorInfo.CLASS_COMM);
                return new UsbLinkPlan(Kind.CDC_ACM, i.index, out.index, in == null ? -1 : in.index, control,
                        String.format(Locale.US, "CDC serial, bulk OUT on interface %d", i.id));
            }
        }
        // 2. Vendor-specific interface with bulk OUT (FTDI, CP210x and similar chips, or custom firmware).
        UsbLinkPlan p = firstWithOut(d, UsbDescriptorInfo.CLASS_VENDOR_SPECIFIC, UsbDescriptorInfo.TYPE_BULK, Kind.BULK,
                "vendor-specific bulk OUT on interface %d");
        if (p != null) {
            return p;
        }
        // 3. HID with an interrupt OUT endpoint. TI's MSP430 USB stack exposes its data pipe this way.
        p = firstWithOut(d, UsbDescriptorInfo.CLASS_HID, UsbDescriptorInfo.TYPE_INTERRUPT, Kind.HID_INTERRUPT,
                "HID, interrupt OUT on interface %d");
        if (p != null) {
            return p;
        }
        // 4. Any other plain data interface, bulk first.
        for (UsbDescriptorInfo.Interface i : d.interfaces) {
            if (isReservedClass(i.interfaceClass) || i.interfaceClass == UsbDescriptorInfo.CLASS_HID) {
                continue;
            }
            UsbDescriptorInfo.Endpoint out = i.find(false, UsbDescriptorInfo.TYPE_BULK);
            if (out != null) {
                return withOut(i, out, UsbDescriptorInfo.TYPE_BULK, Kind.BULK, "bulk OUT on interface %d");
            }
        }
        for (UsbDescriptorInfo.Interface i : d.interfaces) {
            if (isReservedClass(i.interfaceClass) || i.interfaceClass == UsbDescriptorInfo.CLASS_HID) {
                continue;
            }
            UsbDescriptorInfo.Endpoint out = i.find(false, UsbDescriptorInfo.TYPE_INTERRUPT);
            if (out != null) {
                return withOut(i, out, UsbDescriptorInfo.TYPE_INTERRUPT, Kind.INTERRUPT, "interrupt OUT on interface %d");
            }
        }
        return unsupported(d);
    }

    /**
     * Classes owned by Android or by another job on the device (storage, cameras, audio, video,
     * printers, hubs, smart cards). Writing position messages to them does nothing useful and can
     * disrupt them, so they are never claimed.
     */
    static boolean isReservedClass(int interfaceClass) {
        switch (interfaceClass) {
            case UsbDescriptorInfo.CLASS_AUDIO:
            case UsbDescriptorInfo.CLASS_STILL_IMAGE:
            case UsbDescriptorInfo.CLASS_PRINTER:
            case UsbDescriptorInfo.CLASS_MASS_STORAGE:
            case UsbDescriptorInfo.CLASS_HUB:
            case UsbDescriptorInfo.CLASS_SMART_CARD:
            case UsbDescriptorInfo.CLASS_VIDEO:
                return true;
            default:
                return false;
        }
    }

    private static UsbLinkPlan firstWithOut(UsbDescriptorInfo d, int interfaceClass, int type, Kind kind,
                                            String reasonFormat) {
        for (UsbDescriptorInfo.Interface i : d.interfaces) {
            if (i.interfaceClass != interfaceClass) {
                continue;
            }
            UsbDescriptorInfo.Endpoint out = i.find(false, type);
            if (out != null) {
                return withOut(i, out, type, kind, reasonFormat);
            }
        }
        return null;
    }

    private static UsbLinkPlan withOut(UsbDescriptorInfo.Interface i, UsbDescriptorInfo.Endpoint out, int type,
                                       Kind kind, String reasonFormat) {
        UsbDescriptorInfo.Endpoint in = i.find(true, type);
        return new UsbLinkPlan(kind, i.index, out.index, in == null ? -1 : in.index, -1,
                String.format(Locale.US, reasonFormat, i.id));
    }

    private static int firstInterfaceOfClass(UsbDescriptorInfo d, int interfaceClass) {
        for (UsbDescriptorInfo.Interface i : d.interfaces) {
            if (i.interfaceClass == interfaceClass) {
                return i.index;
            }
        }
        return -1;
    }

    private static UsbLinkPlan unsupported(UsbDescriptorInfo d) {
        String reason;
        if (d.interfaces.isEmpty()) {
            reason = "the device reports no interfaces";
        } else if (d.hasInterfaceClass(UsbDescriptorInfo.CLASS_MASS_STORAGE)) {
            reason = "the device presents as USB storage, which Android mounts as a drive rather than a data link";
        } else if (d.hasInterfaceClass(UsbDescriptorInfo.CLASS_STILL_IMAGE)) {
            reason = "the device presents as a camera for photo transfer (PTP/MTP), not a data link";
        } else if (d.hasInterfaceClass(UsbDescriptorInfo.CLASS_VIDEO)) {
            reason = "the device presents as a USB video device, not a data link";
        } else if (d.hasInterfaceClass(UsbDescriptorInfo.CLASS_HID)) {
            reason = "the device is HID but has no interrupt OUT endpoint to send data to";
        } else {
            reason = "the device has no OUT endpoint the plugin can write to";
        }
        return new UsbLinkPlan(Kind.UNSUPPORTED, -1, -1, -1, -1, reason);
    }
}
