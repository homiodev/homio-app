package org.touchhome.bundle.api;

import org.touchhome.bundle.api.json.NotificationEntityJSON;

import javax.validation.constraints.NotNull;
import java.util.Set;

public interface BundleEntrypoint extends Comparable<BundleEntrypoint> {
    String BUNDLE_PREFIX = "org.touchhome.bundle.";

    static String getBundleName(Class clazz) {
        String name = clazz.getName();
        if (name.startsWith(BUNDLE_PREFIX)) {
            return name.substring(BUNDLE_PREFIX.length(), name.indexOf('.', BUNDLE_PREFIX.length()));
        }
        return null;
    }

    void init();

    default void destroy() {

    }

    default String getBundleImage() {
        return getBundleId() + ".png";
    }

    String getBundleId();

    int order();

    default BundleImageColorIndex getBundleImageColorIndex() {
        return BundleImageColorIndex.ZERO;
    }

    @Override
    default int compareTo(@NotNull BundleEntrypoint o) {
        return Integer.compare(this.order(), o.order());
    }

    default Set<NotificationEntityJSON> getNotifications() {
        return null;
    }

    enum BundleImageColorIndex {
        ZERO, ONE, TWO, THREE, FOUR
    }
}
