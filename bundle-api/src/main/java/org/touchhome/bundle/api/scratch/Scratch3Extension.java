package org.touchhome.bundle.api.scratch;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({TYPE})
@Retention(RUNTIME)
public @interface Scratch3Extension {

    Pattern ID_PATTERN = Pattern.compile("[a-z-]*");

    String value();
}
