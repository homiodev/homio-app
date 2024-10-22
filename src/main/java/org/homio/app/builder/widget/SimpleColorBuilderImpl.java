package org.homio.app.builder.widget;

import org.homio.api.ContextWidget.SimpleColorWidgetBuilder;
import org.homio.app.builder.widget.hasBuilder.HasAlignBuilder;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.impl.simple.WidgetSimpleColorEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;

public class SimpleColorBuilderImpl extends WidgetBaseBuilderImpl<SimpleColorWidgetBuilder, WidgetSimpleColorEntity>
        implements SimpleColorWidgetBuilder,
        HasAlignBuilder<WidgetSimpleColorEntity, SimpleColorWidgetBuilder> {

    SimpleColorBuilderImpl(WidgetSimpleColorEntity widget, ContextImpl context) {
        super(widget, context);
    }

    @Override
    public @NotNull SimpleColorWidgetBuilder setColors(String... colors) {
        widget.setColors(String.join(LIST_DELIMITER, colors));
        return this;
    }

    @Override
    public @NotNull SimpleColorWidgetBuilder setCircleSize(int value) {
        widget.setCircleSize(value);
        return this;
    }

    @Override
    public @NotNull SimpleColorWidgetBuilder setCircleSpacing(int value) {
        widget.setCircleSpacing(value);
        return this;
    }

    @Override
    public @NotNull SimpleColorWidgetBuilder setValueDataSource(@Nullable String value) {
        widget.setValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull SimpleColorWidgetBuilder setSetValueDataSource(@Nullable String value) {
        widget.setSetValueDataSource(value);
        return this;
    }
}
