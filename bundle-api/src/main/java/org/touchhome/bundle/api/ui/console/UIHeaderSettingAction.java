package org.touchhome.bundle.api.ui.console;

import org.touchhome.bundle.api.BundleSettingPlugin;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(value = UIHeaderSettingActions.class)
public @interface UIHeaderSettingAction {

    String name();

    Class<? extends BundleSettingPlugin> setting();
}
