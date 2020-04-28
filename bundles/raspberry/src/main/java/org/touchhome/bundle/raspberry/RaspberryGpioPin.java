package org.touchhome.bundle.raspberry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.RaspiBcmPin;
import com.pi4j.io.gpio.RaspiPin;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@AllArgsConstructor
public enum RaspberryGpioPin {
    PIN3(3, "Sda", RaspiPin.GPIO_08, RaspiBcmPin.GPIO_02, "#D0BC7F"),
    PIN5(5, "Scl", RaspiPin.GPIO_09, RaspiBcmPin.GPIO_03, "#D0BC7F"),
    PIN7(7, "B4", RaspiPin.GPIO_07, RaspiBcmPin.GPIO_04, "#8CD1F8"),
    PIN8(8, "Txd", RaspiPin.GPIO_15, RaspiBcmPin.GPIO_14, "#DBB3A7"),
    PIN10(10, "Rxd", RaspiPin.GPIO_16, RaspiBcmPin.GPIO_15, "#DBB3A7"),
    PIN11(11, "B17", RaspiPin.GPIO_00, RaspiBcmPin.GPIO_17, "#8CD1F8"),
    PIN12(12, "B18", RaspiPin.GPIO_01, RaspiBcmPin.GPIO_18, "#8CD1F8"),
    PIN13(13, "B27", RaspiPin.GPIO_02, RaspiBcmPin.GPIO_27, "#8CD1F8"),
    PIN15(15, "B22", RaspiPin.GPIO_03, RaspiBcmPin.GPIO_22, "#8CD1F8"),
    PIN16(16, "B23", RaspiPin.GPIO_04, RaspiBcmPin.GPIO_23, "#8CD1F8"),
    PIN18(18, "B24", RaspiPin.GPIO_05, RaspiBcmPin.GPIO_24, "#8CD1F8"),
    PIN19(19, "B10", RaspiPin.GPIO_12, RaspiBcmPin.GPIO_10, "#F1C16D"),
    PIN21(21, "B9", RaspiPin.GPIO_13, RaspiBcmPin.GPIO_09, "#F1C16D"),
    PIN22(22, "B25", RaspiPin.GPIO_06, RaspiBcmPin.GPIO_25, "#8CD1F8"),
    PIN23(23, "B11", RaspiPin.GPIO_14, RaspiBcmPin.GPIO_11, "#F1C16D"),
    PIN24(24, "CE0", RaspiPin.GPIO_10, RaspiBcmPin.GPIO_08, "#F1C16D"),
    PIN26(26, "CE1", RaspiPin.GPIO_11, RaspiBcmPin.GPIO_07, "#F1C16D"),
    PIN27(27, "B0", RaspiPin.GPIO_30, null, "#F595A3"),
    PIN28(28, "B1", RaspiPin.GPIO_31, null, "#F595A3"),
    PIN29(29, "B5", RaspiPin.GPIO_21, RaspiBcmPin.GPIO_05, "#8CD1F8"),
    PIN31(31, "B6", RaspiPin.GPIO_22, RaspiBcmPin.GPIO_06, "#8CD1F8"),
    PIN32(32, "B12", RaspiPin.GPIO_26, RaspiBcmPin.GPIO_12, "#8CD1F8"),
    PIN33(33, "B13", RaspiPin.GPIO_23, RaspiBcmPin.GPIO_13, "#8CD1F8"),
    PIN35(35, "B19", RaspiPin.GPIO_24, RaspiBcmPin.GPIO_19, "#8CD1F8"),
    PIN36(36, "B16", RaspiPin.GPIO_27, RaspiBcmPin.GPIO_16, "#8CD1F8"),
    PIN37(37, "B26", RaspiPin.GPIO_25, RaspiBcmPin.GPIO_26, "#8CD1F8"),
    PIN38(38, "B20", RaspiPin.GPIO_28, RaspiBcmPin.GPIO_20, "#8CD1F8"),
    PIN40(40, "B21", RaspiPin.GPIO_29, RaspiBcmPin.GPIO_21, "#8CD1F8");

    private final int address;
    private final String name;
    private final Pin pin;
    private final Pin bcmPin;
    private final String color;

    @JsonCreator
    public static RaspberryGpioPin fromValue(String value) {
        return Stream.of(RaspberryGpioPin.values()).filter(dp -> dp.pin.getName().equals(value)).findFirst().orElse(null);
    }

    public static Pin getBcmPinByDefaultPin(Pin defPin) {
        for (RaspberryGpioPin raspberryGpioPin : values()) {
            if (raspberryGpioPin.pin.equals(defPin)) {
                return raspberryGpioPin.bcmPin;
            }
        }
        return null;
    }

    public static String getBcmPinName(int num) {
        for (RaspberryGpioPin raspberryGpioPin : values()) {
            if (raspberryGpioPin.address == num) {
                return raspberryGpioPin.name;
            }
        }
        return null;
    }

    public static String getPinColor(int num) {
        for (RaspberryGpioPin raspberryGpioPin : values()) {
            if (raspberryGpioPin.address == num) {
                return raspberryGpioPin.color;
            }
        }
        return null;
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
