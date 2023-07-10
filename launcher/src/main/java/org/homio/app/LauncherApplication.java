package org.homio.app;

import static java.lang.String.format;
import static org.homio.app.rest.MainController.getErrorMessage;
import static org.homio.app.rest.MainController.getHomioPropertyRequire;
import static org.homio.app.rest.MainController.isPostgresAvailable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.lang3.SystemUtils;
import org.homio.app.config.AppConfig;
import org.homio.hquery.EnableHQuery;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Log4j2
@EnableHQuery(scanBaseClassesPackage = "org.homio")
@SpringBootApplication(exclude = {ErrorMvcAutoConfiguration.class})
public class LauncherApplication implements WebMvcConfigurer {

    private static Path rootPath;

    @SneakyThrows
    public static void main(String[] args) {
        rootPath = Paths.get(getHomioPropertyRequire("rootPath"));
        Path appPath = rootPath.resolve("homio-app.jar");

        updateApp(appPath);

        if (isValidApp(appPath)) {
            log.info("Launch homio-app");
            launchApp(appPath);
        } else {
            log.info("Launch homio-bootstrap-app");
            new SpringApplicationBuilder(AppConfig.class).run(args);
        }

    }

    @SneakyThrows
    private static boolean isValidApp(Path appPath) {
        boolean valid = Files.exists(appPath);
        if (!valid) {
            log.error("Valid failed. Unable to find app: {}", appPath);
        }
        if (valid && !isPostgresAvailable()) {

            // try start postgresql
            tryStartPsql();
            Thread.sleep(1000);
            valid = isPostgresAvailable();
        }
        return valid;
    }

    private static void launchApp(Path appPath) throws IOException, InterruptedException {
        log.info("Launch command: java -jar " + appPath);
        ProcessBuilder builder = new ProcessBuilder().inheritIO().command("java", "-jar", appPath.toString());
        Process process = builder.start();
      /*  if (getHomioProperty("launcher-redirect-logs", "true").equals("true")) {
            catchIO(process);
        }*/
        int exitCode = process.waitFor();
        if (exitCode == 4) { // update app
            updateApp(appPath);
            launchApp(appPath);
        }
    }

    private static void updateApp(Path appPath) {
        Path zipApplication = appPath.getParent().resolve("homio-app.zip");
        try {
            if (Files.exists(zipApplication)) {
                Path backupApp = appPath.getParent().resolve("app_backup.jar");
                if (Files.exists(appPath)) {
                    Files.move(appPath, backupApp, StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(appPath); // make sure file removed
                }
                try {
                    log.info("Found update file: {}. Made app updating...", zipApplication);
                    try (ZipFile zipFile = new ZipFile(zipApplication.toFile())) {
                        zipFile.extractAll(rootPath.toString());
                    }
                    log.info("Successfully extracted file: {}", zipApplication);
                    if (!Files.exists(appPath)) {
                        log.error("Error updating app. File: {} not exists", appPath);
                        if (Files.exists(backupApp)) {
                            Files.copy(backupApp, appPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } finally {
                    Files.deleteIfExists(backupApp);
                }
            }
        } catch (Exception ex) {
            log.error("Unable to update app", ex);
        } finally {
            try {
                Files.deleteIfExists(zipApplication);
            } catch (Exception ignore) {}
        }
    }

    /*private static void catchIO(Process process) {
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while (true) {
                    line = reader.readLine();
                    if (line == null) {
                        // End of stream, break the loop
                        break;
                    }
                    System.out.println(line);
                }
            } catch (IOException ex) {
                log.warn("Error while read from inner process", ex);
            }
        });

        outputThread.start();
    }*/

    private static void tryStartPsql() {
        String cmd = SystemUtils.IS_OS_WINDOWS ?
            format("%s -D %s", rootPath.resolve("install/postgresql/bin/pg_ctl.exe"), rootPath.resolve("db_data")) :
            format("pg_ctl -D %s start", rootPath.resolve("db_data"));
        if (SystemUtils.IS_OS_WINDOWS) {
            try {
                new Thread(() -> {
                    try {
                        Process process = Runtime.getRuntime().exec(cmd);
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
