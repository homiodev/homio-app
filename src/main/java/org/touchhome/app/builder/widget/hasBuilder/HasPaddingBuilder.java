package org.touchhome.app.builder.widget.hasBuilder;

import static java.lang.String.format;

import org.touchhome.bundle.api.EntityContextWidget.HasPadding;

public interface HasPaddingBuilder<T extends org.touchhome.app.model.entity.widget.attributes.HasPadding, R> extends HasPadding<R> {

    T getWidget();

    @Override
    default R setPadding(int top, int right, int bottom, int left) {
        getWidget().setPadding(format("{\"top\":%s,\"right\":%s,\"bottom\":%s,\"left\":%s}", top, right, bottom, left));
        return (R) this;
    }
}
