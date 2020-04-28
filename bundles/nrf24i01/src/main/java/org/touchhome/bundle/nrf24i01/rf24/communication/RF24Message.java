package org.touchhome.bundle.nrf24i01.rf24.communication;

import lombok.Getter;
import org.touchhome.bundle.nrf24i01.rf24.command.RF24CommandPlugin;

import java.nio.ByteBuffer;

@Getter
public class RF24Message {
    private ByteBuffer payloadBuffer;
    private byte messageID; // 2 bytes messageID
    private short target; // 2 byte target
    private RF24CommandPlugin commandPlugin; //2 bytes command

    public RF24Message(byte messageID, short target, RF24CommandPlugin commandPlugin, ByteBuffer payloadBuffer) {
        this.messageID = messageID;
        this.target = target;
        this.commandPlugin = commandPlugin;
        this.payloadBuffer = payloadBuffer;
    }

    @Override
    public String toString() {
        return "RF24Message{" +
                "messageID=" + messageID +
                ", command=" + (commandPlugin == null ? "" : commandPlugin.getName()) +
                '}';
    }
}
