package org.touchhome.bundle.nrf24i01.rf24.options;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import pl.grzeslowski.smarthome.rf24.generated.rf24_pa_dbm_e;

import java.util.function.Supplier;
import java.util.stream.Stream;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum PALevel {
    RF24_PA_MIN(() -> rf24_pa_dbm_e.RF24_PA_MIN),
    RF24_PA_LOW(() -> rf24_pa_dbm_e.RF24_PA_LOW),
    RF24_PA_HIGH(() -> rf24_pa_dbm_e.RF24_PA_HIGH),
    RF24_PA_MAX(() -> rf24_pa_dbm_e.RF24_PA_MAX);

    public Supplier<rf24_pa_dbm_e> valueSupplier;

    PALevel(Supplier<rf24_pa_dbm_e> valueSupplier) {
        this.valueSupplier = valueSupplier;
    }

    @JsonCreator
    public static PALevel fromValue(String name) {
        return Stream.of(PALevel.values()).filter(paLevel -> paLevel.name().equals(name)).findFirst().orElse(null);
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
