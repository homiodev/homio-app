package org.homio.addon.camera.entity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.homio.addon.camera.service.OnvifCameraService;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UICameraActionConditional {

    Class<? extends ActionConditional> value();

    interface ActionConditional {
        boolean match(OnvifCameraService service, Method method, String endpoint);
    }
}
