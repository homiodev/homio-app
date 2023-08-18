package org.homio.app.builder.widget;

import org.homio.api.EntityContextWidget.SimpleValueWidgetBuilder;
import org.homio.app.builder.widget.hasBuilder.HasActionOnClickBuilder;
import org.homio.app.builder.widget.hasBuilder.HasAlignBuilder;
import org.homio.app.builder.widget.hasBuilder.HasIconColorThresholdBuilder;
import org.homio.app.builder.widget.hasBuilder.HasPaddingBuilder;
import org.homio.app.builder.widget.hasBuilder.HasValueConverterBuilder;
import org.homio.app.builder.widget.hasBuilder.HasValueTemplateBuilder;
import org.homio.app.manager.common.EntityContextImpl;
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

    SimpleValueBuilderImpl(WidgetSimpleValueEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public @NotNull SimpleValueWidgetBuilder setValueDataSource(String value) {
        widget.setValueDataSource(value);
        return this;
    }
}
