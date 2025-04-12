package org.homio.app;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.homio.app.config.AppConfig;
import org.homio.app.utils.HardwareUtils;
import org.homio.hquery.EnableHQuery;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.homio.api.util.CommonUtils.getErrorMessage;

@EnableHQuery(scanBaseClassesPackage = "org.homio")
@SpringBootApplication(exclude = {
        ErrorMvcAutoConfiguration.class,
        MongoAutoConfiguration.class
})
public class HomioApplication implements WebMvcConfigurer {

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS] %4$-5s - %5$s%n");
    }

    @SneakyThrows
    public static void main(String[] args) throws IOException {
        String version = "v" + getAppVersion();
        if (args.length == 1 && args[0].equals("--version")) {
            System.out.println(version);
            return;
        }
        System.out.println("Starting Homio app: " + version);
        // set primary class loader
        Thread.currentThread().setContextClassLoader(HomioClassLoader.INSTANCE);
        // set root path before init log4j2
        setRootPath();
        System.setProperty("server.version", version);
        Logger log = LogManager.getLogger(HomioApplication.class);
        HardwareUtils.setDatabaseProperties(log);
        redirectConsoleOutput(log);

        try {
            ConfigurableApplicationContext context = new SpringApplicationBuilder(AppConfig.class)
                    .listeners(new LogService())
                    .run(args);
            if (!context.isRunning()) {
                log.error("Exist Homio due unable to start context");
                HardwareUtils.exitApplication(context, 7);
            } else {
                log.info("Homio app started successfully");
                Files.deleteIfExists(Paths.get(System.getProperty("rootPath")).resolve("homio-app.jar_backup"));
                Files.deleteIfExists(Paths.get(System.getProperty("rootPath")).resolve("homio-app.zip"));
            }
        } catch (Exception ex) {
            Throwable cause = NestedExceptionUtils.getRootCause(ex);
            log.error("Unable to start Homio application: {}", getErrorMessage(cause));
            System.exit(1);
            throw ex;
        }
    }

    private static String getAppVersion() throws IOException {
        String version = null;
        try (InputStream inputStream = HomioApplication.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (inputStream != null) {
                Manifest manifest = new Manifest(inputStream);
                Attributes attributes = manifest.getMainAttributes();
                version = attributes.getValue("Implementation-Version");
            }
        }
        return StringUtils.defaultIfEmpty(version, "0.0.0");
    }

    private static void redirectConsoleOutput(Logger log) {
        System.setOut(new PrintStream(System.out) {
            @Override
            public void println(String message) {
                log.info(message);
            }
        });

        System.setErr(new PrintStream(System.err) {
            @Override
            public void println(String message) {
                log.error(message);
            }
        });
    }

    @SneakyThrows
    private static void setRootPath() {
        String sysRootPath = System.getProperty("rootPath");
        Path rootPath;
        if (StringUtils.isEmpty(sysRootPath)) {
            rootPath = (SystemUtils.IS_OS_WINDOWS ? SystemUtils.getUserHome().toPath().resolve("homio") :
                    Paths.get("/opt/homio"));
        } else {
            rootPath = Paths.get(sysRootPath);
        }
        System.out.println("Set root path: " + rootPath);
        Files.createDirectories(rootPath);
        System.setProperty("rootPath", rootPath.toString());
    }
}
