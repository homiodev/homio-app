package org.touchhome.bundle.zigbee.converter.warningdevice;

/**
 * Possible siren level values (for both warning and squawk commands).
 */
public enum SoundLevel {

    LOW(0),
    MEDIUM(1),
    HIGH(2),
    VERY_HIGH(3);

    private int value;

    SoundLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

}
