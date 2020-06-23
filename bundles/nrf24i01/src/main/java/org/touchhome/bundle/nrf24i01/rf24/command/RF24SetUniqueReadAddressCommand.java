package org.touchhome.bundle.nrf24i01.rf24.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.arduino.model.ArduinoDeviceEntity;
import org.touchhome.bundle.arduino.repository.ArduinoDeviceRepository;
import org.touchhome.bundle.nrf24i01.rf24.communication.RF24Message;

import static org.touchhome.bundle.nrf24i01.rf24.Command.SET_UNIQUE_READ_ADDRESS;

@Component
@RequiredArgsConstructor
public class RF24SetUniqueReadAddressCommand implements RF24CommandPlugin {

    private final EntityContext entityContext;

    @Override
    public Byte getCommandIndex() {
        return (byte) SET_UNIQUE_READ_ADDRESS.getValue();
    }

    @Override
    public String getName() {
        return SET_UNIQUE_READ_ADDRESS.name();
    }

    @Override
    public void onRemoteExecuted(RF24Message message) {
        String sensorID = String.valueOf(message.getTarget());
        ArduinoDeviceEntity entity = entityContext.getEntity(ArduinoDeviceRepository.PREFIX + sensorID);

        // such we paired devices we may send handles, ...
       /* TODO: for (AbstractRepository repository : manager.getEntityManager().getRepositories()) {
            if (repository instanceof AbstractDeviceRepository) {
                List<BaseEntity> list = manager.listAllByRepository(repository);
                for (BaseEntity baseEntity : list) {
                    DeviceBaseEntity device = (DeviceBaseEntity) baseEntity;
                    ((AbstractDeviceRepository) repository).notifyUpdate(device, null);
                }
            }
        }*/
    }
}
