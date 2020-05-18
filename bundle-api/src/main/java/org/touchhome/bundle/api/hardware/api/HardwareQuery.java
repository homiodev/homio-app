package org.touchhome.bundle.api.hardware.api;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(HardwareQueries.class)
public @interface HardwareQuery {
    String[] value();

    int maxSecondsTimeout() default 60;

    // define directory from which should start process
    String dir() default "";

    boolean printOutput() default false;

    boolean ignoreOnError() default false;

    String echo() default "";
}
