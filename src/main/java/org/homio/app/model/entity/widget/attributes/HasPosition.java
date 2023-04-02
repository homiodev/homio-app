package org.homio.app.model.entity.widget.attributes;

import org.homio.bundle.api.entity.HasJsonData;

/**
 * xb - x block position yb - y block position bw - block width bh - block height
 * <p>
 * if position inside layout - xb and yb define layout(parent)
 */
public interface HasPosition<T> extends HasJsonData {

    default T setXb(int xb) {
        setJsonData("xb", xb);
        return (T) this;
    }

    default T setYb(int yb) {
        setJsonData("yb", yb);
        return (T) this;
    }

    default T setBw(int bw) {
        if (bw < 1 || bw > 10) {
            throw new IllegalArgumentException("Block width must be in range 1..10");
        }
        setJsonData("bw", bw);
        return (T) this;
    }

    default T setBh(int bh) {
        if (bh < 1 || bh > 10) {
            throw new IllegalArgumentException("Block height must be in range 1..10");
        }
        setJsonData("bh", bh);
        return (T) this;
    }

    default int getXb() {
        return getJsonData("xb", 0);
    }

    default int getYb() {
        return getJsonData("yb", 0);
    }

    default int getBw() {
        return getJsonData("bw", 1);
    }

    default int getBh() {
        return getJsonData("bh", 1);
    }

    default String getParent() {
        return getJsonData("parent");
    }

    default T setParent(String value) {
        if (value == null) {
            getJsonData().remove("parent");
        } else {
            setJsonData("parent", value);
        }
        return (T) this;
    }
}
