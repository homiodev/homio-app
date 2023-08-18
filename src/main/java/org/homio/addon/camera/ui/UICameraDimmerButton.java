package org.homio.addon.camera.ui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(UICameraDimmerButtons.class)
public @interface UICameraDimmerButton {

    String name();

    String icon() default "";
}
