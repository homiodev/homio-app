package org.homio.app.builder.widget.hasBuilder;

import static java.lang.String.format;

import org.homio.api.ContextWidget.HasPadding;

public interface HasPaddingBuilder<T extends org.homio.app.model.entity.widget.attributes.HasPadding, R> extends HasPadding<R> {

    T getWidget();

    @Override
    default R setPadding(int top, int right, int bottom, int left) {
        getWidget().setPadding(format("{\"top\":%s,\"right\":%s,\"bottom\":%s,\"left\":%s}", top, right, bottom, left));
        return (R) this;
    }
}
