package org.homio.app.builder.widget;

import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.widget.impl.color.WidgetColorEntity;
import org.homio.bundle.api.EntityContextWidget.ColorWidgetBuilder;

public final class ColorWidgetBuilderImpl extends WidgetBaseBuilderImpl<ColorWidgetBuilder, WidgetColorEntity>
    implements ColorWidgetBuilder {

    public ColorWidgetBuilderImpl(WidgetColorEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public ColorWidgetBuilderImpl setLayout(String layout) {
        widget.setLayout(layout);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setColors(String... colors) {
        widget.setColors(String.join("~~~", colors));
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setCircleSize(int value) {
        widget.setCircleSize(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setIcon(String value) {
        widget.setIcon(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setIconColor(String value) {
        widget.setIconColor(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setCircleSpacing(int value) {
        widget.setCircleSpacing(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setColorValueDataSource(String value) {
        widget.setColorValueDataSource(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setColorSetValueDataSource(String value) {
        widget.setColorSetValueDataSource(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setBrightnessValueDataSource(String value) {
        widget.setBrightnessValueDataSource(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setBrightnessSetValueDataSource(String value) {
        widget.setBrightnessSetValueDataSource(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setBrightnessMinValue(int value) {
        widget.setBrightnessMinValue(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setBrightnessMaxValue(int value) {
        widget.setBrightnessMaxValue(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setColorTemperatureValueDataSource(String value) {
        widget.setColorTemperatureValueDataSource(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setColorTemperatureSetValueDataSource(String value) {
        widget.setColorTemperatureSetValueDataSource(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setColorTemperatureMinValue(int value) {
        widget.setColorTemperatureMinValue(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setColorTemperatureMaxValue(int value) {
        widget.setColorTemperatureMaxValue(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setOnOffValueDataSource(String value) {
        widget.setOnOffValueDataSource(value);
        return this;
    }

    @Override
    public ColorWidgetBuilderImpl setOnOffSetValueDataSource(String value) {
        widget.setOnOffSetValueDataSource(value);
        return this;
    }
}
