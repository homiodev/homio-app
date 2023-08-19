package org.homio.app.builder.widget;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.EntityContextWidget;
import org.homio.api.EntityContextWidget.DisplayWidgetBuilder;
import org.homio.api.EntityContextWidget.DisplayWidgetSeriesBuilder;
import org.homio.api.entity.widget.AggregationType;
import org.homio.app.builder.widget.hasBuilder.*;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.homio.app.model.entity.widget.impl.display.WidgetDisplaySeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static java.lang.String.format;

public class DisplayBuilderImpl extends WidgetBaseBuilderImpl<DisplayWidgetBuilder, WidgetDisplayEntity>
        implements DisplayWidgetBuilder,
        HasActionOnClickBuilder<WidgetDisplayEntity, DisplayWidgetBuilder>,
        HasLineChartBehaviourBuilder<WidgetDisplayEntity, DisplayWidgetBuilder>,
        HasHorizontalLineBuilder<WidgetDisplayEntity, DisplayWidgetBuilder>,
        HasChartTimePeriodBuilder<WidgetDisplayEntity, DisplayWidgetBuilder>,
        HasMinMaxChartValueBuilder<WidgetDisplayEntity, DisplayWidgetBuilder>,
        HasChartDataSourceBuilder<WidgetDisplayEntity, DisplayWidgetBuilder>,
        HasPaddingBuilder<WidgetDisplayEntity, DisplayWidgetBuilder>,
        HasNameBuilder<WidgetDisplayEntity, DisplayWidgetBuilder> {

    @Getter
    private final List<WidgetDisplaySeriesEntity> series = new ArrayList<>();

    DisplayBuilderImpl(WidgetDisplayEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public DisplayWidgetBuilder setValueToPushConfirmMessage(String value) {
        widget.setValueToPushConfirmMessage(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setValueToPushSource(@Nullable String value) {
        widget.setSetValueDataSource(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setListenSourceUpdates(@Nullable Boolean value) {
        widget.setListenSourceUpdates(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setShowLastUpdateTimer(@Nullable Boolean value) {
        widget.setShowLastUpdateTimer(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setChartHeight(int value) {
        widget.setChartHeight(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setBarBorderWidth(int top, int right, int bottom, int left) {
        widget.setBarBorderWidth(format("{\"top\": %s, \"right\": %s, \"bottom\": %s, \"left\": %s}", top, right, bottom, left));
        return this;
    }

    @Override
    public DisplayWidgetBuilder setChartType(@NotNull EntityContextWidget.ChartType value) {
        widget.setChartType(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder addSeries(@Nullable String name, @NotNull Consumer<DisplayWidgetSeriesBuilder> builder) {
        WidgetDisplaySeriesEntity entity = new WidgetDisplaySeriesEntity();
        entity.setName(name);
        series.add(entity);
        DisplaySeriesBuilderImpl seriesBuilder = new DisplaySeriesBuilderImpl(entity);
        builder.accept(seriesBuilder);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setSingleLinePos(@Nullable Integer value) {
        widget.setSingleLinePos(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setSingleLineColor(@Nullable String value) {
        widget.setSingleLineColor(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setSingleLineWidth(@Nullable Integer value) {
        widget.setSingleLineWidth(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setShowDynamicLine(@Nullable Boolean value) {
        widget.setShowDynamicLine(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setDynamicLineColor(@Nullable String value) {
        widget.setDynamicLineColor(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setDynamicLineWidth(@Nullable Integer value) {
        widget.setDynamicLineWidth(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setPointBorderColor(String value) {
        widget.setPointBorderColor(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setShowChartFullScreenButton(boolean value) {
        widget.setShowChartFullScreenButton(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setFetchDataFromServerInterval(int value) {
        widget.setFetchDataFromServerInterval(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setLayout(@Nullable String value) {
        widget.setLayout(value);
        return this;
    }
}

@RequiredArgsConstructor
class DisplaySeriesBuilderImpl implements DisplayWidgetSeriesBuilder,
        HasValueConverterBuilder<WidgetDisplaySeriesEntity, DisplayWidgetSeriesBuilder>,
        HasValueTemplateBuilder<WidgetDisplaySeriesEntity, DisplayWidgetSeriesBuilder>,
        HasIconColorThresholdBuilder<WidgetDisplaySeriesEntity, DisplayWidgetSeriesBuilder>,
        HasNameBuilder<WidgetDisplaySeriesEntity, DisplayWidgetSeriesBuilder> {

    private final WidgetDisplaySeriesEntity series;

    @Override
    public DisplayWidgetSeriesBuilder setValueDataSource(String value) {
        series.setValueDataSource(value);
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setValueAggregationType(AggregationType value) {
        series.setValueAggregationType(value);
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setValueAggregationPeriod(int value) {
        series.setValueAggregationPeriod(value);
        return this;
    }

    @Override
    public WidgetDisplaySeriesEntity getWidget() {
        return series;
    }

    @Override
    public DisplayWidgetSeriesBuilder setStyle(String... styles) {
        series.setStyle(String.join("~~~", styles));
        return this;
    }
}
