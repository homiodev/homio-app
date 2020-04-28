package org.touchhome.bundle.zigbee.model;

import lombok.extern.log4j.Log4j2;
import tec.uom.se.AbstractUnit;
import tec.uom.se.quantity.Quantities;

import javax.measure.*;
import java.math.BigDecimal;

@Log4j2
public class QuantityType<T extends Quantity<T>> extends Number
        implements State, Comparable<QuantityType<T>> {

    private final Quantity<T> quantity;

    public QuantityType(Number value, Unit<T> unit) {
        // Avoid scientific notation for double
        BigDecimal bd = new BigDecimal(value.toString());
        quantity = Quantities.getQuantity(bd, unit);
    }

    @Override
    public int compareTo(QuantityType<T> o) {
        if (quantity.getUnit().isCompatible(o.quantity.getUnit())) {
            QuantityType<T> v1 = this.toUnit(getUnit().getSystemUnit());
            QuantityType<?> v2 = o.toUnit(o.getUnit().getSystemUnit());
            if (v1 != null && v2 != null) {
                return Double.compare(v1.doubleValue(), v2.doubleValue());
            } else {
                throw new IllegalArgumentException("Unable to convert to system unit during compare.");
            }
        } else {
            throw new IllegalArgumentException("Can not compare incompatible units.");
        }
    }

    public QuantityType<T> toUnit(Unit<?> targetUnit) {
        if (!targetUnit.equals(getUnit())) {
            try {
                UnitConverter uc = getUnit().getConverterToAny(targetUnit);
                Quantity<?> result = Quantities.getQuantity(uc.convert(quantity.getValue()), targetUnit);

                return new QuantityType<T>(result.getValue(), (Unit<T>) targetUnit);
            } catch (UnconvertibleException | IncommensurableException e) {
                log.debug("Unable to convert unit from {} to {}", getUnit(), targetUnit);
                return null;
            }
        }
        return this;
    }

    private Unit<T> getUnit() {
        return quantity.getUnit();
    }

    @Override
    public int intValue() {
        return quantity.getValue().intValue();
    }

    @Override
    public boolean boolValue() {
        throw new IllegalStateException("Unable to fetch boolean value from Quantity type");
    }

    @Override
    public String stringValue() {
        return quantity.getValue().toString();
    }

    @Override
    public long longValue() {
        return quantity.getValue().longValue();
    }

    @Override
    public float floatValue() {
        return quantity.getValue().floatValue();
    }

    @Override
    public double doubleValue() {
        return quantity.getValue().doubleValue();
    }

    @Override
    public String toString() {
        return toFullString();
    }

    public String toFullString() {
        if (quantity.getUnit() == AbstractUnit.ONE) {
            return quantity.getValue().toString();
        } else {
            return quantity.toString();
        }
    }
}
