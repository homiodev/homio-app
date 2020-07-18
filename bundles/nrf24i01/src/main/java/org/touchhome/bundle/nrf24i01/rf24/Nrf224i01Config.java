package org.touchhome.bundle.nrf24i01.rf24;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.touchhome.bundle.api.BundleConfiguration;
import org.touchhome.bundle.nrf24i01.rf24.command.RF24CommandPlugin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@BundleConfiguration
public class Nrf224i01Config {

    public static final int ARDUINO_MAX_MISSED_PINGS = 5;
    public static final int ARDUINO_PING_INTERVAL = 120000;

    @Bean
    public Map<Byte, RF24CommandPlugin> rf24CommandPlugins(List<RF24CommandPlugin> rf24CommandPlugins) {
        return rf24CommandPlugins.stream().collect(Collectors.toMap(RF24CommandPlugin::getCommandIndex, p -> p));
    }
}
