package org.touchhome.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.touchhome.app.config.TouchHomeConfig;

@SpringBootApplication
public class TouchHomeApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(TouchHomeConfig.class).listeners(new LogService()).run(args);
    }
}
