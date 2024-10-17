package org.homio.app.builder.widget;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.ContextWidget;
import org.homio.api.ContextWidget.DisplayWidgetBuilder;
import org.homio.api.ContextWidget.DisplayWidgetSeriesBuilder;
import org.homio.app.builder.widget.hasBuilder.*;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.homio.app.model.entity.widget.impl.display.WidgetDisplaySeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static java.lang.String.format;
import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;

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

    DisplayBuilderImpl(WidgetDisplayEntity widget, ContextImpl context) {
        super(widget, context);
    }

    @Override
    public @NotNull DisplayWidgetBuilder setValueToPushConfirmMessage(String value) {
        widget.setValueToPushConfirmMessage(value);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setValueToPushSource(@Nullable String value) {
        widget.setSetValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setChartHeight(int value) {
        widget.setChartHeight(value);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setBarBorderWidth(int top, int right, int bottom, int left) {
        widget.setBarBorderWidth(format("{\"top\": %s, \"right\": %s, \"bottom\": %s, \"left\": %s}", top, right, bottom, left));
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setChartType(@NotNull ContextWidget.ChartType value) {
        widget.setChartType(value);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder addSeries(@Nullable String name, @NotNull Consumer<DisplayWidgetSeriesBuilder> builder) {
        WidgetDisplaySeriesEntity entity = new WidgetDisplaySeriesEntity();
        entity.setName(name);
        series.add(entity);
        DisplaySeriesBuilderImpl seriesBuilder = new DisplaySeriesBuilderImpl(entity);
        builder.accept(seriesBuilder);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setSingleLinePos(@Nullable Integer value) {
        widget.setSingleLinePos(value);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setSingleLineColor(@Nullable String value) {
        widget.setSingleLineColor(value);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setSingleLineWidth(@Nullable Integer value) {
        widget.setSingleLineWidth(value);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setShowDynamicLine(@Nullable Boolean value) {
        widget.setShowDynamicLine(value);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setDynamicLineColor(@Nullable String value) {
        widget.setDynamicLineColor(value);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setDynamicLineWidth(@Nullable Integer value) {
        widget.setDynamicLineWidth(value);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setPointBorderColor(String value) {
        widget.setPointBorderColor(value);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setShowChartFullScreenButton(boolean value) {
        widget.setShowChartFullScreenButton(value);
        return this;
    }

    /*@Override
    public @NotNull DisplayWidgetBuilder setFetchDataFromServerInterval(int value) {
        widget.setFetchDataFromServerInterval(value);
        return this;
    }*/

    @Override
    public @NotNull DisplayWidgetBuilder setLayout(@Nullable String value) {
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
    public @NotNull DisplayWidgetSeriesBuilder setValueDataSource(String value) {
        series.setValueDataSource(value);
        return this;
    }

    @Override
    public WidgetDisplaySeriesEntity getWidget() {
        return series;
    }

    @Override
    public @NotNull DisplayWidgetSeriesBuilder setStyle(String... styles) {
        series.setStyle(String.join(LIST_DELIMITER, styles));
        return this;
    }
}
