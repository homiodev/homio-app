package org.touchhome.app.builder.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.builder.widget.hasBuilder.HasAxisBuilder;
import org.touchhome.app.builder.widget.hasBuilder.HasChartDataSourceBuilder;
import org.touchhome.app.builder.widget.hasBuilder.HasChartTimePeriodBuilder;
import org.touchhome.app.builder.widget.hasBuilder.HasHorizontalLineBuilder;
import org.touchhome.app.builder.widget.hasBuilder.HasMinMaxChartValueBuilder;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.impl.chart.bar.WidgetBarTimeChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.bar.WidgetBarTimeChartSeriesEntity;
import org.touchhome.bundle.api.EntityContextWidget.BarChartType;
import org.touchhome.bundle.api.EntityContextWidget.BarTimeChartBuilder;
import org.touchhome.bundle.api.EntityContextWidget.BarTimeChartSeriesBuilder;

public class BarTimeChartBuilderImpl extends WidgetBaseBuilderImpl<BarTimeChartBuilder, WidgetBarTimeChartEntity>
    implements BarTimeChartBuilder,
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
