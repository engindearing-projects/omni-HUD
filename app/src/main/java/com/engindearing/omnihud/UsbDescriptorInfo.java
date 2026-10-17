package com.engindearing.omnihud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Plain-Java snapshot of a USB device's descriptors.
 *
 * It carries no Android types, so device classification and link planning can run in local
 * unit tests. {@link UsbDescriptors#from} builds one from an {@code android.hardware.usb.UsbDevice}.
 */
public final class UsbDescriptorInfo {

    // These values mirror android.hardware.usb.UsbConstants.
    public static final int DIR_IN = 0x80;
    public static final int TYPE_CONTROL = 0;
    public static final int TYPE_ISOCHRONOUS = 1;
    public static final int TYPE_BULK = 2;
    public static final int TYPE_INTERRUPT = 3;

    public static final int CLASS_PER_INTERFACE = 0x00;
    public static final int CLASS_AUDIO = 0x01;
    public static final int CLASS_COMM = 0x02;
    public static final int CLASS_HID = 0x03;
    public static final int CLASS_PHYSICAL = 0x05;
    public static final int CLASS_STILL_IMAGE = 0x06;
    public static final int CLASS_PRINTER = 0x07;
    public static final int CLASS_MASS_STORAGE = 0x08;
    public static final int CLASS_HUB = 0x09;
    public static final int CLASS_CDC_DATA = 0x0A;
    public static final int CLASS_SMART_CARD = 0x0B;
    public static final int CLASS_CONTENT_SECURITY = 0x0D;
    public static final int CLASS_VIDEO = 0x0E;
    public static final int CLASS_WIRELESS = 0xE0;
    public static final int CLASS_MISC = 0xEF;
    public static final int CLASS_APP_SPECIFIC = 0xFE;
    public static final int CLASS_VENDOR_SPECIFIC = 0xFF;

    /** One endpoint of an interface. {@code index} is its position in the interface's endpoint list. */
    public static final class Endpoint {
        public final int index;
        public final int address;
        public final int type;
        public final int maxPacketSize;
        public final int interval;

        public Endpoint(int index, int address, int type, int maxPacketSize, int interval) {
            this.index = index;
            this.address = address;
            this.type = type;
            this.maxPacketSize = maxPacketSize;
            this.interval = interval;
        }

        public boolean isIn() {
            return (address & DIR_IN) != 0;
        }

        public String describe() {
            return String.format(Locale.US, "endpoint 0x%02X %s %s, max packet %d",
                    address, isIn() ? "IN" : "OUT", typeName(type), maxPacketSize);
        }
    }

    /** One interface (or alternate setting). {@code index} is its position in the device's interface list. */
    public static final class Interface {
        public final int index;
        public final int id;
        public final int alternateSetting;
        public final int interfaceClass;
        public final int subclass;
        public final int protocol;
        public final List<Endpoint> endpoints;

        public Interface(int index, int id, int alternateSetting, int interfaceClass,
                         int subclass, int protocol, List<Endpoint> endpoints) {
            this.index = index;
            this.id = id;
            this.alternateSetting = alternateSetting;
            this.interfaceClass = interfaceClass;
            this.subclass = subclass;
            this.protocol = protocol;
            this.endpoints = Collections.unmodifiableList(new ArrayList<>(endpoints));
        }

        /** First endpoint with the given direction and transfer type, or null. */
        public Endpoint find(boolean in, int type) {
            for (Endpoint e : endpoints) {
                if (e.isIn() == in && e.type == type) {
                    return e;
                }
            }
            return null;
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US, "interface %d alt %d: %s (class 0x%02X, subclass 0x%02X, protocol 0x%02X)",
                    id, alternateSetting, className(interfaceClass), interfaceClass, subclass, protocol));
            if (endpoints.isEmpty()) {
                sb.append("\n      no endpoints");
            }
            for (Endpoint e : endpoints) {
                sb.append("\n      ").append(e.describe());
            }
            return sb.toString();
        }
    }

    public final int vendorId;
    public final int productId;
    public final int deviceClass;
    public final int deviceSubclass;
    public final int deviceProtocol;
    public final String manufacturer;
    public final String product;
    public final String path;
    public final List<Interface> interfaces;

    public UsbDescriptorInfo(int vendorId, int productId, int deviceClass, int deviceSubclass,
                             int deviceProtocol, String manufacturer, String product, String path,
                             List<Interface> interfaces) {
        this.vendorId = vendorId;
        this.productId = productId;
        this.deviceClass = deviceClass;
        this.deviceSubclass = deviceSubclass;
        this.deviceProtocol = deviceProtocol;
        this.manufacturer = blankToNull(manufacturer);
        this.product = blankToNull(product);
        this.path = path;
        this.interfaces = Collections.unmodifiableList(new ArrayList<>(interfaces));
    }

    /** Vendor and product ID as four-digit hex, e.g. "2047:0104". */
    public String ids() {
        return String.format(Locale.US, "%04X:%04X", vendorId, productId);
    }

    /** Product string if the device reports one, otherwise the best name we can build. */
    public String displayName() {
        if (product != null) {
            return product;
        }
        if (manufacturer != null) {
            return manufacturer + " device";
        }
        String vendor = UsbDeviceClassifier.vendorName(vendorId);
        return vendor != null ? vendor + " device" : "USB device";
    }

    /** Interface class names in order without repeats, e.g. "CDC + CDC data". */
    public String classSummary() {
        Set<String> names = new LinkedHashSet<>();
        for (Interface i : interfaces) {
            names.add(className(i.interfaceClass));
        }
        if (names.isEmpty()) {
            names.add(className(deviceClass));
        }
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(n);
        }
        return sb.toString();
    }

    public boolean hasInterfaceClass(int interfaceClass) {
        for (Interface i : interfaces) {
            if (i.interfaceClass == interfaceClass) {
                return true;
            }
        }
        return false;
    }

    /** Multi-line description for the dashboard and the USB report. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(ids()).append("  ").append(displayName());
        String vendor = UsbDeviceClassifier.vendorName(vendorId);
        if (vendor != null) {
            sb.append("\n  vendor ID owner: ").append(vendor);
        }
        if (manufacturer != null) {
            sb.append("\n  manufacturer: ").append(manufacturer);
        }
        if (product != null) {
            sb.append("\n  product: ").append(product);
        }
        sb.append(String.format(Locale.US, "\n  device class: %s (0x%02X/0x%02X/0x%02X)",
                className(deviceClass), deviceClass, deviceSubclass, deviceProtocol));
        if (path != null) {
            sb.append("\n  path: ").append(path);
        }
        if (interfaces.isEmpty()) {
            sb.append("\n  no interfaces reported");
        }
        for (Interface i : interfaces) {
            sb.append("\n  ").append(i.describe());
        }
        return sb.toString();
    }

    public static String className(int usbClass) {
        switch (usbClass) {
            case CLASS_PER_INTERFACE: return "per interface";
            case CLASS_AUDIO: return "Audio";
            case CLASS_COMM: return "CDC";
            case CLASS_HID: return "HID";
            case CLASS_PHYSICAL: return "Physical";
            case CLASS_STILL_IMAGE: return "Still image (PTP/MTP)";
            case CLASS_PRINTER: return "Printer";
            case CLASS_MASS_STORAGE: return "Mass storage";
            case CLASS_HUB: return "Hub";
            case CLASS_CDC_DATA: return "CDC data";
            case CLASS_SMART_CARD: return "Smart card";
            case CLASS_CONTENT_SECURITY: return "Content security";
            case CLASS_VIDEO: return "Video";
            case CLASS_WIRELESS: return "Wireless controller";
            case CLASS_MISC: return "Miscellaneous";
            case CLASS_APP_SPECIFIC: return "Application specific";
            case CLASS_VENDOR_SPECIFIC: return "Vendor specific";
            default: return String.format(Locale.US, "class 0x%02X", usbClass);
        }
    }

    public static String typeName(int type) {
        switch (type) {
            case TYPE_CONTROL: return "control";
            case TYPE_ISOCHRONOUS: return "isochronous";
            case TYPE_BULK: return "bulk";
            case TYPE_INTERRUPT: return "interrupt";
            default: return "type " + type;
        }
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
