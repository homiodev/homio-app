package org.touchhome.bundle.nrf24i01.rf24.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.nrf24i01.rf24.communication.RF24Message;
import org.touchhome.bundle.nrf24i01.rf24.communication.SendCommand;

import java.util.Map;

import static org.touchhome.bundle.nrf24i01.rf24.Command.EXECUTED;

@Log4j2
@Component
@RequiredArgsConstructor
public class RF24ExecutedCommand implements RF24CommandPlugin {

    private final EntityContext entityContext;
    private Map<Byte, RF24CommandPlugin> rf24CommandPlugins;

    @Override
    public Byte getCommandIndex() {
        return (byte) EXECUTED.getValue();
    }

    @Override
    public String getName() {
        return EXECUTED.name();
    }

    @Override
    public SendCommand execute(RF24Message message) {
        if (rf24CommandPlugins == null) {
            rf24CommandPlugins = entityContext.getBean("rf24CommandPlugins", Map.class);
        }
        byte executedCommand = message.getPayloadBuffer().get();
        RF24CommandPlugin commandPlugin = rf24CommandPlugins.get(executedCommand);

        log.info("Previous command <{}> with message id <{}> executed successfully on target device <{}>", commandPlugin.getName(), message.getMessageID(), message.getTarget());
        commandPlugin.onRemoteExecuted(message);
        return null;
    }

    @Override
    public boolean canReceiveGeneral() {
        return true;
    }
}
