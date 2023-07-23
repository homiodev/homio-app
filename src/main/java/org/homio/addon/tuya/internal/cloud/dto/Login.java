package org.homio.addon.tuya.internal.cloud.dto;

import com.google.gson.annotations.SerializedName;
import org.homio.addon.tuya.internal.config.ProjectConfiguration;
import org.homio.addon.tuya.internal.util.CryptoUtil;

public class Login {

    public String username;
    public String password;

    @SerializedName("country_code")
    public Integer countryCode;
    public String schema;

    public Login(String username, String password, Integer countryCode, String schema) {
        this.username = username;
        this.password = CryptoUtil.md5(password);
        this.countryCode = countryCode;
        this.schema = schema;
    }

    public static Login fromProjectConfiguration(ProjectConfiguration config) {
        return new Login(config.username, config.password, config.countryCode, config.schema);
    }
}
