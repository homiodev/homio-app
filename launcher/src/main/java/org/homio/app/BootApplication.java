package org.homio.app;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.homio.app.rest.MainController.getRootPath;
import static org.homio.app.rest.MainController.isPostgresAvailable;
import static org.homio.app.rest.RestResponseEntityExceptionHandler.getErrorMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.homio.app.config.AppConfig;
import org.homio.app.rest.MainController;
import org.homio.hquery.EnableHQuery;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Log4j2
@EnableHQuery(scanBaseClassesPackage = "org.homio")
@SpringBootApplication(exclude = {ErrorMvcAutoConfiguration.class})
public class BootApplication implements WebMvcConfigurer {

    @SneakyThrows
    public static void main(String[] args) {
        Path app = Paths.get(defaultString(MainController.configuration.getApp_path(), getRootPath().resolve("homio-app.jar").toString()));
        boolean valid = Files.exists(app);
        if (valid && !isPostgresAvailable()) {
            // try start postgresql
            startPsql();
            valid = isPostgresAvailable();
        }

        if (valid) {
            Runtime.getRuntime().exec("java -jar " + app).waitFor();
        } else {
            new SpringApplicationBuilder(AppConfig.class).run(args);
        }
    }

    private static void startPsql() {
        if (SystemUtils.IS_OS_WINDOWS) {
            Path pgCtl = getRootPath().resolve("install/postgresql/bin/pg_ctl.exe");
            if (Files.exists(pgCtl)) {
                try {
                    new Thread(() -> {
                        try {
                            Process process = Runtime.getRuntime().exec(pgCtl + " -D " + getRootPath().resolve("db_data"));
                            log.info("Starting Postgresql win portable");
                            process.waitFor();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }).start();
                } catch (Exception ex) {
                    log.warn("Error while starting Postgresql db. Maybe already running: {}", getErrorMessage(ex));
                }
            }
        }
    }
}
