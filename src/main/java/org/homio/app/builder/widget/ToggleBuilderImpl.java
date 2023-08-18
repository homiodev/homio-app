package org.homio.app.builder.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.EntityContextWidget.ToggleType;
import org.homio.api.EntityContextWidget.ToggleWidgetBuilder;
import org.homio.api.EntityContextWidget.ToggleWidgetSeriesBuilder;
import org.homio.app.builder.widget.hasBuilder.HasIconColorThresholdBuilder;
import org.homio.app.builder.widget.hasBuilder.HasNameBuilder;
import org.homio.app.builder.widget.hasBuilder.HasPaddingBuilder;
import org.homio.app.builder.widget.hasBuilder.HasToggleBuilder;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.widget.impl.toggle.WidgetToggleEntity;
import org.homio.app.model.entity.widget.impl.toggle.WidgetToggleSeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToggleBuilderImpl extends WidgetBaseBuilderImpl<ToggleWidgetBuilder, WidgetToggleEntity>
        implements ToggleWidgetBuilder,
        HasPaddingBuilder<WidgetToggleEntity, ToggleWidgetBuilder>,
        HasNameBuilder<WidgetToggleEntity, ToggleWidgetBuilder> {

    @Getter
    private final List<WidgetToggleSeriesEntity> series = new ArrayList<>();

    ToggleBuilderImpl(WidgetToggleEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
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

    @Override
    public @NotNull ToggleWidgetBuilder setListenSourceUpdates(@Nullable Boolean value) {
        widget.setListenSourceUpdates(value);
        return this;
    }

    @Override
    public @NotNull ToggleWidgetBuilder setShowLastUpdateTimer(@Nullable Boolean value) {
        widget.setShowLastUpdateTimer(value);
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
