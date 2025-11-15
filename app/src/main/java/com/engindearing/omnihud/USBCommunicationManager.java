package com.engindearing.omnihud;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Manages USB communication with HUD devices.
 * Handles device discovery, permissions, and connection lifecycle.
 */
public class USBCommunicationManager {

    private static final String TAG = USBCommunicationManager.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "com.engindearing.omnihud.USB_PERMISSION";

    private Context context;
    private UsbManager usbManager;
    private HUDDevice currentDevice;
    private List<HUDDevice> supportedDevices;

    private ConnectionListener connectionListener;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.i(TAG, "========================================");
                            Log.i(TAG, "✓ USB PERMISSION GRANTED");
                            Log.i(TAG, "Device: " + device.getDeviceName());
                            Log.i(TAG, "VID/PID: 0x" + String.format("%04X", device.getVendorId()) +
                                       "/0x" + String.format("%04X", device.getProductId()));
                            Log.i(TAG, "Proceeding to connect...");
                            Log.i(TAG, "========================================");
                            connectToDevice(device);
                        }
                    } else {
                        Log.e(TAG, "========================================");
                        Log.e(TAG, "✗ USB PERMISSION DENIED");
                        Log.e(TAG, "Device: " + (device != null ? device.getDeviceName() : "unknown"));
                        Log.e(TAG, "User must grant USB permission for plugin to work");
                        Log.e(TAG, "========================================");
                        if (connectionListener != null) {
                            connectionListener.onConnectionFailed("USB permission denied by user");
                        }
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.i(TAG, "========================================");
                Log.i(TAG, "USB DEVICE ATTACHED EVENT");
                if (device != null) {
                    Log.i(TAG, "Device: " + device.getDeviceName());
                    Log.i(TAG, "VID/PID: 0x" + String.format("%04X", device.getVendorId()) +
                               "/0x" + String.format("%04X", device.getProductId()));
                } else {
                    Log.w(TAG, "Device info not available in intent");
                }
                Log.i(TAG, "========================================");
                if (device != null && connectionListener != null) {
                    connectionListener.onDeviceAttached(device);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.i(TAG, "========================================");
                Log.i(TAG, "USB DEVICE DETACHED EVENT");
                if (device != null) {
                    Log.i(TAG, "Device: " + device.getDeviceName());
                } else {
                    Log.w(TAG, "Device info not available");
                }
                Log.i(TAG, "========================================");
                if (device != null && currentDevice != null && currentDevice.isConnected()) {
                    disconnect();
                    if (connectionListener != null) {
                        connectionListener.onDeviceDetached();
                    }
                }
            }
        }
    };

    public interface ConnectionListener {
        void onConnected(HUDDevice device);
        void onDisconnected();
        void onConnectionFailed(String error);
        void onDeviceAttached(UsbDevice device);
        void onDeviceDetached();
    }

    public USBCommunicationManager(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null in USBCommunicationManager constructor!");
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context.getApplicationContext();
        if (this.context == null) {
            // Fallback to provided context if getApplicationContext() returns null
            this.context = context;
        }
        this.usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
        this.supportedDevices = new ArrayList<>();

        // Register supported HUD devices
        supportedDevices.add(new ECOTIDevice(usbManager));
        // Additional HUD devices can be added here

        // Register USB broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            this.context.registerReceiver(usbReceiver, filter);
        }

        Log.d(TAG, "USBCommunicationManager initialized with " + supportedDevices.size() + " device types");
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    /**
     * Get list of connected USB devices that could be HUD devices
     */
    public List<UsbDevice> getAvailableDevices() {
        List<UsbDevice> devices = new ArrayList<>();
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

        Log.i(TAG, "========================================");
        Log.i(TAG, "Scanning for USB devices...");
        Log.i(TAG, "Total USB devices connected: " + deviceList.size());
        Log.i(TAG, "========================================");

        if (deviceList.isEmpty()) {
            Log.w(TAG, "No USB devices detected. Make sure ECOTI is connected via USB-C.");
            return devices;
        }

        // List all USB devices first (for debugging)
        int deviceNum = 1;
        for (UsbDevice device : deviceList.values()) {
            Log.i(TAG, "USB Device #" + deviceNum + ":");
            Log.i(TAG, "  Name: " + device.getDeviceName());
            Log.i(TAG, "  VID: 0x" + String.format("%04X", device.getVendorId()));
            Log.i(TAG, "  PID: 0x" + String.format("%04X", device.getProductId()));
            deviceNum++;
        }
        Log.i(TAG, "========================================");

        // Check each device against supported HUD drivers
        for (UsbDevice device : deviceList.values()) {
            Log.d(TAG, "Checking if " + device.getDeviceName() + " is supported...");

            // Check if any supported HUD device can handle this USB device
            for (HUDDevice hudDevice : supportedDevices) {
                if (hudDevice.supportsDevice(device)) {
                    Log.i(TAG, "✓ Device " + device.getDeviceName() + " is supported by " +
                               hudDevice.getDeviceName() + " driver");
                    devices.add(device);
                    break;
                }
            }
        }

        Log.i(TAG, "========================================");
        if (devices.isEmpty()) {
            Log.w(TAG, "✗ No compatible HUD devices found");
            Log.w(TAG, "If ECOTI is connected, check the logs above for device details");
        } else {
            Log.i(TAG, "✓ Found " + devices.size() + " compatible HUD device(s)");
        }
        Log.i(TAG, "========================================");

        return devices;
    }

    /**
     * Request permission and connect to a USB device
     */
    public void requestConnectionToDevice(UsbDevice device) {
        Log.i(TAG, "========================================");
        Log.i(TAG, "Connection request initiated");
        Log.i(TAG, "Device: " + device.getDeviceName());
        Log.i(TAG, "VID/PID: 0x" + String.format("%04X", device.getVendorId()) +
                   "/0x" + String.format("%04X", device.getProductId()));

        if (usbManager.hasPermission(device)) {
            Log.i(TAG, "✓ USB permission already granted");
            Log.i(TAG, "Connecting directly...");
            Log.i(TAG, "========================================");
            connectToDevice(device);
        } else {
            Log.i(TAG, "⚠ USB permission not yet granted");
            Log.i(TAG, "Requesting permission from user...");
            Log.i(TAG, "========================================");

            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(ACTION_USB_PERMISSION),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? PendingIntent.FLAG_MUTABLE
                    : PendingIntent.FLAG_UPDATE_CURRENT
            );
            usbManager.requestPermission(device, permissionIntent);
            Log.d(TAG, "Permission dialog should appear on user's screen");
        }
    }

    /**
     * Connect to a specific USB device
     */
    private void connectToDevice(UsbDevice device) {
        Log.i(TAG, "========================================");
        Log.i(TAG, "Starting device connection process...");

        // Disconnect any existing connection
        if (currentDevice != null && currentDevice.isConnected()) {
            Log.d(TAG, "Disconnecting existing device first...");
            disconnect();
        }

        // Find the appropriate HUD device implementation
        boolean foundDriver = false;
        for (HUDDevice hudDevice : supportedDevices) {
            if (hudDevice.supportsDevice(device)) {
                foundDriver = true;
                Log.i(TAG, "Using " + hudDevice.getDeviceName() + " driver");
                Log.i(TAG, "Attempting connection...");

                if (hudDevice.connect(device)) {
                    currentDevice = hudDevice;
                    Log.i(TAG, "========================================");
                    Log.i(TAG, "✓✓✓ CONNECTION SUCCESSFUL ✓✓✓");
                    Log.i(TAG, "HUD Device: " + hudDevice.getDeviceName());
                    Log.i(TAG, "Status: " + hudDevice.getStatusString());
                    Log.i(TAG, "========================================");

                    if (connectionListener != null) {
                        connectionListener.onConnected(hudDevice);
                    }
                    return;
                } else {
                    Log.e(TAG, "========================================");
                    Log.e(TAG, "✗ CONNECTION FAILED");
                    Log.e(TAG, "Driver: " + hudDevice.getDeviceName());
                    Log.e(TAG, "Error: " + hudDevice.getLastError());
                    Log.e(TAG, "========================================");
                }
            }
        }

        // No device could connect
        if (!foundDriver) {
            String error = "No compatible HUD driver found for device";
            Log.e(TAG, "========================================");
            Log.e(TAG, "✗ NO COMPATIBLE DRIVER");
            Log.e(TAG, error);
            Log.e(TAG, "Device VID/PID: 0x" + String.format("%04X", device.getVendorId()) +
                       "/0x" + String.format("%04X", device.getProductId()));
            Log.e(TAG, "========================================");
            if (connectionListener != null) {
                connectionListener.onConnectionFailed(error);
            }
        }
    }

    /**
     * Disconnect from current HUD device
     */
    public void disconnect() {
        if (currentDevice != null) {
            currentDevice.disconnect();
            currentDevice = null;

            if (connectionListener != null) {
                connectionListener.onDisconnected();
            }

            Log.d(TAG, "Disconnected from HUD device");
        }
    }

    /**
     * Check if currently connected to a HUD device
     */
    public boolean isConnected() {
        return currentDevice != null && currentDevice.isConnected();
    }

    /**
     * Get the currently connected device
     */
    public HUDDevice getCurrentDevice() {
        return currentDevice;
    }

    /**
     * Send CoT data to the connected HUD device
     */
    public boolean sendCotData(String cotXml) {
        if (currentDevice == null || !currentDevice.isConnected()) {
            Log.w(TAG, "Cannot send data: not connected to HUD device");
            return false;
        }

        return currentDevice.sendCotData(cotXml);
    }

    /**
     * Send position data to the connected HUD device
     */
    public boolean sendPosition(double lat, double lon, double alt, double heading, String callsign) {
        if (currentDevice == null || !currentDevice.isConnected()) {
            Log.w(TAG, "Cannot send position: not connected to HUD device");
            return false;
        }

        return currentDevice.sendPosition(lat, lon, alt, heading, callsign);
    }

    /**
     * Clean up resources
     */
    public void dispose() {
        try {
            disconnect();
            context.unregisterReceiver(usbReceiver);
            Log.d(TAG, "USBCommunicationManager disposed");
        } catch (Exception e) {
            Log.e(TAG, "Error disposing USBCommunicationManager", e);
        }
    }
}
