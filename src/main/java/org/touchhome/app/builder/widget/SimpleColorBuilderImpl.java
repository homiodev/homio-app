package org.touchhome.app.builder.widget;

import org.jetbrains.annotations.Nullable;
import org.touchhome.app.builder.widget.hasBuilder.HasAlignBuilder;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.impl.color.WidgetSimpleColorEntity;
import org.touchhome.bundle.api.EntityContextWidget.SimpleColorWidgetBuilder;

public class SimpleColorBuilderImpl extends WidgetBaseBuilderImpl<SimpleColorWidgetBuilder, WidgetSimpleColorEntity>
    implements SimpleColorWidgetBuilder,
    HasAlignBuilder<WidgetSimpleColorEntity, SimpleColorWidgetBuilder> {

    SimpleColorBuilderImpl(WidgetSimpleColorEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public SimpleColorWidgetBuilder setColors(String... colors) {
        widget.setColors(String.join("~~~", colors));
        return this;
    }

    @Override
    public SimpleColorWidgetBuilder setCircleSize(int value) {
        widget.setCircleSize(value);
        return this;
    }

    @Override
    public SimpleColorWidgetBuilder setCircleSpacing(int value) {
        widget.setCircleSpacing(value);
        return this;
    }

    @Override
    public SimpleColorWidgetBuilder setValueDataSource(@Nullable String value) {
        widget.setValueDataSource(value);
        return this;
    }

    @Override
    public SimpleColorWidgetBuilder setSetValueDataSource(@Nullable String value) {
        widget.setSetValueDataSource(value);
        return this;
    }
}
