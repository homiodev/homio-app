package org.touchhome.bundle.api;

public interface BundleContext {
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

    enum BundleImageColorIndex {
        ZERO, ONE, TWO, THREE, FOUR
    }
}
