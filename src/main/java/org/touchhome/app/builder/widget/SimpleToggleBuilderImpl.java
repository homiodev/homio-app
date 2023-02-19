package org.touchhome.app.builder.widget;

import org.jetbrains.annotations.Nullable;
import org.touchhome.app.builder.widget.hasBuilder.HasAlignBuilder;
import org.touchhome.app.builder.widget.hasBuilder.HasPaddingBuilder;
import org.touchhome.app.builder.widget.hasBuilder.HasToggleBuilder;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.impl.toggle.WidgetSimpleToggleEntity;
import org.touchhome.bundle.api.EntityContextWidget.SimpleToggleWidgetBuilder;

public class SimpleToggleBuilderImpl extends WidgetBaseBuilderImpl<SimpleToggleWidgetBuilder, WidgetSimpleToggleEntity>
    implements SimpleToggleWidgetBuilder,
    HasPaddingBuilder<WidgetSimpleToggleEntity, SimpleToggleWidgetBuilder>,
    HasAlignBuilder<WidgetSimpleToggleEntity, SimpleToggleWidgetBuilder>,
    HasToggleBuilder<WidgetSimpleToggleEntity, SimpleToggleWidgetBuilder> {

    SimpleToggleBuilderImpl(WidgetSimpleToggleEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public SimpleToggleWidgetBuilder setListenSourceUpdates(@Nullable Boolean value) {
        widget.setListenSourceUpdates(value);
        return this;
    }

    @Override
    public SimpleToggleWidgetBuilder setShowLastUpdateTimer(@Nullable Boolean value) {
        widget.setShowLastUpdateTimer(value);
        return this;
    }

    @Override
    public SimpleToggleWidgetBuilder setValueDataSource(@Nullable String value) {
        widget.setValueDataSource(value);
        return this;
    }

    @Override
    public SimpleToggleWidgetBuilder setSetValueDataSource(@Nullable String value) {
        widget.setSetValueDataSource(value);
        return this;
    }
}
