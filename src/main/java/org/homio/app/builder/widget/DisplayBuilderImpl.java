package org.homio.app.builder.widget;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.EntityContextWidget;
import org.homio.api.EntityContextWidget.DisplayWidgetBuilder;
import org.homio.api.EntityContextWidget.DisplayWidgetSeriesBuilder;
import org.homio.app.builder.widget.hasBuilder.HasActionOnClickBuilder;
import org.homio.app.builder.widget.hasBuilder.HasChartDataSourceBuilder;
import org.homio.app.builder.widget.hasBuilder.HasChartTimePeriodBuilder;
import org.homio.app.builder.widget.hasBuilder.HasHorizontalLineBuilder;
import org.homio.app.builder.widget.hasBuilder.HasIconColorThresholdBuilder;
import org.homio.app.builder.widget.hasBuilder.HasLineChartBehaviourBuilder;
import org.homio.app.builder.widget.hasBuilder.HasMinMaxChartValueBuilder;
import org.homio.app.builder.widget.hasBuilder.HasNameBuilder;
import org.homio.app.builder.widget.hasBuilder.HasPaddingBuilder;
import org.homio.app.builder.widget.hasBuilder.HasValueConverterBuilder;
import org.homio.app.builder.widget.hasBuilder.HasValueTemplateBuilder;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.homio.app.model.entity.widget.impl.display.WidgetDisplaySeriesEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    public @NotNull DisplayWidgetBuilder setListenSourceUpdates(@Nullable Boolean value) {
        widget.setListenSourceUpdates(value);
        return this;
    }

    @Override
    public @NotNull DisplayWidgetBuilder setShowLastUpdateTimer(@Nullable Boolean value) {
        widget.setShowLastUpdateTimer(value);
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
    public @NotNull DisplayWidgetBuilder setChartType(@NotNull EntityContextWidget.ChartType value) {
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

    @Override
    public @NotNull DisplayWidgetBuilder setFetchDataFromServerInterval(int value) {
        widget.setFetchDataFromServerInterval(value);
        return this;
    }

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
        series.setStyle(String.join("~~~", styles));
        return this;
    }
}
