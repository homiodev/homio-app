package org.touchhome.bundle.arduino.workspace;

import com.pi4j.io.gpio.PinMode;
import lombok.Getter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.pi4j.io.gpio.PinMode.*;

public enum ArduinoGpioPin {
    TX1("0", DIGITAL_INPUT, DIGITAL_OUTPUT),
    RX0("1", DIGITAL_INPUT, DIGITAL_OUTPUT),
    D2("2", DIGITAL_INPUT, DIGITAL_OUTPUT),
    D3("3", DIGITAL_INPUT, DIGITAL_OUTPUT, PWM_OUTPUT),
    D4("4", DIGITAL_INPUT, DIGITAL_OUTPUT),
    D5("5", DIGITAL_INPUT, DIGITAL_OUTPUT, PWM_OUTPUT),
    D6("6", DIGITAL_INPUT, DIGITAL_OUTPUT, PWM_OUTPUT),
    D7("7", DIGITAL_INPUT, DIGITAL_OUTPUT),
    D8("8", DIGITAL_INPUT, DIGITAL_OUTPUT),
    D9("9", DIGITAL_INPUT, DIGITAL_OUTPUT, PWM_OUTPUT),
    D10("10", DIGITAL_INPUT, DIGITAL_OUTPUT, PWM_OUTPUT),
    D11("11", DIGITAL_INPUT, DIGITAL_OUTPUT, PWM_OUTPUT),
    D12("12", DIGITAL_INPUT, DIGITAL_OUTPUT),
    D13("13", DIGITAL_INPUT, DIGITAL_OUTPUT),
    A0("14", DIGITAL_INPUT, DIGITAL_OUTPUT, ANALOG_INPUT, ANALOG_OUTPUT),
    A1("15", DIGITAL_INPUT, DIGITAL_OUTPUT, ANALOG_INPUT, ANALOG_OUTPUT),
    A2("16", DIGITAL_INPUT, DIGITAL_OUTPUT, ANALOG_INPUT, ANALOG_OUTPUT),
    A3("17", DIGITAL_INPUT, DIGITAL_OUTPUT, ANALOG_INPUT, ANALOG_OUTPUT),
    A4("18", DIGITAL_INPUT, DIGITAL_OUTPUT, ANALOG_INPUT, ANALOG_OUTPUT),
    A5("18", DIGITAL_INPUT, DIGITAL_OUTPUT, ANALOG_INPUT, ANALOG_OUTPUT),
    A6("18", ANALOG_INPUT, ANALOG_OUTPUT),
    A7("19", ANALOG_INPUT, ANALOG_OUTPUT);

    @Getter
    private final String name;

    @Getter
    private final Set<PinMode> pinModes;

    ArduinoGpioPin(String name, PinMode... pinModes) {
        this.name = name;
        this.pinModes = new HashSet<>(Arrays.asList(pinModes));
    }
}
