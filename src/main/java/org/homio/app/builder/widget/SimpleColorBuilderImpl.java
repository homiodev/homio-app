package org.homio.app.builder.widget;

import org.homio.app.builder.widget.hasBuilder.HasAlignBuilder;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.widget.impl.color.WidgetSimpleColorEntity;
import org.homio.bundle.api.EntityContextWidget.SimpleColorWidgetBuilder;
import org.jetbrains.annotations.Nullable;

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
