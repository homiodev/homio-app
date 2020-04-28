package org.touchhome.bundle.zigbee.converter.warningdevice;

/**
 * Possible values for the warning mode in a warning type.
 */
public enum WarningMode {
    STOP(0),
    BURGLAR(1),
    FIRE(2),
    EMERGENCY(3),
    POLICE_PANIC(4),
    FIRE_PANIC(5),
    EMERGENCY_PANIC(6);

    private int value;

    WarningMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
