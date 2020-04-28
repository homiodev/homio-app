package org.touchhome.bundle.api.ui.field;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(UIFieldColorsMatch.class)
public @interface UIFieldColorMatch {
    String value();

    String color();
}
