package org.touchhome.bundle.nrf24i01.rf24.options;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.stream.Stream;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum RetryDelay {
    DELAY_15((short) 15),
    DELAY_25((short) 25),
    DELAY_50((short) 50),
    DELAY_100((short) 100);

    public short delay;

    RetryDelay(short delay) {
        this.delay = delay;
    }

    @JsonCreator
    public static RetryDelay fromValue(String value) {
        return Stream.of(RetryDelay.values()).filter(retryDelay -> retryDelay.name().equals(value)).findFirst().orElse(null);
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
