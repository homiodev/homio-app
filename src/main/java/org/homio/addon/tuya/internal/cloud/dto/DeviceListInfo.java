package org.homio.addon.tuya.internal.cloud.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.ToString;

@ToString
public class DeviceListInfo {

    public String id = "";
    public String uuid = "";
    public String uid = "";

    @SerializedName("biz_type")
    public int bizType = -1;
    public String name = "";
    @SerializedName("time_zone")
    public String timeZone = "";
    public String ip = "";
    @SerializedName("local_key")
    public String localKey = "";
    @SerializedName("sub")
    public boolean subDevice = false;
    public String model = "";

    @SerializedName("create_time")
    public long createTime = 0;
    @SerializedName("update_time")
    public long updateTime = 0;
    @SerializedName("active_time")
    public long activeTime = 0;

    public List<StatusInfo> status = List.of();

    @SerializedName("owner_id")
    public String ownerId = "";
    @SerializedName("product_id")
    public String productId = "";
    @SerializedName("product_name")
    public String productName = "";

    public String category = "";
    public String icon = "";
    public boolean online = false;

    @SerializedName("node_id")
    public String nodeId = "";
}
