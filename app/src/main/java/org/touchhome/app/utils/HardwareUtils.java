package org.touchhome.app.utils;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.api.HardwareException;
import org.touchhome.bundle.api.hardware.other.GPIOHardwareRepository;
import org.touchhome.bundle.api.hardware.other.PostgreSQLHardwareRepository;
import org.touchhome.bundle.api.hardware.other.StartupHardwareRepository;
import org.touchhome.bundle.api.hardware.wifi.HotSpotHardwareRepository;
import org.touchhome.bundle.api.hardware.wifi.NetworkStat;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;

@Log4j2
final class HardwareUtils {

    private static boolean hardwareChecked = false;

    /**
     * This method fires before ApplicationContext startup to make sure all related dependencies up
     * @param beanFactory
     */
    static void prepareHardware(ConfigurableListableBeanFactory beanFactory) {
        if (hardwareChecked) {
            return;
        }
        hardwareChecked = true;
        if (!EntityContext.isTestApplication()) {
            checkDatabaseInstalled(beanFactory);
            checkWiringPi(beanFactory);
            // TODO: check what to do with this: checkHotSpotAndWifi(beanFactory);
            checkWifi(beanFactory);
            startupCheck(beanFactory);
        }
    }

    private static void startupCheck(ConfigurableListableBeanFactory beanFactory) {
        StartupHardwareRepository repository = beanFactory.getBean(StartupHardwareRepository.class);
        repository.addStartupCommand("sudo java -jar /root/touchHome.jar&");
    }

    private static void checkWifi(ConfigurableListableBeanFactory beanFactory) {
        WirelessHardwareRepository repository = beanFactory.getBean(WirelessHardwareRepository.class);

        NetworkStat networkStat = repository.stat();

        if (!networkStat.hasInternetAccess()) {
            log.warn("!!!Device not connected to wifi.!!!");
        } else {
            log.info("Device connected to wifi network <{}>", networkStat);
        }
    }

    private static void checkHotSpotAndWifi(ConfigurableListableBeanFactory beanFactory) {
        HotSpotHardwareRepository repository = beanFactory.getBean(HotSpotHardwareRepository.class);

        if (repository.isAutoHotSpotServiceExists()) {
            String rpiSysDir = null;
            repository.installHostapd();
            repository.installDnsmasq();
            repository.disableHostapd();
            repository.disableDnsmasq();
            repository.copyHostapdConf(rpiSysDir);
            repository.replaceDaemonConfPath();
            repository.appendDnsmasqContent(rpiSysDir);
            repository.copyHostapdConf(rpiSysDir);
            repository.copyInterfaces(rpiSysDir);
            repository.ignoreWpaConfiguration();
            repository.copyAutoHotSpotService(rpiSysDir);
            repository.enableAutoHotSpotService();
            repository.copyAutoHotSpot(rpiSysDir);
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
        } catch (HardwareException ex) {
            log.warn("PostgreSQL not installed. Installing it...");
            repository.installPostgreSQL();
            log.info("PostgreSQL installed successfully.");
            String pwd = beanFactory.getBean(Environment.class).getProperty("spring.datasource.password");
            repository.changePostgresPassword(pwd);
        }
    }
}
