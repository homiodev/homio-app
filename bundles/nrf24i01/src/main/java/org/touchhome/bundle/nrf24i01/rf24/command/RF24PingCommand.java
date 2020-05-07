package org.touchhome.bundle.nrf24i01.rf24.command;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import static org.touchhome.bundle.nrf24i01.rf24.Command.PING;

@Log4j2
@Component
@RequiredArgsConstructor
public class RF24PingCommand implements RF24CommandPlugin {

    @Override
    public Byte getCommandIndex() {
        return (byte) PING.getValue();
    }

    @Override
    public String getName() {
        return PING.name();
    }
}
