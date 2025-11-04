# ATAK Plugin Best Practices - Map Event Handling

## Overview

This document describes the ATAK Map Event Handling best practices implemented in the omni-HUD plugin. These practices ensure the plugin is robust, efficient, and compatible with both ATAK (military) and ATAK-CIV (civilian) versions.

## Table of Contents

1. [MapEventDispatcher Overview](#mapeventdispatcher-overview)
2. [Event-Driven vs Polling](#event-driven-vs-polling)
3. [Push/Pop Listener Stack Pattern](#pushpop-listener-stack-pattern)
4. [Listener Registration and Cleanup](#listener-registration-and-cleanup)
5. [Implementation Examples](#implementation-examples)
6. [Common Event Types](#common-event-types)

---

## MapEventDispatcher Overview

The `MapEventDispatcher` is ATAK's primary event bus for all high-level map and item events. It provides an efficient, event-driven way to respond to map interactions and item changes.

### Obtaining MapEventDispatcher

```java
MapView mapView = ...;
MapEventDispatcher eventDispatcher = mapView.getMapEventDispatcher();
```

**Implementation in omni-HUD:**
- See `OmniHUDDropDownReceiver.java:79`

---

## Event-Driven vs Polling

### ❌ BAD PRACTICE: Polling (Old Implementation)

```java
// DON'T DO THIS - Inefficient polling every 200ms
Handler handler = new Handler();
Runnable runnable = new Runnable() {
    @Override
    public void run() {
        sendCurrentPositionToHUD();
        handler.postDelayed(this, 200); // Polls 5 times per second!
    }
};
```

**Problems:**
- Wastes CPU/battery even when nothing changes
- Fixed update rate regardless of actual changes
- Not reactive to real-time events

### ✅ GOOD PRACTICE: Event-Driven (New Implementation)

```java
// DO THIS - React to actual map events
MapEventDispatcher.MapEventDispatchListener listener = new MapEventDispatcher.MapEventDispatchListener() {
    @Override
    public void onMapEvent(MapEvent event) {
        // Only called when something actually changes
        sendCurrentPositionToHUD();
    }
};

eventDispatcher.addMapEventListener(MapEvent.MAP_MOVED, listener);
```

**Benefits:**
- Updates only when position actually changes
- Immediate response to events (lower latency)
- Reduced CPU/battery usage
- More responsive user experience

**Implementation in omni-HUD:**
- See `OmniHUDDropDownReceiver.java:190-246` (`setupMapEventListeners()`)
- Position updates now use `MAP_MOVED` events with 1Hz fallback

---

## Push/Pop Listener Stack Pattern

The MapEventDispatcher uses a **stack-based data structure** to manage listeners. This enables temporary event suppression for tools that need exclusive event handling.

### When to Use Push/Pop

Use this pattern when:
- Your tool needs to temporarily intercept map clicks/taps
- You want to prevent other handlers (like Radial Menu) from interfering
- You need exclusive event handling during a specific workflow
- You want to restore the previous event state when done

### Example Use Cases

1. **Item Selection Tool**: Select a map item for processing
2. **Drawing Tool**: Create shapes without triggering item clicks
3. **Measurement Tool**: Measure distances without opening menus
4. **Custom Workflows**: Any temporary, exclusive event handling

### How It Works

```java
// 1. PUSH - Save current listener state
eventDispatcher.pushListeners();

// 2. CLEAR - Remove specific event handlers from top of stack
eventDispatcher.clearListeners(MapEvent.ITEM_CLICK);

// 3. ADD - Register your custom handler
eventDispatcher.addMapEventListener(MapEvent.ITEM_CLICK, myCustomListener);

// ... User interacts with map ...

// 4. POP - Restore previous listener state
eventDispatcher.popListeners();
```

**Visual Representation:**

```
Initial State:
[RadialMenuHandler, DefaultClickHandler] ← Top of stack

After pushListeners():
[RadialMenuHandler, DefaultClickHandler] ← Copy (now inactive)
[RadialMenuHandler, DefaultClickHandler] ← Top of stack (active)

After clearListeners(ITEM_CLICK):
[RadialMenuHandler, DefaultClickHandler] ← Copy (inactive)
[] ← Top of stack (active, but cleared)

After addMapEventListener(...):
[RadialMenuHandler, DefaultClickHandler] ← Copy (inactive)
[MyCustomHandler] ← Top of stack (active)

After popListeners():
[RadialMenuHandler, DefaultClickHandler] ← Top of stack (restored!)
```

**Implementation in omni-HUD:**
- See `ItemSelectionTool.java` for complete example
- `activate()` method: Push/clear/add pattern (lines 68-95)
- `deactivate()` method: Pop to restore (lines 100-118)

---

## Listener Registration and Cleanup

### Registration Patterns

**1. Global Subscription (all events)**
```java
eventDispatcher.addMapEventListener(listener);
```

**2. Event Type Specific (recommended)**
```java
eventDispatcher.addMapEventListener(MapEvent.ITEM_CLICK, listener);
```

**3. Item Specific**
```java
eventDispatcher.addMapItemEventListener(specificMapItem, listener);
```

### Cleanup - CRITICAL for Memory Leaks

**Always unregister listeners in disposal methods:**

```java
@Override
protected void disposeImpl() {
    // ALWAYS clean up listeners
    if (eventDispatcher != null && myListener != null) {
        eventDispatcher.removeMapEventListener(MapEvent.MAP_MOVED, myListener);
    }
}
```

**Implementation in omni-HUD:**
- See `OmniHUDDropDownReceiver.java:467-492` (`disposeImpl()`)
- Unregisters all listeners: MAP_MOVED, ITEM_ADDED, ITEM_REMOVED, ITEM_REFRESH

---

## Implementation Examples

### Example 1: Position Streaming (Event-Driven)

**File:** `OmniHUDDropDownReceiver.java`

```java
// Setup listener (lines 190-201)
selfPositionListener = new MapEventDispatcher.MapEventDispatchListener() {
    @Override
    public void onMapEvent(MapEvent event) {
        if (isStreaming && usbManager.isConnected()) {
            sendCurrentPositionToHUD();
        }
    }
};

// Register for MAP_MOVED events (line 240)
eventDispatcher.addMapEventListener(MapEvent.MAP_MOVED, selfPositionListener);

// Cleanup on disposal (line 475)
eventDispatcher.removeMapEventListener(MapEvent.MAP_MOVED, selfPositionListener);
```

**Benefits:**
- Updates sent immediately when position changes
- No wasted updates when stationary
- Much more efficient than old 5Hz polling

### Example 2: COT Item Tracking

**File:** `OmniHUDDropDownReceiver.java`

```java
// Setup listener to track all COT items (lines 205-235)
itemTrackingListener = new MapEventDispatcher.MapEventDispatchListener() {
    @Override
    public void onMapEvent(MapEvent event) {
        MapItem item = event.getItem();
        String eventType = event.getType();

        if (item instanceof PointMapItem) {
            switch (eventType) {
                case MapEvent.ITEM_ADDED:
                    // New COT item appeared
                    break;
                case MapEvent.ITEM_REMOVED:
                    // COT item disappeared
                    break;
                case MapEvent.ITEM_REFRESH:
                    // COT item properties changed
                    break;
            }
        }
    }
};

// Register for multiple event types (lines 241-243)
eventDispatcher.addMapEventListener(MapEvent.ITEM_ADDED, itemTrackingListener);
eventDispatcher.addMapEventListener(MapEvent.ITEM_REMOVED, itemTrackingListener);
eventDispatcher.addMapEventListener(MapEvent.ITEM_REFRESH, itemTrackingListener);
```

**Use Cases:**
- Track friendly/hostile units on HUD in real-time
- Update HUD when items move or change
- Remove items from HUD when they disappear

### Example 3: Item Selection Tool (Push/Pop Pattern)

**File:** `ItemSelectionTool.java`

```java
// Activate tool - exclusive item selection (lines 68-95)
public void activate(ItemSelectionCallback callback) {
    this.callback = callback;
    isActive = true;

    // PUSH: Save current state
    eventDispatcher.pushListeners();

    // CLEAR: Suppress other handlers (like Radial Menu)
    eventDispatcher.clearListeners(MapEvent.ITEM_CLICK);

    // ADD: Our custom handler
    itemClickListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            handleItemClick(event);
        }
    };
    eventDispatcher.addMapEventListener(MapEvent.ITEM_CLICK, itemClickListener);

    Toast.makeText(context, "Tap a map item to select it", Toast.LENGTH_SHORT).show();
}

// Deactivate tool - restore previous state (lines 100-118)
public void deactivate() {
    if (!isActive) return;
    isActive = false;

    // POP: Restore everything (Radial Menu works again!)
    eventDispatcher.popListeners();

    if (callback != null) {
        callback.onSelectionCancelled();
    }
}
```

**Use Cases:**
- Select COT item to add to alert
- Select AOI to configure geofence
- Select item to track on HUD
- Any workflow requiring temporary exclusive event handling

---

## Common Event Types

### Map Events

| Event Type | Description | Use Case in omni-HUD |
|------------|-------------|----------------------|
| `MAP_CLICK` | Map clicked (possibly double tap) | Future: Quick AOI creation |
| `MAP_CONFIRMED_CLICK` | Confirmed single click | Future: Place markers |
| `MAP_LONG_PRESS` | Map long pressed | Future: Context menus |
| `MAP_MOVED` | Map moved (pan/zoom/programmatic) | **✅ Position streaming** |
| `MAP_RESIZED` | Map viewport changed | Future: UI adjustments |

### Item Events

| Event Type | Description | Use Case in omni-HUD |
|------------|-------------|----------------------|
| `ITEM_CLICK` | Item clicked | **✅ Item selection tool** |
| `ITEM_CONFIRMED_CLICK` | Confirmed item click | Future: Quick actions |
| `ITEM_LONG_PRESS` | Item long pressed | Future: Advanced options |
| `ITEM_ADDED` | New item on map | **✅ Track new COT items** |
| `ITEM_REMOVED` | Item removed | **✅ Remove from HUD** |
| `ITEM_REFRESH` | Item properties changed | **✅ Update HUD display** |
| `ITEM_DRAG_STARTED` | Item drag started | Future: Manual positioning |
| `ITEM_DRAG_DROPPED` | Item drag completed | Future: Position updates |

### Group Events

| Event Type | Description | Use Case in omni-HUD |
|------------|-------------|----------------------|
| `GROUP_ADDED` | New group created | Future: Monitor group changes |
| `GROUP_REMOVED` | Group removed | Future: Cleanup tracking |

---

## Summary of Improvements

### What We Changed

1. **Position Updates** (`OmniHUDDropDownReceiver.java`)
   - ❌ Old: Polling every 200ms (5Hz) regardless of changes
   - ✅ New: Event-driven via `MAP_MOVED` with 1Hz fallback
   - **Result:** ~80% reduction in unnecessary updates

2. **Item Tracking** (`OmniHUDDropDownReceiver.java`)
   - ❌ Old: No tracking of COT items at all
   - ✅ New: Real-time tracking via `ITEM_ADDED`, `ITEM_REMOVED`, `ITEM_REFRESH`
   - **Result:** Real-time awareness of battlefield changes

3. **Interactive Selection** (`ItemSelectionTool.java`)
   - ❌ Old: No interactive selection capability
   - ✅ New: Full push/pop pattern for exclusive event handling
   - **Result:** Professional tool behavior without conflicts

4. **Listener Cleanup** (All files)
   - ❌ Old: No event listeners to clean up
   - ✅ New: Proper cleanup in all `dispose()` methods
   - **Result:** No memory leaks, proper lifecycle management

### Best Practices Checklist

- [x] Use MapEventDispatcher for all map/item events
- [x] Prefer event-driven over polling
- [x] Register listeners for specific event types (not global)
- [x] Always unregister listeners in disposal methods
- [x] Use push/pop pattern for temporary exclusive event handling
- [x] Clear only specific event types when using push/pop
- [x] Pop listeners stack when tool deactivates
- [x] Log all event registration/unregistration for debugging
- [x] Handle null checks for items and events
- [x] Use appropriate event types for each use case

---

## Testing Recommendations

### Position Streaming Test
1. Connect HUD device
2. Enable streaming
3. Move around the map - verify immediate updates
4. Stay stationary - verify 1Hz fallback only
5. Check logs for "MAP_MOVED" events

### Item Tracking Test
1. Add new COT markers to map
2. Check logs for "ITEM_ADDED" events
3. Move markers - check for "ITEM_REFRESH"
4. Delete markers - check for "ITEM_REMOVED"
5. Verify no memory leaks over time

### Item Selection Tool Test
1. Activate ItemSelectionTool
2. Try to open Radial Menu (should be suppressed)
3. Click a map item (should be captured by tool)
4. Deactivate tool
5. Try Radial Menu again (should work normally)

---

## References

- **ATAK SDK Documentation**: Map Event Handling
- **omni-HUD Implementation Files**:
  - `OmniHUDDropDownReceiver.java` - Event-driven streaming & tracking
  - `ItemSelectionTool.java` - Push/pop pattern example
  - `OmniHUDMapComponent.java` - Lifecycle management

---

## Contributing

When adding new features that interact with the map, always:

1. Review this best practices document
2. Use MapEventDispatcher instead of polling
3. Implement proper listener cleanup
4. Use push/pop pattern for exclusive event handling
5. Test for memory leaks and event conflicts
6. Document your event listener usage

---

## License

This plugin is open source for ATAK-CIV (civilian) use. See LICENSE file for details.

---

**Last Updated:** 2025-11-04
**Plugin Version:** 1.0
**ATAK Compatibility:** ATAK 4.5+ / ATAK-CIV 4.5+
