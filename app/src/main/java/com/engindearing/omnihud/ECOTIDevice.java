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
    // TODO: Update these with actual ECOTI USB VID/PID values
    private static final int ECOTI_VENDOR_ID = 0x0000;  // Placeholder
    private static final int ECOTI_PRODUCT_ID = 0x0000; // Placeholder

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
        // For now, accept any USB device since we don't have the actual VID/PID
        // TODO: Update this when ECOTI VID/PID is known
        if (ECOTI_VENDOR_ID != 0x0000) {
            return device.getVendorId() == ECOTI_VENDOR_ID &&
                   device.getProductId() == ECOTI_PRODUCT_ID;
        }

        // Generic USB serial device check as fallback
        Log.d(TAG, "Checking device: VID=" + String.format("0x%04X", device.getVendorId()) +
                   " PID=" + String.format("0x%04X", device.getProductId()));
        return true; // Accept any device for testing
    }

    @Override
    public boolean connect(UsbDevice device) {
        try {
            this.usbDevice = device;

            if (!usbManager.hasPermission(device)) {
                lastError = "No USB permission for device";
                Log.e(TAG, lastError);
                return false;
            }

            connection = usbManager.openDevice(device);
            if (connection == null) {
                lastError = "Failed to open USB device connection";
                Log.e(TAG, lastError);
                return false;
            }

            // Find the output endpoint
            if (device.getInterfaceCount() > 0) {
                UsbInterface usbInterface = device.getInterface(0);

                if (!connection.claimInterface(usbInterface, true)) {
                    lastError = "Failed to claim USB interface";
                    Log.e(TAG, lastError);
                    connection.close();
                    connection = null;
                    return false;
                }

                // Find OUT endpoint
                for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                    UsbEndpoint endpoint = usbInterface.getEndpoint(i);
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                        endpointOut = endpoint;
                        break;
                    }
                }

                if (endpointOut == null) {
                    lastError = "No OUT endpoint found on USB device";
                    Log.e(TAG, lastError);
                    disconnect();
                    return false;
                }
            } else {
                lastError = "USB device has no interfaces";
                Log.e(TAG, lastError);
                disconnect();
                return false;
            }

            Log.d(TAG, "Successfully connected to ECOTI device: " + device.getDeviceName());
            return true;

        } catch (Exception e) {
            lastError = "Connection error: " + e.getMessage();
            Log.e(TAG, lastError, e);
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
            Log.e(TAG, lastError);
            return false;
        }

        try {
            // Send CoT XML to ECOTI
            byte[] data = cotXml.getBytes(StandardCharsets.UTF_8);
            int bytesTransferred = connection.bulkTransfer(endpointOut, data, data.length, TIMEOUT_MS);

            if (bytesTransferred < 0) {
                lastError = "Failed to send data to device";
                Log.e(TAG, lastError);
                return false;
            }

            Log.d(TAG, "Sent " + bytesTransferred + " bytes to ECOTI");
            return true;

        } catch (Exception e) {
            lastError = "Send error: " + e.getMessage();
            Log.e(TAG, lastError, e);
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
