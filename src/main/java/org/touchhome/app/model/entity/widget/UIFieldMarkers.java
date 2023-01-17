package org.touchhome.app.model.entity.widget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UIFieldMarkers {

    MarkerOP value();

    enum MarkerOP {
        label,
        opacity
    }
}
