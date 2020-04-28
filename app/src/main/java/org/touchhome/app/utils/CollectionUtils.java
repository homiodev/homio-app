package org.touchhome.app.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class CollectionUtils {

    public static <T> Set<T> ignoreNullSet() {
        return new HashSet<T>() {
            @Override
            public boolean add(T t) {
                return t != null && super.add(t);
            }

            @Override
            public boolean addAll(Collection<? extends T> c) {
                return c != null && super.addAll(c);
            }
        };
    }
}
