package org.touchhome.bundle.nrf24i01.rf24.communication;

public interface ReadListener {
    boolean canReceive(RF24Message rf24Message);

    void received(RF24Message rf24Message) throws Exception;

    void notReceived();

    default Integer maxTimeout() {
        return null; // if null - not delete from listener
    }

    String getId();
}
