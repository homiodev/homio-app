package org.homio.addon.camera.ui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UICameraSelectionAttributeValues {

    String value();

    String path() default "";

    String[] prependValues() default {};
}
