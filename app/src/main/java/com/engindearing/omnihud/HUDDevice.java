package com.engindearing.omnihud;

import android.hardware.usb.UsbDevice;

/**
 * Interface for HUD device integration.
 * Manufacturers can implement this interface to add support for their HUD hardware.
 */
public interface HUDDevice {

    /**
     * Get the display name of this HUD device type
     * @return Human-readable device name
     */
    String getDeviceName();

    /**
     * Get the manufacturer name
     * @return Manufacturer name
     */
    String getManufacturer();

    /**
     * Check if this device implementation supports the given USB device
     * @param device USB device to check
     * @return true if this implementation can handle the device
     */
    boolean supportsDevice(UsbDevice device);

    /**
     * Initialize connection to the HUD device
     * @param device USB device to connect to
     * @return true if connection successful
     */
    boolean connect(UsbDevice device);

    /**
     * Disconnect from the HUD device
     */
    void disconnect();

    /**
     * Check if currently connected to a device
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Send CoT XML data to the HUD for display
     * @param cotXml CoT XML message in standard TAK format
     * @return true if data sent successfully
     */
    boolean sendCotData(String cotXml);

    /**
     * Send formatted position data to HUD
     * @param lat Latitude
     * @param lon Longitude
     * @param alt Altitude in meters HAE
     * @param heading Heading in degrees
     * @param callsign User callsign
     * @return true if data sent successfully
     */
    boolean sendPosition(double lat, double lon, double alt, double heading, String callsign);

    /**
     * Get the last error message if any operation failed
     * @return Error message or null if no error
     */
    String getLastError();

    /**
     * Get device connection status as human-readable string
     * @return Status string
     */
    String getStatusString();
}
