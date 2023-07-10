package org.homio.app.rest;

import static org.apache.commons.lang3.StringUtils.defaultString;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.homio.app.ble.BluetoothBundleService;
import org.homio.app.config.WebSocketConfig;
import org.homio.app.hardware.StartupHardwareRepository;
import org.homio.hquery.Curl;
import org.homio.hquery.ProgressBar;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@Log4j2
@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
public class MainController {

    private static Properties homioProperties;
    private static Path rootPath;
    private static Path propertiesLocation;

    private final BluetoothBundleService bluetoothBundleService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MachineHardwareRepository machineHardwareRepository;
    private final StartupHardwareRepository startupHardwareRepository;
    private boolean installingApp;
    private boolean initInstalling;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorHolderModel> handleException(Exception ex, WebRequest request) {
        String msg = getErrorMessage(ex);
        if (ex instanceof NullPointerException) {
            msg += ". src: " + ex.getStackTrace()[0].toString();
        }
        log.error("Error <{}>", msg, ex);
        Objects.requireNonNull(((ServletWebRequest) request).getResponse())
               .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(new ErrorHolderModel("ERROR", msg, ex));
    }

    @GetMapping("/auth/status")
    public StatusResponse getStatus() {
        return new StatusResponse(407, "0");
    }

    @GetMapping("/device/characteristic/{uuid}")
    public OptionModel getDeviceCharacteristic(@PathVariable("uuid") String uuid) {
        return OptionModel.key(bluetoothBundleService.getDeviceCharacteristic(uuid));
    }

    @PutMapping("/device/characteristic/{uuid}")
    public void setDeviceCharacteristic(@PathVariable("uuid") String uuid, @RequestBody byte[] value) {
        bluetoothBundleService.setDeviceCharacteristic(uuid, value);
    }

    @SneakyThrows
    @PostMapping("/app/config/finish")
    public void finishConfiguration() {
        if (SystemUtils.IS_OS_LINUX) {
            log.info("Update /etc/systemd/system/homio.service");

            machineHardwareRepository.execute("sed -i '3 i After=postgresql.service' /etc/systemd/system/homio.service");
            machineHardwareRepository.execute("sed -i '4 i Requires=postgresql.service' /etc/systemd/system/homio.service");
            machineHardwareRepository.reboot();
        }
    }

    @GetMapping("/app/config")
    public DeviceConfig getConfiguration() {
        DeviceConfig deviceConfig = new DeviceConfig();
        deviceConfig.hasApp = Files.exists(rootPath.resolve("homio-app.jar"));
        deviceConfig.installingApp = this.installingApp;
        deviceConfig.initInstalling = this.initInstalling;
        deviceConfig.hasInitSetup = isPostgresAvailable();
        return deviceConfig;
    }

