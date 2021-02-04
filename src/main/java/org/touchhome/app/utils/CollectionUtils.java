package org.touchhome.app.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class CollectionUtils {

    /**
     * Set which skip adding null values, and replace exiting values
     */
    public static <T> Set<T> nullSafeSet() {
        return new HashSet<T>() {
            @Override
            public boolean add(T t) {
                if (t == null) return false;
                super.remove(t);
                return super.add(t);
            }

            @Override
            public boolean addAll(Collection<? extends T> c) {
                return c != null && super.addAll(c);
            }
        };
    }
}
