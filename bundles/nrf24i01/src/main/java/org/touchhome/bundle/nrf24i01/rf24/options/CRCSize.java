package org.touchhome.bundle.nrf24i01.rf24.options;

import lombok.AllArgsConstructor;
import pl.grzeslowski.smarthome.rf24.generated.rf24_crclength_e;

import java.util.function.Supplier;

@AllArgsConstructor
public enum CRCSize {
    DISABLE(() -> rf24_crclength_e.RF24_CRC_DISABLED),
    ENABLE_8_BITS(() -> rf24_crclength_e.RF24_CRC_8),
    ENABLE_16_BITS(() -> rf24_crclength_e.RF24_CRC_16);

    public Supplier<rf24_crclength_e> valueSupplier;
}
