package org.touchhome.app.model.entity.widget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UIFieldUpdateFontSize {

    float min() default 0.1F;

    float max() default 2F;
}
