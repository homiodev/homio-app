package org.touchhome.app.utils;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.env.Environment;
import org.touchhome.app.hardware.HotSpotHardwareRepository;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.api.HardwareException;
import org.touchhome.bundle.api.hardware.other.GPIOHardwareRepository;
import org.touchhome.bundle.api.hardware.other.PostgreSQLHardwareRepository;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;
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
    static void prepareHardware(ConfigurableListableBeanFactory beanFactory) {
        if (hardwareChecked) {
            return;
        }
        hardwareChecked = true;
        checkDatabaseInstalled(beanFactory);
        checkInternetConnection(beanFactory);
        if (!EntityContext.isTestEnvironment()) {
            copyResources();
            if (EntityContext.isLinuxEnvironment()) {
                checkWiringPi(beanFactory);
                checkHotSpotAndWifi(beanFactory);
            }
        }
    }

    private static void checkInternetConnection(ConfigurableListableBeanFactory beanFactory) {
        WirelessHardwareRepository repository = beanFactory.getBean(WirelessHardwareRepository.class);

        boolean hasInetAccess = repository.hasInternetAccess("http://www.google.com");
        if (hasInetAccess) {
            log.info("Device connected to network <{}>", repository.getNetworkDescription());
        } else {
            log.error("!!!Device not connected to internet.!!!");
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
        Path target = TouchHomeUtils.getFilesPath();
        if (url.getProtocol().equals("jar")) {
            try (FileSystem fs = FileSystems.newFileSystem(url.toURI(), Collections.emptyMap())) {
                String jarFiles = "/BOOT-INF/classes/asm_files";
                Files.walk(fs.getPath(jarFiles)).filter(f -> Files.isRegularFile(f)).forEach((Path path) -> {
                    try {
                        Path resolve = target.resolve(path.toString().substring(jarFiles.length() + 1));
                        Files.createDirectories(resolve.getParent());
                        Files.copy(path, resolve, StandardCopyOption.REPLACE_EXISTING);
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
