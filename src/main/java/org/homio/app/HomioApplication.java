package org.homio.app;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.homio.api.util.CommonUtils;
import org.homio.app.config.AppConfig;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextSettingImpl;
import org.homio.hquery.EnableHQuery;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
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
        System.out.println("Starting Homio app " + version);
        // set primary class loader
        Thread.currentThread().setContextClassLoader(HomioClassLoader.INSTANCE);
        // set root path before init log4j2
        setRootPath();
        System.setProperty("server.version", version);
        Logger log = LogManager.getLogger(HomioApplication.class);
        setDatabaseProperties(log);
        redirectConsoleOutput(log);

        try {
            ConfigurableApplicationContext context = new SpringApplicationBuilder(AppConfig.class)
                    .listeners(new LogService())
                    .run(args);
            if (!context.isRunning()) {
                log.error("Exist Homio due unable to start context");
                ContextImpl.exitApplication(context, 7);
            } else {
                log.info("Homio app started successfully");
                Files.deleteIfExists(Paths.get(System.getProperty("rootPath")).resolve("homio-app.jar_backup"));
                Files.deleteIfExists(Paths.get(System.getProperty("rootPath")).resolve("homio-app.zip"));
            }
        } catch (Exception ex) {
            Throwable cause = NestedExceptionUtils.getRootCause(ex);
            cause = cause == null ? ex : cause;
            log.error("Unable to start Homio application: {}", getErrorMessage(cause));
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
            if (!Files.exists(rootPath)) {
                ApplicationHome applicationHome = new ApplicationHome();
                rootPath = applicationHome.getDir().toPath();
            }
        } else {
            rootPath = Paths.get(sysRootPath);
        }
        Files.createDirectories(rootPath);
        System.setProperty("rootPath", rootPath.toString());
    }

    private static void setDatabaseProperties(Logger log) {
        Properties properties = ContextSettingImpl.getHomioProperties();

        @NotNull String type = properties.getProperty("dbType", "sqlite");
        @NotNull String url = properties.getProperty("dbUrl", "");
        @NotNull String user = properties.getProperty("dbUser", "postgres");
        @NotNull String pwd = properties.getProperty("dbPassword", "password");

        String defaultURL;
        String dialect = "org.hibernate.community.dialect.SQLiteDialect";
        String driverClassName = "org.sqlite.JDBC";

        if (type.equals("postgresql")) {
            defaultURL = "jdbc:postgresql://localhost:5432/postgres";
            if (!url.startsWith("jdbc:postgresql")) {
                url = "";
            }
            dialect = "org.hibernate.dialect.PostgreSQLDialect";
            driverClassName = "org.postgresql.Driver";
        } else {
            defaultURL = "jdbc:sqlite:" + CommonUtils.getRootPath().resolve("data.db");
            if (!url.startsWith("jdbc:sqlite")) {
                url = "";
            }
        }

        log.info("Use database of type '{}'. Url: {}. Auth: '{}'/'{}'", type,
                defaultIfEmpty(url, defaultURL), user, pwd);

        setProperty("databaseType", "dbType", defaultIfEmpty(type, "sqlite"));
        setProperty("spring.datasource.url", "dbUrl", defaultIfEmpty(url, defaultURL));
        setProperty("spring.datasource.username", "dbUser", user);
        setProperty("spring.datasource.password", "dbPassword", pwd);
        System.setProperty("hibernate.dialect", dialect);
        System.setProperty("spring.datasource.driverClassName", driverClassName);
    }

    @SneakyThrows
    private static void setProperty(String key, String configKey, String value) {
        Properties properties = ContextSettingImpl.getHomioProperties();
        if (!value.equals(properties.get(configKey))) {
            properties.setProperty(configKey, value);
            properties.store(Files.newOutputStream(ContextSettingImpl.getPropertiesLocation()), null);
        }
        if (key != null) {
            System.setProperty(key, value);
        }
    }
}
