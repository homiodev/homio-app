package org.touchhome.bundle.nrf24i01.rf24.communication;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.nrf24i01.rf24.command.RF24CommandPlugin;
import org.touchhome.bundle.nrf24i01.rf24.repository.NRF24I01DeviceRepository;
import pl.grzeslowski.smarthome.rf24.helpers.Pipe;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.Collections.singletonList;

@Log4j2
@Component
public class Rf24Communicator extends RF24Base {

    private static final List<Pipe> globalReadPipes = singletonList(new Pipe("1Node"));
    private static final byte RECEIVE_BYTE = 36;
    private final Map<ReadListener, Long> rf24ReadingSubscriptions = new ConcurrentHashMap<>();

    private final BlockingQueue<SendDescriptor> sendQueue = new LinkedBlockingQueue<>();

    private final Object waitForAllowGlobalReading = new Object();
    private final Map<Byte, RF24CommandPlugin> rf24CommandPlugins;
    private Thread globalReadingThread;
    private Thread globalWritingThread;
    private volatile boolean isReadingDone = false;
    private volatile boolean isAllowGlobalReading = true;

    public Rf24Communicator(Map<Byte, RF24CommandPlugin> rf24CommandPlugins, EntityContext entityContext, NRF24I01DeviceRepository nrf24I01DeviceRepository) {
        super(entityContext, nrf24I01DeviceRepository);
        this.rf24CommandPlugins = rf24CommandPlugins;
    }

    public void runPipeReadWrite() {
        globalReadingThread = new Thread(() -> {
            trySetNewReadingPipes(globalReadPipes);

            while (!Thread.interrupted()) {
                try {
                    tryReadFromPipe();
                } catch (Exception ex) {
                    log.error("Error while reading pipe: " + ex.getMessage(), ex);
                }
            }
        }, "NRF24 global reading thread");
        globalReadingThread.start();
        globalWritingThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    writeToPipe();
                } catch (Exception ex) {
                    log.error("Error occurs while sending Rf24 message", ex);
                }
            }
        }, "NRF24 global writing thread");
        globalWritingThread.start();
    }

    public void send(SendCommand sendCommand, RF24Message message, Pipe pipe) {
        sendQueue.offer(new SendDescriptor(sendCommand, message, pipe));
    }

    public void subscribeForReading(ReadListener readListener) {
        rf24ReadingSubscriptions.put(readListener, System.currentTimeMillis());
    }

    private void writeToPipe() throws InterruptedException {
        SendDescriptor sendDescriptor = sendQueue.take();
        isAllowGlobalReading = false;
        while (!isReadingDone) {
            Thread.yield();
        }

        send(sendDescriptor.getSendCommand().getCommandID(),
                sendDescriptor.getMessage().getTarget(),
                sendDescriptor.getMessage().getMessageID(),
                sendDescriptor.getSendCommand().getPayload(),
                sendDescriptor.getPipe());
        while (null != (sendDescriptor = sendQueue.poll())) {
            send(sendDescriptor.getSendCommand().getCommandID(),
                    sendDescriptor.getMessage().getTarget(),
                    sendDescriptor.getMessage().getMessageID(),
                    sendDescriptor.getSendCommand().getPayload(),
                    sendDescriptor.getPipe());
        }

        isAllowGlobalReading = true;
        isReadingDone = false;
        synchronized (waitForAllowGlobalReading) {
            waitForAllowGlobalReading.notify();
        }
        log.info("Sending done.");
    }

    private void tryReadFromPipe() throws Exception {
        if (isAllowGlobalReading) {
            if (radio.available()) {
                readFromPipe();
            }
            removeOldReadListeners();
        } else {
            log.info("Reading suspended");
            isReadingDone = true;
            synchronized (waitForAllowGlobalReading) {
                waitForAllowGlobalReading.wait();
            }
            log.info("Reading resumed");
            trySetNewReadingPipes(globalReadPipes);
        }
    }

    private void removeOldReadListeners() {
        long currentTimeMillis = System.currentTimeMillis();

        for (Map.Entry<ReadListener, Long> rf24ReadingSubscription : rf24ReadingSubscriptions.entrySet()) {
            ReadListener subscription = rf24ReadingSubscription.getKey();

            if (subscription.maxTimeout() != null
                    && currentTimeMillis - rf24ReadingSubscription.getValue() > subscription.maxTimeout()) {

                rf24ReadingSubscriptions.remove(subscription);
                log.info(" ReadListener <{}> not received any message", subscription.getId());
                subscription.notReceived();
            }
        }
    }

    private void readFromPipe() throws Exception {
        readBuffer.clear();
        radio.read(readBuffer.array(), (short) (readBuffer.capacity()));
        byte firstByte = readBuffer.get();
        byte secondByte = readBuffer.get();

        if (firstByte == RECEIVE_BYTE && secondByte == RECEIVE_BYTE) {
            Rf24RawMessage rawMessage = Rf24RawMessage.readRawMessage(readBuffer);
            if (rawMessage.isValidCRC()) {
                RF24Message rf24Message = rawMessage.toParsedMessage(rf24CommandPlugins);
                boolean anyReceived = false;
                for (ReadListener readListener : rf24ReadingSubscriptions.keySet()) {
                    if (readListener.canReceive(rf24Message)) {
                        readListener.received(rf24Message);

                        // remove after recieving only if timeout not null
                        if (readListener.maxTimeout() != null) {
                            rf24ReadingSubscriptions.remove(readListener);
                        }
                        anyReceived = true;
                    }
                }
                if (!anyReceived) {
                    log.error("No one subscription for message: <{}>", rf24Message);
                }
            } else {
                log.error("Received CRC value isn't correct");
            }
        } else {
            log.error("First or value inti bytes not match with 36. 1 - <{}>; 2 - <{}>", firstByte, secondByte);
        }
    }

    @Data
    @AllArgsConstructor
    private static class SendDescriptor {
        SendCommand sendCommand;
        RF24Message message;
        Pipe pipe;
    }

    private static class Rf24RawMessage {
        private ByteBuffer payloadBuffer;
        private byte messageID; // 2 bytes messageID
        private short target; // 2 byte target
        private byte commandID;
        private byte payloadLength;
        private short crc;

        private static Rf24RawMessage readRawMessage(ByteBuffer readBuffer) {
            Rf24RawMessage message = new Rf24RawMessage();
            message.payloadLength = readBuffer.get();
            message.crc = readBuffer.getShort();

            message.messageID = readBuffer.get();
            message.target = readBuffer.getShort();
            message.commandID = readBuffer.get();

            // TODO: something wrong here!!!
            message.payloadBuffer = ByteBuffer.wrap(readBuffer.array(), readBuffer.position(), message.payloadLength).asReadOnlyBuffer();
            return message;
        }

        private boolean isValidCRC() {
            ByteBuffer tmpBuffer = payloadBuffer.duplicate();
            byte[] payload = new byte[payloadLength];
            tmpBuffer.get(payload);
            return crc == RF24Base.calcCRC(messageID, target, commandID, payload);
        }

        private RF24Message toParsedMessage(Map<Byte, RF24CommandPlugin> rf24CommandPlugins) {
            RF24CommandPlugin commandPlugin = rf24CommandPlugins.get(commandID);
            if (commandPlugin == null) {
                throw new IllegalArgumentException("Unable to find RF24CommandPlugin for commandID: " + commandID);
            }
            return new RF24Message(messageID, target, commandPlugin, payloadBuffer);
        }
    }
}
