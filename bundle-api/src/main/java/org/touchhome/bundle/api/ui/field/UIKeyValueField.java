package org.touchhome.bundle.api.ui.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UIKeyValueField {
    int maxSize() default Integer.MAX_VALUE;

    UIFieldType keyType();

    String defaultKey();

    String keyFormat() default "{0}";

    UIFieldType valueType();

    String defaultValue();

    String valueFormat() default "{0}";

    KeyValueType keyValueType() default KeyValueType.object;

    enum KeyValueType {
        array, object
    }
}
