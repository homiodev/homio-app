package org.homio.addon.tuya.internal.cloud.dto;



/**
 * The {@link StatusInfo} encapsulates device status data
 *
 * @author Jan N. Klug - Initial contribution
 */

public class StatusInfo {
    public String code = "";
    public String value = "";
    public String t = "";

    @Override
    public String toString() {
        return "StatusInfo{" + "code='" + code + "', value='" + value + "', t='" + t + "'}";
    }
}
