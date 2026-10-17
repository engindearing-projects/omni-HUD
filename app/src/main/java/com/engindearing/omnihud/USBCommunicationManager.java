package com.engindearing.omnihud;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import com.atakmap.coremap.log.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Lists USB devices, asks Android for permission and owns the HUD link.
 *
 * Everything Android attributes to an app (the broadcast receiver and the permission
 * PendingIntent) goes through ATAK's own context. The plugin context reports the plugin's
 * package name, and ATAK's process may not create a PendingIntent for another package, so in
 * 0.1 the permission request failed before Android could show its prompt.
 */
public class USBCommunicationManager {

    private static final String TAG = USBCommunicationManager.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "com.engindearing.omnihud.USB_PERMISSION";

    public interface ConnectionListener {
        void onConnected(HUDDevice device);
        void onDisconnected();
        void onConnectionFailed(String error);
        void onDeviceAttached(UsbDevice device);
        void onDeviceDetached();
        /** Link activity worth showing, such as send results or data from the device. Called off the UI thread. */
        void onLinkEvent(String message);
    }

    private final Context hostContext;
    private final UsbManager usbManager;
    private final UsbEventLog eventLog = new UsbEventLog(150);
    private final ECOTIDevice ecoti;
    private volatile HUDDevice currentDevice;
    private volatile String connectedDeviceName;
    private volatile ConnectionListener connectionListener;
    private boolean receiverRegistered;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (ACTION_USB_PERMISSION.equals(action)) {
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                log("USB permission " + (granted ? "granted" : "denied") + " for " + name(device));
                if (granted && device != null) {
                    connectToDevice(device);
                } else {
                    failed("USB permission was not granted for " + name(device) + ".");
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                log("Attached: " + name(device));
                ConnectionListener l = connectionListener;
                if (device != null && l != null) {
                    l.onDeviceAttached(device);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                log("Detached: " + name(device));
                String connected = connectedDeviceName;
                if (device != null && connected != null && connected.equals(device.getDeviceName())) {
                    disconnect();
                }
                ConnectionListener l = connectionListener;
                if (l != null) {
                    l.onDeviceDetached();
                }
            }
        }
    };

