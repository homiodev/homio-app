package org.touchhome.bundle.nrf24i01.rf24.command;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.arduino.model.ArduinoDeviceEntity;
import org.touchhome.bundle.arduino.repository.ArduinoDeviceRepository;
import org.touchhome.bundle.nrf24i01.rf24.Command;
import org.touchhome.bundle.nrf24i01.rf24.communication.RF24Message;
import org.touchhome.bundle.nrf24i01.rf24.communication.SendCommand;
import pl.grzeslowski.smarthome.rf24.helpers.Pipe;

import static org.touchhome.bundle.nrf24i01.rf24.Command.REGISTER_COMMAND;

@Log4j2
@Component
@AllArgsConstructor
public class RF24RegisterCommand implements RF24CommandPlugin {

    private final EntityContext entityContext;
    private final ArduinoDeviceRepository arduinoDeviceRepository;

    @Override
    public Byte getCommandIndex() {
        return (byte) REGISTER_COMMAND.getValue();
    }

    @Override
    public String getName() {
        return REGISTER_COMMAND.name();
    }

    @Override
    public SendCommand execute(RF24Message message) {
        log.info("Got registering slave device command.");
        byte dt = message.getPayloadBuffer().get();
        if (dt == 17) { // Arduino
            String sensorID = String.valueOf(message.getTarget());
            ArduinoDeviceEntity entity = entityContext.getEntity(sensorID);
            if (entity == null) {
                entity = new ArduinoDeviceEntity();
                entity.computeEntityID(() -> sensorID);

                long pipeIndex = arduinoDeviceRepository.getPipeIndex(435476713011L);
                entity.setPipe(pipeIndex);
                entityContext.save(entity);
            } else {
                log.warn("Arduino device with id <{}> already registered", sensorID);
                /*entity.setMissedPings(0);
                entityContext.save(entity);*/
            }
            return SendCommand.sendPayload(Command.SET_UNIQUE_READ_ADDRESS, new Pipe(entity.getPipe()).getPipe());
        } else {
            log.error("Unable to register unknown device type: <{}>", dt);
        }
        return SendCommand.SEND_ERROR;
    }

    @Override
    public boolean canReceiveGeneral() {
        return true;
    }
}
