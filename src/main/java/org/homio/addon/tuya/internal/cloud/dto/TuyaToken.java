package org.homio.addon.tuya.internal.cloud.dto;

import com.google.gson.annotations.SerializedName;
import lombok.ToString;

/**
 * The {@link TuyaToken} encapsulates the Access Tokens
 */

@ToString
public class TuyaToken {
    @SerializedName("access_token")
    public final String accessToken;
    @SerializedName("refresh_token")
    public final String refreshToken;
    public final String uid;
    @SerializedName("expire_time")
    public final long expire;

    public transient long expireTimestamp = 0;

    public TuyaToken() {
        this("", "", "", 0);
    }

    public TuyaToken(String accessToken, String refreshToken, String uid, long expire) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.uid = uid;
        this.expire = expire;
    }
}
