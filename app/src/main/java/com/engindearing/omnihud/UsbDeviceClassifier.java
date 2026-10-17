package com.engindearing.omnihud;

/**
 * Ranks and labels USB devices for the device list.
 *
 * Nothing is filtered out: version 0.1 hid every device that missed a short allow-list, which is
 * how a real e-COTY ended up invisible. The classifier now only decides the order and the label.
 */
public final class UsbDeviceClassifier {

    public enum Match { ECOTI, LIKELY, OTHER }

    /** Texas Instruments' MSP430 USB vendor ID. The "2047" in the original ECOTI report is this hex value. */
    public static final int VID_TI_MSP430 = 0x2047;
    /** Texas Instruments' main USB vendor ID. */
    public static final int VID_TI = 0x0451;
    /** The value 0.1 shipped: the reported "2047" read as a decimal number. Kept in case the report really was decimal. */
    public static final int VID_ECOTI_AS_DECIMAL = 0x07FF;
    /** Product ID from the same report. */
    public static final int PID_ECOTI = 0x0104;

    private static final int[] SERIAL_CHIP_VENDORS = {
        0x0403, // FTDI
        0x067B, // Prolific
        0x10C4, // Silicon Labs CP210x
        0x1A86, // QinHeng CH34x
        0x2341, // Arduino
        0x16C0, // VOTI
    };

    private UsbDeviceClassifier() {
    }

    public static Match classify(UsbDescriptorInfo d) {
        if (d.productId == PID_ECOTI
                && (d.vendorId == VID_TI_MSP430 || d.vendorId == VID_ECOTI_AS_DECIMAL)) {
            return Match.ECOTI;
        }
        if (d.vendorId == VID_TI_MSP430 || d.vendorId == VID_TI) {
            return Match.LIKELY;
        }
        for (int v : SERIAL_CHIP_VENDORS) {
            if (d.vendorId == v) {
                return Match.LIKELY;
            }
        }
        if (UsbLinkPlan.plan(d).isUsable()) {
            return Match.LIKELY;
        }
        return Match.OTHER;
    }

    /** Sort key: ECOTI matches first, then likely data links, then everything else. */
    public static int rank(UsbDescriptorInfo d) {
        return classify(d).ordinal();
    }

    /** One-line label for the device spinner. */
    public static String label(UsbDescriptorInfo d) {
        String base = d.displayName() + " (" + d.ids() + ", " + d.classSummary() + ")";
        switch (classify(d)) {
            case ECOTI:
                return "ECOTI ID match: " + base;
            case LIKELY:
                return "Possible HUD: " + base;
            default:
                return base;
        }
    }

    /** Owner of a vendor ID for the vendors this plugin cares about, or null. */
    public static String vendorName(int vendorId) {
        switch (vendorId) {
            case VID_TI_MSP430:
            case VID_TI:
                return "Texas Instruments";
            case 0x0403: return "FTDI";
            case 0x067B: return "Prolific";
            case 0x10C4: return "Silicon Labs";
            case 0x1A86: return "QinHeng";
            case 0x2341: return "Arduino";
            case 0x16C0: return "VOTI";
            default: return null;
        }
    }
}
