package org.touchhome.app.utils;

import com.sun.nio.zipfs.ZipFileSystem;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.touchhome.app.hardware.HotSpotHardwareRepository;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.api.HardwareException;
import org.touchhome.bundle.api.hardware.other.GPIOHardwareRepository;
import org.touchhome.bundle.api.hardware.other.PostgreSQLHardwareRepository;
import org.touchhome.bundle.api.hardware.other.StartupHardwareRepository;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.Enumeration;

@Log4j2
final class HardwareUtils {

    private static boolean hardwareChecked = false;

    /**
     * This method fires before ApplicationContext startup to make sure all related dependencies up
     */
    static void prepareHardware(ConfigurableListableBeanFactory beanFactory) {
        if (hardwareChecked) {
            return;
        }
        hardwareChecked = true;
        if (!EntityContext.isTestApplication()) {
            copyResources();
            checkDatabaseInstalled(beanFactory);
            checkWiringPi(beanFactory);
            checkHotSpotAndWifi(beanFactory);
            checkInternetConnection(beanFactory);
            startupCheck(beanFactory);
        }
    }

    private static void startupCheck(ConfigurableListableBeanFactory beanFactory) {
        StartupHardwareRepository repository = beanFactory.getBean(StartupHardwareRepository.class);
        repository.addStartupCommand("sudo java -jar /root/touchHome.jar&");
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

        if (!repository.isAutoHotSpotServiceExists()) {
            repository.installAutoHotSpot(TouchHomeUtils.resolvePath("files", "hotspot").toAbsolutePath().toString());
        }
    }

    private static void checkWiringPi(ConfigurableListableBeanFactory beanFactory) {
        GPIOHardwareRepository repository = beanFactory.getBean(GPIOHardwareRepository.class);
        log.info("Printing wiring PI version...");
        repository.printWiringPiVersion();

        log.info("Printing wiring PI info...");
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
        try {
            log.info("PostgreSQL already installed <{}>", repository.getPostgreSQLVersion());
            if (!repository.isPostgreSQLRunning()) {
                repository.startPostgreSQLService();
            }
        } catch (HardwareException ex) {
            log.warn("PostgreSQL not installed. Installing it...");
            repository.installPostgreSQL();
            log.info("PostgreSQL installed successfully.");
            repository.startPostgreSQLService();
            String pwd = beanFactory.getBean(Environment.class).getProperty("spring.datasource.password");
            repository.changePostgresPassword(pwd);
        }
    }

    @SneakyThrows
    private static void copyResources() {
        Enumeration<URL> resources = TouchHomeUtils.class.getClassLoader().getResources("files");
        Path target = TouchHomeUtils.resolvePath("files");

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if (url.getProtocol().equals("jar")) {
                try (ZipFileSystem fs = (ZipFileSystem) FileSystems.newFileSystem(url.toURI(), Collections.emptyMap())) {
                    String jarFiles = "/BOOT-INF/classes/files";
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
}
