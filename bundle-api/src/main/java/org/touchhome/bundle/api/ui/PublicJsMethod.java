package org.touchhome.bundle.api.ui;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface PublicJsMethod {
    String value() default "";
}
