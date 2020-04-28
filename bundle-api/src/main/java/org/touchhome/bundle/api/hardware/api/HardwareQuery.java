package org.touchhome.bundle.api.hardware.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HardwareQuery {
    String value();

    int maxSecondsTimeout() default 60;

    // define directory from which should start process
    String dir() default "";

    boolean printOutput() default false;
}
