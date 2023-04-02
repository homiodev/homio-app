package org.homio.app.builder.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.app.builder.widget.hasBuilder.HasAxisBuilder;
import org.homio.app.builder.widget.hasBuilder.HasChartDataSourceBuilder;
import org.homio.app.builder.widget.hasBuilder.HasChartTimePeriodBuilder;
import org.homio.app.builder.widget.hasBuilder.HasHorizontalLineBuilder;
import org.homio.app.builder.widget.hasBuilder.HasLegendBuilder;
import org.homio.app.builder.widget.hasBuilder.HasMinMaxChartValueBuilder;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.widget.impl.chart.bar.WidgetBarTimeChartEntity;
import org.homio.app.model.entity.widget.impl.chart.bar.WidgetBarTimeChartSeriesEntity;
import org.homio.bundle.api.EntityContextWidget.BarChartType;
import org.homio.bundle.api.EntityContextWidget.BarTimeChartBuilder;
import org.homio.bundle.api.EntityContextWidget.BarTimeChartSeriesBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BarTimeChartBuilderImpl extends WidgetBaseBuilderImpl<BarTimeChartBuilder, WidgetBarTimeChartEntity>
    implements BarTimeChartBuilder,
    HasLegendBuilder<WidgetBarTimeChartEntity, BarTimeChartBuilder>,
    HasChartTimePeriodBuilder<WidgetBarTimeChartEntity, BarTimeChartBuilder>,
    HasHorizontalLineBuilder<WidgetBarTimeChartEntity, BarTimeChartBuilder>,
    HasMinMaxChartValueBuilder<WidgetBarTimeChartEntity, BarTimeChartBuilder>,
    HasAxisBuilder<WidgetBarTimeChartEntity, BarTimeChartBuilder> {

    @Getter
    private final List<WidgetBarTimeChartSeriesEntity> series = new ArrayList<>();

    BarTimeChartBuilderImpl(WidgetBarTimeChartEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public BarTimeChartBuilder addSeries(@Nullable String name, @NotNull Consumer<BarTimeChartSeriesBuilder> builder) {
        WidgetBarTimeChartSeriesEntity entity = new WidgetBarTimeChartSeriesEntity();
        entity.setName(name);
        series.add(entity);
        BarTimeSeriesBuilderImpl seriesBuilder = new BarTimeSeriesBuilderImpl(entity);
        builder.accept(seriesBuilder);
        return this;
    }

    @Override
    public BarTimeChartBuilder setAxisLabel(String value) {
        widget.setAxisLabel(value);
        return this;
    }

    @Override
    public BarTimeChartBuilder setDisplayType(BarChartType value) {
        widget.setDisplayType(value);
        return this;
    }

    @Override
    public BarTimeChartBuilder setBarBorderWidth(String value) {
        widget.setBarBorderWidth(value);
        return this;
    }

    @Override
    public BarTimeChartBuilder setShowChartFullScreenButton(boolean value) {
        widget.setShowChartFullScreenButton(value);
        return this;
    }

    @Override
    public BarTimeChartBuilder setFetchDataFromServerInterval(int value) {
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
