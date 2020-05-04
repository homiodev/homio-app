package org.touchhome.bundle.nrf24i01.rf24.communication;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.nrf24i01.rf24.Command;
import org.touchhome.bundle.nrf24i01.rf24.NRF24IL01DeviceEntity;
import org.touchhome.bundle.nrf24i01.rf24.options.*;
import org.touchhome.bundle.nrf24i01.rf24.repository.NRF24I01DeviceRepository;
import org.touchhome.bundle.nrf24i01.rf24.setting.Nrf24i01StatusMessageSetting;
import org.touchhome.bundle.nrf24i01.rf24.setting.Nrf24i01StatusSetting;
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
    final ByteBuffer readBuffer = ByteBuffer.allocate(32);
    final EntityContext entityContext;
    final NRF24I01DeviceRepository nrf24I01DeviceRepository;
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
        if (actualReadPipes == null) return true;

        if (actualReadPipes.size() != readPiped.size()) return true;

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
            nrf24I01DeviceRepository.deleteAll();
            NRF24IL01DeviceEntity nrf24IL01DeviceEntity = new NRF24IL01DeviceEntity();
            nrf24IL01DeviceEntity.setCrcSize(CRCSize.ENABLE_8_BITS);
            nrf24IL01DeviceEntity.setPaLevel(PALevel.RF24_PA_MIN);
            nrf24IL01DeviceEntity.setRetryCount(RetryCount.RETRY_15);
            nrf24IL01DeviceEntity.setRetryDelay(RetryDelay.DELAY_15);
            nrf24IL01DeviceEntity.setDataRate(DataRate.RF24_250KBPS);
            entityContext.save(nrf24IL01DeviceEntity);

            try {
                radio = new RF24(pins.getCePin(), pins.getCsPin(), pins.getClockSpeed());
                //rf24.setPayloadSize(payload.getSize());
                radio.begin();
                radio.setRetries(nrf24IL01DeviceEntity.getRetryDelay().delay, nrf24IL01DeviceEntity.getRetryCount().count);
                radio.setCRCLength(nrf24IL01DeviceEntity.getCrcSize().valueSupplier.get());
                radio.setPALevel((short) nrf24IL01DeviceEntity.getPaLevel().valueSupplier.get().swigValue());
                radio.setDataRate(nrf24IL01DeviceEntity.getDataRate().valueSupplier.get());

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
                nrf24I01DeviceRepository.deleteAll();
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
