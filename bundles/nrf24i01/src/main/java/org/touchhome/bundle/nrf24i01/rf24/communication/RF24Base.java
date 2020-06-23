package org.touchhome.bundle.nrf24i01.rf24.communication;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.nrf24i01.rf24.Command;
import org.touchhome.bundle.nrf24i01.rf24.setting.*;
import pl.grzeslowski.smarthome.rf24.exceptions.CloseRf24Exception;
import pl.grzeslowski.smarthome.rf24.exceptions.WriteRf24Exception;
import pl.grzeslowski.smarthome.rf24.generated.RF24;
import pl.grzeslowski.smarthome.rf24.helpers.ClockSpeed;
import pl.grzeslowski.smarthome.rf24.helpers.Pins;
import pl.grzeslowski.smarthome.rf24.helpers.Pipe;
import pl.grzeslowski.smarthome.rf24.rpi.RpiGpio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@RequiredArgsConstructor
public abstract class RF24Base {

    private static boolean initialized = false;
    final EntityContext entityContext;
    final ByteBuffer readBuffer = ByteBuffer.allocate(32);
    private final ByteBuffer sendBuffer = ByteBuffer.allocate(32);
    RF24 radio;
    private List<Pipe> actualReadPipes;

    static short calcCRC(byte messageID, short target, byte commandID, byte[] payload) {
        int calcCRC = messageID + target + commandID;
        for (byte value : payload) {
            calcCRC += Math.abs(value);
        }
        return (short) ((0xbeaf + calcCRC) & 0x0FFFF);
    }

    private boolean write(Pipe write, byte[] toSend) {
        if (write == null) {
            throw new NullPointerException("Write pipe cannot nbe null!");
        }
        try {
            radio.stopListening();
            radio.openWritingPipe(write.getBinaryPipe());
            return radio.write(toSend, (short) toSend.length);
        } catch (Exception e) {
            throw new WriteRf24Exception(write, e);
        }
    }

    void send(byte commandId, short target, byte messageId, byte[] payload, Pipe pipe) {
        // prepare buffer
        ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.put((byte) 0x25);
        buffer.put((byte) 0x25);
        buffer.put((byte) (payload.length));
        short crc = calcCRC(messageId, target, commandId, payload);
        buffer.putShort(crc);
        buffer.put(messageId);
        buffer.putShort(target);
        buffer.put(commandId);
        buffer.put(payload);
        for (int i = buffer.position(); i < buffer.capacity(); i++) {
            buffer.put((byte) 0);
        }

        // actual send
        sendBuffer.clear();
        sendBuffer.put(buffer.array());
        try {
            if (!write(pipe, sendBuffer.array())) {
                log.error("Failed sending!. Command <{}>. MessageId <{}>. Payload <{}>", commandId, messageId, payload);
            } else {
                log.info("Success send message");
            }
        } catch (WriteRf24Exception ex) {
            log.error("Failed sending.", ex);
        }
        String cmd = String.valueOf(commandId);
        for (Command command : Command.values()) {
            if (command.getValue() == commandId) {
                cmd = command.name();
            }
        }
        log.info("Send: Cmd <{}>. Target: <{}>. MessageID: <{}>. Payload <{}>", cmd, target, messageId, payload);
    }

    void trySetNewReadingPipes(List<Pipe> readPipes) {
        if (isNewReadingPipes(readPipes)) {
            radio.stopListening();

            actualReadPipes = new ArrayList<>(readPipes.size());
            for (short i = 1; i <= readPipes.size(); i++) {
                final Pipe pipe = readPipes.get(i - 1);
                radio.openReadingPipe(i, pipe.getBinaryPipe());
                actualReadPipes.add(i - 1, pipe);
            }
            getNRF24L01().startListening();
        } else {
            getNRF24L01().startListening();
        }
    }

    private boolean isNewReadingPipes(List<Pipe> readPiped) {
        if (actualReadPipes == null) {
            return true;
        }

        if (actualReadPipes.size() != readPiped.size()) {
            return true;
        }

        for (int i = 0; i < actualReadPipes.size(); i++) {
            final Pipe actual = actualReadPipes.get(i);
            final Pipe pipe = readPiped.get(i);
            if (!actual.equals(pipe)) {
                return true;
            }
        }

        return false;
    }

    public RF24 getNRF24L01() {
        if (radio == null && !initialized) {
            log.info("Start init NRF24L01");
            initialized = true;
            sendBuffer.order(ByteOrder.LITTLE_ENDIAN);
            readBuffer.order(ByteOrder.LITTLE_ENDIAN);

            Pins pins = new Pins(RpiGpio.RPI_V2_GPIO_P1_22.getGpioPin(), RpiGpio.RPI_V2_GPIO_P1_24.getGpioPin(), ClockSpeed.BCM2835_SPI_SPEED_8MHZ);
//            Retry retry = new Retry((short) 15, (short) 15);

            try {
                radio = new RF24(pins.getCePin(), pins.getCsPin(), pins.getClockSpeed());
                //rf24.setPayloadSize(payload.getSize());
                radio.begin();
                radio.setRetries(entityContext.getSettingValue(Nrf24i01RetryDelaySetting.class).delay, entityContext.getSettingValue(Nrf24i01RetryCountSetting.class).count);
                radio.setCRCLength(entityContext.getSettingValue(Nrf24i01CrcSizeSetting.class).valueSupplier.get());
                radio.setPALevel((short) entityContext.getSettingValue(Nrf24i01PALevelSetting.class).valueSupplier.get().swigValue());
                radio.setDataRate(entityContext.getSettingValue(Nrf24i01DataRateSetting.class).valueSupplier.get());

                radio.setChannel((short) 0x4c);
                radio.printDetails();

                //TODO: nrf24IL01DeviceEntity.setDetails(radio.printDetails();getDetails());
                log.info("Start listening NRF24L01");

                radio.startListening();
                entityContext.setSettingValue(Nrf24i01StatusMessageSetting.class, "");
                entityContext.setSettingValue(Nrf24i01StatusSetting.class, DeviceStatus.ONLINE);
            } catch (Exception ex) {
                log.error("Error while init NRF24L01", ex);
                entityContext.setSettingValue(Nrf24i01StatusMessageSetting.class, TouchHomeUtils.getErrorMessage(ex));
                entityContext.setSettingValue(Nrf24i01StatusSetting.class, DeviceStatus.OFFLINE);
                radio = null;
            }
        }
        return radio;
    }

    public void close() {
        if (radio == null) {
            throw new IllegalStateException("RF24 was not initialized! Please call init() before calling close().");
        }
        try {
            radio.delete();
        } catch (Error e) {
            throw new CloseRf24Exception(e);
        } finally {
            radio = null;
        }
    }
}
