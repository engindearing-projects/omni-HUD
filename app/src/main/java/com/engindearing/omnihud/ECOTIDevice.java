package com.engindearing.omnihud;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import com.atakmap.coremap.log.Log;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * ECOTI HUD device implementation.
 * Supports ECOTI thermal/night vision HUD via USB-C adapter.
 */
public class ECOTIDevice implements HUDDevice {

    private static final String TAG = ECOTIDevice.class.getSimpleName();

    // ECOTI USB identifiers - these will need to be updated with actual VID/PID
    // TODO: Update these with actual ECOTI USB VID/PID values from device testing
    // Common USB-Serial chip vendors that ECOTI might use:
    // - FTDI: 0x0403
    // - Prolific: 0x067B
    // - CP210x: 0x10C4
    private static final int ECOTI_VENDOR_ID = 0x0000;  // Placeholder - will be detected
    private static final int ECOTI_PRODUCT_ID = 0x0000; // Placeholder - will be detected

    // Known USB-Serial converter vendor IDs (likely candidates for ECOTI)
    private static final int[] USB_SERIAL_VENDORS = {
        0x0403,  // FTDI
        0x067B,  // Prolific
        0x10C4,  // Silicon Labs CP210x
        0x1A86,  // QinHeng Electronics (CH340)
        0x2341,  // Arduino
        0x16C0   // VOTI (Van Ooijen Technische Informatica)
    };

    private UsbManager usbManager;
    private UsbDevice usbDevice;
    private UsbDeviceConnection connection;
    private UsbEndpoint endpointOut;
    private String lastError;

    private static final int TIMEOUT_MS = 1000;

    public ECOTIDevice(UsbManager usbManager) {
        this.usbManager = usbManager;
    }

    @Override
    public String getDeviceName() {
        return "ECOTI";
    }

    @Override
    public String getManufacturer() {
        return "ECOTI";
    }

    @Override
    public boolean supportsDevice(UsbDevice device) {
        int vendorId = device.getVendorId();
        int productId = device.getProductId();

        // Log detailed device information for discovery
        Log.i(TAG, "========================================");
        Log.i(TAG, "USB Device Detection:");
        Log.i(TAG, "  Device Name: " + device.getDeviceName());
        Log.i(TAG, "  Vendor ID: 0x" + String.format("%04X", vendorId) + " (" + vendorId + ")");
        Log.i(TAG, "  Product ID: 0x" + String.format("%04X", productId) + " (" + productId + ")");
        Log.i(TAG, "  Device Class: " + device.getDeviceClass());
        Log.i(TAG, "  Device Subclass: " + device.getDeviceSubclass());
        Log.i(TAG, "  Interface Count: " + device.getInterfaceCount());

        if (device.getInterfaceCount() > 0) {
            UsbInterface iface = device.getInterface(0);
            Log.i(TAG, "  Interface[0] Class: " + iface.getInterfaceClass());
            Log.i(TAG, "  Interface[0] Subclass: " + iface.getInterfaceSubclass());
            Log.i(TAG, "  Interface[0] Protocol: " + iface.getInterfaceProtocol());
        }
        Log.i(TAG, "========================================");

        // If we have the actual ECOTI VID/PID, use exact match
        if (ECOTI_VENDOR_ID != 0x0000) {
            boolean exactMatch = (vendorId == ECOTI_VENDOR_ID && productId == ECOTI_PRODUCT_ID);
            if (exactMatch) {
                Log.i(TAG, "✓ EXACT MATCH: This is a known ECOTI device!");
                return true;
            } else {
                Log.d(TAG, "✗ Not an exact ECOTI match (VID/PID don't match configured values)");
            }
        }

        // Check if this is a USB serial device (likely for ECOTI communication)
        boolean isUsbSerial = isUsbSerialDevice(device);
        if (isUsbSerial) {
            Log.i(TAG, "✓ DETECTED: USB Serial device - likely compatible with ECOTI");
            Log.w(TAG, "NOTE: Using USB serial fallback. Update ECOTI_VENDOR_ID and ECOTI_PRODUCT_ID in ECOTIDevice.java");
            Log.w(TAG, "      Set ECOTI_VENDOR_ID = 0x" + String.format("%04X", vendorId));
            Log.w(TAG, "      Set ECOTI_PRODUCT_ID = 0x" + String.format("%04X", productId));
            return true;
        }

        Log.w(TAG, "✗ REJECTED: Not a recognized USB serial device");
        return false;
    }

