package org.touchhome.app.utils;

import lombok.extern.log4j.Log4j2;
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
            String rpiSysDir = null;
            repository.installHostapd(rpiSysDir);
        }
    }

    private static void checkWiringPi(ConfigurableListableBeanFactory beanFactory) {
        GPIOHardwareRepository repository = beanFactory.getBean(GPIOHardwareRepository.class);
        try {
            log.info("Printing wiring PI version...");
            repository.printWiringPiVersion();

            log.info("Printing wiring PI info...");
            repository.printWiringPiInfo();
        } catch (HardwareException ex) {
            log.warn("Unable to get wiring PI info");
            repository.installWiringPiAuto();
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
}
