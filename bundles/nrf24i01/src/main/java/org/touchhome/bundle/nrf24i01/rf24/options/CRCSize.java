package org.touchhome.bundle.nrf24i01.rf24.options;

import lombok.RequiredArgsConstructor;
import pl.grzeslowski.smarthome.rf24.generated.rf24_crclength_e;

import java.util.function.Supplier;

@RequiredArgsConstructor
public enum CRCSize {
    DISABLE(() -> rf24_crclength_e.RF24_CRC_DISABLED),
    ENABLE_8_BITS(() -> rf24_crclength_e.RF24_CRC_8),
    ENABLE_16_BITS(() -> rf24_crclength_e.RF24_CRC_16);

    public final Supplier<rf24_crclength_e> valueSupplier;
}
