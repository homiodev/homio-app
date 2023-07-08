package org.homio.app.rest;

import static org.apache.commons.lang3.StringUtils.defaultString;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.homio.app.ble.BluetoothBundleService;
import org.homio.app.config.WebSocketConfig;
import org.homio.app.hardware.StartupHardwareRepository;
import org.homio.hquery.HQueryProgressBar;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
public class MainController {

    public static final ConfFile configuration = getConfFile();

    private final BluetoothBundleService bluetoothBundleService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MachineHardwareRepository machineHardwareRepository;
    private final StartupHardwareRepository startupHardwareRepository;
    private boolean installingApp;
    private boolean initInstalling;

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
        log.info("Update /etc/systemd/system/homio.service");
        machineHardwareRepository.execute("sed -i 's/boot/core/g' /etc/systemd/system/homio.service");
        machineHardwareRepository.execute("sed -i '3 i After=postgresql.service' /etc/systemd/system/homio.service");
        machineHardwareRepository.execute("sed -i '4 i Requires=postgresql.service' /etc/systemd/system/homio.service");
        machineHardwareRepository.reboot();
    }

    @GetMapping("/app/config")
    public DeviceConfig getConfiguration() {
        DeviceConfig deviceConfig = new DeviceConfig();
        deviceConfig.hasApp = Files.exists(getRootPath().resolve("homio-app.jar"));
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
                HQueryProgressBar progressBar = new HQueryProgressBar(0, 100, 500) {
                    @Override
                    public void progress(double value, String message, boolean isError) {
                        messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + "-global",
                            new Progress(Progress.Type.init, value, message));
                    }
                };
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
        /*if (SystemUtils.IS_OS_WINDOWS) {
            Path pgCtl = getRootPath().resolve("installs/postgresql/bin/pg_ctl.exe");
            return Files.exists(pgCtl);
        }
        return machineHardwareRepository.isSoftwareInstalled("psql");*/
        try {
            DriverManager.getConnection(
                defaultString(configuration.db_url, "jdbc:postgresql://localhost:5432/postgres"),
                defaultString(configuration.db_user, "postgres"),
                defaultString(configuration.db_password, "password"));
            return true;
        } catch (Exception ignore) {

        }
        return false;
    }

    private void installPostgreSql(HQueryProgressBar progressBar) {
        if (SystemUtils.IS_OS_WINDOWS) {
            installWindowsPostgresql(progressBar);
            return;
        }
        machineHardwareRepository.installSoftware("postgresql", 1200, progressBar);
        String postgresPath = machineHardwareRepository.execute("find /usr -wholename '*/bin/postgres'");
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
    private void installWindowsPostgresql(HQueryProgressBar progressBar) {
        Path rootPath = getRootPath();
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
    @PostMapping("/app/config/downloadApp")
    public void downloadApp() {
        if (installingApp) {
            throw new IllegalStateException("App already installing...");
        }
        BiConsumer<Double, String> progressBar = (progress, message) ->
            messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + "-global",
                new Progress(Progress.Type.download, progress, message));
        Path targetPath = getRootPath().resolve("homio-app.jar");
        if (Files.exists(targetPath)) {
            progressBar.accept(100D, "App already downloaded.");
            return;
        }
        Path tmpPath = getRootPath().resolve("homio-app_tmp.jar");
        try {
            this.installingApp = true;
            Files.deleteIfExists(tmpPath);

            log.info("Installing application...");
            GitHubDescription gitHubDescription =
                Curl.get("https://api.github.com/repos/homiodev/homio-app/releases/latest", GitHubDescription.class);

            String md5HashValue = getExpectedMD5Hash(gitHubDescription);

            GitHubDescription.Asset asset =
                gitHubDescription.assets.stream().filter(a -> a.name.equals("homio-app.jar")).findAny().orElse(null);
            if (asset == null) {
                throw new IllegalStateException("Unable to find homio-app.jar asset from server");
            }
            log.info("Downloading homio-app.jar to <{}>", tmpPath);
            Curl.downloadWithProgress(asset.browser_download_url, tmpPath, progressBar);

            // test downloaded file md5 hash
            if (!md5HashValue.equals(DigestUtils.md5Hex(Files.newInputStream(tmpPath)))) {
                Files.delete(tmpPath);
                throw new IllegalStateException("Downloaded file corrupted");
            }
            Files.move(tmpPath, targetPath);
            log.info("App installation finished");
        } finally {
            installingApp = false;
        }
    }

    private String getExpectedMD5Hash(GitHubDescription gitHubDescription) {
        GitHubDescription.Asset md5Asset =
            gitHubDescription.assets.stream().filter(a -> a.name.equals("md5.hex")).findAny().orElse(null);
        if (md5Asset == null) {
            throw new IllegalStateException("Unable to find md5.hex asset from server");
        }
        return new String(Curl.download(md5Asset.browser_download_url).getBytes());
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

    public static Path getRootPath() {
        return SystemUtils.IS_OS_WINDOWS ? SystemUtils.getUserHome().toPath().resolve("homio") :
            createDirectoriesIfNotExists(Paths.get("/opt/homio"));
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

    public static List<String> readFile(String fileName) {
        try {
            return IOUtils.readLines(MainController.class.getClassLoader().getResourceAsStream(fileName),
                Charset.defaultCharset());
        } catch (Exception ex) {
            log.error("Error reading file", ex);

        }
        return Collections.emptyList();
    }

    public record StatusResponse(int status, String version) {

    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class ConfFile {

        private String app_path;
        private String uuid;

        private int run_count;

        private String db_url;
        private String db_user;
        private String db_password;
    }

    private static ConfFile getConfFile() {
        Path confFilePath = getRootPath().resolve("homio.conf");
        if (Files.exists(confFilePath)) {
            try {
                return new ObjectMapper().readValue(confFilePath.toFile(), ConfFile.class);
            } catch (Exception ex) {
                log.error("Found corrupted config file. Regenerate new one.");
            }
        }
        return new ConfFile();
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
}
