package org.homio.app.builder.widget;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.ContextWidget;
import org.homio.api.ContextWidget.GaugeCustomValueWidgetSeriesBuilder;
import org.homio.api.ContextWidget.GaugeLineWidgetSeriesBuilder;
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

import static org.homio.api.ContextWidget.GaugeCapType;
import static org.homio.api.ContextWidget.GaugeSeriesType;
import static org.homio.api.ContextWidget.GaugeSeriesType.CustomValue;
import static org.homio.api.ContextWidget.GaugeSeriesType.GaugeValue;
import static org.homio.api.ContextWidget.GaugeSeriesType.Line;

@Getter
public class GaugeBuilderImpl extends WidgetBaseBuilderImpl<GaugeWidgetBuilder, WidgetGaugeEntity>
  implements GaugeWidgetBuilder,
  HasValueConverterBuilder<WidgetGaugeEntity, GaugeWidgetBuilder>,
  HasMarginBuilder<WidgetGaugeEntity, GaugeWidgetBuilder>,
  HasNameBuilder<WidgetGaugeEntity, GaugeWidgetBuilder> {

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
  public @NotNull GaugeWidgetBuilder setDisplayType(ContextWidget.@NotNull GaugeDisplayType value) {
    widget.setDisplayType(value);
    return this;
  }

  @Override
  public @NotNull GaugeWidgetBuilder setThick(int value) {
    widget.setThick(value);
    return this;
  }

  @Override
  public @NotNull GaugeWidgetBuilder setCapType(@NotNull GaugeCapType value) {
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
  public @NotNull GaugeWidgetBuilder addSeriesGaugeValue(@Nullable String name, @NotNull Consumer<GaugeWidgetSeriesBuilder> builder) {
    WidgetGaugeSeriesEntity entity = addSeries(name, GaugeValue);
    builder.accept(new GaugeSeriesBuilderImpl(entity));
    return this;
  }

  @Override
  public @NotNull GaugeWidgetBuilder addSeriesCustomValue(@Nullable String name, @NotNull Consumer<GaugeCustomValueWidgetSeriesBuilder> builder) {
    WidgetGaugeSeriesEntity entity = addSeries(name, CustomValue);
    builder.accept(new GaugeCustomValueSeriesBuilderImpl(entity));
    return this;
  }

  @Override
  public @NotNull GaugeWidgetBuilder addSeriesLine(@Nullable String name, @NotNull Consumer<GaugeLineWidgetSeriesBuilder> builder) {
    WidgetGaugeSeriesEntity entity = addSeries(name, Line);
    builder.accept(new GaugeLineSeriesBuilderImpl(entity));
    return this;
  }

  private @NotNull WidgetGaugeSeriesEntity addSeries(@Nullable String name, @NotNull GaugeSeriesType gaugeSeriesType) {
    WidgetGaugeSeriesEntity entity = new WidgetGaugeSeriesEntity();
    entity.setSeriesType(gaugeSeriesType);
    entity.setName(name);
    series.add(entity);
    return entity;
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
class GaugeSeriesBuilderImpl implements
  GaugeWidgetSeriesBuilder,
  HasValueConverterBuilder<WidgetGaugeSeriesEntity, GaugeWidgetSeriesBuilder>,
  HasValueTemplateBuilder<WidgetGaugeSeriesEntity, GaugeWidgetSeriesBuilder>,
  HasIconColorThresholdBuilder<WidgetGaugeSeriesEntity, GaugeWidgetSeriesBuilder> {

  private final WidgetGaugeSeriesEntity series;

  @Override
  public WidgetGaugeSeriesEntity getWidget() {
    return series;
  }

  @Override
  public @NotNull GaugeWidgetSeriesBuilder setHorizontalValuePosition(int value) {
    series.setHorizontalPosition(value);
    return this;
  }

  @Override
  public @NotNull GaugeWidgetSeriesBuilder setVerticalValuePosition(int value) {
    series.setVerticalPosition(value);
    return this;
  }
}

@RequiredArgsConstructor
class GaugeCustomValueSeriesBuilderImpl implements
  GaugeCustomValueWidgetSeriesBuilder,
  HasValueConverterBuilder<WidgetGaugeSeriesEntity, GaugeCustomValueWidgetSeriesBuilder>,
  HasValueTemplateBuilder<WidgetGaugeSeriesEntity, GaugeCustomValueWidgetSeriesBuilder>,
  HasIconColorThresholdBuilder<WidgetGaugeSeriesEntity, GaugeCustomValueWidgetSeriesBuilder> {

  private final WidgetGaugeSeriesEntity series;

  @Override
  public WidgetGaugeSeriesEntity getWidget() {
    return series;
  }

  @Override
  public @NotNull GaugeCustomValueWidgetSeriesBuilder setHorizontalValuePosition(int value) {
    series.setHorizontalPosition(value);
    return this;
  }

  @Override
  public @NotNull GaugeCustomValueWidgetSeriesBuilder setVerticalValuePosition(int value) {
    series.setVerticalPosition(value);
    return this;
  }

  @Override
  public @NotNull GaugeCustomValueWidgetSeriesBuilder setValueDataSource(@Nullable String value) {
    series.setValueDataSource(value);
    return this;
  }
}

@RequiredArgsConstructor
class GaugeLineSeriesBuilderImpl implements
  GaugeLineWidgetSeriesBuilder {

  private final WidgetGaugeSeriesEntity series;

  @Override
  public @NotNull GaugeLineWidgetSeriesBuilder setHorizontalValuePosition(int value) {
    series.setHorizontalPosition(value);
    return this;
  }

  @Override
  public @NotNull GaugeLineWidgetSeriesBuilder setVerticalValuePosition(int value) {
    series.setVerticalPosition(value);
    return this;
  }

  @Override
  public @NotNull GaugeLineWidgetSeriesBuilder setLineColor(String value) {
    series.setLineColor(value);
    return this;
  }

  @Override
  public @NotNull GaugeLineWidgetSeriesBuilder setLineThickness(int value) {
    series.setLineThickness(value);
    return this;
  }

  @Override
  public @NotNull GaugeLineWidgetSeriesBuilder setLineWidth(int value) {
    series.setLineWidth(value);
    return this;
  }

  @Override
  public @NotNull GaugeLineWidgetSeriesBuilder setLineType(GaugeLineType lineType) {
    series.setLineType(lineType);
    return this;
  }

  @Override
  public @NotNull GaugeLineWidgetSeriesBuilder setLineBorderRadius(int value) {
    series.setLineBorderRadius(value);
    return this;
  }

  @Override
  public @NotNull GaugeLineWidgetSeriesBuilder setAsVerticalLine(boolean verticalLine) {
    series.setVerticalLine(verticalLine);
    return this;
  }
}