package org.touchhome.bundle.zigbee.converter.warningdevice;

/**
 * Possible values for the squawk mode in a squawk type.
 */
public enum SquawkMode {
    ARMED(0),
    DISARMED(1);

    private int value;

    SquawkMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
