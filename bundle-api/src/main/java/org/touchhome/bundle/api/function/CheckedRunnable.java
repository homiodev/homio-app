package org.touchhome.bundle.api.function;

@FunctionalInterface
public interface CheckedRunnable {
    void run() throws Exception;
}
