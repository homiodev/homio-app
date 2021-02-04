package org.touchhome.app.videoStream.ui;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(UICameraDimmerButtons.class)
public @interface UICameraDimmerButton {
    String name();

    String icon() default "";
}
