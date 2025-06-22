package org.homio.addon.homekit.annotations;

import java.lang.annotation.*;

@Repeatable(HomekitValidValuesList.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface HomekitValidValues {

    Class<? extends Enum> value();
}
