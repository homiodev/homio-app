package org.homio.app.builder.widget;

import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;

import org.homio.api.EntityContextWidget.ColorWidgetBuilder;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.widget.impl.color.WidgetColorEntity;
import org.jetbrains.annotations.NotNull;

public final class ColorWidgetBuilderImpl extends WidgetBaseBuilderImpl<ColorWidgetBuilder, WidgetColorEntity>
        implements ColorWidgetBuilder {

    public ColorWidgetBuilderImpl(WidgetColorEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setLayout(String layout) {
        widget.setLayout(layout);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setColors(String... colors) {
        widget.setColors(String.join(LIST_DELIMITER, colors));
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setCircleSize(int value) {
        widget.setCircleSize(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setIcon(String value) {
        widget.setWidgetIcon(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setIconColor(String value) {
        widget.setWidgetIconColor(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setCircleSpacing(int value) {
        widget.setCircleSpacing(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setColorValueDataSource(String value) {
        widget.setColorValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setColorSetValueDataSource(String value) {
        widget.setColorSetValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setBrightnessValueDataSource(String value) {
        widget.setBrightnessValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setBrightnessSetValueDataSource(String value) {
        widget.setBrightnessSetValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setBrightnessMinValue(int value) {
        widget.setBrightnessMinValue(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setBrightnessMaxValue(int value) {
        widget.setBrightnessMaxValue(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setColorTemperatureValueDataSource(String value) {
        widget.setColorTemperatureValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setColorTemperatureSetValueDataSource(String value) {
        widget.setColorTemperatureSetValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setColorTemperatureMinValue(int value) {
        widget.setColorTemperatureMinValue(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setColorTemperatureMaxValue(int value) {
        widget.setColorTemperatureMaxValue(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setOnOffValueDataSource(String value) {
        widget.setOnOffValueDataSource(value);
        return this;
    }

    @Override
    public @NotNull ColorWidgetBuilderImpl setOnOffSetValueDataSource(String value) {
        widget.setOnOffSetValueDataSource(value);
        return this;
    }
}
