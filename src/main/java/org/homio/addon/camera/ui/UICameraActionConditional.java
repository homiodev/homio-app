package org.homio.addon.camera.ui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Predicate;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UICameraActionConditional {

  Class<? extends Predicate<Object>> value();
}
