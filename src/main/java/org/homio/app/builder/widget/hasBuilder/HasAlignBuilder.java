package org.homio.app.builder.widget.hasBuilder;

import org.homio.bundle.api.EntityContextWidget.HasAlign;
import org.homio.bundle.api.EntityContextWidget.HorizontalAlign;
import org.homio.bundle.api.EntityContextWidget.VerticalAlign;

public interface HasAlignBuilder<T extends org.homio.app.model.entity.widget.attributes.HasAlign, R>
    extends HasAlign<R> {

    T getWidget();

    @Override
    default R setAlign(HorizontalAlign horizontalAlign, VerticalAlign verticalAlign) {
        getWidget().setAlign(String.format("%sx%s", horizontalAlign.ordinal() + 1, verticalAlign.ordinal() + 1));
        return (R) this;
    }
}
