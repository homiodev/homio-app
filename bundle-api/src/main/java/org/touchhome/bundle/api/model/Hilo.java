package org.touchhome.bundle.api.model;

import com.pi4j.io.gpio.PinState;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Predicate;

@Getter
@RequiredArgsConstructor
public enum Hilo {
    high(PinState::isHigh, PinState.HIGH), low(PinState::isLow, PinState.LOW);

    private final Predicate<PinState> supplier;
    private final PinState pinState;

    public boolean match(PinState state) {
        return supplier.test(state);
    }
}
