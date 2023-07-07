package org.homio.app.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String version;
    private int httpPort;
    private String serverSiteURL;
    private boolean disableSecurity;
    private Duration internetTestInterval;
    private Duration checkPortInterval;
    private Duration sendUiUpdatesInterval;
    private Duration minScriptThreadSleep;
    private Duration maxJavaScriptOnceCallBeforeInterrupt;
    private Duration maxJavaScriptCompileBeforeInterrupt;
    private Source source;

    @Getter
    @Setter
    public static class Source {

        private String mosquitto;
        private String ffmpeg;
        private String node;
    }
}
