package org.touchhome.bundle.nrf24i01.rf24.options;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import pl.grzeslowski.smarthome.rf24.generated.rf24_crclength_e;

import java.util.function.Supplier;
import java.util.stream.Stream;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum CRCSize {
    DISABLE(() -> rf24_crclength_e.RF24_CRC_DISABLED),
    ENABLE_8_BITS(() -> rf24_crclength_e.RF24_CRC_8),
    ENABLE_16_BITS(() -> rf24_crclength_e.RF24_CRC_16);

    public Supplier<rf24_crclength_e> valueSupplier;

    CRCSize(Supplier<rf24_crclength_e> valueSupplier) {
        this.valueSupplier = valueSupplier;
    }

    @JsonCreator
    public static DataRate fromValue(String name) {
        return Stream.of(DataRate.values()).filter(dataRate -> dataRate.name().equals(name)).findFirst().orElse(null);
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
