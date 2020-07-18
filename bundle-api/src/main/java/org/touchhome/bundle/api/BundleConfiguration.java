package org.touchhome.bundle.api;

import org.springframework.context.annotation.Configuration;

import java.lang.annotation.*;

/**
 * Defines entry root for loading bundle spring context
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Configuration
public @interface BundleConfiguration {

    BundleConfiguration.Env[] env() default {}; // defines env variables.

    @interface Env {
        String key();

        String value();
    }
}
