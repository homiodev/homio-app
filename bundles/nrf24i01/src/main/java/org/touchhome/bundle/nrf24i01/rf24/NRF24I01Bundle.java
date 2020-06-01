package org.touchhome.bundle.nrf24i01.rf24;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.BundleContext;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.util.RaspberryGpioPin;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.arduino.model.ArduinoDeviceEntity;
import org.touchhome.bundle.arduino.repository.ArduinoDeviceRepository;
import org.touchhome.bundle.nrf24i01.rf24.communication.RF24Message;
import org.touchhome.bundle.nrf24i01.rf24.communication.ReadListener;
import org.touchhome.bundle.nrf24i01.rf24.communication.Rf24Communicator;
import org.touchhome.bundle.nrf24i01.rf24.communication.SendCommand;
import org.touchhome.bundle.nrf24i01.rf24.options.*;
import org.touchhome.bundle.nrf24i01.rf24.setting.Nrf24i01EnableButtonsSetting;
import org.touchhome.bundle.nrf24i01.rf24.setting.Nrf24i01StatusMessageSetting;
import org.touchhome.bundle.nrf24i01.rf24.setting.Nrf24i01StatusSetting;
import pl.grzeslowski.smarthome.rf24.helpers.Pipe;

import static org.touchhome.bundle.api.util.RaspberryGpioPin.*;

@Log4j2
@Component
@RequiredArgsConstructor
public class NRF24I01Bundle implements BundleContext {
    private final Rf24Communicator rf24Communicator;
    private final EntityContext entityContext;

    private static final Pipe GLOBAL_WRITE_PIPE = new Pipe("2Node");
    private static byte messageID = 0;
    private static String errorLoadingLibrary = null;
    private boolean libLoaded = false;

    @Value("${rf24RetryDelay:15}")
    private short rf24RetryDelay;

    @Value("${rf24RetryCount:15}")
    private short rf24RetryCount;

    @Value("${rf24WaitResponseTimeout:500}")
    private short rf24WaitResponseTimeout;

    @Value("${rf24AutoAck:false}")
    private boolean rf24AutoAck;

    public boolean isNrf24L01Works() {
        return libLoaded && errorLoadingLibrary == null && rf24Communicator.getNRF24L01() != null;
    }

    private void loadLibrary() {
        if (!libLoaded) {
            try {
                System.load(TouchHomeUtils.getFilesPath().resolve("nrf24i01/librf24bcmjava.so").toAbsolutePath().toString());
            } catch (Throwable ex) {
                log.error("Error while load nrf24i01 library");
                errorLoadingLibrary = TouchHomeUtils.getErrorMessage(ex);
                entityContext.setSettingValue(Nrf24i01StatusMessageSetting.class, errorLoadingLibrary);
                entityContext.setSettingValue(Nrf24i01StatusSetting.class, DeviceStatus.OFFLINE);
            }
        }
    }

    public void init() {
        loadLibrary();
        if (!isNrf24L01Works()) {
            entityContext.disableFeature(EntityContext.DeviceFeature.NRF21I01);
        } else {
            RaspberryGpioPin.occupyPins("NRF21I01", PIN19, PIN21, PIN22, PIN23, PIN24);
        }
        entityContext.listenSettingValue(Nrf24i01EnableButtonsSetting.class, enable -> {
            if (enable) {
                if (isNrf24L01Works()) {
                    rf24Communicator.runPipeReadWrite();
                }
            } else {
                if (!rf24Communicator.stopRunPipeReadWrite()) {
                    log.error("Unable to stop read/write threads");
                }
            }
        });
    }

    @Override
    public String getBundleId() {
        return "nrf24i01";
    }

    @Override
    public int order() {
        return 1000;
    }

    public synchronized void subscribeForReading(ReadListener readListener) {
        rf24Communicator.subscribeForReading(readListener);
    }

    public void scheduleSend(SendCommand sendCommand, RF24Message message, Pipe pipe) {
        rf24Communicator.send(sendCommand, message, pipe);
    }

    public void scheduleGlobalSend(SendCommand sendCommand, RF24Message message) {
        rf24Communicator.send(sendCommand, message, GLOBAL_WRITE_PIPE);
    }

