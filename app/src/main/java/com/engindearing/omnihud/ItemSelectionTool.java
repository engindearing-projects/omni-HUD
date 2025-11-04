package com.engindearing.omnihud;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import com.atak.plugins.impl.AbstractPluginTool;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.log.Log;

import gov.tak.api.util.Disposable;

/**
 * Item Selection Tool demonstrating ATAK MapEventDispatcher best practices
 *
 * This tool uses the push/pop listener stack pattern to temporarily intercept
 * map click events for item selection without interfering with other handlers.
 *
 * BEST PRACTICES DEMONSTRATED:
 * 1. Push listener stack when tool activates
 * 2. Clear specific event types (ITEM_CLICK) to suppress other handlers
 * 3. Add custom listener for temporary event handling
 * 4. Pop listener stack when tool deactivates to restore previous state
 *
 * This pattern is essential for tools that need exclusive event handling
 * (e.g., selecting an item for further processing) without permanently
 * affecting the event system.
 */
public class ItemSelectionTool extends AbstractPluginTool implements Disposable {

    private static final String TAG = ItemSelectionTool.class.getSimpleName();

    private final MapView mapView;
    private final Context context;
    private MapEventDispatcher eventDispatcher;
    private MapEventDispatcher.MapEventDispatchListener itemClickListener;
    private boolean isActive = false;

    // Callback interface for when an item is selected
    public interface ItemSelectionCallback {
        void onItemSelected(MapItem item);
        void onSelectionCancelled();
    }

    private ItemSelectionCallback callback;

    public ItemSelectionTool(Context context, MapView mapView) {
        super(context,
              "Item Selection",
              "Select Map Item",
              context.getResources().getDrawable(R.drawable.ic_launcher),
              "");

        this.context = context;
        this.mapView = mapView;
        this.eventDispatcher = mapView.getMapEventDispatcher();

        Log.d(TAG, "ItemSelectionTool created (demonstrates push/pop best practices)");
    }

    /**
     * Activate the tool and begin listening for item selection
     * Uses PUSH/POP listener stack pattern (ATAK best practices)
     *
     * @param callback Callback to receive selected item
     */
    public void activate(ItemSelectionCallback callback) {
        if (isActive) {
            Log.w(TAG, "Tool already active");
            return;
        }

        this.callback = callback;
        isActive = true;

        // BEST PRACTICE: Push the listener stack
        // This preserves the current listener state so we can restore it later
        eventDispatcher.pushListeners();
        Log.d(TAG, "Pushed listener stack");

        // BEST PRACTICE: Clear the specific event type we want to handle exclusively
        // This suppresses other handlers (e.g., Radial Menu) while our tool is active
        eventDispatcher.clearListeners(MapEvent.ITEM_CLICK);
        Log.d(TAG, "Cleared ITEM_CLICK listeners from top of stack");

        // BEST PRACTICE: Add our custom listener to handle the event
        itemClickListener = new MapEventDispatcher.MapEventDispatchListener() {
            @Override
            public void onMapEvent(MapEvent event) {
                handleItemClick(event);
            }
        };

        eventDispatcher.addMapEventListener(MapEvent.ITEM_CLICK, itemClickListener);
        Log.d(TAG, "Added custom ITEM_CLICK listener");

        Toast.makeText(context, "Tap a map item to select it", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "ItemSelectionTool activated (push/pop pattern)");
    }

    /**
     * Deactivate the tool and stop listening for item selection
     * BEST PRACTICE: Pop listener stack to restore previous state
     */
    public void deactivate() {
        if (!isActive) {
            return;
        }

        isActive = false;

        // BEST PRACTICE: Pop the listener stack
        // This restores the listener subscription state to before we called pushListeners()
        // All other handlers (like Radial Menu) will work again
        eventDispatcher.popListeners();
        Log.d(TAG, "Popped listener stack - restored previous listener state");

        if (callback != null) {
            callback.onSelectionCancelled();
            callback = null;
        }

        Log.d(TAG, "ItemSelectionTool deactivated");
    }

    /**
     * Handle item click event
     */
    private void handleItemClick(MapEvent event) {
        MapItem item = event.getItem();

        if (item == null) {
            Log.d(TAG, "No item in click event");
            return;
        }

        Log.d(TAG, "Item clicked: " + item.getTitle() + " (UID: " + item.getUID() + ")");

        // Process the selected item
        if (item instanceof PointMapItem) {
            PointMapItem pointItem = (PointMapItem) item;
            String callsign = pointItem.getTitle();
            String uid = pointItem.getUID();

            Toast.makeText(context,
                "Selected: " + callsign,
                Toast.LENGTH_SHORT).show();

            Log.d(TAG, "Selected PointMapItem: " + callsign + " (UID: " + uid + ")");

            // Notify callback
            if (callback != null) {
                callback.onItemSelected(item);
            }

            // Deactivate tool after selection
            deactivate();
        } else {
            Log.d(TAG, "Selected item is not a PointMapItem: " + item.getClass().getSimpleName());
        }
    }

    @Override
    public void dispose() {
        // BEST PRACTICE: Always clean up when disposing
        if (isActive) {
            deactivate();
        }
        Log.d(TAG, "ItemSelectionTool disposed");
    }

    /**
     * Example usage in another component:
     *
     * ItemSelectionTool selectionTool = new ItemSelectionTool(context, mapView);
     *
     * selectionTool.activate(new ItemSelectionTool.ItemSelectionCallback() {
     *     @Override
     *     public void onItemSelected(MapItem item) {
     *         // Process selected item (e.g., add to alert, track on HUD, etc.)
     *         Log.d(TAG, "User selected: " + item.getTitle());
     *     }
     *
     *     @Override
     *     public void onSelectionCancelled() {
     *         Log.d(TAG, "Selection cancelled");
     *     }
     * });
     *
     * // When done or cancelled:
     * selectionTool.deactivate();
     */
}
