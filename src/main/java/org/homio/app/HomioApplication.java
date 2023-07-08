package org.homio.app;

import static org.homio.api.util.CommonUtils.getErrorMessage;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.app.config.AppConfig;
import org.homio.app.utils.HardwareUtils;
import org.homio.hquery.EnableHQuery;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Log4j2
@EnableHQuery(scanBaseClassesPackage = "org.homio")
@SpringBootApplication(exclude = {
    ErrorMvcAutoConfiguration.class,
    MongoAutoConfiguration.class
})
public class HomioApplication implements WebMvcConfigurer {

    @SneakyThrows
    public static void main(String[] args) throws IOException {
        // copy resources from jars
        log.info("Copying resources");
        for (URL resource : Collections.list(HomioApplication.class.getClassLoader().getResources("external_files.7z"))) {
            HardwareUtils.copyResources(resource);
        }
        log.info("Copying resources done");

        try {
            new SpringApplicationBuilder(AppConfig.class).listeners(new LogService()).run(args);
        } catch (Exception ex) {
            Throwable cause = NestedExceptionUtils.getRootCause(ex);
            cause = cause == null ? ex : cause;
            log.error("Unable to start Homio application: {}", getErrorMessage(cause));
            throw ex;
        }
    }
}
