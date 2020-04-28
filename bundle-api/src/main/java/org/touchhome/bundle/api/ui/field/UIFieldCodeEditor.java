package org.touchhome.bundle.api.ui.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UIFieldCodeEditor {

    CodeEditorType editorType();

    boolean autoFormat() default false;

    enum CodeEditorType {
        javascript, json
    }

}
