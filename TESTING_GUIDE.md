# OmniHUD Testing Guide for ECOTI Integration

## Recent Improvements

The plugin has been updated with:

1. **Smarter USB device detection** - No longer accepts ANY USB device
   - Now only accepts USB serial devices (likely ECOTI communication method)
   - Checks for known USB-serial chip vendors (FTDI, CP210x, CH340, etc.)
   - Validates device class and interface types

2. **Comprehensive debug logging** - All USB events are now logged with details:
   - Device VID/PID displayed prominently
   - Connection steps tracked with visual indicators (✓/✗)
   - Clear error messages explaining failures
   - Logs will help identify the ECOTI USB identifiers

3. **USB intent filters** - Plugin can now detect when USB devices are attached

## How to Collect Proper Logs

### Option 1: Using ADB from Computer (Recommended)

1. Connect Android device to computer via USB
2. Enable USB debugging on Android device
3. Open terminal/command prompt on computer
4. Run this command:
   ```bash
   adb logcat -v time USBCommunicationManager:I ECOTIDevice:I UsbManager:I *:E > omnihud_test.log
   ```
5. Perform the test (see steps below)
6. Stop logging with Ctrl+C
7. Send `omnihud_test.log` file

### Option 2: Using Mobile App

If you can't use ADB, use **MatLog** or **aLogcat** app:

1. Install MatLog from Play Store
2. Open MatLog and tap the filter icon
3. Set filter to: `USBCommunicationManager|ECOTIDevice|UsbManager`
4. Set level to "Info" or "Debug"
5. Clear existing logs
6. Start recording
7. Perform the test (see steps below)
8. Stop recording and export logs

## Test Procedure

Perform this EXACT sequence and capture logs throughout:

1. **Start logging** (using option 1 or 2 above)

2. **Open ATAK** (should see ATAK startup in logs)

3. **Open OmniHUD plugin** from ATAK menu
   - Look for: `USBCommunicationManager initialized` in logs

4. **Plug in ECOTI device** via USB-C adapter
   - Should see: `USB DEVICE ATTACHED EVENT` in logs
   - Should see: Device VID/PID logged

5. **Grant USB permission** when Android prompts
   - Should see: `✓ USB PERMISSION GRANTED` in logs

6. **Click Connect/Sync in OmniHUD**
   - Should see detailed connection attempt logs
   - Should see USB device scanning logs

7. **Wait 10 seconds** for connection to complete

8. **Stop logging** and export the log file

## What the Logs Will Show

With the new logging, you'll see clear sections like this:

```
========================================
Scanning for USB devices...
Total USB devices connected: 1
========================================
USB Device #1:
  Name: /dev/bus/usb/001/002
  VID: 0x0403
  PID: 0x6001
========================================
```

And connection attempts:
```
========================================
✓ USB PERMISSION GRANTED
Device: /dev/bus/usb/001/002
VID/PID: 0x0403/0x6001
Proceeding to connect...
========================================
```

## Expected Outcomes

### Success Scenario
You should see:
```
✓✓✓ CONNECTION SUCCESSFUL ✓✓✓
HUD Device: ECOTI
Status: Connected to /dev/bus/usb/001/002
```

### Failure Scenarios

If you see this:
```
✗ REJECTED: Not a recognized USB serial device
```
- ECOTI may use a different communication method
- We need the VID/PID to add it to the known devices list

If you see:
```
✗ CONNECTION FAILED: Failed to claim USB interface
```
- Another app may be using the device
- Kernel driver may be holding the interface

## What We Need From You

Please send:
1. **Complete log file** from start to finish of test
2. **Screenshots** of any error messages in ATAK
3. **ECOTI device information** if available:
   - Model number
   - Any documentation about USB communication
   - Known USB VID/PID if documented

## Next Steps After Testing

Once we have your logs, we can:
1. **Identify the ECOTI VID/PID** from the USB device detection logs
2. **Update the code** with the exact device identifiers
3. **Diagnose connection issues** from detailed error messages
4. **Optimize the communication protocol** based on what we learn

## Questions?

If you encounter issues:
- Make sure USB debugging is enabled if using ADB
- Try unplugging/replugging ECOTI to see USB events
- Check that ECOTI is getting power from USB-C
- Ensure no other apps are using the USB device

---

Thank you for testing! Your logs will be invaluable for getting ECOTI working properly.