    public SendCommand executeCommand(RF24Message message) {
        return message.getCommandPlugin().execute(message);
    }

    public void updateCRCSize(CRCSize crcSize) {
        if (rf24Communicator.getNRF24L01() != null) {
            rf24Communicator.getNRF24L01().setCRCLength(crcSize.valueSupplier.get());
        }
    }

    public void updatePALevel(PALevel paLevel) {
        if (rf24Communicator.getNRF24L01() != null) {
            rf24Communicator.getNRF24L01().setPALevel((short) paLevel.valueSupplier.get().swigValue());
        }
    }

    public void updateDataRate(DataRate dataRate) {
        if (rf24Communicator.getNRF24L01() != null) {
            rf24Communicator.getNRF24L01().setDataRate(dataRate.valueSupplier.get());
        }
    }

    public void updateRetry(RetryCount retryCount, RetryDelay retryDelay) {
        if (rf24Communicator.getNRF24L01() != null) {
            rf24Communicator.getNRF24L01().setRetries(retryDelay.delay, retryCount.count);
        }
    }

    /*
    public String getPinMode(String deviceID, Pin arduinoPin) {
        String messageID = String.valueOf(System.currentTimeMillis()).substring(0, 10);
        String command = String.format("%s&%s&%s&%d", messageID, GET_PIN_MODE_COMMAND, deviceID, arduinoPin.getAddress());
        return sendCommandAndWaitValue(command);
    }

    public String setPinMode(String deviceID, Pin arduinoPin, PinMode mode) {
        String messageID = String.valueOf(System.currentTimeMillis()).substring(0, 10);
        String command = String.format("%s&%s&%s&%d&%s", messageID, SET_PIN_MODE_COMMAND, deviceID, arduinoPin.getAddress(), mode.getDirection());
        return sendCommandAndWaitValue(command);
    }

    public String setDelay(String deviceID, int delay) {
        String messageID = String.valueOf(System.currentTimeMillis()).substring(0, 10);
        String command = String.format("%s&%s&%s&%d", messageID, SET_DELAY_COMMAND, deviceID, delay);
        return sendCommandAndWaitValue(command);
    }

    public String setPinValue(String deviceID, Pin arduinoPin, ArduinoPin.Value value) {
        String messageID = String.valueOf(System.currentTimeMillis()).substring(0, 10);
        String commandName = arduinoPin.getSupportedPinModes().contains(PinMode.DIGITAL_OUTPUT) ? SET_DIGITAL_PIN_VALUE_COMMAND : SET_ANALOG_PIN_VALUE_COMMAND;
        String command = String.format("%s&%s&%s&%d&%d", messageID, commandName, deviceID, arduinoPin.getAddress(), value.ordinal());
        return sendCommandAndWaitValue(command);
    }*/

    /*
    public String getPinValue(String deviceID, Pin arduinoPin) {
        String messageID = String.valueOf(System.currentTimeMillis()).substring(0, 10);
        String commandName = arduinoPin.getSupportedPinModes().contains(PinMode.DIGITAL_OUTPUT) ? SET_DIGITAL_PIN_VALUE_COMMAND : SET_ANALOG_PIN_VALUE_COMMAND;
        String command = String.format("%s&%s&%s&%d", messageID, commandName, deviceID, arduinoPin.getAddress());
        return sendCommandAndWaitValue(command);
    }

    public String getTime(String deviceID) {
        String messageID = String.valueOf(System.currentTimeMillis()).substring(0, 10);
        String command = String.format("%s&%s&%s", messageID, GET_TIME_COMMAND, deviceID);
        return sendCommandAndWaitValue(command);
    }*/

       /* private List<String> sendCommandAndWaitValues(String command) {
        return sendMessage(command, true);
    }
    */

    public RF24Message createPingCommand(ArduinoDeviceEntity arduinoDeviceEntity) {
        return generateCommand(Short.parseShort(arduinoDeviceEntity.getEntityID().substring(ArduinoDeviceRepository.PREFIX.length())));
    }

