package com.engindearing.omnihud;

import com.atakmap.coremap.log.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Formats and parses Cursor-on-Target (CoT) messages for HUD display
 */
public class CotFormatter {

    private static final String TAG = CotFormatter.class.getSimpleName();

    /**
     * Data extracted from a CoT message
     */
    public static class CotData {
        public String uid;
        public String type;
        public String callsign;
        public double lat;
        public double lon;
        public double hae;  // Height above ellipsoid
        public double ce;   // Circular error
        public double le;   // Linear error
        public String time;
        public String stale;
        public String how;

        public boolean isValid() {
            return uid != null && !uid.isEmpty() &&
                   callsign != null && !callsign.isEmpty();
        }

        @Override
        public String toString() {
            return "CotData{" +
                   "callsign='" + callsign + '\'' +
                   ", lat=" + lat +
                   ", lon=" + lon +
                   ", hae=" + hae +
                   ", type='" + type + '\'' +
                   '}';
        }
    }

    /**
     * Parse CoT XML and extract relevant data
     */
    public static CotData parseCot(String cotXml) {
        if (cotXml == null || cotXml.isEmpty()) {
            Log.w(TAG, "Empty CoT XML provided");
            return null;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(cotXml)));

            Element root = doc.getDocumentElement();
            if (!"event".equals(root.getNodeName())) {
                Log.w(TAG, "Not a valid CoT event: root element is " + root.getNodeName());
                return null;
            }

            CotData data = new CotData();

            // Extract event attributes
            data.uid = root.getAttribute("uid");
            data.type = root.getAttribute("type");
            data.time = root.getAttribute("time");
            data.stale = root.getAttribute("stale");
            data.how = root.getAttribute("how");

            // Extract point data
            Element point = (Element) root.getElementsByTagName("point").item(0);
            if (point != null) {
                data.lat = parseDouble(point.getAttribute("lat"), 0.0);
                data.lon = parseDouble(point.getAttribute("lon"), 0.0);
                data.hae = parseDouble(point.getAttribute("hae"), 0.0);
                data.ce = parseDouble(point.getAttribute("ce"), 9999999.0);
                data.le = parseDouble(point.getAttribute("le"), 9999999.0);
            }

            // Extract detail/contact callsign
            Element detail = (Element) root.getElementsByTagName("detail").item(0);
            if (detail != null) {
                Element contact = (Element) detail.getElementsByTagName("contact").item(0);
                if (contact != null) {
                    data.callsign = contact.getAttribute("callsign");
                }
            }

            // Fallback: use UID as callsign if no callsign found
            if (data.callsign == null || data.callsign.isEmpty()) {
                data.callsign = data.uid;
            }

            Log.d(TAG, "Parsed CoT: " + data);
            return data;

        } catch (Exception e) {
            Log.e(TAG, "Error parsing CoT XML", e);
            return null;
        }
    }

    /**
     * Check if CoT type represents a friendly unit
     */
    public static boolean isFriendly(String cotType) {
        if (cotType == null) {
            return false;
        }
        // CoT types starting with 'a-f' are friendly
        return cotType.startsWith("a-f");
    }

    /**
     * Check if CoT type represents a hostile unit
     */
    public static boolean isHostile(String cotType) {
        if (cotType == null) {
            return false;
        }
        // CoT types starting with 'a-h' are hostile
        return cotType.startsWith("a-h");
    }

    /**
     * Check if CoT type represents a neutral unit
     */
    public static boolean isNeutral(String cotType) {
        if (cotType == null) {
            return false;
        }
        // CoT types starting with 'a-n' are neutral
        return cotType.startsWith("a-n");
    }

    /**
     * Check if CoT type represents unknown affiliation
     */
    public static boolean isUnknown(String cotType) {
        if (cotType == null) {
            return false;
        }
        // CoT types starting with 'a-u' are unknown
        return cotType.startsWith("a-u");
    }

    /**
     * Get human-readable affiliation from CoT type
     */
    public static String getAffiliation(String cotType) {
        if (isFriendly(cotType)) {
            return "FRIENDLY";
        } else if (isHostile(cotType)) {
            return "HOSTILE";
        } else if (isNeutral(cotType)) {
            return "NEUTRAL";
        } else if (isUnknown(cotType)) {
            return "UNKNOWN";
        }
        return "PENDING";
    }

    /**
     * Format CoT data into simplified HUD display string
     */
    public static String formatForHUD(CotData data) {
        if (data == null || !data.isValid()) {
            return "NO DATA";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CALLSIGN: ").append(data.callsign).append("\n");
        sb.append("POS: ").append(String.format("%.6f", data.lat))
          .append(", ").append(String.format("%.6f", data.lon)).append("\n");
        sb.append("ALT: ").append(String.format("%.1f", data.hae)).append("m\n");
        sb.append("AFFIL: ").append(getAffiliation(data.type));

        return sb.toString();
    }

    /**
     * Safe double parsing with default value
     */
    private static double parseDouble(String value, double defaultValue) {
        try {
            return value != null && !value.isEmpty() ? Double.parseDouble(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
