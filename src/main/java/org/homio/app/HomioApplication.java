package org.homio.app;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.util.CommonUtils;
import org.homio.app.config.AppConfig;
import org.homio.app.manager.common.impl.EntityContextSettingImpl;
import org.homio.hquery.EnableHQuery;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.homio.api.util.CommonUtils.getErrorMessage;

@Log4j2
@EnableHQuery(scanBaseClassesPackage = "org.homio")
@SpringBootApplication(exclude = {
        ErrorMvcAutoConfiguration.class,
        MongoAutoConfiguration.class
})
public class HomioApplication implements WebMvcConfigurer {

    @SneakyThrows
    public static void main(String[] args) throws IOException {
        String version = StringUtils.defaultIfEmpty(HomioApplication.class.getPackage().getImplementationVersion(), "0.0");
        System.setProperty("server.version", version);
        setProperty("server.port", "port", "9111");
        log.info("Run homio-app v.{}", version);
        setDatabaseProperties();

        try {
            new SpringApplicationBuilder(AppConfig.class).listeners(new LogService()).run(args);
        } catch (Exception ex) {
            Throwable cause = NestedExceptionUtils.getRootCause(ex);
            cause = cause == null ? ex : cause;
            log.error("Unable to start Homio application: {}", getErrorMessage(cause));
            throw ex;
        }
    }

    private static void setDatabaseProperties() {
        Properties properties = EntityContextSettingImpl.getHomioProperties();

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
        Properties properties = EntityContextSettingImpl.getHomioProperties();
        if (!value.equals(properties.get(configKey))) {
            properties.setProperty(configKey, value);
            properties.store(Files.newOutputStream(EntityContextSettingImpl.getPropertiesLocation()), null);
        }
        if (key != null) {
            System.setProperty(key, value);
        }
    }
}
