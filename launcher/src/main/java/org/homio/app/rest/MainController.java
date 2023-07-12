package org.homio.app.rest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.homio.app.ble.BluetoothBundleService;
import org.homio.app.config.WebSocketConfig;
import org.homio.app.rest.MainController.GitHubDescription.Asset;
import org.homio.hquery.Curl;
import org.homio.hquery.ProgressBar;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
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


@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
public class MainController {

    private static Properties homioProperties;
    private static Path rootPath;
    private static Path propertiesLocation;

    private final BluetoothBundleService bluetoothBundleService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationContext applicationContext;
    private boolean installing;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorHolderModel> handleException(Exception ex, WebRequest request) {
        String msg = getErrorMessage(ex);
        if (ex instanceof NullPointerException) {
            msg += ". src: " + ex.getStackTrace()[0].toString();
        }
        System.err.printf("Error '%s'%n", msg);
        Objects.requireNonNull(((ServletWebRequest) request).getResponse())
               .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(new ErrorHolderModel("ERROR", msg, ex));
    }

    @GetMapping("/auth/status")
    public StatusResponse getStatus() {
        return new StatusResponse(407, installing ? "1" : "0", SystemUtils.IS_OS_LINUX);
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
    @PostMapping("/app/config/install")
    public void downloadApp() {
        if (installing) {
            throw new IllegalStateException("App already installing...");
        }
        installing = true;
        new Thread(() -> {
            ProgressBar progressBar = (progress, message, error) ->
                messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + "-global",
                    new Progress(progress, message));
            try {
                Path archiveAppPath = rootPath.resolve("homio-app.zip");
                Files.deleteIfExists(archiveAppPath);

                System.out.println("Downloading application..");
                GitHubDescription gitHubDescription =
                    Curl.get("https://api.github.com/repos/homiodev/homio-app/releases/latest", GitHubDescription.class);

                Asset asset = gitHubDescription.assets.stream()
                                                      .filter(a -> a.name.equals(archiveAppPath.getFileName().toString()))
                                                      .findAny().orElse(null);
                if (asset == null) {
                    throw new IllegalStateException("Unable to find " + archiveAppPath.getFileName() + " asset from server");
                }
                System.out.printf("Downloading '%s' to '%s'%n", archiveAppPath.getFileName(), archiveAppPath);
                Curl.downloadWithProgress(asset.browser_download_url, archiveAppPath, progressBar);

                // this command send 100 to ui that fires 'reload-page'
                progressBar.accept(100D, "Restarting application...");
                System.out.println("App installation finished. Restarting...");
                exitApplication();
            } catch (Exception ex) {
                System.err.printf("Error while downloading app: %s%n", ex.getMessage());
                progressBar.accept(-1D, ex.getMessage());
            } finally {
                installing = false;
            }
        }).start();
    }

    @SneakyThrows
    private void exitApplication() {
        System.out.println("Exit app to restart it after update");
        SpringApplication.exit(applicationContext, () -> 4);
        System.exit(4);
        // sleep to allow program exist
        Thread.sleep(30000);
        System.out.println("Unable to stop app in 30sec. Force stop it...");
        // force exit
        Runtime.getRuntime().halt(4);
    }

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
    @AllArgsConstructor
    private static class Progress {

        private double value;
        private String title;
    }

    @Setter
    @Getter
    public static class GitHubDescription {

        private String name;
        private String tag_name;
        private List<Asset> assets = new ArrayList<>();

        @Setter
        @Getter
        public static class Asset {

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
                System.err.printf("Unable to create path: '%s'%n", path.toAbsolutePath());
            }
        }
        return path;
    }

    public record StatusResponse(int status, String version, boolean isLinux) {

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
