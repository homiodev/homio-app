package org.touchhome.bundle.zigbee.internal;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.zsmartsystems.zigbee.transport.ZigBeePort;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.web.server.PortInUseException;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.zigbee.setting.ZigbeePortSetting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.stream.Stream;

import static com.fazecast.jSerialComm.SerialPort.TIMEOUT_NONBLOCKING;

/**
 * The default/reference Java serial port implementation using serial events to provide a non-blocking read call.
 */
@Log4j2
public class ZigBeeSerialPort implements ZigBeePort, SerialPortDataListener {

    /**
     * The length of the receive buffer
     */
    private static final int RX_BUFFER_LEN = 512;
    /**
     * The baud rate.
     */
    @Getter
    private final int baudRate;
    /**
     * True to enable RTS / CTS flow control
     */
    private final FlowControl flowControl;
    /**
     * The circular fifo queue for receive data
     */
    private final int[] buffer = new int[RX_BUFFER_LEN];
    /**
     * Synchronisation object for buffer queue manipulation
     */
    private final Object bufferSynchronisationObject = new Object();
    private Runnable portUnavailableListener;
    /**
     * The portName portName.
     */
    @Getter
    private SerialPort serialPort;
    /**
     * The serial port input stream.
     */
    private InputStream inputStream;
    /**
     * The serial port output stream.
     */
    private OutputStream outputStream;
    private String coordinator;
    private EntityContext entityContext;
    /**
     * The receive buffer end pointer (where we put the newly received data)
     */
    private int end = 0;
    /**
     * The receive buffer start pointer (where we take the data to pass to the application)
     */
    private int start = 0;

    public ZigBeeSerialPort(String coordinator,
                            EntityContext entityContext,
                            SerialPort serialPort, int baudRate,
                            FlowControl flowControl,
                            Runnable portUnavailableListener) {
        this.coordinator = coordinator;
        this.entityContext = entityContext;
        this.serialPort = serialPort;
        this.baudRate = baudRate;
        this.flowControl = flowControl;
        this.portUnavailableListener = portUnavailableListener;
    }

    @Override
    public boolean open() {
        return open(baudRate, flowControl);
    }

    @Override
    public boolean open(int baudRate) {
        return open(baudRate, flowControl);
    }

    @Override
    public boolean open(int baudRate, FlowControl flowControl) {
        try {
            log.debug("Connecting to serial port [{}] at {} baud, flow control {}.",
                    serialPort == null ? "null" : serialPort.getSystemPortName(), baudRate, flowControl);
            try {
                if (serialPort == null) {
                    serialPort = Stream.of(SerialPort.getCommPorts()).filter(p -> p.getPortDescription().toLowerCase().contains(coordinator)).findAny().orElse(null);

                    if (serialPort == null) {
                        log.error("Serial Error: Port does not exist.");
                        return false;
                    } else {
                        entityContext.setSettingValueSilenceRaw(ZigbeePortSetting.class, serialPort.getSystemPortName());
                    }
                }
                switch (flowControl) {
                    case FLOWCONTROL_OUT_NONE:
                        serialPort.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
                        break;
                    case FLOWCONTROL_OUT_RTSCTS:
                        serialPort.setFlowControl(SerialPort.FLOW_CONTROL_RTS_ENABLED);
                        break;
                    case FLOWCONTROL_OUT_XONOFF:
                        serialPort.setFlowControl(SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED);
                        break;
                    default:
                        break;
                }

                serialPort.setComPortTimeouts(TIMEOUT_NONBLOCKING, 100, 0);
                serialPort.addDataListener(this);

                log.debug("Serial port [{}] is initialized.", serialPort.getSystemPortName());
                serialPort.openPort();
            } catch (PortInUseException e) {
                log.error("Serial Error: Port {} in use.", serialPort.getSystemPortName());
                return false;
            } catch (RuntimeException e) {
                log.error("Serial Error: Device cannot be opened on Port {}. Caused by {}",
                        serialPort == null ? "UNKNOWN_SYSTEM_PORT" : serialPort.getSystemPortName(), e.getMessage());
                return false;
            }

            inputStream = this.serialPort.getInputStream();
            outputStream = this.serialPort.getOutputStream();

            return true;
        } catch (Exception e) {
            log.error("Unable to open serial port: ", e);
            return false;
        }
    }

    @Override
    public void close() {
        String serialPortName = "";
        try {
            if (serialPort != null) {
                serialPortName = serialPort.getSystemPortName();
                serialPort.removeDataListener();

                outputStream.flush();

                inputStream.close();
                outputStream.close();

                serialPort.closePort();

                serialPort = null;
                inputStream = null;
                outputStream = null;

                synchronized (this) {
                    this.notify();
                }

                log.debug("Serial port '{}' closed.", serialPortName);
            }
        } catch (Exception e) {
            log.error("Error closing serial port: '{}' ", serialPortName, e);
        }
    }

    @Override
    public void write(int value) {
        if (outputStream == null) {
            return;
        }
        try {
            outputStream.write(value);
        } catch (IOException ignore) {
        }
    }

    @Override
    public int read() {
        return read(9999999);
    }

    @Override
    public int read(int timeout) {
        long endTime = System.currentTimeMillis() + timeout;

        try {
            while (System.currentTimeMillis() < endTime) {
                synchronized (bufferSynchronisationObject) {
                    if (start != end) {
                        int value = buffer[start++];
                        if (start >= RX_BUFFER_LEN) {
                            start = 0;
                        }
                        return value;
                    }
                }

                synchronized (this) {
                    if (serialPort == null) {
                        return -1;
                    }

                    wait(endTime - System.currentTimeMillis());
                }
            }
            return -1;
        } catch (InterruptedException ignore) {
        }
        return -1;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
            try {
                synchronized (bufferSynchronisationObject) {
                    int available = inputStream.available();
                    log.trace("Processing DATA_AVAILABLE event: have {} bytes available", available);
                    byte buf[] = new byte[available];
                    int offset = 0;
                    while (offset != available) {
                        if (log.isTraceEnabled()) {
                            log.trace("Processing DATA_AVAILABLE event: try read  {} at offset {}",
                                    available - offset, offset);
                        }
                        int n = inputStream.read(buf, offset, available - offset);
                        if (log.isTraceEnabled()) {
                            log.trace("Processing DATA_AVAILABLE event: did read {} of {} at offset {}", n,
                                    available - offset, offset);
                        }
                        if (n <= 0) {
                            throw new IOException("Expected to be able to read " + available
                                    + " bytes, but saw error after " + offset);
                        }
                        offset += n;
                    }
                    for (int i = 0; i < available; i++) {
                        buffer[end++] = buf[i] & 0xff;
                        if (end >= RX_BUFFER_LEN) {
                            end = 0;
                        }
                        if (end == start) {
                            log.warn("Processing DATA_AVAILABLE event: Serial buffer overrun");
                            if (++start == RX_BUFFER_LEN) {
                                start = 0;
                            }

                        }
                    }
                }
            } catch (IOException e) {
                log.warn("Processing DATA_AVAILABLE event: received IOException in serial port event", e);
            } catch (Exception ex) {
                this.portUnavailableListener.run();
                return;
            }

            synchronized (this) {
                this.notify();
            }
        }
    }

    @Override
    public void purgeRxBuffer() {
        synchronized (bufferSynchronisationObject) {
            start = 0;
            end = 0;
        }
    }

    @Override
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
    }
}
