package org.homio.app.builder.widget;

import org.homio.api.ContextWidget.SimpleValueWidgetBuilder;
import org.homio.app.builder.widget.hasBuilder.*;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.impl.WidgetSimpleValueEntity;
import org.jetbrains.annotations.NotNull;

public class SimpleValueBuilderImpl extends WidgetBaseBuilderImpl<SimpleValueWidgetBuilder, WidgetSimpleValueEntity>
        implements SimpleValueWidgetBuilder,
        HasActionOnClickBuilder<WidgetSimpleValueEntity, SimpleValueWidgetBuilder>,
        HasValueConverterBuilder<WidgetSimpleValueEntity, SimpleValueWidgetBuilder>,
        HasPaddingBuilder<WidgetSimpleValueEntity, SimpleValueWidgetBuilder>,
        HasAlignBuilder<WidgetSimpleValueEntity, SimpleValueWidgetBuilder>,
        HasIconColorThresholdBuilder<WidgetSimpleValueEntity, SimpleValueWidgetBuilder>,
        HasValueTemplateBuilder<WidgetSimpleValueEntity, SimpleValueWidgetBuilder> {

    SimpleValueBuilderImpl(WidgetSimpleValueEntity widget, ContextImpl context) {
        super(widget, context);
    }

    @Override
    public @NotNull SimpleValueWidgetBuilder setValueDataSource(String value) {
        widget.setValueDataSource(value);
        return this;
    }
}