    /**
     * Check if device is a USB serial converter (CDC or using known serial chip)
     */
    private boolean isUsbSerialDevice(UsbDevice device) {
        int vendorId = device.getVendorId();

        // Check against known USB-Serial chip vendors
        for (int knownVendor : USB_SERIAL_VENDORS) {
            if (vendorId == knownVendor) {
                Log.d(TAG, "Device uses known USB-Serial chip vendor: 0x" + String.format("%04X", knownVendor));
                return true;
            }
        }

        // Check if device uses CDC (Communications Device Class)
        if (device.getDeviceClass() == 2) { // USB_CLASS_COMM
            Log.d(TAG, "Device is CDC (Communications Device Class)");
            return true;
        }

        // Check interface class for CDC or vendor-specific
        if (device.getInterfaceCount() > 0) {
            UsbInterface iface = device.getInterface(0);
            int ifaceClass = iface.getInterfaceClass();

            // CDC interface or vendor-specific
            if (ifaceClass == 2 || ifaceClass == 0xFF) {
                Log.d(TAG, "Interface is CDC (2) or Vendor-specific (0xFF)");
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean connect(UsbDevice device) {
        try {
            Log.i(TAG, "========================================");
            Log.i(TAG, "Attempting ECOTI connection...");
            Log.i(TAG, "Device: " + device.getDeviceName());
            Log.i(TAG, "VID/PID: 0x" + String.format("%04X", device.getVendorId()) +
                       "/0x" + String.format("%04X", device.getProductId()));
            Log.i(TAG, "========================================");

            this.usbDevice = device;

            if (!usbManager.hasPermission(device)) {
                lastError = "No USB permission for device";
                Log.e(TAG, "✗ CONNECTION FAILED: " + lastError);
                Log.e(TAG, "User needs to grant USB permission in Android system dialog");
                return false;
            }
            Log.d(TAG, "✓ USB permission granted");

            connection = usbManager.openDevice(device);
            if (connection == null) {
                lastError = "Failed to open USB device connection";
                Log.e(TAG, "✗ CONNECTION FAILED: " + lastError);
                Log.e(TAG, "This may indicate the device is already in use by another app");
                return false;
            }
            Log.d(TAG, "✓ USB device opened successfully");

            // Find the output endpoint
            if (device.getInterfaceCount() > 0) {
                UsbInterface usbInterface = device.getInterface(0);
                Log.d(TAG, "Using interface 0: Class=" + usbInterface.getInterfaceClass() +
                           " Endpoints=" + usbInterface.getEndpointCount());

                if (!connection.claimInterface(usbInterface, true)) {
                    lastError = "Failed to claim USB interface";
                    Log.e(TAG, "✗ CONNECTION FAILED: " + lastError);
                    Log.e(TAG, "Interface may be in use by kernel driver or another app");
                    connection.close();
                    connection = null;
                    return false;
                }
                Log.d(TAG, "✓ USB interface claimed");

                // Find OUT endpoint
                Log.d(TAG, "Searching for output endpoint...");
                for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                    UsbEndpoint endpoint = usbInterface.getEndpoint(i);
                    Log.d(TAG, "  Endpoint " + i + ": " +
                               "Direction=" + (endpoint.getDirection() == UsbConstants.USB_DIR_OUT ? "OUT" : "IN") +
                               " Type=" + endpoint.getType() +
                               " MaxPacketSize=" + endpoint.getMaxPacketSize());

                    if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                        endpointOut = endpoint;
                        Log.d(TAG, "✓ Found OUT endpoint at index " + i);
                        break;
                    }
                }

                if (endpointOut == null) {
                    lastError = "No OUT endpoint found on USB device";
                    Log.e(TAG, "✗ CONNECTION FAILED: " + lastError);
                    Log.e(TAG, "Device has " + usbInterface.getEndpointCount() + " endpoints but none are OUT");
                    disconnect();
                    return false;
                }
            } else {
                lastError = "USB device has no interfaces";
                Log.e(TAG, "✗ CONNECTION FAILED: " + lastError);
                disconnect();
                return false;
            }

            Log.i(TAG, "========================================");
            Log.i(TAG, "✓ SUCCESSFULLY CONNECTED TO ECOTI");
            Log.i(TAG, "  Device: " + device.getDeviceName());
            Log.i(TAG, "  Ready to send data");
            Log.i(TAG, "========================================");
            return true;

        } catch (Exception e) {
            lastError = "Connection error: " + e.getMessage();
            Log.e(TAG, "========================================");
            Log.e(TAG, "✗ CONNECTION EXCEPTION: " + lastError, e);
            Log.e(TAG, "========================================");
            disconnect();
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
        endpointOut = null;
        usbDevice = null;
        Log.d(TAG, "Disconnected from ECOTI device");
    }

    @Override
    public boolean isConnected() {
        return connection != null && endpointOut != null;
    }

    @Override
    public boolean sendCotData(String cotXml) {
        if (!isConnected()) {
            lastError = "Not connected to device";
            Log.e(TAG, "Cannot send CoT data: " + lastError);
            return false;
        }

        try {
            // Send CoT XML to ECOTI
            byte[] data = cotXml.getBytes(StandardCharsets.UTF_8);
            Log.d(TAG, "Sending CoT data to ECOTI: " + data.length + " bytes");
            Log.v(TAG, "CoT XML preview: " + cotXml.substring(0, Math.min(200, cotXml.length())) + "...");

            int bytesTransferred = connection.bulkTransfer(endpointOut, data, data.length, TIMEOUT_MS);

            if (bytesTransferred < 0) {
                lastError = "Failed to send data to device (bulkTransfer returned " + bytesTransferred + ")";
                Log.e(TAG, "✗ SEND FAILED: " + lastError);
                Log.e(TAG, "This may indicate the device was disconnected or endpoint error");
                return false;
            }

            if (bytesTransferred < data.length) {
                Log.w(TAG, "Partial send: " + bytesTransferred + "/" + data.length + " bytes transferred");
            } else {
                Log.d(TAG, "✓ Successfully sent " + bytesTransferred + " bytes to ECOTI");
            }
            return true;

        } catch (Exception e) {
            lastError = "Send error: " + e.getMessage();
            Log.e(TAG, "✗ SEND EXCEPTION: " + lastError, e);
            return false;
        }
    }

    @Override
    public boolean sendPosition(double lat, double lon, double alt, double heading, String callsign) {
        // Generate CoT XML for position
        String cotXml = generatePositionCot(lat, lon, alt, heading, callsign);
        return sendCotData(cotXml);
    }

    @Override
    public String getLastError() {
        return lastError;
    }

    @Override
    public String getStatusString() {
        if (isConnected()) {
            return "Connected to " + (usbDevice != null ? usbDevice.getDeviceName() : "ECOTI");
        }
        return "Not connected";
    }

    /**
     * Generate CoT XML for position data in ECOTI format
     */
    private String generatePositionCot(double lat, double lon, double alt, double heading, String callsign) {
        // Generate timestamp in Zulu time
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        String time = dateFormat.format(new Date());
        String stale = dateFormat.format(new Date(System.currentTimeMillis() + 3600000)); // 1 hour stale

        String uid = UUID.randomUUID().toString();

        return "<?xml version='1.0'?>\n" +
               "<event version='2.0' uid='" + uid + "' type='a-u-G' " +
               "time='" + time + "' start='" + time + "' stale='" + stale + "' " +
               "how='h-g-i-g-o' access='Undefined'>\n" +
               "<point lat='" + lat + "' lon='" + lon + "' hae='" + alt + "' " +
               "ce='9999999.0' le='9999999.0' />\n" +
               "<detail>\n" +
               "<contact callsign='" + (callsign != null ? callsign : "OmniHUD") + "'/>\n" +
               "<status readiness='true'/>\n" +
               "</detail>\n" +
               "</event>";
    }
}
