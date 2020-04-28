package org.touchhome.bundle.zigbee.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ButtonState implements State {
    private final ButtonPressType buttonPressType;

    @Override
    public float floatValue() {
        throw new IllegalStateException("Unable to fetch float value from button");
    }

    @Override
    public int intValue() {
        throw new IllegalStateException("Unable to fetch int value from button");
    }

    @Override
    public boolean boolValue() {
        throw new IllegalStateException("Unable to fetch boolean value from button");
    }

    @Override
    public String stringValue() {
        return buttonPressType.parameterValue;
    }

    @Override
    public String toString() {
        return "ButtonState{buttonPressType=" + buttonPressType + '}';
    }

    public enum ButtonPressType {
        SHORT_PRESS("shortpress"),
        DOUBLE_PRESS("doublepress"),
        LONG_PRESS("longpress");

        private String parameterValue;

        ButtonPressType(String parameterValue) {
            this.parameterValue = parameterValue;
        }

        @Override
        public String toString() {
            return parameterValue;
        }
    }
}
