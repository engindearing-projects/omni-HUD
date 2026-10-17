package com.engindearing.omnihud;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;

import com.atakmap.coremap.log.Log;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ECOTI HUD link over USB.
 *
 * Safran does not publish the ECOTI's phone protocol, so this driver stays generic. It opens
 * whichever data interface the device actually has (CDC serial, vendor bulk or HID), sends the
 * position as Cursor on Target XML, and records what it sends and receives so a field test
 * produces a report we can work from. Writes run on a background thread so a device that never
 * reads cannot stall ATAK's UI.
 */
public class ECOTIDevice implements HUDDevice {

    private static final String TAG = ECOTIDevice.class.getSimpleName();

    private static final int TIMEOUT_MS = 1000;
    private static final int READ_TIMEOUT_MS = 250;
    private static final int CDC_BAUD = 115200;
    private static final int MAX_BULK_CHUNK = 16384;
    private static final int CAPTURE_LIMIT = 2048;
    private static final int HID_DESCRIPTOR_MAX = 1024;

    private static final int REQTYPE_CLASS_INTERFACE_OUT = 0x21;
    private static final int REQTYPE_STANDARD_INTERFACE_IN = 0x81;
    private static final int CDC_SET_LINE_CODING = 0x20;
    private static final int CDC_SET_CONTROL_LINE_STATE = 0x22;
    private static final int CDC_DTR_AND_RTS = 0x03;
    private static final int GET_DESCRIPTOR = 0x06;
    private static final int HID_REPORT_DESCRIPTOR_TYPE = 0x22;

    /** Receives link activity. Called on the reader or writer thread, never on the UI thread. */
    public interface LinkListener {
        void onLinkEvent(String message);
    }

    private final UsbManager usbManager;
    private final UsbEventLog eventLog;
    private volatile LinkListener linkListener;

    // Set by connect() before the worker threads start; cleared by disconnect().
    private volatile UsbDescriptorInfo info;
    private volatile UsbLinkPlan plan;
    private volatile UsbDeviceConnection connection;
    private final List<UsbInterface> claimed = new ArrayList<>();
    private volatile UsbEndpoint endpointOut;
    private volatile UsbEndpoint endpointIn;
    private volatile UsbFraming.Mode framing = UsbFraming.Mode.RAW;
    private volatile int frameSize = MAX_BULK_CHUNK;
    private volatile int frameReportId;
    private volatile String framingDescription = "raw";
    private byte[] hidReportDescriptor;
    private volatile String lastError;

    private ExecutorService writer;
    private Thread reader;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean writeInFlight = new AtomicBoolean(false);
    private final AtomicLong messagesSent = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong sendFailures = new AtomicLong();
    private final AtomicLong sendsSkipped = new AtomicLong();
    private final AtomicLong bytesReceived = new AtomicLong();
    private final byte[] capture = new byte[CAPTURE_LIMIT];
    private int captureLength; // guarded by capture
    private volatile String lastWriteResult = "nothing sent yet";
    private volatile long lastReceiveNotice;

    public ECOTIDevice(UsbManager usbManager, UsbEventLog eventLog) {
        this.usbManager = usbManager;
        this.eventLog = eventLog;
    }

    public void setLinkListener(LinkListener listener) {
        this.linkListener = listener;
    }

    @Override
    public String getDeviceName() {
        return "ECOTI";
    }

    @Override
    public String getManufacturer() {
        return "Safran";
    }

    /**
     * The ECOTI's USB identity is not confirmed, so any device can be tried. The classifier only
     * decides the order and labels in the device list.
     */
    @Override
    public boolean supportsDevice(UsbDevice device) {
        return device != null;
    }

