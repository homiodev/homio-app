package org.touchhome.bundle.api.hquery;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Import({HQueryConfiguration.class})
@Documented
public @interface EnableHQuery {

    String scanBaseClassesPackage();
}
