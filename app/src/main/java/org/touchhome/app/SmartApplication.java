package org.touchhome.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.touchhome.app.config.SmartConfig;

@SpringBootApplication
public class SmartApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(SmartConfig.class).listeners(new LogService()).run(args);
    }
}
