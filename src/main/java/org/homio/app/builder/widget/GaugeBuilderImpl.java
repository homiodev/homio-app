package org.homio.app.builder.widget;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.ContextWidget;
import org.homio.api.ContextWidget.GaugeWidgetBuilder;
import org.homio.api.ContextWidget.GaugeWidgetSeriesBuilder;
import org.homio.app.builder.widget.hasBuilder.HasIconColorThresholdBuilder;
import org.homio.app.builder.widget.hasBuilder.HasMarginBuilder;
import org.homio.app.builder.widget.hasBuilder.HasNameBuilder;
import org.homio.app.builder.widget.hasBuilder.HasValueConverterBuilder;
import org.homio.app.builder.widget.hasBuilder.HasValueTemplateBuilder;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.impl.gauge.WidgetGaugeEntity;
import org.homio.app.model.entity.widget.impl.gauge.WidgetGaugeSeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GaugeBuilderImpl extends WidgetBaseBuilderImpl<GaugeWidgetBuilder, WidgetGaugeEntity>
        implements GaugeWidgetBuilder,
        HasValueConverterBuilder<WidgetGaugeEntity, GaugeWidgetBuilder>,
        HasMarginBuilder<WidgetGaugeEntity, GaugeWidgetBuilder>,
        HasNameBuilder<WidgetGaugeEntity, GaugeWidgetBuilder> {

    @Getter
    private final List<WidgetGaugeSeriesEntity> series = new ArrayList<>();

    GaugeBuilderImpl(WidgetGaugeEntity widget, ContextImpl context) {
        super(widget, context);
    }

    @Override
    public @NotNull GaugeWidgetBuilder setSetValuePrecision(int value) {
        widget.setValuePrecision(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setUpdateOnMove(boolean value) {
        widget.setUpdateOnMove(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setAnimateDuration(int value) {
        widget.setAnimateDuration(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setDisplayType(ContextWidget.GaugeDisplayType value) {
        widget.setDisplayType(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setThick(int value) {
        widget.setThick(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setCapType(ContextWidget.GaugeCapType value) {
        widget.setGaugeCapType(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setGaugeForegroundColor(@NotNull String value) {
        widget.setGaugeForeground(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setGaugeBackgroundColor(@NotNull String value) {
        widget.setGaugeBackground(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setShowBackgroundGradient(boolean value) {
        widget.setShowGradient(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setDotBorderWidth(int value) {
        widget.setDotBorderWidth(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setDotRadiusWidth(int value) {
        widget.setDotRadiusWidth(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setDotBorderColor(@NotNull String value) {
        widget.setDotBorderColor(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setDotColor(@NotNull String value) {
        widget.setDotColor(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setForegroundAsSegments(boolean value) {
        widget.setDrawForegroundAsSegments(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setBackgroundAsSegments(boolean value) {
        widget.setDrawBackgroundAsSegments(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setSegmentLength(int value) {
        widget.setSegmentLength(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setSegmentGap(int value) {
        widget.setSegmentGap(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setDrawNeedle(boolean value) {
        widget.setDrawNeedle(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setNeedleColor(@NotNull String value) {
        widget.setNeedleColor(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setSecondDotWidth(int value) {
        widget.setSecondDotRadiusWidth(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setSecondDotColor(@NotNull String value) {
        widget.setSecondDotColor(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setSecondDotDataSource(@Nullable String value) {
        widget.setSecondValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder addSeries(@Nullable String name, @NotNull Consumer<GaugeWidgetSeriesBuilder> builder) {
        WidgetGaugeSeriesEntity entity = new WidgetGaugeSeriesEntity();
        entity.setName(name);
        series.add(entity);
        GaugeSeriesBuilderImpl seriesBuilder = new GaugeSeriesBuilderImpl(entity);
        builder.accept(seriesBuilder);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setSetValueDataSource(@Nullable String value) {
        widget.setSetValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setValueDataSource(@Nullable String value) {
        widget.setValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setMin(@Nullable Integer value) {
        widget.setMin(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetBuilder setMax(@Nullable Integer value) {
        widget.setMax(value);
        return this;
    }
}

@RequiredArgsConstructor
class GaugeSeriesBuilderImpl implements GaugeWidgetSeriesBuilder,
        HasValueTemplateBuilder<WidgetGaugeSeriesEntity, GaugeWidgetSeriesBuilder>,
        HasIconColorThresholdBuilder<WidgetGaugeSeriesEntity, GaugeWidgetSeriesBuilder> {

    private final WidgetGaugeSeriesEntity series;

    @Override
    public @NotNull GaugeWidgetSeriesBuilder setValueDataSource(String value) {
        series.setValueDataSource(value);
        return this;
    }

    @Override
    public WidgetGaugeSeriesEntity getWidget() {
        return series;
    }

    @Override
    public @NotNull GaugeWidgetSeriesBuilder setUseGaugeValue(boolean value) {
        series.setUseGaugeValue(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetSeriesBuilder setHorizontalValuePosition(int value) {
        series.setShift(value);
        return this;
    }

    @Override
    public @NotNull GaugeWidgetSeriesBuilder setVerticalValuePosition(int value) {
        series.setPosition(value);
        return this;
    }
}
