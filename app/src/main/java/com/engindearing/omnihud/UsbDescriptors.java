package com.engindearing.omnihud;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

import java.util.ArrayList;
import java.util.List;

/** Builds the plain-Java {@link UsbDescriptorInfo} snapshot from an Android {@link UsbDevice}. */
final class UsbDescriptors {

    private UsbDescriptors() {
    }

    static UsbDescriptorInfo from(UsbDevice device) {
        List<UsbDescriptorInfo.Interface> interfaces = new ArrayList<>();
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface ui = device.getInterface(i);
            List<UsbDescriptorInfo.Endpoint> endpoints = new ArrayList<>();
            for (int e = 0; e < ui.getEndpointCount(); e++) {
                UsbEndpoint ep = ui.getEndpoint(e);
                endpoints.add(new UsbDescriptorInfo.Endpoint(e, ep.getAddress(), ep.getType(),
                        ep.getMaxPacketSize(), ep.getInterval()));
            }
            interfaces.add(new UsbDescriptorInfo.Interface(i, ui.getId(), ui.getAlternateSetting(),
                    ui.getInterfaceClass(), ui.getInterfaceSubclass(), ui.getInterfaceProtocol(), endpoints));
        }
        String manufacturer = null;
        String product = null;
        try {
            manufacturer = device.getManufacturerName();
            product = device.getProductName();
        } catch (RuntimeException ignored) {
            // Some vendor builds throw instead of returning null when a string descriptor is missing.
        }
        return new UsbDescriptorInfo(device.getVendorId(), device.getProductId(), device.getDeviceClass(),
                device.getDeviceSubclass(), device.getDeviceProtocol(), manufacturer, product,
                device.getDeviceName(), interfaces);
    }
}
