package org.touchhome.app.builder.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.impl.toggle.WidgetToggleEntity;
import org.touchhome.app.model.entity.widget.impl.toggle.WidgetToggleSeriesEntity;
import org.touchhome.bundle.api.EntityContextWidget.IconColorBuilder;
import org.touchhome.bundle.api.EntityContextWidget.ToggleType;
import org.touchhome.bundle.api.EntityContextWidget.ToggleWidgetBuilder;
import org.touchhome.bundle.api.EntityContextWidget.ToggleWidgetSeriesBuilder;

public class ToggleBuilderImpl extends WidgetBaseBuilderImpl<ToggleWidgetBuilder, WidgetToggleEntity>
    implements ToggleWidgetBuilder {

    @Getter
    private final List<WidgetToggleSeriesEntity> series = new ArrayList<>();

    ToggleBuilderImpl(WidgetToggleEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public ToggleWidgetBuilder setShowAllButton(Boolean value) {
        widget.setShowAllButton(value);
        return this;
    }

    @Override
    public ToggleWidgetBuilder setDisplayType(ToggleType value) {
        widget.setDisplayType(value);
        return this;
    }

    @Override
    public ToggleWidgetBuilder addSeries(@Nullable String name, @NotNull Consumer<ToggleWidgetSeriesBuilder> builder) {
        WidgetToggleSeriesEntity entity = new WidgetToggleSeriesEntity();
        entity.setName(name);
        series.add(entity);
        ToggleSeriesBuilderImpl seriesBuilder = new ToggleSeriesBuilderImpl(entity);
        builder.accept(seriesBuilder);
        return this;
    }

    @Override
    public ToggleWidgetBuilder setLayout(String value) {
        widget.setLayout(value);
        return this;
    }

    @Override
    public ToggleWidgetBuilder setShowName(boolean value) {
        widget.setShowName(value);
        return this;
    }

    @Override
    public ToggleWidgetBuilder setNameColor(String value) {
        widget.setNameColor(value);
        return this;
    }

    @Override
    public ToggleWidgetBuilder setListenSourceUpdates(@Nullable Boolean value) {
        widget.setListenSourceUpdates(value);
        return this;
    }

    @Override
    public ToggleWidgetBuilder setShowLastUpdateTimer(@Nullable Boolean value) {
        widget.setShowLastUpdateTimer(value);
        return this;
    }
}

@RequiredArgsConstructor
class ToggleSeriesBuilderImpl implements ToggleWidgetSeriesBuilder {

    private final WidgetToggleSeriesEntity series;

    @Override
    public ToggleWidgetSeriesBuilder setColor(String value) {
        series.setColor(value);
        return this;
    }

    @Override
    public ToggleWidgetSeriesBuilder setOnName(String value) {
        series.setOnName(value);
        return this;
    }

    @Override
    public ToggleWidgetSeriesBuilder setOnValues(String... values) {
        series.setOnValues(String.join("~~~", values));
        return this;
    }

    @Override
    public ToggleWidgetSeriesBuilder setOffName(String value) {
        series.setOffName(value);
        return this;
    }

    @Override
    public ToggleWidgetSeriesBuilder setPushToggleOffValue(String value) {
        series.setPushToggleOffValue(value);
        return this;
    }

    @Override
    public ToggleWidgetSeriesBuilder setPushToggleOnValue(String value) {
        series.setPushToggleOnValue(value);
        return this;
    }

    @Override
    public ToggleWidgetSeriesBuilder setIcon(String value) {
        series.setIcon(value);
        return this;
    }

    @Override
    public ToggleWidgetSeriesBuilder setIconColor(Consumer<IconColorBuilder> colorBuilder) {
        IconColorBuilderImpl builder = new IconColorBuilderImpl();
        colorBuilder.accept(builder);
        series.setIconColor(builder.build());
        return this;
    }

    @Override
    public ToggleWidgetSeriesBuilder setName(String value) {
        series.setName(value);
        return this;
    }

    @Override
    public ToggleWidgetSeriesBuilder setShowName(boolean value) {
        series.setShowName(value);
        return this;
    }

    @Override
    public ToggleWidgetSeriesBuilder setNameColor(String value) {
        series.setNameColor(value);
        return this;
    }

    @Override
    public ToggleWidgetSeriesBuilder setValueDataSource(String value) {
        series.setValueDataSource(value);
        return this;
    }

    @Override
    public ToggleWidgetSeriesBuilder setSetValueDataSource(String value) {
        series.setSetValueDataSource(value);
        return this;
    }
}
