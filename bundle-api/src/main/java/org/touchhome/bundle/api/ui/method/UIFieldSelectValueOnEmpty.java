package org.touchhome.bundle.api.ui.method;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation on field to handle when appropriate field has empty value
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UIFieldSelectValueOnEmpty {

    String label();

    String color();

    String method();
}
