package org.touchhome.bundle.api.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import com.pi4j.io.gpio.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@RequiredArgsConstructor
public enum RaspberryGpioPin {
    PIN3(3, "SDA.1", RaspiPin.GPIO_08, RaspiBcmPin.GPIO_02, "#D0BC7F", null),
    PIN5(5, "SCL.1", RaspiPin.GPIO_09, RaspiBcmPin.GPIO_03, "#D0BC7F", null),
    PIN7(7, "GPIO. 7", RaspiPin.GPIO_07, RaspiBcmPin.GPIO_04, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN8(8, "TxD", RaspiPin.GPIO_15, RaspiBcmPin.GPIO_14, "#DBB3A7", PinMode.DIGITAL_INPUT),
    PIN10(10, "RxD", RaspiPin.GPIO_16, RaspiBcmPin.GPIO_15, "#DBB3A7", PinMode.DIGITAL_INPUT),
    PIN11(11, "GPIO.0", RaspiPin.GPIO_00, RaspiBcmPin.GPIO_17, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN12(12, "GPIO.1", RaspiPin.GPIO_01, RaspiBcmPin.GPIO_18, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN13(13, "GPIO.2", RaspiPin.GPIO_02, RaspiBcmPin.GPIO_27, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN15(15, "GPIO.3", RaspiPin.GPIO_03, RaspiBcmPin.GPIO_22, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN16(16, "GPIO.4", RaspiPin.GPIO_04, RaspiBcmPin.GPIO_23, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN18(18, "GPIO.5", RaspiPin.GPIO_05, RaspiBcmPin.GPIO_24, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN19(19, "MOSI", RaspiPin.GPIO_12, RaspiBcmPin.GPIO_10, "#F1C16D", null),
    PIN21(21, "MISO", RaspiPin.GPIO_13, RaspiBcmPin.GPIO_09, "#F1C16D", null),
    PIN22(22, "GPIO.6", RaspiPin.GPIO_06, RaspiBcmPin.GPIO_25, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN23(23, "SCLK", RaspiPin.GPIO_14, RaspiBcmPin.GPIO_11, "#F1C16D", null),
    PIN24(24, "CE0", RaspiPin.GPIO_10, RaspiBcmPin.GPIO_08, "#F1C16D", PinMode.DIGITAL_OUTPUT),
    PIN26(26, "CE1", RaspiPin.GPIO_11, RaspiBcmPin.GPIO_07, "#F1C16D", PinMode.DIGITAL_OUTPUT),
    PIN27(27, "SDA.0", RaspiPin.GPIO_30, null, "#F595A3", null),
    PIN28(28, "SCL.0", RaspiPin.GPIO_31, null, "#F595A3", null),
    PIN29(29, "GPIO.21", RaspiPin.GPIO_21, RaspiBcmPin.GPIO_05, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN31(31, "GPIO.22", RaspiPin.GPIO_22, RaspiBcmPin.GPIO_06, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN32(32, "GPIO.26", RaspiPin.GPIO_26, RaspiBcmPin.GPIO_12, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN33(33, "GPIO.23", RaspiPin.GPIO_23, RaspiBcmPin.GPIO_13, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN35(35, "GPIO.24", RaspiPin.GPIO_24, RaspiBcmPin.GPIO_19, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN36(36, "GPIO.27", RaspiPin.GPIO_27, RaspiBcmPin.GPIO_16, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN37(37, "GPIO.25", RaspiPin.GPIO_25, RaspiBcmPin.GPIO_26, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN38(38, "GPIO.28", RaspiPin.GPIO_28, RaspiBcmPin.GPIO_20, "#8CD1F8", PinMode.DIGITAL_INPUT),
    PIN40(40, "GPIO.29", RaspiPin.GPIO_29, RaspiBcmPin.GPIO_21, "#8CD1F8", PinMode.DIGITAL_INPUT);

    private final int address;
    private final String name;
    private final Pin pin;
    private final Pin bcmPin;
    private final String color;
    private final PinMode pinMode;
    private String occupied;

    @JsonCreator
    public static RaspberryGpioPin fromValue(String value) {
        return Stream.of(RaspberryGpioPin.values()).filter(dp -> dp.pin.getName().equals(value)).findFirst().orElse(null);
    }

    public static void occupyPins(String device, RaspberryGpioPin... pins) {
        for (RaspberryGpioPin pin : pins) {
            pin.occupied = device;
        }
    }

    public static List<RaspberryGpioPin> values(PinMode pinMode, PinPullResistance pinPullResistance) {
        return Stream.of(RaspberryGpioPin.values())
                .filter(p ->
                        p.getPin().getSupportedPinModes().contains(pinMode) &&
                                (pinPullResistance == null || p.getPin().getSupportedPinPullResistance().contains(pinPullResistance)))
                .collect(Collectors.toList());
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
