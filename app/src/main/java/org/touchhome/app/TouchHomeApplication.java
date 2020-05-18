package org.touchhome.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.touchhome.app.config.TouchHomeConfig;

@SpringBootApplication(exclude = {ErrorMvcAutoConfiguration.class})
public class TouchHomeApplication implements WebMvcConfigurer {

    public static void main(String[] args) {
        new SpringApplicationBuilder(TouchHomeConfig.class).listeners(new LogService()).run(args);
    }
}
