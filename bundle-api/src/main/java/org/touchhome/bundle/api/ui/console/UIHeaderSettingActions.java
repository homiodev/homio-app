package org.touchhome.bundle.api.ui.console;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Action to place on header UI and fire setting changes
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface UIHeaderSettingActions {
    UIHeaderSettingAction[] value();
}