    /**
     * @param hostContext ATAK's context, for example {@code mapView.getContext()}. Not the plugin context.
     */
    // Before Android 13 there is no RECEIVER_NOT_EXPORTED flag, so the receiver is exported there.
    // That is safe: a spoofed "permission granted" broadcast cannot open a device, because
    // ECOTIDevice.connect() checks UsbManager.hasPermission() before it opens anything.
    // ContextCompat's workaround would tie delivery to a permission declared in ATAK's manifest.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public USBCommunicationManager(Context hostContext) {
        if (hostContext == null) {
            throw new IllegalArgumentException("hostContext cannot be null");
        }
        Context app = hostContext.getApplicationContext();
        this.hostContext = app != null ? app : hostContext;
        this.usbManager = (UsbManager) this.hostContext.getSystemService(Context.USB_SERVICE);
        this.ecoti = new ECOTIDevice(usbManager, eventLog);
        ecoti.setLinkListener(message -> {
            ConnectionListener l = connectionListener;
            if (l != null) {
                l.onLinkEvent(message);
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this.hostContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                this.hostContext.registerReceiver(usbReceiver, filter);
            }
            receiverRegistered = true;
        } catch (RuntimeException e) {
            log("Could not register the USB receiver: " + e.getMessage());
        }
        log("USB manager ready in " + this.hostContext.getPackageName()
                + (usbManager == null ? ", but this phone reports no USB host service" : ""));
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    /** Every USB device Android reports, best HUD candidates first. Nothing is filtered out. */
    public List<UsbDevice> getAvailableDevices() {
        List<UsbDevice> devices = new ArrayList<>();
        if (usbManager == null) {
            return devices;
        }
        devices.addAll(usbManager.getDeviceList().values());
        Collections.sort(devices, (a, b) -> {
            int byRank = Integer.compare(rank(a), rank(b));
            return byRank != 0 ? byRank : a.getDeviceName().compareTo(b.getDeviceName());
        });
        StringBuilder sb = new StringBuilder();
        for (UsbDevice d : devices) {
            sb.append(sb.length() == 0 ? ": " : "; ").append(label(d));
        }
        log(devices.size() + " USB device(s) visible" + sb);
        return devices;
    }

    /** One-line label for the device list. */
    public String label(UsbDevice device) {
        return UsbDeviceClassifier.label(UsbDescriptors.from(device));
    }

    /** Multi-line USB details for the dashboard. */
    public String describe(UsbDevice device) {
        UsbDescriptorInfo info = UsbDescriptors.from(device);
        return info.describe()
                + "\n  link: " + UsbLinkPlan.plan(info).describe()
                + "\n  USB permission: " + (usbManager != null && usbManager.hasPermission(device) ? "granted" : "not granted yet");
    }

    /** Ask Android for permission if needed, then connect. The result arrives on the listener. */
    public void requestConnectionToDevice(UsbDevice device) {
        if (usbManager == null) {
            failed("This phone reports no USB host service.");
            return;
        }
        log("Connect requested: " + label(device));
        if (usbManager.hasPermission(device)) {
            connectToDevice(device);
            return;
        }
        try {
            // Explicit package (ATAK's) and FLAG_MUTABLE: Android 14+ rejects mutable implicit
            // PendingIntents, and the USB service has to fill in the device and the grant result.
            Intent intent = new Intent(ACTION_USB_PERMISSION).setPackage(hostContext.getPackageName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent permissionIntent = PendingIntent.getBroadcast(hostContext, 0, intent, flags);
            usbManager.requestPermission(device, permissionIntent);
            log("Asked Android for USB permission");
        } catch (RuntimeException e) {
            failed("Could not ask Android for USB permission: " + e.getMessage());
        }
    }

    private void connectToDevice(UsbDevice device) {
        if (isConnected()) {
            disconnect();
        }
        if (ecoti.connect(device)) {
            currentDevice = ecoti;
            connectedDeviceName = device.getDeviceName();
            ConnectionListener l = connectionListener;
            if (l != null) {
                l.onConnected(ecoti);
            }
        } else {
            currentDevice = null;
            connectedDeviceName = null;
            String error = ecoti.getLastError();
            failed(error != null ? error : "Connection failed.");
        }
    }

    public void disconnect() {
        HUDDevice device = currentDevice;
        currentDevice = null;
        connectedDeviceName = null;
        if (device != null) {
            device.disconnect();
            ConnectionListener l = connectionListener;
            if (l != null) {
                l.onDisconnected();
            }
        }
    }

    public boolean isConnected() {
        HUDDevice device = currentDevice;
        return device != null && device.isConnected();
    }

    public HUDDevice getCurrentDevice() {
        return currentDevice;
    }

    public boolean sendCotData(String cotXml) {
        HUDDevice device = currentDevice;
        if (device == null || !device.isConnected()) {
            Log.w(TAG, "Cannot send data: not connected to HUD device");
            return false;
        }
        return device.sendCotData(cotXml);
    }

    public boolean sendPosition(double lat, double lon, double alt, double heading, String callsign) {
        HUDDevice device = currentDevice;
        if (device == null || !device.isConnected()) {
            Log.w(TAG, "Cannot send position: not connected to HUD device");
            return false;
        }
        return device.sendPosition(lat, lon, alt, heading, callsign);
    }

    /**
     * Plain-text report for the user to paste into an email: phone, ATAK version, every USB device
     * with its full descriptors, the last connection attempt and recent USB events. No location data.
     */
    public String buildReport() {
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss'Z'", Locale.US);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        StringBuilder sb = new StringBuilder();
        sb.append("OmniHUD USB report, ").append(utc.format(new Date())).append('\n');
        sb.append("Plugin: ").append(BuildConfig.VERSION_NAME).append('\n');
        sb.append("Phone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("ATAK: ").append(hostContext.getPackageName()).append(' ').append(hostVersion()).append('\n');

        List<UsbDevice> devices = getAvailableDevices();
        sb.append("\nUSB devices visible to Android: ").append(devices.size()).append('\n');
        int n = 1;
        for (UsbDevice d : devices) {
            UsbDescriptorInfo info = UsbDescriptors.from(d);
            sb.append('\n').append(n++).append(". ").append(UsbDeviceClassifier.label(info)).append('\n');
            sb.append(info.describe()).append('\n');
            sb.append("  link: ").append(UsbLinkPlan.plan(info).describe()).append('\n');
            sb.append("  USB permission: ").append(usbManager.hasPermission(d) ? "granted" : "not granted").append('\n');
        }
        sb.append("\nConnection\n").append(ecoti.diagnostics());
        sb.append("\nRecent USB events\n");
        for (String line : eventLog.snapshot()) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public void dispose() {
        disconnect();
        if (receiverRegistered) {
            try {
                hostContext.unregisterReceiver(usbReceiver);
            } catch (RuntimeException e) {
                Log.e(TAG, "Error unregistering the USB receiver", e);
            }
            receiverRegistered = false;
        }
        Log.d(TAG, "USBCommunicationManager disposed");
    }

    private int rank(UsbDevice device) {
        return UsbDeviceClassifier.rank(UsbDescriptors.from(device));
    }

    private String hostVersion() {
        try {
            PackageInfo pi = hostContext.getPackageManager().getPackageInfo(hostContext.getPackageName(), 0);
            return pi.versionName != null ? pi.versionName : "unknown version";
        } catch (Exception e) {
            return "unknown version";
        }
    }

    private void failed(String error) {
        log("Failed: " + error);
        ConnectionListener l = connectionListener;
        if (l != null) {
            l.onConnectionFailed(error);
        }
    }

    private void log(String message) {
        Log.i(TAG, message);
        eventLog.add(message);
    }

    private static String name(UsbDevice device) {
        if (device == null) {
            return "unknown device";
        }
        return String.format(Locale.US, "%04X:%04X %s", device.getVendorId(), device.getProductId(),
                device.getDeviceName());
    }
}
