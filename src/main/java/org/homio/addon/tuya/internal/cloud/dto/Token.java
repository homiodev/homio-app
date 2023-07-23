package org.homio.addon.tuya.internal.cloud.dto;

import com.google.gson.annotations.SerializedName;
import lombok.ToString;

/**
 * The {@link Token} encapsulates the Access Tokens
 */

@ToString
public class Token {
    @SerializedName("access_token")
    public final String accessToken;
    @SerializedName("refresh_token")
    public final String refreshToken;
    public final String uid;
    @SerializedName("expire_time")
    public final long expire;

    public transient long expireTimestamp = 0;

    public Token() {
        this("", "", "", 0);
    }

    public Token(String accessToken, String refreshToken, String uid, long expire) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.uid = uid;
        this.expire = expire;
    }
}
