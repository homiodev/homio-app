package org.touchhome.bundle.nrf24i01.rf24;

import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.model.HasEntityIdentifier;

import java.util.List;

@Component
public class NRF24I01ConsolePlugin implements ConsolePlugin {

    @Override
    @SneakyThrows
    public List<? extends HasEntityIdentifier> drawEntity() {
        return null;
    }

    @Override
    public int order() {
        return 1500;
    }
}
