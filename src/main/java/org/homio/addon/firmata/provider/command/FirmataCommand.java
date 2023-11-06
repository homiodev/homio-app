package org.homio.addon.firmata.provider.command;

import lombok.Getter;
import org.firmata4j.firmata.parser.FirmataToken;

/**
 * This class contains set of constants that represent tokens of Firmata protocol.
 */
@Getter
public enum FirmataCommand {
  SYSEX_REGISTER(0x40),
  SYSEX_PING(0x49),
  SYSEX_GET_TIME_COMMAND(0x50),

  ONEWIRE_SEARCH_REPLY(0x42),
  ONEWIRE_DATA(FirmataToken.ONEWIRE_DATA)
    /*,
    FAILED_EXECUTED(1),
    DEBUG(2),

    REGISTER_COMMAND(10),

    GET_ID_COMMAND(20),
    GET_TIME_COMMAND(21),
    PING(22),

    SET_PIN_VALUE_ON_HANDLER_REQUEST_COMMAND(30),
    GET_PIN_VALUE_COMMAND(31),

    SET_PIN_VALUE_COMMAND(40),

    GET_PIN_VALUE_REQUEST_COMMAND(60),
    REMOVE_GET_PIN_VALUE_REQUEST_COMMAND(61),

    HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN(70),
    REMOVE_HANDLER_REQUEST_WHEN_PIN_VALUE_OP_THAN(71)*/;

  private final byte value;

  FirmataCommand(int value) {
    this.value = (byte) value;
  }
}