    private RF24Message generateCommand(short target) {
        if (messageID > 125) {
            messageID = 0;
        }
        messageID++;
        return new RF24Message(messageID, target, null, null);
    }

    /**
     * Made bulk putToCache of values
     */
    /*public void sendArduinoUpdatePinBulkValues(ArduinoDeviceEntity arduinoDeviceEntity, Map<Pin, Byte> bulkUpdate) {
        if (!bulkUpdate.isEmpty()) {
            ByteBuffer buffer = ByteBuffer.allocate(1 + bulkUpdate.size() * 2);
            buffer.put((byte) bulkUpdate.size());
            for (Map.Entry<Pin, Byte> item : bulkUpdate.entrySet()) {
                buffer.put((byte) item.getKey().getAddress()); // pinID
                buffer.put(item.getValue()); // value
            }
            SendCommand sendCommand = SendCommand.sendPayload(Command.SET_PIN_VALUE_BULK_COMMAND, buffer);
            RF24Message sendMessage = generateCommand(Short.parseShort(arduinoDeviceEntity.getEntityID().substring(ArduinoDeviceRepository.PREFIX.length())));

            scheduleSend(sendCommand, sendMessage, new Pipe(arduinoDeviceEntity.getPipe()));
        } else {
            log.info("No bulk values to putToCache arduino");
        }
    }*/

    /**
     * Should fire arduino device to putToCache it's responseManager to send value each <interval> seconds
     */
    /*public void sendArduinoRequestValue(ArduinoDeviceEntity arduinoDeviceEntity, byte secondsInterval, Pin pin, Byte handlerID, Byte pinRequestType, boolean remove) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.put((byte) pin.getAddress());
        buffer.put(secondsInterval);
        buffer.put(handlerID);
        buffer.put(pinRequestType);

        SendCommand sendCommand;
        if (remove) {
            sendCommand = SendCommand.sendPayload(Command.REMOVE_GET_PIN_VALUE_REQUEST_COMMAND, buffer);
        } else {
            sendCommand = SendCommand.sendPayload(Command.GET_PIN_VALUE_REQUEST_COMMAND, buffer);
        }
        RF24Message sendMessage = generateCommand(Short.parseShort(arduinoDeviceEntity.getEntityID().substring(ArduinoDeviceRepository.PREFIX.length())));
        scheduleSend(sendCommand, sendMessage, new Pipe(arduinoDeviceEntity.getPipe()));
    }*/

    /**
     * Send handler to arduino when specific pin has value more than 'value'
     * moreThanValue - 0..1024. For digital pin 1024 - '1'
     */
   /* public void sendArduinoHandlerRequestWhenPinValueOpThan(ArduinoDeviceEntity arduinoDeviceEntity, Pin pin, CommandBuilder commandBuilder, PinMode pinMode, byte moreThanValue, byte op, boolean remove) {
        ByteBuffer buffer = ByteBuffer.allocate(3 + commandBuilder.getSize());
        buffer.put((byte) pin.getAddress()); // pinID

        // value
        buffer.put(moreThanValue);
        byte opMode = -1;
        switch (op) {
            case 0:
                opMode = (byte) (pinMode == PinMode.DIGITAL_INPUT ? 0 : 3);
                break;
            case 1:
                opMode = (byte) (pinMode == PinMode.DIGITAL_INPUT ? 1 : 4);
                break;
            case 2:
                opMode = (byte) (pinMode == PinMode.DIGITAL_INPUT ? 2 : 5);
                break;
        }

        buffer.put(opMode); // operation: >, <, == and mode
        buffer.put(commandBuilder.getBytes()); // handler type and related data...

        SendCommand sendCommand;
        if (remove) {
            sendCommand = SendCommand.sendPayload(Command.REMOVE_HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN, buffer);
        } else {
            sendCommand = SendCommand.sendPayload(Command.HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN, buffer);
        }

        RF24Message sendMessage = generateCommand(Short.parseShort(arduinoDeviceEntity.getEntityID().substring(ArduinoDeviceRepository.PREFIX.length())));
        scheduleSend(sendCommand, sendMessage, new Pipe(arduinoDeviceEntity.getPipe()));
    }*/
}
















