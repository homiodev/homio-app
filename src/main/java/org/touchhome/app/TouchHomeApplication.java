package org.touchhome.app;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.touchhome.app.config.TouchHomeConfig;
import org.touchhome.app.utils.HardwareUtils;
import org.touchhome.bundle.hquery.EnableHQuery;

@Log4j2
@EnableHQuery(scanBaseClassesPackage = "org.touchhome")
@SpringBootApplication(exclude = {ErrorMvcAutoConfiguration.class, MongoAutoConfiguration.class})
public class TouchHomeApplication implements WebMvcConfigurer {

    public static void main(String[] args) throws IOException {
        // copy resources from jars
        log.info("Copying resources");
        for (URL resource :
                Collections.list(
                        TouchHomeApplication.class
                                .getClassLoader()
                                .getResources("external_files.7z"))) {
            HardwareUtils.copyResources(resource);
        }
        log.info("Copying resources done");

        new SpringApplicationBuilder(TouchHomeConfig.class).listeners(new LogService()).run(args);
    }

    /*public static void main(String[] args) throws IOException {
        System.out.println(DigestUtils.md5Hex(Files.newInputStream(Paths.get("target/touchhome-core.jar"))));
    }*/
}
