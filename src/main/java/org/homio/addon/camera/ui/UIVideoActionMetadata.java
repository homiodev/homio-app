package org.homio.addon.camera.ui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UIVideoActionMetadata {

    String subGroup() default "";

    String subGroupIcon() default "";

    // for dimmer typ
    int min() default 0;

    // for dimmer typ
    int max() default 100;

    // uses if want replace dimmer with select box from min:
    String selectReplacer() default "";
}
