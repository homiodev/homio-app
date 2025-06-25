package org.homio.app.utils;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.homio.api.Context;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.model.Icon;
import org.homio.api.ui.field.action.ActionInputParameter;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextSettingImpl;
import org.json.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.homio.api.ui.field.action.ActionInputParameter.message;
import static org.homio.api.util.CommonUtils.getConfigPath;

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
        var properties = ContextSettingImpl.getHomioProperties();

        String dbUrl = properties.getProperty("db-url", "jdbc:sqlite:" + getConfigPath().resolve("data.db"));

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

    public static void migrateDatabase(ContextImpl context, String newDbUrl, boolean copyVariableBackup, Set<String> tables) {
        String oldDbUrl = context.setting().getEnv("db-url");
        if (oldDbUrl == null) {
            oldDbUrl = "jdbc:sqlite:%s".formatted(getConfigPath().resolve("data.db"));
        }
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
            for (String table : tables) {
                if (!copyVariableBackup && table.equals("variable_backup")) {
                    continue;
                }
                log.info("Migrating table: {}", table);
                // clean existed data
                newStmt.execute("TRUNCATE " + table + " CASCADE");
                try (ResultSet oldResultSet = oldStmt.executeQuery("SELECT * FROM " + table);
                     PreparedStatement insertStmt = newConn.prepareStatement(generateInsertSQL(table, oldResultSet))) {

                    int batchSize = 0;

                    while (oldResultSet.next()) {
                        for (int i = 1; i <= oldResultSet.getMetaData().getColumnCount(); i++) {
                            Object value = oldResultSet.getObject(i);
                            String columnName = oldResultSet.getMetaData().getColumnName(i).toLowerCase();
                            int columnType = oldResultSet.getMetaData().getColumnType(i);
                            switch (columnType) {
                                case Types.TIMESTAMP:
                                    value = value instanceof Timestamp ? value : new Timestamp((Long) value);
                                    break;
                                case Types.BOOLEAN:
                                    value = value instanceof Number n ? n.intValue() == 1 : (boolean) value;
                                    break;
                            }

                            insertStmt.setObject(i, value);
                        }
                        insertStmt.addBatch();
                        batchSize++;

                        if (batchSize % 1000 == 0) {
                            insertStmt.executeBatch();
                            batchSize = 0;
                        }
                    }
                    insertStmt.executeBatch();
                }
            }

            // Enable constraints
            newStmt.execute("SET session_replication_role = 'origin'");

            newConn.commit(); // Commit changes
            context.setting().setEnv("db-url", newDbUrl);
            log.info("DB migrated successfully");

            // Restart application
            HardwareUtils.exitApplication(context.getApplicationContext(), 5);
        } catch (Exception e) {
            log.error("Unable to migrate to new database", e);
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

    @SneakyThrows
    public static Map<String, Integer> countTableSizes(Context context) {
        Map<String, Integer> tables = new HashMap<>();
        String oldDbUrl = context.setting().getEnv("db-url");
        if (oldDbUrl == null) {
            oldDbUrl = "jdbc:sqlite:%s".formatted(getConfigPath().resolve("data.db"));
        }
        try (Connection oldConn = DriverManager.getConnection(oldDbUrl);
             Statement oldStmt = oldConn.createStatement()) {

            // Migrate tables
            for (String table : new String[]{"devices", "scripts", "settings", "widgets", "variable_backup", "widget_series", "widget_tabs", "workspace_group", "workspace_variable", "workspaces"}) {
                ResultSet rs = oldStmt.executeQuery("SELECT COUNT(*) FROM " + table);
                if (rs.next()) {
                    int count = rs.getInt(1);
                    tables.put(table, count);
                }
            }
        }
        return tables;
    }

    public static void fireMigrationWorkflow(JSONObject params, ContextImpl context) {
        String databaseURL = params.getString("URL");
        try (Connection conn = DriverManager.getConnection(databaseURL)) {
            if (conn != null && !conn.isClosed()) {
                var tableSizes = HardwareUtils.countTableSizes(context)
                        .entrySet().stream().filter(s -> s.getValue() > 0)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                context.ui().dialog().sendDialogRequest(
                        "change-db",
                        "TITLE.CHANGE_DB",
                        (responseType, pressedButton, parameters) -> {
                            var copyVariableBackup = parameters.path("copy_variable_backup").asBoolean(true);
                            HardwareUtils.migrateDatabase(context, databaseURL, copyVariableBackup, tableSizes.keySet());
                        },
                        dialogModel -> {
                            dialogModel.disableKeepOnUi();
                            dialogModel.appearance(new Icon("fas fa-database", "#C926C9"), null);
                            List<ActionInputParameter> inputs = new ArrayList<>();
                            inputs.add(message("TITLE.CHANGE_DB_DESC"));
                            if (tableSizes.containsKey("variable_backup") && tableSizes.get("variable_backup") > 100) {
                                inputs.add(ActionInputParameter.bool("copy_variable_backup", true));
                            }
                            for (Map.Entry<String, Integer> entry : tableSizes.entrySet()) {
                                inputs.add(message("Table '" + entry.getKey() + "' row count: " + entry.getValue()));
                            }
                            dialogModel.submitButton("TITLE.START_MIGRATION", button -> {
                            }).group("General", inputs);
                        });
            }
        } catch (Exception e) {
            log.error("Unable connect to db: {}", databaseURL, e);
            context.ui().toastr().error("Unable to connect to database: " + e.getMessage());
        }
    }
}
