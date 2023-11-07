package org.homio.app;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.homio.api.util.CommonUtils.getErrorMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.homio.api.util.CommonUtils;
import org.homio.app.config.AppConfig;
import org.homio.app.manager.common.impl.ContextSettingImpl;
import org.homio.hquery.EnableHQuery;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
        // set primary class loader
        Thread.currentThread().setContextClassLoader(HomioClassLoader.INSTANCE);
        // set root path before init log4j2
        setRootPath();
        System.setProperty("server.version", defaultIfEmpty(HomioApplication.class.getPackage().getImplementationVersion(), "0.0"));
        setProperty("server.port", "port", "9111");
        Logger log = LogManager.getLogger(HomioApplication.class);
        setDatabaseProperties(log);

        try {
            new SpringApplicationBuilder(AppConfig.class)
                    .listeners(new LogService())
                    .run(args);
        } catch (Exception ex) {
            Throwable cause = NestedExceptionUtils.getRootCause(ex);
            cause = cause == null ? ex : cause;
            log.error("Unable to start Homio application: {}", getErrorMessage(cause));
            throw ex;
        }
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
