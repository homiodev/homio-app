package org.touchhome.bundle.api.condition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExecuteOnce {

    String[] skipIfInstalled() default "";

    boolean requireInternet() default false;
}
