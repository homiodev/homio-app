package org.touchhome.bundle.api.hardware.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ErrorsHandler {
    boolean throwError() default false;

    boolean logError() default true;

    String onRetCodeError();

    ErrorHandler[] errorHandlers();

    String notRecognizeError() default "";

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ErrorHandler {

        String throwError();

        String onError();
    }
}
