package org.homio.addon.tuya.internal.local.dto;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link DiscoveryMessage} represents the UDP discovery messages sent by Tuya devices
 */
public class DiscoveryMessage {
    public String ip = "";
    @SerializedName("gwId")
    public String deviceId = "";
    public int active = 0;
    @SerializedName(value = "ability", alternate = { "ablilty" })
    public int ability = 0;
    public int mode = 0;
    public boolean encrypt = true;
    public String productKey = "";
    public String version = "";

    public boolean token = true;
    public boolean wf_cfg = true;

    @Override
    public String toString() {
        return "DiscoveryMessage{ip='" + ip + "', deviceId='" + deviceId + "', active=" + active + ", ability="
                + ability + ", mode=" + mode + ", encrypt=" + encrypt + ", productKey='" + productKey + "', version='"
                + version + "', token= " + token + ", wf_cfg=" + wf_cfg + "}";
    }
}
