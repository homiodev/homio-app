package org.homio.app.model.entity.widget.attributes;

import org.homio.api.entity.HasJsonData;

import java.util.Map;

/**
 * xb - x block position yb - y block position bw - block width bh - block height
 * <p>
 * if position inside layout - xb and yb define layout(parent)
 */
public interface HasPosition<T> extends HasJsonData {

  default T setXb(int xb) {
    if (xb < -1 || xb > 10) {
      throw new IllegalArgumentException("Block x position must be in range -1..10");
    }
    setJsonData("xb", xb);
    return (T) this;
  }

  default T setYb(int yb) {
    if (yb < -1 || yb > 10) {
      throw new IllegalArgumentException("Block y position must be in range -1..10");
    }
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
    setJsonData("parent", value);
    return (T) this;
  }

  default void setXb(int xb, String key) {
    if (xb < -1 || xb > 10) {
      throw new IllegalArgumentException("Block x position must be in range 0..10");
    }
    setCoordinate(xb, key, "xb");
  }

  default void setYb(int yb, String key) {
    if (yb < -1 || yb > 10) {
      throw new IllegalArgumentException("Block y position must be in range 0..10");
    }
    setCoordinate(yb, key, "yb");
  }

  default void setBw(int bw, String key) {
    if (bw < 1 || bw > 10) {
      throw new IllegalArgumentException("Block width must be in range 1..10");
    }
    setCoordinate(bw, key, "bw");
  }

  default void setBh(int bh, String key) {
    if (bh < 1 || bh > 10) {
      throw new IllegalArgumentException("Block height must be in range 1..10");
    }
    setCoordinate(bh, key, "bh");
  }

  default int getXb(String key) {
    return getMapLayoutOr("xbp", key, getXb());
  }

  default int getYb(String key) {
    return getMapLayoutOr("ybp", key, getYb());
  }

  default int getBw(String key) {
    return getMapLayoutOr("bwp", key, getBw());
  }

  default int getBh(String key) {
    return getMapLayoutOr("bhp", key, getBh());
  }

  private int getMapLayoutOr(String bhp, String key, int defValue) {
    Map<String, Integer> map = getJsonDataMap(bhp, Integer.class);
    if (map == null) {
      return defValue;
    }
    return map.getOrDefault(key, defValue);
  }

  private void setCoordinate(int xb, String key, String jsonKey) {
    if (getParent() == null) {
      updateJsonDataMap(jsonKey + "p", Integer.class, m -> m.put(key, xb));
    } else {
      setJsonData(jsonKey, xb);
    }
  }
}
