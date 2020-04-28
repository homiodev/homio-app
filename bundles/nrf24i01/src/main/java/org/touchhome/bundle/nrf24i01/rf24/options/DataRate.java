package org.touchhome.bundle.nrf24i01.rf24.options;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import pl.grzeslowski.smarthome.rf24.generated.rf24_datarate_e;

import java.util.function.Supplier;
import java.util.stream.Stream;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum DataRate {
    RF24_250KBPS(() -> rf24_datarate_e.RF24_250KBPS),
    RF24_1MBPS(() -> rf24_datarate_e.RF24_1MBPS),
    RF24_2MBPS(() -> rf24_datarate_e.RF24_2MBPS);

    public Supplier<rf24_datarate_e> valueSupplier;

    DataRate(Supplier<rf24_datarate_e> valueSupplier) {
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
