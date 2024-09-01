package org.homio.app.builder.widget;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.ContextWidget.LineChartBuilder;
import org.homio.api.ContextWidget.LineChartSeriesBuilder;
import org.homio.app.builder.widget.hasBuilder.*;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity;
import org.homio.app.model.entity.widget.impl.chart.line.WidgetLineChartSeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LineChartBuilderImpl extends WidgetBaseBuilderImpl<LineChartBuilder, WidgetLineChartEntity>
        implements LineChartBuilder,
        HasLegendBuilder<WidgetLineChartEntity, LineChartBuilder>,
        HasLineChartBehaviourBuilder<WidgetLineChartEntity, LineChartBuilder>,
        HasAxisBuilder<WidgetLineChartEntity, LineChartBuilder>,
        HasMinMaxChartValueBuilder<WidgetLineChartEntity, LineChartBuilder>,
        HasChartTimePeriodBuilder<WidgetLineChartEntity, LineChartBuilder>,
        HasHorizontalLineBuilder<WidgetLineChartEntity, LineChartBuilder> {

    @Getter
    private final List<WidgetLineChartSeriesEntity> series = new ArrayList<>();

    LineChartBuilderImpl(WidgetLineChartEntity widget, ContextImpl context) {
        super(widget, context);
    }

    @Override
    public @NotNull LineChartBuilder addSeries(@Nullable String name, @NotNull Consumer<LineChartSeriesBuilder> builder) {
        WidgetLineChartSeriesEntity entity = new WidgetLineChartSeriesEntity();
        entity.setName(name);
        series.add(entity);
        LineSeriesBuilderImpl seriesBuilder = new LineSeriesBuilderImpl(entity);
        builder.accept(seriesBuilder);
        return this;
    }

    @Override
    public @NotNull LineChartBuilder setShowChartFullScreenButton(boolean value) {
        widget.setShowChartFullScreenButton(value);
        return this;
    }

    @Override
    public @NotNull LineChartBuilder setFetchDataFromServerInterval(int value) {
        widget.setFetchDataFromServerInterval(value);
        return this;
    }
}

@RequiredArgsConstructor
class LineSeriesBuilderImpl implements LineChartSeriesBuilder,
        HasChartDataSourceBuilder<WidgetLineChartSeriesEntity, LineChartSeriesBuilder> {

    private final WidgetLineChartSeriesEntity series;

    @Override
    public WidgetLineChartSeriesEntity getWidget() {
        return series;
    }
}
