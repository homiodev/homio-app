package org.touchhome.bundle.api.hardware.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ListParse {
    String delimiter();

    Class<?> clazz();

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    @interface LineParse {
        String value();

        int group() default 1; // group to take after find()
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    @interface BooleanLineParse {
        String value();

        String when();

        boolean inverse() default false;

        int group() default 1;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface LineParsers {
        LineParse[] value();
    }
}
