package org.touchhome.app.builder.widget;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.touchhome.app.model.entity.widget.impl.display.WidgetDisplaySeriesEntity;
import org.touchhome.bundle.api.EntityContextWidget;
import org.touchhome.bundle.api.EntityContextWidget.DisplayWidgetBuilder;
import org.touchhome.bundle.api.EntityContextWidget.DisplayWidgetSeriesBuilder;
import org.touchhome.bundle.api.EntityContextWidget.Fill;
import org.touchhome.bundle.api.EntityContextWidget.IconColorBuilder;
import org.touchhome.bundle.api.EntityContextWidget.PointStyle;
import org.touchhome.bundle.api.EntityContextWidget.Stepped;
import org.touchhome.bundle.api.entity.widget.AggregationType;

public class DisplayBuilderImpl extends WidgetBaseBuilderImpl<DisplayWidgetBuilder, WidgetDisplayEntity>
    implements DisplayWidgetBuilder {

    @Getter
    private final List<WidgetDisplaySeriesEntity> series = new ArrayList<>();

    DisplayBuilderImpl(WidgetDisplayEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public DisplayWidgetBuilder setLayout(String value) {
        widget.setLayout(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setShowName(boolean value) {
        widget.setShowName(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setNameColor(String value) {
        widget.setNameColor(value);
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
        series.add(entity);
        DisplaySeriesBuilderImpl seriesBuilder = new DisplaySeriesBuilderImpl(entity);
        builder.accept(seriesBuilder);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setChartDataSource(String value) {
        widget.setChartDataSource(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setChartAggregationType(AggregationType value) {
        widget.setChartAggregationType(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setFinalChartValueConverter(String value) {
        widget.setFinalChartValueConverter(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setChartColor(String value) {
        widget.setChartColor(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setChartColorOpacity(int value) {
        widget.setChartColorOpacity(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setChartLabel(String value) {
        widget.setChartLabel(value);
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
    public DisplayWidgetBuilder setLineBorderWidth(int value) {
        widget.setLineBorderWidth(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setLineFill(Fill value) {
        widget.setLineFill(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setStepped(Stepped value) {
        widget.setStepped(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setTension(int value) {
        widget.setTension(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setPointRadius(double value) {
        widget.setPointRadius(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setPointStyle(PointStyle value) {
        widget.setPointStyle(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setPointBackgroundColor(String value) {
        widget.setPointBackgroundColor(value);
        return this;
    }

    @Override
    public DisplayWidgetBuilder setPointBorderColor(String value) {
        widget.setPointBorderColor(value);
        return this;
    }
}

@RequiredArgsConstructor
class DisplaySeriesBuilderImpl implements DisplayWidgetSeriesBuilder {

    private final WidgetDisplaySeriesEntity series;

    @Override
    public DisplayWidgetSeriesBuilder setIcon(@Nullable String value) {
        series.setIcon(value);
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setIconColor(@Nullable String color, @Nullable Consumer<IconColorBuilder> colorBuilder) {
        if (colorBuilder == null) {
            series.setIconColor(color);
        } else {
            IconColorBuilderImpl builder = new IconColorBuilderImpl(color);
            colorBuilder.accept(builder);
            series.setIconColor(builder.build());
        }
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setName(String value) {
        series.setName(value);
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setShowName(boolean value) {
        series.setShowName(value);
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setNameColor(String value) {
        series.setNameColor(value);
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setValueDataSource(String value) {
        series.setValueDataSource(value);
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setSetValueDataSource(String value) {
        series.setSetValueDataSource(value);
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
    public DisplayWidgetSeriesBuilder setValueConverter(String value) {
        series.setValueConverter(value);
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setValueTemplate(@Nullable String prefix, @Nullable String suffix) {
        series.setValueTemplate(format("%s~~~%s", prefix == null ? "" : prefix, suffix == null ? "" : suffix));
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setValueColor(String value) {
        series.setValueColor(value);
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setNoValueText(String value) {
        series.setNoValueText(value);
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setValueTemplateFontSize(double value) {
        series.setValueTemplateFontSize(value);
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setValueTemplatePrefixFontSize(double value) {
        series.setValueTemplatePrefixFontSize(value);
        return this;
    }

    @Override
    public DisplayWidgetSeriesBuilder setValueTemplateSuffixFontSize(double value) {
        series.setValueTemplateSuffixFontSize(value);
        return this;
    }
}
