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
                            Log.d(TAG, "USB permission granted for device: " + device.getDeviceName());
                            connectToDevice(device);
                        }
                    } else {
                        Log.w(TAG, "USB permission denied for device: " +
                              (device != null ? device.getDeviceName() : "unknown"));
                        if (connectionListener != null) {
                            connectionListener.onConnectionFailed("USB permission denied");
                        }
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.d(TAG, "USB device attached: " + (device != null ? device.getDeviceName() : "unknown"));
                if (device != null && connectionListener != null) {
                    connectionListener.onDeviceAttached(device);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.d(TAG, "USB device detached: " + (device != null ? device.getDeviceName() : "unknown"));
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

        for (UsbDevice device : deviceList.values()) {
            // Check if any supported HUD device can handle this USB device
            for (HUDDevice hudDevice : supportedDevices) {
                if (hudDevice.supportsDevice(device)) {
                    devices.add(device);
                    break;
                }
            }
        }

        Log.d(TAG, "Found " + devices.size() + " potential HUD devices");
        return devices;
    }

    /**
     * Request permission and connect to a USB device
     */
    public void requestConnectionToDevice(UsbDevice device) {
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "Already have permission, connecting directly");
            connectToDevice(device);
        } else {
            Log.d(TAG, "Requesting USB permission for device: " + device.getDeviceName());
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(ACTION_USB_PERMISSION),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? PendingIntent.FLAG_MUTABLE
                    : PendingIntent.FLAG_UPDATE_CURRENT
            );
            usbManager.requestPermission(device, permissionIntent);
        }
    }

    /**
     * Connect to a specific USB device
     */
    private void connectToDevice(UsbDevice device) {
        // Disconnect any existing connection
        if (currentDevice != null && currentDevice.isConnected()) {
            disconnect();
        }

        // Find the appropriate HUD device implementation
        for (HUDDevice hudDevice : supportedDevices) {
            if (hudDevice.supportsDevice(device)) {
                Log.d(TAG, "Attempting connection with " + hudDevice.getDeviceName() + " driver");

                if (hudDevice.connect(device)) {
                    currentDevice = hudDevice;
                    Log.d(TAG, "Successfully connected to " + hudDevice.getDeviceName());

                    if (connectionListener != null) {
                        connectionListener.onConnected(hudDevice);
                    }
                    return;
                } else {
                    Log.w(TAG, "Failed to connect with " + hudDevice.getDeviceName() +
                          " driver: " + hudDevice.getLastError());
                }
            }
        }

        // No device could connect
        String error = "No compatible HUD driver found for device";
        Log.e(TAG, error);
        if (connectionListener != null) {
            connectionListener.onConnectionFailed(error);
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
