package org.touchhome.app.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "touchhome")
public class TouchHomeProperties {

    private Cloud cloud;
    private String version;
    private int httpPort;
    private String serverSiteURL;
    private boolean disableSecurity;
    private Duration internetTestInterval;
    private Duration checkPortInterval;
    private Duration minScriptThreadSleep;
    private Duration maxJavaScriptOnceCallBeforeInterrupt;
    private Duration maxJavaScriptCompileBeforeInterrupt;

    @Getter
    @Setter
    public static class Cloud {

        private String hostname;
        private String loginUrl;
    }
}
