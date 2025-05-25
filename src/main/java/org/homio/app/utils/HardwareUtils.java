package org.homio.app.utils;

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.Collections;
import java.util.Properties;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextSettingImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

@Log4j2
public final class HardwareUtils {

    /**
     * Fully restart application
     */
    @SneakyThrows
    public static void exitApplication(ApplicationContext applicationContext, int code) {
        SpringApplication.exit(applicationContext, () -> code);
        System.exit(code);
        // sleep to allow the program to exist
        Thread.sleep(30000);
        log.info("Unable to stop app in 30sec. Force stop it");
        // force exit
        Runtime.getRuntime().halt(code);
    }

    @SneakyThrows
    public static void copyResources(URL url) {
        if (url != null) {
            Path target = CommonUtils.getFilesPath();
            InputStream stream = HardwareUtils.class.getClassLoader().getResourceAsStream(url.toString());
            FileSystem fileSystem = null;
            if (stream == null) {
                fileSystem = FileSystems.newFileSystem(url.toURI(), Collections.emptyMap());
                Path filesPath = fileSystem.getPath("external_files.7z");
                stream = Files.exists(filesPath) ? Files.newInputStream(filesPath) : null;
            }
            if (stream != null) {
                String addonJar = url.getFile().replaceAll(".jar!/", "_");
                addonJar = addonJar.substring(addonJar.lastIndexOf("/") + 1);
                Path targetPath = target.resolve(target.resolve(addonJar));
                if (!Files.exists(targetPath) || Files.size(targetPath) != stream.available()) {
                    // copy files
                    log.info("Copy resource <{}>", url);
                    Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Unzip resource <{}>", targetPath);
                    ArchiveUtil.unzip(targetPath, targetPath.getParent(), null, false, null, ArchiveUtil.UnzipFileIssueHandler.replace);
                    // Files.move();
                    log.info("Done copy resource <{}>", url);
                }
                stream.close();
                if (fileSystem != null) {
                    fileSystem.close();
                }
            }
        }
    }

    public static void setDatabaseProperties(Logger log) {
        Properties properties = ContextSettingImpl.getHomioProperties();

        String dbUrl = properties.getProperty("db-url", "jdbc:sqlite:" + CommonUtils.getRootPath().resolve("data.db") +
                                                        "?user=postgres&password=password");

        System.setProperty("spring.datasource.url", dbUrl.split("\\?")[0]);
        String databaseType = dbUrl.split("\\?")[0].split(":")[1];

        // Extract user & password from query params
        if (dbUrl.contains("?")) {
            String[] params = dbUrl.split("\\?")[1].split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    if ("user".equalsIgnoreCase(keyValue[0])) {
                        System.setProperty("spring.datasource.username", keyValue[1]);
                    }
                    if ("password".equalsIgnoreCase(keyValue[0])) {
                        System.setProperty("spring.datasource.password", keyValue[1]);
                    }
                }
            }
        }

        switch (databaseType) {
            case "postgresql":
                System.setProperty("spring.datasource.driverClassName", "org.postgresql.Driver");
                System.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
                break;
            case "sqlite":
                System.setProperty("spring.datasource.driverClassName", "org.sqlite.JDBC");
                System.setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
                // for sqlite enable only 1 thread
                System.setProperty("spring.datasource.hikari.maximum-pool-size", "1");
                break;
            default:
                log.error("Unsupported database type '{}'", databaseType);
                System.exit(1);
        }

        System.setProperty("databaseType", databaseType);
        System.setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        System.out.println("Use db url: " + dbUrl);
    }

    public static void migrateDatabase(ContextImpl context, String newDbUrl) {
        String oldDbUrl = context.setting().getEnvRequire("db-url");
        try (Connection oldConn = DriverManager.getConnection(oldDbUrl);
             Connection newConn = DriverManager.getConnection(newDbUrl);
             Statement newStmt = newConn.createStatement();
             Statement oldStmt = oldConn.createStatement()) {

            newConn.setAutoCommit(false); // Start transaction

            // Apply schema
            try (InputStream schemaUrl = HardwareUtils.class.getClassLoader().getResourceAsStream("schema.sql")) {
                String schemaSql = IOUtils.toString(requireNonNull(schemaUrl), StandardCharsets.UTF_8);
                newStmt.execute(schemaSql);
            }

            // Disable constraints
            newStmt.execute("SET session_replication_role = 'replica'");

            // Migrate tables
            for (String table : new String[]{"devices", "scripts", "settings", "widgets", "variable_backup", "widget_series", "widget_tabs", "workspace_group", "workspace_variable", "workspaces"}) {
                String selectSQL = "SELECT * FROM " + table;
                try (ResultSet oldResultSet = oldStmt.executeQuery(selectSQL);
                     PreparedStatement insertStmt = newConn.prepareStatement(generateInsertSQL(table, oldResultSet))) {

                    int batchSize = 0;
                    while (oldResultSet.next()) {
                        for (int i = 1; i <= oldResultSet.getMetaData().getColumnCount(); i++) {
                            insertStmt.setObject(i, oldResultSet.getObject(i));
                        }
                        insertStmt.addBatch();
                        batchSize++;

                        if (batchSize % 1000 == 0) { // Execute batch every 1000 rows
                            insertStmt.executeBatch();
                            batchSize = 0;
                        }
                    }
                    insertStmt.executeBatch(); // Execute remaining records;
                }
            }

            // Enable constraints
            newStmt.execute("SET session_replication_role = 'origin'");

            newConn.commit(); // Commit changes
            context.setting().setEnv("db-url", newDbUrl);

            // Restart application
            HardwareUtils.exitApplication(context.getApplicationContext(), 5);
        } catch (Exception e) {
            context.ui().toastr().error("Error during DB migration: " + e.getMessage());
        }
    }

    private static String generateInsertSQL(String table, ResultSet rs) throws SQLException {
        int columnCount = rs.getMetaData().getColumnCount();
        StringBuilder sql = new StringBuilder("INSERT INTO " + table + " (");

        // Add column names
        for (int i = 1; i <= columnCount; i++) {
            sql.append(rs.getMetaData().getColumnName(i)).append(",");
        }
        sql.setLength(sql.length() - 1); // Remove last comma
        sql.append(") VALUES (").append("?,".repeat(columnCount));
        sql.setLength(sql.length() - 1); // Remove last comma
        sql.append(")");

        return sql.toString();
    }
}
