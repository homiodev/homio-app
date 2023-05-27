package org.homio.app.builder.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.EntityContextWidget.SliderWidgetBuilder;
import org.homio.api.EntityContextWidget.SliderWidgetSeriesBuilder;
import org.homio.app.builder.widget.hasBuilder.HasIconColorThresholdBuilder;
import org.homio.app.builder.widget.hasBuilder.HasNameBuilder;
import org.homio.app.builder.widget.hasBuilder.HasPaddingBuilder;
import org.homio.app.builder.widget.hasBuilder.HasValueTemplateBuilder;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.widget.impl.slider.WidgetSliderEntity;
import org.homio.app.model.entity.widget.impl.slider.WidgetSliderSeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SliderBuilderImpl extends WidgetBaseBuilderImpl<SliderWidgetBuilder, WidgetSliderEntity>
    implements SliderWidgetBuilder,
    HasPaddingBuilder<WidgetSliderEntity, SliderWidgetBuilder>,
    HasNameBuilder<WidgetSliderEntity, SliderWidgetBuilder> {

    @Getter
    private final List<WidgetSliderSeriesEntity> series = new ArrayList<>();

    SliderBuilderImpl(WidgetSliderEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public SliderWidgetBuilder addSeries(@Nullable String name, @NotNull Consumer<SliderWidgetSeriesBuilder> builder) {
        WidgetSliderSeriesEntity entity = new WidgetSliderSeriesEntity();
        entity.setName(name);
        series.add(entity);
        SliderSeriesBuilderImpl seriesBuilder = new SliderSeriesBuilderImpl(entity);
        builder.accept(seriesBuilder);
        return this;
    }

    @Override
    public SliderWidgetBuilder setLayout(String value) {
        widget.setLayout(value);
        return this;
    }

    @Override
    public SliderWidgetBuilder setListenSourceUpdates(@Nullable Boolean value) {
        widget.setListenSourceUpdates(value);
        return this;
    }

    @Override
    public SliderWidgetBuilder setShowLastUpdateTimer(@Nullable Boolean value) {
        widget.setShowLastUpdateTimer(value);
        return this;
    }
}

@RequiredArgsConstructor
class SliderSeriesBuilderImpl implements SliderWidgetSeriesBuilder,
    HasPaddingBuilder<WidgetSliderSeriesEntity, SliderWidgetSeriesBuilder>,
    HasValueTemplateBuilder<WidgetSliderSeriesEntity, SliderWidgetSeriesBuilder>,
    HasIconColorThresholdBuilder<WidgetSliderSeriesEntity, SliderWidgetSeriesBuilder>,
    HasNameBuilder<WidgetSliderSeriesEntity, SliderWidgetSeriesBuilder> {

    private final WidgetSliderSeriesEntity series;

    @Override
    public WidgetSliderSeriesEntity getWidget() {
        return series;
    }

    @Override
    public SliderWidgetSeriesBuilder setValueDataSource(String value) {
        series.setValueDataSource(value);
        return this;
    }

    @Override
    public SliderWidgetSeriesBuilder setSetValueDataSource(String value) {
        series.setSetValueDataSource(value);
        return this;
    }

    @Override
    public SliderWidgetSeriesBuilder setSliderColor(String value) {
        series.setSliderColor(value);
        return this;
    }

    @Override
    public SliderWidgetSeriesBuilder setMin(int value) {
        series.setMin(value);
        return this;
    }

    @Override
    public SliderWidgetSeriesBuilder setMax(int value) {
        series.setMax(value);
        return this;
    }

    @Override
    public SliderWidgetSeriesBuilder setStep(int value) {
        series.setStep(value);
        return this;
    }

    @Override
    public SliderWidgetSeriesBuilder setTextConverter(String value) {
        series.setTextConverter(value);
        return this;
    }
}
