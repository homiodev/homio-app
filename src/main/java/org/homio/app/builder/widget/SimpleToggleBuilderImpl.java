package org.homio.app.builder.widget;

import org.homio.api.ContextWidget.SimpleToggleWidgetBuilder;
import org.homio.app.builder.widget.hasBuilder.HasAlignBuilder;
import org.homio.app.builder.widget.hasBuilder.HasPaddingBuilder;
import org.homio.app.builder.widget.hasBuilder.HasToggleBuilder;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.impl.toggle.WidgetSimpleToggleEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimpleToggleBuilderImpl extends WidgetBaseBuilderImpl<SimpleToggleWidgetBuilder, WidgetSimpleToggleEntity>
        implements SimpleToggleWidgetBuilder,
        HasPaddingBuilder<WidgetSimpleToggleEntity, SimpleToggleWidgetBuilder>,
        HasAlignBuilder<WidgetSimpleToggleEntity, SimpleToggleWidgetBuilder>,
        HasToggleBuilder<WidgetSimpleToggleEntity, SimpleToggleWidgetBuilder> {

    SimpleToggleBuilderImpl(WidgetSimpleToggleEntity widget, ContextImpl context) {
        super(widget, context);
    }

    @Override
    public @NotNull SimpleToggleWidgetBuilder setListenSourceUpdates(@Nullable Boolean value) {
        widget.setListenSourceUpdates(value);
        return this;
    }

    @Override
    public @NotNull SimpleToggleWidgetBuilder setShowLastUpdateTimer(@Nullable Boolean value) {
        widget.setShowLastUpdateTimer(value);
        return this;
    }

    @Override
    public @NotNull SimpleToggleWidgetBuilder setValueDataSource(@Nullable String value) {
        widget.setValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull SimpleToggleWidgetBuilder setSetValueDataSource(@Nullable String value) {
        widget.setSetValueDataSource(value);
        return this;
    }
}
