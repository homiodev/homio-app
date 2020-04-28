package org.touchhome.bundle.api.ui.method;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Action to be available via UI context menu/regular menu
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UIMethodAction {
    String name();

    ResponseAction responseAction() default ResponseAction.ShowToastr;

    enum ResponseAction {
        ShowToastr, ShowJson
    }
}
