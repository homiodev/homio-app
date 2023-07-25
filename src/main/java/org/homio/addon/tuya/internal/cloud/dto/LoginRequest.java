package org.homio.addon.tuya.internal.cloud.dto;

import com.google.gson.annotations.SerializedName;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.util.CryptoUtil;

public class LoginRequest {

    public String username;
    public String password;

    @SerializedName("country_code")
    public Integer countryCode;
    public String schema;

    public LoginRequest(String username, String password, Integer countryCode, String schema) {
        this.username = username;
        this.password = CryptoUtil.md5(password);
        this.countryCode = countryCode;
        this.schema = schema;
    }

    public static LoginRequest fromProjectConfiguration(TuyaProjectEntity config) {
        return new LoginRequest(config.getUser(), config.getPassword().asString(),
            config.getCountryCode(), config.getProjectSchema().name());
    }
}
