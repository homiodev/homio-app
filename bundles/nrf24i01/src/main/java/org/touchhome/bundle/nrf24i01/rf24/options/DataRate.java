package org.touchhome.bundle.nrf24i01.rf24.options;

import lombok.AllArgsConstructor;
import pl.grzeslowski.smarthome.rf24.generated.rf24_datarate_e;

import java.util.function.Supplier;

@AllArgsConstructor
public enum DataRate {
    RF24_250KBPS(() -> rf24_datarate_e.RF24_250KBPS),
    RF24_1MBPS(() -> rf24_datarate_e.RF24_1MBPS),
    RF24_2MBPS(() -> rf24_datarate_e.RF24_2MBPS);

    public Supplier<rf24_datarate_e> valueSupplier;
}
