package org.touchhome.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "touchhome")
public class TouchHomeProperties {
    private int version;
    private int httpPort;
    private String serverSiteURL;
    private String checkConnectivityURL;
    private boolean disableSecurity;
    private Duration internetTestInterval;
    private Duration checkPortInterval;
    private Duration minScriptThreadSleep;
    private Duration maxJavaScriptOnceCallBeforeInterrupt;
    private Duration maxJavaScriptCompileBeforeInterrupt;


    public void setVersion(String version) {
        if (version.endsWith("-SNAPSHOT")) {
            version = version.substring(0, version.indexOf("-SNAPSHOT"));
        }
        this.version = Integer.parseInt(version.replaceAll("\\.", ""));
    }
}
