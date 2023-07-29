package org.homio.addon.tuya.internal.cloud.dto;

import com.google.gson.annotations.SerializedName;
import lombok.ToString;

@ToString
public class TuyaSubDeviceInfoDTO {
    public String id = "";
    public String name = "";
    public String category = "";
    public String icon = "";
    public boolean online = false;
    @SerializedName("node_id")
    public String nodeId = "";
    @SerializedName("product_id")
    public String productId = "";
    @SerializedName("owner_id")
    public String ownerId = "";
    @SerializedName("active_time")
    public long activeTime = 0;
    @SerializedName("update_time")
    public long updateTime = 0;
}
