package org.touchhome.bundle.nrf24i01.rf24.command;

import org.touchhome.bundle.nrf24i01.rf24.communication.RF24Message;
import org.touchhome.bundle.nrf24i01.rf24.communication.SendCommand;

public interface RF24CommandPlugin {

    Byte getCommandIndex();

    String getName();

    default SendCommand execute(RF24Message message) {
        throw new IllegalStateException("Unable execute command " + getName() + " on master");
    }

    // calls when 'execute' method was executed remotely and 'ack' was received
    default void onRemoteExecuted(RF24Message message) {
        throw new IllegalStateException("onRemoteExecuted not implemented for command" + getName());
    }

    default boolean canReceiveGeneral() {
        return false;
    }
}
