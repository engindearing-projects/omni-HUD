package com.engindearing.omnihud;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class OmniHUDDropDownReceiver extends DropDownReceiver implements DropDown.OnStateListener {

    public static final String TAG = OmniHUDDropDownReceiver.class.getSimpleName();
    public static final String SHOW_PLUGIN = "com.engindearing.omnihud.SHOW_PLUGIN";

    private final Context pluginContext;
    private final MapView mapView;
    private final View dashboardView;

    // USB Communication
    private USBCommunicationManager usbManager;
    private Handler streamingHandler;
    private Runnable streamingRunnable;
    private boolean isStreaming = false;

    // Map Event Handling (best practices)
    private MapEventDispatcher eventDispatcher;
    private MapEventDispatcher.MapEventDispatchListener selfPositionListener;
    private MapEventDispatcher.MapEventDispatchListener itemTrackingListener;

    // UI Components
    private TextView txtConnectionStatus;
    private TextView txtDeviceInfo;
    private Spinner spinnerHUDDevices;
    private Button btnConnect;
    private Button btnDisconnect;
    private Spinner spinnerStreamType;
    private Spinner spinnerUpdateRate;
    private SwitchCompat switchEnableStreaming;
    private TextView txtHUDPreview;
    private Button btnRefreshDevices;
    private Button btnTestConnection;
    private ImageButton btnSettings;
    private ImageButton btnHelp;

    // Device list
    private List<UsbDevice> availableDevices;
    private ReadableSpinnerAdapter deviceAdapter;

    public OmniHUDDropDownReceiver(final MapView mapView, final Context context, View dashboardView) {
        super(mapView);
        // Use provided context, fallback to MapView's context if null
        this.pluginContext = (context != null) ? context : mapView.getContext();
        this.mapView = mapView;
        this.dashboardView = dashboardView;

        // Initialize MapEventDispatcher (ATAK best practices)
        eventDispatcher = mapView.getMapEventDispatcher();
        setupMapEventListeners();

        // Initialize USB communication manager
        usbManager = new USBCommunicationManager(pluginContext);
        usbManager.setConnectionListener(new USBCommunicationManager.ConnectionListener() {
            @Override
            public void onConnected(HUDDevice device) {
                updateConnectionStatus(true, device);
                Toast.makeText(pluginContext, "Connected to " + device.getDeviceName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDisconnected() {
                updateConnectionStatus(false, null);
                stopStreaming();
                Toast.makeText(pluginContext, "HUD disconnected", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onConnectionFailed(String error) {
                updateConnectionStatus(false, null);
                Toast.makeText(pluginContext, "Connection failed: " + error, Toast.LENGTH_LONG).show();
                Log.e(TAG, "Connection failed: " + error);
            }

            @Override
            public void onDeviceAttached(UsbDevice device) {
                Toast.makeText(pluginContext, "USB device attached", Toast.LENGTH_SHORT).show();
                refreshDeviceList();
            }

            @Override
            public void onDeviceDetached() {
                refreshDeviceList();
            }
        });

        // Initialize streaming handler
        streamingHandler = new Handler(Looper.getMainLooper());

        initializeUI();
        refreshDeviceList();

        Log.d(TAG, "OmniHUDDropDownReceiver initialized");
    }

    private void initializeUI() {
        // Connection status
        txtConnectionStatus = dashboardView.findViewById(R.id.txtConnectionStatus);
        txtDeviceInfo = dashboardView.findViewById(R.id.txtDeviceInfo);

        // Device selection
        spinnerHUDDevices = dashboardView.findViewById(R.id.spinnerHUDDevices);
        btnConnect = dashboardView.findViewById(R.id.btnConnect);
        btnDisconnect = dashboardView.findViewById(R.id.btnDisconnect);

        // Streaming configuration
        spinnerStreamType = dashboardView.findViewById(R.id.spinnerStreamType);
        spinnerUpdateRate = dashboardView.findViewById(R.id.spinnerUpdateRate);
        switchEnableStreaming = dashboardView.findViewById(R.id.switchEnableStreaming);

        // Preview
        txtHUDPreview = dashboardView.findViewById(R.id.txtHUDPreview);

        // Advanced buttons
        btnRefreshDevices = dashboardView.findViewById(R.id.btnRefreshDevices);
        btnTestConnection = dashboardView.findViewById(R.id.btnTestConnection);

        // Header buttons
        btnSettings = dashboardView.findViewById(R.id.btnSettings);
        btnHelp = dashboardView.findViewById(R.id.btnHelp);

        // Setup device spinner
        // Use MapView's Activity context for Spinner to avoid BadTokenException when showing dialog
        // Use ReadableSpinnerAdapter for proper text visibility
        availableDevices = new ArrayList<>();
        deviceAdapter = new ReadableSpinnerAdapter(mapView.getContext(), new ArrayList<String>());
        if (spinnerHUDDevices != null) {
            spinnerHUDDevices.setAdapter(deviceAdapter);
        }

        // Setup stream type spinner with readable adapter
        if (spinnerStreamType != null) {
            String[] streamTypes = pluginContext.getResources().getStringArray(R.array.stream_types);
            List<String> streamTypeList = Arrays.asList(streamTypes);
            ReadableSpinnerAdapter streamTypeAdapter = new ReadableSpinnerAdapter(mapView.getContext(), streamTypeList);
            spinnerStreamType.setAdapter(streamTypeAdapter);
        }

        // Setup update rate spinner with readable adapter
        if (spinnerUpdateRate != null) {
            String[] updateRates = pluginContext.getResources().getStringArray(R.array.update_rates);
            List<String> updateRateList = Arrays.asList(updateRates);
            ReadableSpinnerAdapter updateRateAdapter = new ReadableSpinnerAdapter(mapView.getContext(), updateRateList);
            spinnerUpdateRate.setAdapter(updateRateAdapter);
        }

        setupListeners();

        Log.d(TAG, "UI initialized");
    }

    private void setupListeners() {
        // Add null checks for all UI elements before setting listeners
        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> connectToSelectedDevice());
        }

        if (btnDisconnect != null) {
            btnDisconnect.setOnClickListener(v -> disconnectDevice());
        }

        if (btnRefreshDevices != null) {
            btnRefreshDevices.setOnClickListener(v -> refreshDeviceList());
        }

        if (btnTestConnection != null) {
            btnTestConnection.setOnClickListener(v -> sendTestData());
        }

        if (switchEnableStreaming != null) {
            switchEnableStreaming.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    startStreaming();
                } else {
                    stopStreaming();
                }
            });
        }

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                Toast.makeText(pluginContext, "Settings coming soon", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnHelp != null) {
            btnHelp.setOnClickListener(v -> {
                showHelpDialog();
            });
        }
    }

    /**
     * Setup MapEventDispatcher listeners following ATAK best practices
     * Instead of polling, we use event-driven updates for efficiency
     */
    private void setupMapEventListeners() {
        // Listener for self position updates (efficient replacement for polling)
        // Subscribe to MAP_MOVED event to detect when self position changes
        selfPositionListener = new MapEventDispatcher.MapEventDispatchListener() {
            @Override
            public void onMapEvent(MapEvent event) {
                if (isStreaming && usbManager.isConnected()) {
                    // Only send updates when streaming is active
                    sendCurrentPositionToHUD();
                }
            }
        };

        // Listener for tracking COT items (ITEM_ADDED, ITEM_REMOVED, ITEM_REFRESH)
        // This allows the HUD to display nearby units in real-time
        itemTrackingListener = new MapEventDispatcher.MapEventDispatchListener() {
            @Override
            public void onMapEvent(MapEvent event) {
                MapItem item = event.getItem();
                String eventType = event.getType();

                if (item instanceof PointMapItem) {
                    PointMapItem pointItem = (PointMapItem) item;
                    String callsign = pointItem.getTitle();
                    String uid = pointItem.getUID();

                    switch (eventType) {
                        case MapEvent.ITEM_ADDED:
                            Log.d(TAG, "COT Item added: " + callsign + " (UID: " + uid + ")");
                            // Could send to HUD for nearby unit tracking
                            break;

                        case MapEvent.ITEM_REMOVED:
                            Log.d(TAG, "COT Item removed: " + callsign + " (UID: " + uid + ")");
                            // Could remove from HUD display
                            break;

                        case MapEvent.ITEM_REFRESH:
                            // Item properties changed (position, affiliation, etc.)
                            Log.d(TAG, "COT Item refreshed: " + callsign);
                            // Could update HUD display
                            break;
                    }
                }
            }
        };

        // Register listeners following best practices:
        // - MAP_MOVED for self position updates (event-driven instead of polling)
        // - ITEM_ADDED, ITEM_REMOVED, ITEM_REFRESH for COT item tracking
        eventDispatcher.addMapEventListener(MapEvent.MAP_MOVED, selfPositionListener);
        eventDispatcher.addMapEventListener(MapEvent.ITEM_ADDED, itemTrackingListener);
        eventDispatcher.addMapEventListener(MapEvent.ITEM_REMOVED, itemTrackingListener);
        eventDispatcher.addMapEventListener(MapEvent.ITEM_REFRESH, itemTrackingListener);

        Log.d(TAG, "MapEventDispatcher listeners registered (best practices)");
    }

    private void refreshDeviceList() {
        if (usbManager == null || deviceAdapter == null) {
            Log.e(TAG, "Cannot refresh device list - usbManager or deviceAdapter is null");
            return;
        }

        // DIAGNOSTIC: Get ALL USB devices, not just filtered ones
        android.hardware.usb.UsbManager sysUsbManager =
            (android.hardware.usb.UsbManager) pluginContext.getSystemService(Context.USB_SERVICE);

        if (sysUsbManager != null) {
            HashMap<String, UsbDevice> allDevices = sysUsbManager.getDeviceList();
            Log.d(TAG, "=== USB DEVICE DIAGNOSTIC ===");
            Log.d(TAG, "Total USB devices found: " + allDevices.size());

            for (UsbDevice device : allDevices.values()) {
                Log.d(TAG, String.format("Device: %s | VID=0x%04X | PID=0x%04X | Manufacturer=%s | Product=%s | HasPermission=%s",
                    device.getDeviceName(),
                    device.getVendorId(),
                    device.getProductId(),
                    device.getManufacturerName(),
                    device.getProductName(),
                    sysUsbManager.hasPermission(device)));
            }
            Log.d(TAG, "=== END DIAGNOSTIC ===");
        }

        availableDevices = usbManager.getAvailableDevices();

        deviceAdapter.clear();
        if (availableDevices.isEmpty()) {
            deviceAdapter.add("No USB devices found");
            if (btnConnect != null) {
                btnConnect.setEnabled(false);
            }
        } else {
            for (UsbDevice device : availableDevices) {
                String deviceName = device.getProductName() != null ? device.getProductName() :
                                   "USB Device " + String.format("0x%04X:0x%04X",
                                                                device.getVendorId(),
                                                                device.getProductId());
                deviceAdapter.add(deviceName);
            }
            if (btnConnect != null) {
                btnConnect.setEnabled(true);
            }
        }
        deviceAdapter.notifyDataSetChanged();

        Log.d(TAG, "Refreshed device list: " + availableDevices.size() + " devices found");
    }

    private void connectToSelectedDevice() {
        if (spinnerHUDDevices == null || usbManager == null || availableDevices == null) {
            Log.e(TAG, "Cannot connect - UI or manager not initialized");
            return;
        }

        int selectedPosition = spinnerHUDDevices.getSelectedItemPosition();

        if (selectedPosition < 0 || selectedPosition >= availableDevices.size()) {
            Toast.makeText(pluginContext, "Please select a valid device", Toast.LENGTH_SHORT).show();
            return;
        }

        UsbDevice selectedDevice = availableDevices.get(selectedPosition);
        Log.d(TAG, "Connecting to device: " + selectedDevice.getDeviceName());

        usbManager.requestConnectionToDevice(selectedDevice);
    }

    private void disconnectDevice() {
        if (usbManager != null) {
            usbManager.disconnect();
        }
        stopStreaming();
    }

    private void updateConnectionStatus(boolean connected, HUDDevice device) {
        if (connected && device != null) {
            if (txtConnectionStatus != null) {
                txtConnectionStatus.setText("Connected");
                txtConnectionStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
            }
            if (txtDeviceInfo != null) {
                txtDeviceInfo.setText(device.getStatusString());
            }

            if (btnConnect != null) btnConnect.setEnabled(false);
            if (btnDisconnect != null) btnDisconnect.setEnabled(true);
            if (switchEnableStreaming != null) switchEnableStreaming.setEnabled(true);
            if (btnTestConnection != null) btnTestConnection.setEnabled(true);
        } else {
            if (txtConnectionStatus != null) {
                txtConnectionStatus.setText("Not Connected");
                txtConnectionStatus.setTextColor(Color.parseColor("#FF6B6B")); // Red
            }
            if (txtDeviceInfo != null) {
                txtDeviceInfo.setText("No device connected");
            }

            if (btnConnect != null) btnConnect.setEnabled(availableDevices != null && !availableDevices.isEmpty());
            if (btnDisconnect != null) btnDisconnect.setEnabled(false);
            if (switchEnableStreaming != null) {
                switchEnableStreaming.setEnabled(false);
                switchEnableStreaming.setChecked(false);
            }
            if (btnTestConnection != null) btnTestConnection.setEnabled(false);
        }
    }

    /**
     * Start streaming position data to HUD using event-driven updates (best practices)
     * Primary updates come from MAP_MOVED events (event-driven)
     * Fallback polling at low rate ensures updates even when map is stationary
     */
    private void startStreaming() {
        if (usbManager == null || !usbManager.isConnected()) {
            Toast.makeText(pluginContext, "Not connected to HUD device", Toast.LENGTH_SHORT).show();
            if (switchEnableStreaming != null) {
                switchEnableStreaming.setChecked(false);
            }
            return;
        }

        isStreaming = true;

        // Event-driven updates via MAP_MOVED are primary (already registered in setupMapEventListeners)
        // Add fallback polling at low rate for GPS updates when map is stationary
        long fallbackIntervalMs = 1000; // 1 Hz fallback (much lower than old polling rate)

        streamingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isStreaming && usbManager.isConnected()) {
                    // Fallback update (most updates come from MAP_MOVED events)
                    sendCurrentPositionToHUD();
                    streamingHandler.postDelayed(this, fallbackIntervalMs);
                }
            }
        };

        // Start fallback polling
        streamingHandler.post(streamingRunnable);

        Toast.makeText(pluginContext, "Started streaming data to HUD (event-driven)", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Started event-driven streaming with 1Hz fallback (ATAK best practices)");
    }

    private void stopStreaming() {
        isStreaming = false;
        if (streamingRunnable != null && streamingHandler != null) {
            streamingHandler.removeCallbacks(streamingRunnable);
        }
        if (txtHUDPreview != null) {
            txtHUDPreview.setText("Streaming stopped");
        }
        Log.d(TAG, "Stopped streaming");
    }

    private void sendCurrentPositionToHUD() {
        if (usbManager == null || mapView == null) {
            Log.e(TAG, "Cannot send position - usbManager or mapView is null");
            return;
        }

        try {
            // Get self marker from ATAK
            PointMapItem selfMarker = mapView.getSelfMarker();
            if (selfMarker == null) {
                if (txtHUDPreview != null) {
                    txtHUDPreview.setText("ERROR: Cannot get self position");
                }
                return;
            }

            GeoPoint selfPoint = selfMarker.getPoint();
            String callsign = mapView.getDeviceCallsign();

            double lat = selfPoint.getLatitude();
            double lon = selfPoint.getLongitude();
            double alt = selfPoint.getAltitude();
            // Get heading from ATAK MapView metadata (device bearing from GPS/sensors)
            double heading = mapView.getMapData().getMetaDouble("mockLocationBearing", 0.0);

            // Send to HUD
            boolean success = usbManager.sendPosition(lat, lon, alt, heading, callsign);

            // Update preview
            if (success) {
                String previewText = String.format(
                    "STREAMING TO HUD:\n" +
                    "Callsign: %s\n" +
                    "Lat: %.6f°\n" +
                    "Lon: %.6f°\n" +
                    "Alt: %.1f m\n" +
                    "Hdg: %.1f°",
                    callsign, lat, lon, alt, heading
                );
                if (txtHUDPreview != null) {
                    txtHUDPreview.setText(previewText);
                }
            } else {
                if (txtHUDPreview != null) {
                    txtHUDPreview.setText("ERROR: Failed to send data");
                }
                Log.e(TAG, "Failed to send position to HUD");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error sending position to HUD", e);
            if (txtHUDPreview != null) {
                txtHUDPreview.setText("ERROR: " + e.getMessage());
            }
        }
    }

    private void sendTestData() {
        if (usbManager == null || !usbManager.isConnected()) {
            Toast.makeText(pluginContext, "Not connected to HUD device", Toast.LENGTH_SHORT).show();
            return;
        }

        // Send test position data
        String callsign = mapView != null ? mapView.getDeviceCallsign() : "TEST";
        boolean success = usbManager.sendPosition(39.2, -77.0, 121.0, 270.0, callsign);

        if (success) {
            Toast.makeText(pluginContext, "Test data sent successfully", Toast.LENGTH_SHORT).show();
            if (txtHUDPreview != null) {
                txtHUDPreview.setText("TEST DATA SENT:\nLat: 39.2°\nLon: -77.0°\nAlt: 121.0 m\nHdg: 270.0°");
            }
        } else {
            Toast.makeText(pluginContext, "Failed to send test data", Toast.LENGTH_SHORT).show();
        }
    }

    private void showHelpDialog() {
        Toast.makeText(pluginContext,
            "1. Connect HUD via USB-C\n" +
            "2. Select device and click Connect\n" +
            "3. Enable streaming to send ATAK data to HUD",
            Toast.LENGTH_LONG).show();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive called with intent: " + intent);
        final String action = intent.getAction();
        if (action == null) {
            Log.d(TAG, "Action is null");
            return;
        }

        if (action.equals(SHOW_PLUGIN)) {
            // Check if already open
            if (!isClosed()) {
                unhideDropDown();
                return;
            }

            showDropDown(dashboardView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false, this);
            setAssociationKey("omniHUDPreference");

            // Refresh device list when opening
            refreshDeviceList();
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }

    @Override
    protected void disposeImpl() {
        // Stop streaming first
        stopStreaming();

        // Unregister MapEventDispatcher listeners (ATAK best practices)
        // Always clean up event listeners to prevent memory leaks
        if (eventDispatcher != null) {
            if (selfPositionListener != null) {
                eventDispatcher.removeMapEventListener(MapEvent.MAP_MOVED, selfPositionListener);
                Log.d(TAG, "Unregistered MAP_MOVED listener");
            }
            if (itemTrackingListener != null) {
                eventDispatcher.removeMapEventListener(MapEvent.ITEM_ADDED, itemTrackingListener);
                eventDispatcher.removeMapEventListener(MapEvent.ITEM_REMOVED, itemTrackingListener);
                eventDispatcher.removeMapEventListener(MapEvent.ITEM_REFRESH, itemTrackingListener);
                Log.d(TAG, "Unregistered item tracking listeners");
            }
        }

        // Dispose USB manager
        if (usbManager != null) {
            usbManager.dispose();
        }

        Log.d(TAG, "OmniHUDDropDownReceiver disposed (all listeners cleaned up)");
    }
}
