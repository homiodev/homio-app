package org.touchhome.bundle.zigbee.model;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;

@Log4j2
public class DecimalType extends Number implements State, Comparable<DecimalType> {

    public static final DecimalType TRUE = new DecimalType(1);
    public static final DecimalType FALSE = new DecimalType(0);

    public static final DecimalType ZERO = new DecimalType(0);
    public static final DecimalType HUNDRED = new DecimalType(100);

    @Getter
    private BigDecimal value;

    public DecimalType(long value) {
        this.value = BigDecimal.valueOf(value);
    }

    public DecimalType(double value) {
        this.value = BigDecimal.valueOf(value);
    }

    public DecimalType(float value) {
        this.value = BigDecimal.valueOf(value);
    }

    public DecimalType(BigDecimal value) {
        this.value = value;
    }

    @Override
    public int compareTo(DecimalType o) {
        return value.compareTo(o.value);
    }

    @Override
    public double doubleValue() {
        return value.doubleValue();
    }

    @Override
    public float floatValue() {
        return value.floatValue();
    }

    @Override
    public int intValue() {
        return value.intValue();
    }

    @Override
    public boolean boolValue() {
        throw new IllegalStateException("Unable to fetch boolean value from DecimalType");
    }

    @Override
    public String stringValue() {
        return value.toPlainString();
    }

    @Override
    public long longValue() {
        return value.longValue();
    }

    public BigDecimal toBigDecimal() {
        return value;
    }

    @Override
    public String toString() {
        return toFullString();
    }

    public String toFullString() {
        return value.toPlainString();
    }
}