    @Override
    public synchronized boolean connect(UsbDevice device) {
        disconnect();
        resetSession();
        info = UsbDescriptors.from(device);
        plan = UsbLinkPlan.plan(info);
        log("Connecting to " + info.ids() + " " + info.displayName() + ": " + plan.describe());

        if (!plan.isUsable()) {
            return fail("Cannot connect: " + plan.reason + ".");
        }
        if (usbManager == null) {
            return fail("This phone has no USB host service.");
        }
        if (!usbManager.hasPermission(device)) {
            return fail("Android has not granted USB permission for this device.");
        }
        UsbDeviceConnection opened = usbManager.openDevice(device);
        if (opened == null) {
            return fail("Android could not open the device. Another app may be using it.");
        }
        connection = opened;

        try {
            if (plan.controlInterface >= 0) {
                UsbInterface control = device.getInterface(plan.controlInterface);
                if (!claim(control)) {
                    log("Could not claim CDC control interface " + control.getId() + ", continuing without line setup");
                }
            }
            UsbInterface data = device.getInterface(plan.dataInterface);
            if (!claim(data)) {
                return failAndClose("Could not claim interface " + data.getId() + ". Android or another app owns it.");
            }
            endpointOut = data.getEndpoint(plan.outEndpoint);
            endpointIn = plan.inEndpoint >= 0 ? data.getEndpoint(plan.inEndpoint) : null;

            switch (plan.kind) {
                case CDC_ACM:
                    setUpCdc(device);
                    useRaw(MAX_BULK_CHUNK);
                    break;
                case HID_INTERRUPT:
                    setUpHid(data);
                    break;
                case INTERRUPT:
                    useRaw(Math.max(1, endpointOut.getMaxPacketSize()));
                    break;
                default:
                    useRaw(MAX_BULK_CHUNK);
                    break;
            }
        } catch (RuntimeException e) {
            return failAndClose("Connection error: " + e.getMessage());
        }

        running.set(true);
        writer = Executors.newSingleThreadExecutor();
        startReader();
        log("Connected. Writing to " + describe(endpointOut) + " as " + framingDescription
                + (endpointIn != null ? ", listening on " + describe(endpointIn)
                        : ". The device has no input endpoint, so replies cannot be seen."));
        return true;
    }

