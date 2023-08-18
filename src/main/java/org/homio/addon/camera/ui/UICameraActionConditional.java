package org.homio.addon.camera.ui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.homio.addon.camera.service.OnvifCameraService;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UICameraActionConditional {

    Class<? extends BiPredicate<OnvifCameraService, Method>> value();
}
