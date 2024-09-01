package org.homio.app.builder.widget;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.ContextWidget.BarChartType;
import org.homio.api.ContextWidget.BarTimeChartBuilder;
import org.homio.api.ContextWidget.BarTimeChartSeriesBuilder;
import org.homio.app.builder.widget.hasBuilder.*;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.impl.chart.bar.WidgetBarTimeChartEntity;
import org.homio.app.model.entity.widget.impl.chart.bar.WidgetBarTimeChartSeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BarTimeChartBuilderImpl extends WidgetBaseBuilderImpl<BarTimeChartBuilder, WidgetBarTimeChartEntity>
        implements BarTimeChartBuilder,
        HasLegendBuilder<WidgetBarTimeChartEntity, BarTimeChartBuilder>,
        HasChartTimePeriodBuilder<WidgetBarTimeChartEntity, BarTimeChartBuilder>,
        HasHorizontalLineBuilder<WidgetBarTimeChartEntity, BarTimeChartBuilder>,
        HasMinMaxChartValueBuilder<WidgetBarTimeChartEntity, BarTimeChartBuilder>,
        HasAxisBuilder<WidgetBarTimeChartEntity, BarTimeChartBuilder> {

    @Getter
    private final List<WidgetBarTimeChartSeriesEntity> series = new ArrayList<>();

    BarTimeChartBuilderImpl(WidgetBarTimeChartEntity widget, ContextImpl context) {
        super(widget, context);
    }

    @Override
    public @NotNull BarTimeChartBuilder addSeries(@Nullable String name, @NotNull Consumer<BarTimeChartSeriesBuilder> builder) {
        WidgetBarTimeChartSeriesEntity entity = new WidgetBarTimeChartSeriesEntity();
        entity.setName(name);
        series.add(entity);
        BarTimeSeriesBuilderImpl seriesBuilder = new BarTimeSeriesBuilderImpl(entity);
        builder.accept(seriesBuilder);
        return this;
    }

    @Override
    public @NotNull BarTimeChartBuilder setAxisLabel(String value) {
        widget.setAxisLabel(value);
        return this;
    }

    @Override
    public @NotNull BarTimeChartBuilder setDisplayType(BarChartType value) {
        widget.setDisplayType(value);
        return this;
    }

    @Override
    public @NotNull BarTimeChartBuilder setBarBorderWidth(String value) {
        widget.setBarBorderWidth(value);
        return this;
    }

    @Override
    public @NotNull BarTimeChartBuilder setShowChartFullScreenButton(boolean value) {
        widget.setShowChartFullScreenButton(value);
        return this;
    }

    @Override
    public @NotNull BarTimeChartBuilder setFetchDataFromServerInterval(int value) {
        widget.setFetchDataFromServerInterval(value);
        return this;
    }
}

@RequiredArgsConstructor
class BarTimeSeriesBuilderImpl implements BarTimeChartSeriesBuilder,
        HasChartDataSourceBuilder<WidgetBarTimeChartSeriesEntity, BarTimeChartSeriesBuilder> {

    private final WidgetBarTimeChartSeriesEntity series;

    @Override
    public WidgetBarTimeChartSeriesEntity getWidget() {
        return series;
    }
}
