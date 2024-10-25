package org.homio.app.builder.widget;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.ContextWidget.ToggleType;
import org.homio.api.ContextWidget.ToggleWidgetBuilder;
import org.homio.api.ContextWidget.ToggleWidgetSeriesBuilder;
import org.homio.app.builder.widget.hasBuilder.HasIconColorThresholdBuilder;
import org.homio.app.builder.widget.hasBuilder.HasMarginBuilder;
import org.homio.app.builder.widget.hasBuilder.HasNameBuilder;
import org.homio.app.builder.widget.hasBuilder.HasToggleBuilder;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.impl.toggle.WidgetToggleEntity;
import org.homio.app.model.entity.widget.impl.toggle.WidgetToggleSeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ToggleBuilderImpl extends WidgetBaseBuilderImpl<ToggleWidgetBuilder, WidgetToggleEntity>
        implements ToggleWidgetBuilder,
        HasMarginBuilder<WidgetToggleEntity, ToggleWidgetBuilder>,
        HasNameBuilder<WidgetToggleEntity, ToggleWidgetBuilder> {

    @Getter
    private final List<WidgetToggleSeriesEntity> series = new ArrayList<>();

    ToggleBuilderImpl(WidgetToggleEntity widget, ContextImpl context) {
        super(widget, context);
    }

    @Override
    public @NotNull ToggleWidgetBuilder setShowAllButton(Boolean value) {
        widget.setShowAllButton(value);
        return this;
    }

    @Override
    public @NotNull ToggleWidgetBuilder setDisplayType(@NotNull ToggleType value) {
        widget.setDisplayType(value);
        return this;
    }

    @Override
    public @NotNull ToggleWidgetBuilder addSeries(@Nullable String name, @NotNull Consumer<ToggleWidgetSeriesBuilder> builder) {
        WidgetToggleSeriesEntity entity = new WidgetToggleSeriesEntity();
        entity.setName(name);
        series.add(entity);
        ToggleSeriesBuilderImpl seriesBuilder = new ToggleSeriesBuilderImpl(entity);
        builder.accept(seriesBuilder);
        return this;
    }

    @Override
    public @NotNull ToggleWidgetBuilder setLayout(String value) {
        widget.setLayout(value);
        return this;
    }
}

@RequiredArgsConstructor
class ToggleSeriesBuilderImpl implements ToggleWidgetSeriesBuilder,
        HasToggleBuilder<WidgetToggleSeriesEntity, ToggleWidgetSeriesBuilder>,
        HasIconColorThresholdBuilder<WidgetToggleSeriesEntity, ToggleWidgetSeriesBuilder>,
        HasNameBuilder<WidgetToggleSeriesEntity, ToggleWidgetSeriesBuilder> {

    private final WidgetToggleSeriesEntity series;

    @Override
    public WidgetToggleSeriesEntity getWidget() {
        return series;
    }

    @Override
    public @NotNull ToggleWidgetSeriesBuilder setValueDataSource(String value) {
        series.setValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull ToggleWidgetSeriesBuilder setSetValueDataSource(String value) {
        series.setSetValueDataSource(value);
        return this;
    }
}
