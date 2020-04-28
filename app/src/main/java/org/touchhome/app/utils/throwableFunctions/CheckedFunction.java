package org.touchhome.app.utils.throwableFunctions;

@FunctionalInterface
public interface CheckedFunction<T, R> {
    R apply(T t) throws Exception;
}