package org.touchhome.app.builder.widget.hasBuilder;

import org.touchhome.bundle.api.EntityContextWidget.HasAlign;
import org.touchhome.bundle.api.EntityContextWidget.HorizontalAlign;
import org.touchhome.bundle.api.EntityContextWidget.VerticalAlign;

public interface HasAlignBuilder<T extends org.touchhome.app.model.entity.widget.attributes.HasAlign, R>
    extends HasAlign<R> {

    T getWidget();

    @Override
    default R setAlign(HorizontalAlign horizontalAlign, VerticalAlign verticalAlign) {
        getWidget().setAlign(String.format("%sx%s", horizontalAlign.ordinal() + 1, verticalAlign.ordinal() + 1));
        return (R) this;
    }
}
