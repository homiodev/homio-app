package org.touchhome.app.model.entity.widget.impl.chart;

import org.touchhome.bundle.api.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.bundle.api.entity.widget.WidgetGroup;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

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

    @UIField(order = 12)
    public TimePeriod getTimePeriod() {
        return getJsonDataEnum("timePeriod", TimePeriod.All);
    }

    public T setTimePeriod(TimePeriod value) {
        setJsonData("timePeriod", value);
        return (T) this;
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
    @UIFieldGroup("Axis")
    public Boolean getShowAxisX() {
        return getJsonData("showAxisX", Boolean.TRUE);
    }

    public T setShowAxisX(Boolean value) {
        setJsonData("showAxisX", value);
        return (T) this;
    }

    @UIField(order = 81)
    @UIFieldGroup("Axis")
    public Boolean getShowAxisY() {
        return getJsonData("showAxisY", Boolean.TRUE);
    }

    public T setShowAxisY(Boolean value) {
        setJsonData("showAxisY", value);
        return (T) this;
    }

    @UIField(order = 82)
    @UIFieldGroup("Axis")
    public Integer getMin() {
        return getJsonData().has("min") ? getJsonData().getInt("min") : null;
    }

    public T setMin(int value) {
        setJsonData("min", value);
        return (T) this;
    }

    @UIField(order = 83)
    @UIFieldGroup("Axis")
    public Integer getMax() {
        return getJsonData().has("max") ? getJsonData().getInt("max") : null;
    }

    public T setMax(int value) {
        setJsonData("max", value);
        return (T) this;
    }

    @UIField(order = 84)
    @UIFieldGroup("Axis")
    public String getAxisLabelX() {
        return getJsonData("axisLabelX");
    }

    public T setAxisLabelX(String value) {
        setJsonData("axisLabelX", value);
        return (T) this;
    }

    @UIField(order = 85)
    @UIFieldGroup("Axis")
    public String getAxisLabelY() {
        return getJsonData("axisLabelY");
    }

    public T setAxisLabelY(String value) {
        setJsonData("axisLabelY", value);
        return (T) this;
    }

    @UIField(order = 100)
    public Boolean getAnimations() {
        return getJsonData("am", Boolean.FALSE);
    }

    public T setAnimations(Boolean value) {
        setJsonData("am", value);
        return (T) this;
    }

    @UIField(order = 120, showInContextMenu = true)
    public Boolean getShowTimeButtons() {
        return getJsonData("stb", Boolean.FALSE);
    }

    public T setShowTimeButtons(Boolean value) {
        setJsonData("stb", value);
        return (T) this;
    }

    @UIField(order = 200)
    @UIFieldGroup("Update")
    public UpdateInterval getReloadDataInterval() {
        return getJsonDataEnum("rdi", UpdateInterval.Never);
    }

    public T setReloadDataInterval(UpdateInterval value) {
        setJsonData("rdi", value);
        return (T) this;
    }

    @UIField(order = 210)
    @UIFieldGroup("Update")
    public Boolean getListenSourceUpdates() {
        return getJsonData("lsu", Boolean.TRUE);
    }

    public T setListenSourceUpdates(Boolean value) {
        setJsonData("lsu", value);
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
