package org.touchhome.bundle.api.ui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface UISidebarMenu {

    TopSidebarMenu parent() default TopSidebarMenu.ITEMS;

    String icon();

    String bg();

    Class<?> itemType() default UISidebarMenu.class;

    boolean allowCreateNewItems() default false;

    int order() default 1000;

    enum TopSidebarMenu {
        HARDWARE, ITEMS
    }
}
