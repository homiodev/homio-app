package org.touchhome.bundle.zigbee;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

/**
 * The {@link ZigBeeBindingConstants} class defines common constants, which are
 * used across the whole binding.
 */
public class ZigBeeBindingConstants {

    public static final String THING_PROPERTY_LOGICALTYPE = "zigbee_logicaltype";
    public static final String THING_PROPERTY_NETWORKADDRESS = "zigbee_networkaddress";
    public static final String THING_PROPERTY_ROUTES = "zigbee_routes";
    public static final String THING_PROPERTY_NEIGHBORS = "zigbee_neighbors";
    public static final String THING_PROPERTY_LASTUPDATE = "zigbee_lastupdate";
    public static final String THING_PROPERTY_ASSOCIATEDDEVICES = "zigbee_devices";
    static final String THING_PROPERTY_STACKCOMPLIANCE = "zigbee_stkcompliance";

    /**
     * Return an ISO 8601 combined date and time string for specified date/time
     *
     * @param date Date
     * @return String with format "yyyy-MM-dd'T'HH:mm:ss'Z'"
     */
    public static String getISO8601StringForDate(Date date) {
        if (date == null) {
            return "";
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(date);
    }

    /**
     * Convert a map into a json encoded string.
     *
     * @param properties a map with the to-be-converted properties.
     * @return a String with a Json representation of the properties.
     */
    public static String propertiesToJson(Map<String, Object> properties) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) {
                jsonBuilder.append(",");
            }
            first = false;

            jsonBuilder.append("\"");
            jsonBuilder.append(entry.getKey());
            jsonBuilder.append("\":\"");
            jsonBuilder.append(entry.getValue());
            jsonBuilder.append("\"");
        }
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }
}
