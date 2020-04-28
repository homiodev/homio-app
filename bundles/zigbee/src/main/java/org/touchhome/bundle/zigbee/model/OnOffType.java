package org.touchhome.bundle.zigbee.model;

public enum OnOffType implements State {
    ON, OFF;

    @Override
    public String toString() {
        return this.name();
    }

    @Override
    public float floatValue() {
        return this == ON ? 1 : 0;
    }

    @Override
    public int intValue() {
        return this == ON ? 1 : 0;
    }

    @Override
    public boolean boolValue() {
        return this == ON;
    }

    @Override
    public String stringValue() {
        return String.valueOf(intValue());
    }

    public <T extends State> T as(Class<T> target) {
        if (target == DecimalType.class) {
            return target.cast(this == ON ? new DecimalType(1) : DecimalType.ZERO);
        } else if (target == HSBType.class) {
            return target.cast(this == ON ? HSBType.WHITE : HSBType.BLACK);
        } else {
            return State.super.as(target);
        }
    }
}