    @Override
    public synchronized void disconnect() {
        boolean wasRunning = running.getAndSet(false);
        ExecutorService w = writer;
        writer = null;
        if (w != null) {
            w.shutdownNow();
            try {
                w.awaitTermination(TIMEOUT_MS + 100L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Thread r = reader;
        reader = null;
        if (r != null && r != Thread.currentThread()) {
            try {
                r.join(READ_TIMEOUT_MS * 2L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeQuietly();
        endpointOut = null;
        endpointIn = null;
        if (wasRunning && info != null) {
            log("Disconnected from " + info.ids());
        }
    }

    @Override
    public boolean isConnected() {
        return running.get() && connection != null && endpointOut != null;
    }

    @Override
    public boolean sendCotData(String cotXml) {
        if (cotXml == null) {
            return false;
        }
        return sendBytes(cotXml.getBytes(StandardCharsets.UTF_8), "CoT message");
    }

    @Override
    public boolean sendPosition(double lat, double lon, double alt, double heading, String callsign) {
        return sendCotData(generatePositionCot(lat, lon, alt, heading, callsign));
    }

    @Override
    public String getLastError() {
        return lastError;
    }

    @Override
    public String getStatusString() {
        UsbDescriptorInfo i = info;
        UsbLinkPlan p = plan;
        if (isConnected() && i != null && p != null) {
            return i.displayName() + " " + i.ids() + ", " + p.reason + ", " + framingDescription
                    + "\nLast write: " + lastWriteResult
                    + "\nReceived from device: " + bytesReceived.get() + " bytes";
        }
        return "Not connected";
    }

    /** Everything the last connection attempt learned, for the USB report. */
    public synchronized String diagnostics() {
        if (info == null || plan == null) {
            return "No connection attempted yet.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Last device: ").append(info.ids()).append(' ').append(info.displayName()).append('\n');
        sb.append("Plan: ").append(plan.describe()).append('\n');
        sb.append("State: ").append(isConnected() ? "connected" : "not connected").append('\n');
        if (lastError != null) {
            sb.append("Last error: ").append(lastError).append('\n');
        }
        sb.append("Framing: ").append(framingDescription).append('\n');
        sb.append(String.format(Locale.US, "Sent: %d messages, %d bytes, %d failed, %d skipped while a write was in flight\n",
                messagesSent.get(), bytesSent.get(), sendFailures.get(), sendsSkipped.get()));
        sb.append("Last write: ").append(lastWriteResult).append('\n');
        sb.append("Received: ").append(bytesReceived.get()).append(" bytes\n");
        if (hidReportDescriptor != null) {
            sb.append("HID report descriptor (").append(hidReportDescriptor.length).append(" bytes):\n")
                    .append(UsbFraming.hexDump(hidReportDescriptor, hidReportDescriptor.length)).append('\n');
        }
        synchronized (capture) {
            if (captureLength > 0) {
                sb.append("First bytes received:\n").append(UsbFraming.hexDump(capture, captureLength)).append('\n');
            }
        }
        return sb.toString();
    }

    private boolean claim(UsbInterface iface) {
        UsbDeviceConnection c = connection;
        if (c == null || !c.claimInterface(iface, true)) {
            return false;
        }
        claimed.add(iface);
        if (iface.getAlternateSetting() != 0 && !c.setInterface(iface)) {
            log("Could not select alternate setting " + iface.getAlternateSetting() + " on interface " + iface.getId());
        }
        return true;
    }

    private void setUpCdc(UsbDevice device) {
        if (plan.controlInterface < 0) {
            log("No CDC control interface, skipping line setup");
            return;
        }
        int ifaceId = device.getInterface(plan.controlInterface).getId();
        byte[] coding = UsbFraming.cdcLineCoding(CDC_BAUD);
        int coded = connection.controlTransfer(REQTYPE_CLASS_INTERFACE_OUT, CDC_SET_LINE_CODING, 0, ifaceId,
                coding, coding.length, TIMEOUT_MS);
        int lines = connection.controlTransfer(REQTYPE_CLASS_INTERFACE_OUT, CDC_SET_CONTROL_LINE_STATE,
                CDC_DTR_AND_RTS, ifaceId, null, 0, TIMEOUT_MS);
        log("CDC line setup: " + CDC_BAUD + " 8N1 " + (coded >= 0 ? "accepted" : "rejected")
                + ", DTR and RTS " + (lines >= 0 ? "raised" : "rejected"));
    }

    private void setUpHid(UsbInterface hid) {
        byte[] buf = new byte[HID_DESCRIPTOR_MAX];
        int n = connection.controlTransfer(REQTYPE_STANDARD_INTERFACE_IN, GET_DESCRIPTOR,
                HID_REPORT_DESCRIPTOR_TYPE << 8, hid.getId(), buf, buf.length, TIMEOUT_MS);
        List<HidReportDescriptor.Report> outputs;
        if (n > 0) {
            hidReportDescriptor = Arrays.copyOf(buf, n);
            outputs = HidReportDescriptor.outputReports(buf, n);
            log("HID report descriptor: " + n + " bytes, output reports " + outputs);
        } else {
            outputs = new ArrayList<>();
            log("Could not read the HID report descriptor (result " + n + ")");
        }
        UsbFraming.HidFraming f = UsbFraming.chooseHidFraming(outputs, endpointOut.getMaxPacketSize(), info.vendorId);
        framing = f.mode;
        frameSize = f.size;
        frameReportId = f.reportId;
        framingDescription = f.describe();
    }

    private void useRaw(int size) {
        framing = UsbFraming.Mode.RAW;
        frameSize = size;
        frameReportId = 0;
        framingDescription = "raw bytes, up to " + size + " per transfer";
    }

    private void startReader() {
        final UsbDeviceConnection c = connection;
        final UsbEndpoint in = endpointIn;
        if (c == null || in == null) {
            return;
        }
        final byte[] buf = new byte[Math.max(4096, in.getMaxPacketSize())];
        reader = new Thread(() -> {
            try {
                while (running.get()) {
                    long started = SystemClock.elapsedRealtime();
                    int n = c.bulkTransfer(in, buf, buf.length, READ_TIMEOUT_MS);
                    if (n > 0) {
                        onReceived(buf, n);
                    } else if (SystemClock.elapsedRealtime() - started < READ_TIMEOUT_MS / 2) {
                        // Failed without waiting: back off so a dead endpoint cannot spin the CPU.
                        SystemClock.sleep(100);
                    }
                }
            } catch (RuntimeException e) {
                // An uncaught exception here would crash ATAK, so stop listening instead.
                log("Stopped reading from the device: " + e);
            }
        }, "OmniHUD-USB-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void onReceived(byte[] data, int n) {
        long total = bytesReceived.addAndGet(n);
        boolean first = total == n;
        synchronized (capture) {
            int room = CAPTURE_LIMIT - captureLength;
            if (room > 0) {
                int k = Math.min(room, n);
                System.arraycopy(data, 0, capture, captureLength, k);
                captureLength += k;
            }
        }
        if (first) {
            log("Device sent its first data: " + n + " bytes, starting " + UsbFraming.hex(data, 0, Math.min(n, 24)));
        }
        long now = SystemClock.elapsedRealtime();
        if (first || now - lastReceiveNotice > 2000) {
            lastReceiveNotice = now;
            notifyListener("Received " + total + " bytes from the device");
        }
    }

    private boolean sendBytes(final byte[] payload, final String what) {
        ExecutorService w = writer;
        if (!isConnected() || w == null) {
            lastError = "Not connected to device";
            return false;
        }
        if (!writeInFlight.compareAndSet(false, true)) {
            // The previous write is still waiting on the device; drop this update rather than queue stale positions.
            sendsSkipped.incrementAndGet();
            return true;
        }
        try {
            w.execute(() -> {
                try {
                    writeNow(payload, what);
                } catch (RuntimeException e) {
                    // An uncaught exception here would crash ATAK, so record it instead.
                    sendFailures.incrementAndGet();
                    lastWriteResult = "write error: " + e;
                    lastError = lastWriteResult;
                    log(lastWriteResult);
                } finally {
                    writeInFlight.set(false);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            writeInFlight.set(false);
            lastError = "The link is closing";
            return false;
        }
    }

    private void writeNow(byte[] payload, String what) {
        UsbDeviceConnection c = connection;
        UsbEndpoint out = endpointOut;
        if (c == null || out == null) {
            return;
        }
        List<byte[]> frames = UsbFraming.frame(payload, framing, frameSize, frameReportId);
        for (int i = 0; i < frames.size(); i++) {
            byte[] f = frames.get(i);
            int r = c.bulkTransfer(out, f, f.length, TIMEOUT_MS);
            if (r < 0) {
                sendFailures.incrementAndGet();
                String result = "write failed on transfer " + (i + 1) + " of " + frames.size()
                        + ": the device did not take the data within " + TIMEOUT_MS + " ms";
                lastWriteResult = result;
                lastError = result;
                if (sendFailures.get() <= 3) {
                    log(result);
                }
                notifyListener(result);
                return;
            }
        }
        long count = messagesSent.incrementAndGet();
        bytesSent.addAndGet(payload.length);
        String result = "sent " + what + ", " + payload.length + " bytes in " + frames.size()
                + (frames.size() == 1 ? " transfer" : " transfers");
        lastWriteResult = result;
        if (count == 1) {
            log("First write succeeded: " + result);
        }
        notifyListener(result);
    }

    private void resetSession() {
        info = null;
        plan = null;
        hidReportDescriptor = null;
        lastError = null;
        lastWriteResult = "nothing sent yet";
        useRaw(MAX_BULK_CHUNK);
        messagesSent.set(0);
        bytesSent.set(0);
        sendFailures.set(0);
        sendsSkipped.set(0);
        bytesReceived.set(0);
        writeInFlight.set(false);
        synchronized (capture) {
            captureLength = 0;
        }
    }

    private void closeQuietly() {
        UsbDeviceConnection c = connection;
        connection = null;
        if (c != null) {
            for (UsbInterface i : claimed) {
                try {
                    c.releaseInterface(i);
                } catch (RuntimeException ignored) {
                    // The device may already be gone.
                }
            }
            try {
                c.close();
            } catch (RuntimeException ignored) {
                // Same.
            }
        }
        claimed.clear();
    }

    private boolean fail(String reason) {
        lastError = reason;
        log(reason);
        return false;
    }

    private boolean failAndClose(String reason) {
        closeQuietly();
        endpointOut = null;
        endpointIn = null;
        return fail(reason);
    }

    private void log(String message) {
        Log.i(TAG, message);
        eventLog.add(message);
    }

    private void notifyListener(String message) {
        LinkListener l = linkListener;
        if (l != null) {
            l.onLinkEvent(message);
        }
    }

    private static String describe(UsbEndpoint ep) {
        if (ep == null) {
            return "no endpoint";
        }
        return new UsbDescriptorInfo.Endpoint(0, ep.getAddress(), ep.getType(), ep.getMaxPacketSize(),
                ep.getInterval()).describe();
    }

    /** Position as Cursor on Target XML, the format 0.1 sent. */
    private String generatePositionCot(double lat, double lon, double alt, double heading, String callsign) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        String time = dateFormat.format(new Date());
        String stale = dateFormat.format(new Date(System.currentTimeMillis() + 3600000));
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
