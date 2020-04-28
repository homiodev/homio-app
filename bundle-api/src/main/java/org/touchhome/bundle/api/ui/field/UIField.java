package org.touchhome.bundle.api.ui.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UIField {

    // show field in context menu
    boolean showInContextMenu() default false;

    // disable editing
    boolean readOnly() default false;

    /**
     * Should be available only in editMode. If true - readOnly flag ignored
     */
    boolean onlyEdit() default false;

    // override field name
    String label() default "";

    UIFieldType type() default UIFieldType.AutoDetect;

    int order();

    boolean hideOnEmpty() default false;

    // required not null validation before save
    boolean required() default false;

    // able to edit field directly from view mode (now works only in console)
    boolean inlineEdit() default false;

    /**
     * Determine if this field should be ignored at all
     * Userfull if need hide field in sub class
     */
    boolean transparent() default false;

    // override for field name, useful in methods
    String name() default "";

    // specify field color for ui
    String color() default "";
}
