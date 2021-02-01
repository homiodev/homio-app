package org.touchhome.app.camera.openhub;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UICameraAction {
    String name();

    ActionType type() default ActionType.AutoDiscover;

    enum ActionType {
        Dimmer,
        String,
        Switch,
        AutoDiscover // discover on type of first parameter type in method
    }
}