    @PostMapping("/app/config/init")
    public void initialSetup() {
        if (initInstalling) {
            throw new IllegalStateException("Already installing...");
        }
        initInstalling = true;
        new Thread(() -> {
            try {
                ProgressBar progressBar = (message, progress, error) ->
                    messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + "-global",
                        new Progress(Progress.Type.init, message, progress));
                try {
                    if (!isPostgresAvailable()) {
                        if (SystemUtils.IS_OS_LINUX) {
                            progressBar.progress(5D, "Update os", false);
                            machineHardwareRepository.execute("apt-get update", 600, progressBar);
                            progressBar.progress(20D, "Full upgrade os", false);
                            machineHardwareRepository.execute("apt-get -y full-upgrade", 1200, progressBar);
                            progressBar.progress(40D, "Installing Postgresql", false);
                            installPostgreSql(progressBar);
                            machineHardwareRepository.execute("apt-get clean");
                        } else if (SystemUtils.IS_OS_WINDOWS) {
                            installPostgreSql(progressBar);
                        }
                    }
                    progressBar.progress(100D, "Done.", false);
                } catch (Exception ex) {
                    progressBar.progress(100D, "Error: " + ex.getMessage(), true);
                    throw new IllegalStateException(ex);
                }
            } finally {
                initInstalling = false;
            }
        }).start();
    }

    public static boolean isPostgresAvailable() {
        try {
            DriverManager.getConnection(
                defaultString(getHomioProperty("dbUrl", "jdbc:postgresql://localhost:5432/postgres")),
                defaultString(getHomioProperty("dbUser", "postgres")),
                defaultString(getHomioProperty("dbPassword", "password")));
            return true;
        } catch (Exception ignore) {

        }
        return false;
    }

    @SneakyThrows
    @PostMapping("/app/config/downloadApp")
    public void downloadApp() {
        if (installingApp) {
            throw new IllegalStateException("App already installing...");
        }
        ProgressBar progressBar = (progress, message, error) ->
            messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + "-global",
                new Progress(Progress.Type.download, progress, message));
        Path targetPath = rootPath.resolve("homio-app.jar");
        if (Files.exists(targetPath)) {
            progressBar.accept(100D, "App already downloaded.");
            return;
        }
        Path archiveAppPath = rootPath.resolve("homio-app.zip");
        try {
            this.installingApp = true;
            Files.deleteIfExists(archiveAppPath);

            log.info("Installing application...");
            GitHubDescription gitHubDescription =
                Curl.get("https://api.github.com/repos/homiodev/homio-app/releases/latest", GitHubDescription.class);

            GitHubDescription.Asset asset =
                gitHubDescription.assets.stream().filter(a -> a.name.equals(archiveAppPath.getFileName().toString())).findAny().orElse(null);
            if (asset == null) {
                throw new IllegalStateException("Unable to find " + archiveAppPath.getFileName() + " asset from server");
            }
            log.info("Downloading '{}' to '{}'", archiveAppPath.getFileName(), archiveAppPath);
            Curl.downloadWithProgress(asset.browser_download_url, archiveAppPath, progressBar);

            progressBar.accept(90D, "Uncompressing files...");
            log.info("Uncompressing files...");

            try (ZipFile zipFile = new ZipFile(archiveAppPath.toFile())) {
                zipFile.extractAll(rootPath.toString());
            }

            Files.deleteIfExists(archiveAppPath);
            log.info("App installation finished");
        } finally {
            installingApp = false;
        }
    }

    public static List<String> readFile(String fileName) {
        try {
            return IOUtils.readLines(Objects.requireNonNull(MainController.class.getClassLoader().getResourceAsStream(fileName)),
                Charset.defaultCharset());
        } catch (Exception ex) {
            log.error("Error reading file", ex);

        }
        return Collections.emptyList();
    }

    /*
        # IN CASE OF sub:
        # export PRIMARY_IP=192.168.0.110
        # sudo systemctl stop postgresql
        # sudo -H -u postgres bash -c 'rm -rf $PSQL_DATA_PATH/main/*'
        # sudo PGPASSWORD="password" -H -u postgres bash -c "pg_basebackup -h $PRIMARY_IP -D /usr/local/pgsql/data -P -U replicator
         --xlog-method=stream"
        # sudo sed -i "s/#hot_standby = 'off'/hot_standby = 'on'/g" $PSQL_CONF_PATH/postgresql.conf
        # echo "standby_mode = 'on'\nprimary_conninfo = 'host=$PRIMARY_IP port=5432 user=replicator
        password=password'\ntrigger_file = '/var/lib/postgresql/9.6/trigger'\nrestore_command = 'cp /var/lib/postgresql/9.6/archive/%f \"%p\"'" >>
        $PSQL_DATA_PATH/main/recovery.conf
        # sudo systemctl start postgresql
    */
    public static String getErrorMessage(Throwable ex) {
        if (ex == null) {
            return null;
        }
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        if (cause instanceof NullPointerException) {
            return "Unexpected NullPointerException at line: " + ex.getStackTrace()[0].toString();
        }

        return StringUtils.defaultString(cause.getMessage(), cause.toString());
    }

    @Getter
    @Setter
    private static class DeviceConfig {

        private final boolean bootOnly = true;
        public boolean initInstalling;
        private boolean hasInitSetup;
        private boolean installingApp;
        private boolean hasApp;
    }

    @Getter
    @AllArgsConstructor
    private static class Progress {

        private final Type type;
        private double value;
        private String title;

        private enum Type {
            download, init
        }
    }

    @Setter
    @Getter
    private static class GitHubDescription {

        private String name;
        private String tag_name;
        private List<Asset> assets = new ArrayList<>();

        @Setter
        @Getter
        private static class Asset {

            private String name;
            private long size;
            private String browser_download_url;
            private String updated_at;
        }
    }

    public static Path createDirectoriesIfNotExists(Path path) {
        if (Files.notExists(path)) {
            try {
                Files.createDirectories(path);
            } catch (Exception ex) {
                log.error("Unable to create path: <{}>", path.toAbsolutePath().toString());
            }
        }
        return path;
    }

    public static String getHomioPropertyRequire(String key) {
        return getHomioProperties().getProperty(key);
    }

    public record StatusResponse(int status, String version) {

    }

    @SneakyThrows
    public static String getHomioProperty(String key, String defaultValue) {
        Properties properties = getHomioProperties();
        if (properties.containsKey(key)) {
            return properties.getProperty(key);
        }
        properties.setProperty(key, defaultValue);
        properties.store(Files.newOutputStream(propertiesLocation), null);
        return defaultValue;
    }

    private void installPostgreSql(ProgressBar progressBar) {
        if (SystemUtils.IS_OS_WINDOWS) {
            installWindowsPostgresql(progressBar);
            return;
        }
        machineHardwareRepository.installSoftware("postgresql", 1200, progressBar);
        String postgresPath = machineHardwareRepository.execute("find /usr -wholename '*/bin/postgres'", 60, progressBar);
        String version = Paths.get(postgresPath).subpath(3, 4).toString();
        for (String config : readFile("configurePostgresql.conf")) {
            config = config.replace("$PSQL_CONF_PATH", "/etc/postgresql/" + version + "/main");
            machineHardwareRepository.execute(config);
        }
        if (!startupHardwareRepository.isPostgreSQLRunning()) {
            throw new IllegalStateException("Postgresql is not running");
        }
        machineHardwareRepository.execute("sudo -u postgres psql -c \"ALTER user postgres WITH PASSWORD 'password'\"");
        machineHardwareRepository.execute(
            "sudo -u postgres psql -c \"CREATE ROLE replication WITH REPLICATION PASSWORD 'password' LOGIN\"");
    }

    @SneakyThrows
    private void installWindowsPostgresql(ProgressBar progressBar) {
        Path installPath = rootPath.resolve("installs");
        Path postgresqlPath = installPath.resolve("postgresql");
        Path pgCtl = postgresqlPath.resolve("bin/pg_ctl.exe");
        String url = "https://get.enterprisedb.com/postgresql/postgresql-15.3-2-windows-x64-binaries.zip";
        Path postgresqlZip = installPath.resolve("postgresql.zip");
        if (!Files.exists(pgCtl)) {
            Curl.download(url, postgresqlZip);
        }
        try (ZipFile zipFile = new ZipFile(postgresqlZip.toString())) {
            zipFile.extractAll(postgresqlPath.toString());
        }
        Files.deleteIfExists(postgresqlZip);
        String initDb = postgresqlPath.resolve("pgsql/bin/initdb.exe").toString();
        machineHardwareRepository.execute(initDb + " -D " + rootPath.resolve("db_data"), progressBar);

        machineHardwareRepository.execute(pgCtl + " -D " + rootPath.resolve("db_data"), progressBar);
    }

    @SneakyThrows
    private static Properties getHomioProperties() {
        if (homioProperties == null) {
            propertiesLocation = getHomioPropertiesLocation();
            homioProperties = new Properties();
            try {
                homioProperties.load(Files.newInputStream(propertiesLocation));
                rootPath = Paths.get(homioProperties.getProperty("rootPath"));
                Files.createDirectories(rootPath);
            } catch (Exception ignore) {
            }
            if (rootPath == null || !Files.exists(rootPath)) {
                rootPath = propertiesLocation.getParent();
                homioProperties.put("rootPath", rootPath.toString());
                homioProperties.store(Files.newOutputStream(propertiesLocation), null);
            }
        }
        return homioProperties;
    }

    @SneakyThrows
    private static Path getHomioPropertiesLocation() {
        Path propertiesFile = (SystemUtils.IS_OS_WINDOWS ? SystemUtils.getUserHome().toPath().resolve("homio") :
            createDirectoriesIfNotExists(Paths.get("/opt/homio"))).resolve("homio.properties");
        if (!Files.exists(propertiesFile)) {
            Path jarLocation = Paths.get(MainController.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path launchPath = jarLocation.resolve("homio.properties");
            if (Files.exists(launchPath)) {
                propertiesFile = launchPath;
            }
        }
        return propertiesFile;
    }
}
