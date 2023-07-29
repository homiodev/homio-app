package org.homio.addon.tuya.internal.local.dto;

import com.google.gson.annotations.SerializedName;
import lombok.ToString;

/**
 * Represents the UDP discovery messages sent by Tuya devices
 */
@ToString
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
}
