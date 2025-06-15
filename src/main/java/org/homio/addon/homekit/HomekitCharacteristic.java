package org.homio.addon.homekit;

import io.github.hapjava.characteristics.impl.base.BaseCharacteristic;
import org.homio.addon.homekit.enums.HomekitCharacteristicType;
import org.homio.api.ContextVar;

import java.lang.annotation.*;

@Repeatable(HomekitCharacteristics.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface HomekitCharacteristic {

    Class<? extends BaseCharacteristic> value();

    HomekitCharacteristicType type();

    int defaultIntValue() default 0;

    double defaultDoubleValue() default 0;

    String defaultStringValue() default "";

    Class<? extends CharacteristicSupplier> impl() default DefaultSupplier.class;

    interface CharacteristicSupplier {

        BaseCharacteristic get(HomekitEndpointContext c, ContextVar.Variable v);

    }

    class DefaultSupplier implements CharacteristicSupplier {
        @Override
        public BaseCharacteristic get(HomekitEndpointContext c, ContextVar.Variable v) {
            throw new RuntimeException("Must be implemented in target class");
        }
    }
}
