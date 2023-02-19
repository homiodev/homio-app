package org.touchhome.app.builder.widget;

import org.jetbrains.annotations.Nullable;
import org.touchhome.app.builder.widget.hasBuilder.HasAlignBuilder;
import org.touchhome.app.builder.widget.hasBuilder.HasIconColorThresholdBuilder;
import org.touchhome.app.builder.widget.hasBuilder.HasPaddingBuilder;
import org.touchhome.app.builder.widget.hasBuilder.HasValueConverterBuilder;
import org.touchhome.app.builder.widget.hasBuilder.HasValueTemplateBuilder;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.impl.WidgetSimpleValueEntity;
import org.touchhome.bundle.api.EntityContextWidget.SimpleValueWidgetBuilder;
import org.touchhome.bundle.api.exception.ProhibitedExecution;

public class SimpleValueBuilderImpl extends WidgetBaseBuilderImpl<SimpleValueWidgetBuilder, WidgetSimpleValueEntity>
    implements SimpleValueWidgetBuilder,
    HasValueConverterBuilder<WidgetSimpleValueEntity, SimpleValueWidgetBuilder>,
    HasPaddingBuilder<WidgetSimpleValueEntity, SimpleValueWidgetBuilder>,
    HasAlignBuilder<WidgetSimpleValueEntity, SimpleValueWidgetBuilder>,
    HasIconColorThresholdBuilder<WidgetSimpleValueEntity, SimpleValueWidgetBuilder>,
    HasValueTemplateBuilder<WidgetSimpleValueEntity, SimpleValueWidgetBuilder> {

    SimpleValueBuilderImpl(WidgetSimpleValueEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public SimpleValueWidgetBuilder setValueDataSource(String value) {
        widget.setValueDataSource(value);
        return this;
    }

    @Override
    public SimpleValueWidgetBuilder setSetValueDataSource(@Nullable String value) {
        throw new ProhibitedExecution();
    }
}
