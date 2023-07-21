package org.homio.app;

import static org.homio.app.rest.MainController.getHomioProperty;

import lombok.SneakyThrows;
import org.homio.app.config.LaunchConfig;
import org.homio.hquery.EnableHQuery;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@EnableHQuery(scanBaseClassesPackage = "org.homio")
@SpringBootApplication(exclude = {ErrorMvcAutoConfiguration.class})
public class LauncherApplication implements WebMvcConfigurer {

    @SneakyThrows
    public static void main(String[] args) {
        System.out.printf("Run homio-launcher v.%s%n".formatted(LauncherApplication.class.getPackage().getImplementationVersion()));
        System.setProperty("server.port", getHomioProperty("port", "9111"));
        new SpringApplicationBuilder(LaunchConfig.class).run(args);
    }
}
