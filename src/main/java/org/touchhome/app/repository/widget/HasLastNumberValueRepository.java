package org.touchhome.app.repository.widget;

public interface HasLastNumberValueRepository<T> {
    double getLastNumberValue(T source);
}
