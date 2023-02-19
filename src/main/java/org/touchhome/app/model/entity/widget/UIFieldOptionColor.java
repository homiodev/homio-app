package org.touchhome.app.model.entity.widget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Add ability to set color for text
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UIFieldOptionColor {

    // override default field prefix. Default field prefix - as field name that has this annotation
    String value() default "";
}
