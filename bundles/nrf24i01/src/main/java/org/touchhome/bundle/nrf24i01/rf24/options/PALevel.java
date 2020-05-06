package org.touchhome.bundle.nrf24i01.rf24.options;

import lombok.AllArgsConstructor;
import pl.grzeslowski.smarthome.rf24.generated.rf24_pa_dbm_e;

import java.util.function.Supplier;

@AllArgsConstructor
public enum PALevel {
    RF24_PA_MIN(() -> rf24_pa_dbm_e.RF24_PA_MIN),
    RF24_PA_LOW(() -> rf24_pa_dbm_e.RF24_PA_LOW),
    RF24_PA_HIGH(() -> rf24_pa_dbm_e.RF24_PA_HIGH),
    RF24_PA_MAX(() -> rf24_pa_dbm_e.RF24_PA_MAX);

    public Supplier<rf24_pa_dbm_e> valueSupplier;
}
