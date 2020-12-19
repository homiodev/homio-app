package org.touchhome.app.utils;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.env.Environment;
import org.touchhome.app.hardware.HotSpotHardwareRepository;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.other.GPIOHardwareRepository;
import org.touchhome.bundle.api.hardware.other.PostgreSQLHardwareRepository;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;
import org.touchhome.bundle.api.hquery.api.HardwareException;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.Objects;

@Log4j2
public final class HardwareUtils {

    private static boolean hardwareChecked = false;

    /**
     * This method fires before ApplicationContext startup to make sure all related dependencies up
     */
    public static void prepareHardware(ConfigurableListableBeanFactory beanFactory) {
        if (hardwareChecked) {
            return;
        }
        hardwareChecked = true;
        checkDatabaseInstalled(beanFactory);
        copyResources();
        if (EntityContext.isLinuxEnvironment()) {
            checkWiringPi(beanFactory);
            checkHotSpotAndWifi(beanFactory);
        }
    }

    private static void checkHotSpotAndWifi(ConfigurableListableBeanFactory beanFactory) {
        HotSpotHardwareRepository repository = beanFactory.getBean(HotSpotHardwareRepository.class);
        WirelessHardwareRepository wifiRepository = beanFactory.getBean(WirelessHardwareRepository.class);
        String iface = wifiRepository.getActiveNetworkInterface();
        if (iface != null) {
            wifiRepository.setWifiPowerSaveOff(iface);
        }

        if (!repository.isAutoHotSpotServiceExists()) {
            repository.installAutoHotSpot(TouchHomeUtils.getFilesPath().resolve("hotspot").toAbsolutePath().toString());
        }
    }

    private static void checkWiringPi(ConfigurableListableBeanFactory beanFactory) {
        GPIOHardwareRepository repository = beanFactory.getBean(GPIOHardwareRepository.class);
        if (!repository.printWiringPiInfo()) {
            log.warn("Unable to get wiring PI info");
            repository.installWiringPiAuto();
            if (!repository.printWiringPiInfo()) {
                log.error("Unable to install wiring PI");
            }
        }
    }

    private static void checkDatabaseInstalled(BeanFactory beanFactory) {
        PostgreSQLHardwareRepository repository = beanFactory.getBean(PostgreSQLHardwareRepository.class);
        Environment env = beanFactory.getBean(Environment.class);
        String url = env.getProperty("spring.datasource.url");
        String pwd = env.getProperty("spring.datasource.password");
        DataSource dataSource = DataSourceBuilder.create().url(url)
                .driverClassName(env.getProperty("spring.datasource.driver-class-name"))
                .username(env.getProperty("spring.datasource.username")).password(pwd).build();
        try {
            log.debug("Check db connection");
            // check that we able connect to target db
            dataSource.getConnection();
            log.info("Db check connection success");
        } catch (Exception ex) {
            log.warn("Db connection not alive. url: <{}>. Msg: <{}>", url, TouchHomeUtils.getErrorMessage(ex));

            // try install postgresql if url points to localhost
            if (Objects.requireNonNull(url).startsWith("jdbc:postgresql://localhost:5432")) {
                log.debug("Database url require local postgres. Trying start/install it");
                try {
                    log.info("PostgreSQL already installed <{}>", repository.getPostgreSQLVersion());
                    if (!repository.isPostgreSQLRunning()) {
                        repository.startPostgreSQLService();
                    }
                    repository.changePostgresPassword(pwd);
                } catch (HardwareException he) {
                    log.warn("PostgreSQL not installed. Installing it...");
                    repository.installPostgreSQL();
                    log.info("PostgreSQL installed successfully.");
                    repository.startPostgreSQLService();
                    repository.changePostgresPassword(pwd);
                }
            }
        }
    }

    @SneakyThrows
    private static void copyResources() {
        URL url = HardwareUtils.class.getClassLoader().getResource("asm_files");
        if (url == null) {
            throw new IllegalStateException("Unable to find 'asm_files' directory.");
        }
        copyResources(url, "/BOOT-INF/classes/asm_files");
    }

    @SneakyThrows
    public static void copyResources(URL url, String jarFiles) {
        if (url != null) {
            Path target = TouchHomeUtils.getFilesPath();
            if (url.getProtocol().equals("jar")) {
                try (FileSystem fs = FileSystems.newFileSystem(url.toURI(), Collections.emptyMap())) {
                    Files.walk(fs.getPath(jarFiles)).filter(f -> Files.isRegularFile(f)).forEach((Path path) -> {
                        try {
                            Path resolve = target.resolve(path.toString().substring(jarFiles.length() + 1));
                            Files.createDirectories(resolve.getParent());
                            if (!Files.exists(resolve) || (Files.exists(resolve) && Files.getLastModifiedTime(resolve).compareTo(Files.getLastModifiedTime(path)) < 0)) {
                                if (mayCopyResource(path.getFileName().toString())) {
                                    log.info("Copy resource <{}>", path.getFileName());
                                    Files.copy(path, resolve, StandardCopyOption.REPLACE_EXISTING);
                                    if (path.getFileName().toString().endsWith(".zip")) {
                                        log.info("Unzip resource <{}>", path.getFileName());
                                        ZipFile zipFile = new ZipFile(resolve.toFile());
                                        zipFile.extractAll(resolve.getParent().toString());
                                        log.info("Done unzip resource <{}>", path.getFileName());
                                    }
                                    log.info("Done copy resource <{}>", path.getFileName());
                                } else {
                                    log.warn("Skip copying resource <{}>", path.getFileName());
                                }
                            } else {
                                log.info("Skip copy resource <{}>", path.getFileName());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            } else {
                FileUtils.copyDirectory(new File(url.toURI()), target.toFile(), false);
            }
        }
    }

    // not to smart but works well :)
    private static boolean mayCopyResource(String fileName) {
        if (fileName.endsWith("_filter.zip")) {
            if (SystemUtils.IS_OS_LINUX && !fileName.endsWith(".avr_filter.zip")) {
                return false;
            }
            return !SystemUtils.IS_OS_WINDOWS || fileName.endsWith(".win_filter.zip");
        }
        return true;
    }
}
