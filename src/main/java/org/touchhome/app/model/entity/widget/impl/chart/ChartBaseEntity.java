package org.touchhome.app.model.entity.widget.impl.chart;

import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.app.model.entity.widget.WidgetGroup;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import javax.persistence.Entity;

@Entity
public abstract class ChartBaseEntity<T extends WidgetBaseEntityAndSeries, S extends WidgetSeriesEntity<T>>
        extends WidgetBaseEntityAndSeries<T, S> {

    @Override
    public WidgetGroup getGroup() {
        return WidgetGroup.Chart;
    }

    @Override
    @UIField(order = 10)
    public String getTitle() {
        return super.getName();
    }

    public void setTitle(String value) {
        super.setName(value);
    }

    @UIField(order = 70)
    @UIFieldGroup("Legend")
    public Boolean getLegendShow() {
        return getJsonData("ls", Boolean.TRUE);
    }

    public T setLegendShow(Boolean value) {
        setJsonData("ls", value);
        return (T) this;
    }

    @UIField(order = 71)
    @UIFieldGroup("Legend")
    public LegendPosition getLegendPosition() {
        return getJsonDataEnum("lp", LegendPosition.top);
    }

    public T setLegendPosition(LegendPosition value) {
        setJsonData("lp", value);
        return (T) this;
    }

    @UIField(order = 72)
    @UIFieldGroup("Legend")
    public LegendAlign getLegendAlign() {
        return getJsonDataEnum("la", LegendAlign.center);
    }

    public T setLegendAlign(LegendAlign value) {
        setJsonData("la", value);
        return (T) this;
    }

    @UIField(order = 80)
    @UIFieldGroup("Chart axis")
    public Boolean getShowAxisX() {
        return getJsonData("showAxisX", Boolean.TRUE);
    }

    public T setShowAxisX(Boolean value) {
        setJsonData("showAxisX", value);
        return (T) this;
    }

    @UIField(order = 81)
    @UIFieldGroup("Chart axis")
    public Boolean getShowAxisY() {
        return getJsonData("showAxisY", Boolean.TRUE);
    }

    public T setShowAxisY(Boolean value) {
        setJsonData("showAxisY", value);
        return (T) this;
    }

    @UIField(order = 84)
    @UIFieldGroup("Chart axis")
    public String getAxisLabelX() {
        return getJsonData("axisLabelX");
    }

    public T setAxisLabelX(String value) {
        setJsonData("axisLabelX", value);
        return (T) this;
    }

    @UIField(order = 85)
    @UIFieldGroup("Chart axis")
    public String getAxisLabelY() {
        return getJsonData("axisLabelY");
    }

    public T setAxisLabelY(String value) {
        setJsonData("axisLabelY", value);
        return (T) this;
    }

    @UIField(order = 1)
    @UIFieldGroup("Data labels")
    public boolean getShowDataLabels() {
        return getJsonData("sdl", true);
    }

    public void setShowDataLabels(boolean value) {
        setJsonData("sdl", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("Data labels")
    @UIFieldColorPicker
    public String getDataLabelsColor() {
        return getJsonData("dlc", "#ADB5BD");
    }

    public void setDataLabelsColor(String value) {
        setJsonData("dlc", value);
    }

    @UIField(order = 100)
    public Boolean getAnimations() {
        return getJsonData("am", Boolean.FALSE);
    }

    public T setAnimations(Boolean value) {
        setJsonData("am", value);
        return (T) this;
    }

    public enum LegendPosition {
        top,
        right,
        bottom,
        left
    }

    public enum LegendAlign {
        start,
        center,
        end
    }
}
