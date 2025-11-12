package com.engindearing.omnihud;

import android.content.Context;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DashboardActivity {

    private static final String TAG = DashboardActivity.class.getSimpleName();

    private final Context context;
    private final MapView mapView;
    private final View dashboardView;
    private final OmniHUDDropDownReceiver receiver;

    // UI Components
    private TextView txtActiveAOIs;
    private TextView txtActiveAlerts;
    private TextView txtCOTModified;
    private LinearLayout cardCOTManagement;
    private LinearLayout cardAOIManagement;
    private LinearLayout cardCreateAlert;
    private LinearLayout cardViewHistory;
    private RecyclerView recentActivityRecyclerView;
    private ImageButton btnSettings;
    private ImageButton btnHelp;

    // Activity tracking
    private static int cotModifiedCount = 0;
    private static List<String> recentActivities = new ArrayList<>();

    public DashboardActivity(Context context, MapView mapView, View dashboardView, OmniHUDDropDownReceiver receiver) {
        this.context = context;
        this.mapView = mapView;
        this.dashboardView = dashboardView;
        this.receiver = receiver;

        initializeUI();
        updateStats();
    }

    private void initializeUI() {
        // TODO: This class is a stub for future AOI/COT management features
        // The current omnihud_dashboard.xml is for HUD connection management
        // These UI elements don't exist in the current layout and are commented out for compilation

        // Header buttons (these exist in the layout)
        btnSettings = dashboardView.findViewById(R.id.btnSettings);
        btnHelp = dashboardView.findViewById(R.id.btnHelp);

        if (btnSettings != null && btnHelp != null) {
            setupListeners();
        }
    }

    private void setupListeners() {
        // TODO: Quick action cards not implemented yet
        // Only setting up header buttons that exist in current layout

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(context, "Settings - Coming Soon", Toast.LENGTH_SHORT).show();
            }
        });

        btnHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHelp();
            }
        });
    }

    public void updateStats() {
        // TODO: Stats UI not implemented in current HUD-focused dashboard
        // Just logging for now
        int aoiCount = getAOICount();
        int alertCount = getActiveAlertCount();

        Log.d(TAG, "Dashboard stats - AOIs: " + aoiCount + ", Alerts: " + alertCount + ", COT: " + cotModifiedCount);
    }

    private int getAOICount() {
        try {
            // Get the Drawing Objects group where shapes are stored
            MapGroup drawingGroup = mapView.getRootGroup().findMapGroup("Drawing Objects");
            if (drawingGroup == null) {
                Log.d(TAG, "Drawing Objects group not found");
                return 0;
            }

            int count = 0;
            Collection<com.atakmap.android.maps.MapItem> items = drawingGroup.deepFindItems("type", "shape");
            if (items != null) {
                for (com.atakmap.android.maps.MapItem item : items) {
                    if (item instanceof Shape) {
                        count++;
                        Log.d(TAG, "Found AOI: " + item.getTitle());
                    }
                }
            }

            Log.d(TAG, "Total AOIs found: " + count);
            return count;
        } catch (Exception e) {
            Log.e(TAG, "Error counting AOIs", e);
            return 0;
        }
    }

    private int getActiveAlertCount() {
        // Count AOIs with alerts enabled
        // This would need to track which AOIs have alerts configured
        return 0; // Placeholder
    }

    public static void incrementCOTModified() {
        cotModifiedCount++;
        addActivity("COT marker affiliation modified");
    }

    public static void addActivity(String activity) {
        recentActivities.add(0, activity);
        if (recentActivities.size() > 10) {
            recentActivities.remove(recentActivities.size() - 1);
        }
    }

    private void onCOTManagementClick() {
        Log.d(TAG, "COT Management clicked");
        // TODO: showCOTManagement() not implemented yet
        Toast.makeText(context, "COT Management - Coming Soon", Toast.LENGTH_SHORT).show();
    }

    private void onAOIManagementClick() {
        Log.d(TAG, "AOI Management clicked");
        // TODO: showAOIManagement() not implemented yet
        Toast.makeText(context, "AOI Management - Coming Soon", Toast.LENGTH_SHORT).show();
    }

    private void onCreateAlertClick() {
        Toast.makeText(context, "Create Alert - Select an AOI first", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Create Alert clicked");
    }

    private void onViewHistoryClick() {
        StringBuilder history = new StringBuilder("Recent Activity:\n\n");
        if (recentActivities.isEmpty()) {
            history.append("No recent activity");
        } else {
            for (int i = 0; i < Math.min(5, recentActivities.size()); i++) {
                history.append("• ").append(recentActivities.get(i)).append("\n");
            }
        }
        Toast.makeText(context, history.toString(), Toast.LENGTH_LONG).show();
        Log.d(TAG, "View History clicked");
    }

    private void showHelp() {
        String helpText = "OmniHUD Dashboard Help:\n\n" +
                "• COT Management - Modify marker affiliations\n" +
                "• AOI Management - Manage areas of interest\n" +
                "• Create Alert - Set up geofence alerts\n" +
                "• View History - See recent activities\n\n" +
                "Stats show active AOIs, alerts, and modified COT markers.";

        Toast.makeText(context, helpText, Toast.LENGTH_LONG).show();
    }
}
