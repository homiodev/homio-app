package org.touchhome.app.utils.throwableFunctions;

@FunctionalInterface
public interface CheckedBiFunction<T, U, R> {
    R apply(T t, U u) throws Exception;
}