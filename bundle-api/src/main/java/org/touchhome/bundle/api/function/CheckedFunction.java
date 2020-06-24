package org.touchhome.bundle.api.function;

@FunctionalInterface
public interface CheckedFunction<T, R> {
    R apply(T t) throws Exception;
}
