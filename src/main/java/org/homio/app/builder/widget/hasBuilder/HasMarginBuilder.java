package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget;
import org.homio.app.model.entity.widget.attributes.HasMargin;
import org.jetbrains.annotations.NotNull;

import static java.lang.String.format;

public interface HasMarginBuilder<T extends HasMargin, R> extends ContextWidget.HasMargin<R> {

    T getWidget();

    @Override
    default @NotNull R setMargin(int top, int right, int bottom, int left) {
        getWidget().setMargin(format("{\"top\":%s,\"right\":%s,\"bottom\":%s,\"left\":%s}", top, right, bottom, left));
        return (R) this;
    }
}
