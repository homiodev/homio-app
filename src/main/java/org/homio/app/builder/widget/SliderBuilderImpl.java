package org.homio.app.builder.widget;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.ContextWidget.SliderWidgetBuilder;
import org.homio.api.ContextWidget.SliderWidgetSeriesBuilder;
import org.homio.app.builder.widget.hasBuilder.HasIconColorThresholdBuilder;
import org.homio.app.builder.widget.hasBuilder.HasMarginBuilder;
import org.homio.app.builder.widget.hasBuilder.HasNameBuilder;
import org.homio.app.builder.widget.hasBuilder.HasValueTemplateBuilder;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.impl.slider.WidgetSliderEntity;
import org.homio.app.model.entity.widget.impl.slider.WidgetSliderSeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SliderBuilderImpl extends WidgetBaseBuilderImpl<SliderWidgetBuilder, WidgetSliderEntity>
  implements SliderWidgetBuilder,
  HasMarginBuilder<WidgetSliderEntity, SliderWidgetBuilder>,
  HasNameBuilder<WidgetSliderEntity, SliderWidgetBuilder> {

  @Getter
  private final List<WidgetSliderSeriesEntity> series = new ArrayList<>();

  SliderBuilderImpl(WidgetSliderEntity widget, ContextImpl context) {
    super(widget, context);
  }

  @Override
  public @NotNull SliderWidgetBuilder addSeries(@Nullable String name, @NotNull Consumer<SliderWidgetSeriesBuilder> builder) {
    WidgetSliderSeriesEntity entity = new WidgetSliderSeriesEntity();
    entity.setName(name);
    series.add(entity);
    SliderSeriesBuilderImpl seriesBuilder = new SliderSeriesBuilderImpl(entity);
    builder.accept(seriesBuilder);
    return this;
  }

  @Override
  public @NotNull SliderWidgetBuilder setLayout(String value) {
    widget.setLayout(value);
    return this;
  }
}

@RequiredArgsConstructor
class SliderSeriesBuilderImpl implements SliderWidgetSeriesBuilder,
  HasMarginBuilder<WidgetSliderSeriesEntity, SliderWidgetSeriesBuilder>,
  HasValueTemplateBuilder<WidgetSliderSeriesEntity, SliderWidgetSeriesBuilder>,
  HasIconColorThresholdBuilder<WidgetSliderSeriesEntity, SliderWidgetSeriesBuilder>,
  HasNameBuilder<WidgetSliderSeriesEntity, SliderWidgetSeriesBuilder> {

  private final WidgetSliderSeriesEntity series;

  @Override
  public WidgetSliderSeriesEntity getWidget() {
    return series;
  }

  @Override
  public @NotNull SliderWidgetSeriesBuilder setValueDataSource(String value) {
    series.setValueDataSource(value);
    return this;
  }

  @Override
  public @NotNull SliderWidgetSeriesBuilder setSetValueDataSource(String value) {
    series.setSetValueDataSource(value);
    return this;
  }

  @Override
  public @NotNull SliderWidgetSeriesBuilder setSliderColor(String value) {
    series.setSliderColor(value);
    return this;
  }

  @Override
  public @NotNull SliderWidgetSeriesBuilder setMin(int value) {
    series.setMin(value);
    return this;
  }

  @Override
  public @NotNull SliderWidgetSeriesBuilder setMax(int value) {
    series.setMax(value);
    return this;
  }

  @Override
  public @NotNull SliderWidgetSeriesBuilder setStep(int value) {
    series.setStep(value);
    return this;
  }
}
