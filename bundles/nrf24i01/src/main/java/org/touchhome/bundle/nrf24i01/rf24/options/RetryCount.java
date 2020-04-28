package org.touchhome.bundle.nrf24i01.rf24.options;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.stream.Stream;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum RetryCount {
    RETRY_15(15),
    RETRY_25(25),
    RETRY_50(50),
    RETRY_100(100);

    public short count;

    RetryCount(int count) {
        this.count = (short) count;
    }

    @JsonCreator
    public static RetryCount fromValue(String value) {
        return Stream.of(RetryCount.values()).filter(retryCount -> retryCount.name().equals(value)).findFirst().orElse(null);
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
