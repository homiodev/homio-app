package org.touchhome.app.model.entity.widget.impl.chart;

import org.touchhome.bundle.api.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.Entity;

@Entity
public abstract class ChartBaseEntity<T extends WidgetBaseEntityAndSeries, S extends WidgetSeriesEntity<T>>
        extends WidgetBaseEntityAndSeries<T, S> {

    @UIField(order = 20)
    public UpdateInterval getUpdateInterval() {
        return getJsonDataEnum("updateInterval", UpdateInterval.Never);
    }

    public T setUpdateInterval(UpdateInterval value) {
        setJsonData("updateInterval", value);
        return (T) this;
    }

    @UIField(order = 21)
    public String getLegendTitle() {
        return getJsonData("legendTitle", "Legend");
    }

    public T setLegendTitle(String value) {
        setJsonData("legendTitle", value);
        return (T) this;
    }

    @UIField(order = 22)
    public LegendPosition getLegendPosition() {
        return getJsonDataEnum("legendPosition", LegendPosition.right);
    }

    public T setLegendPosition(LegendPosition value) {
        setJsonData("legendPosition", value);
        return (T) this;
    }

    @UIField(order = 23, showInContextMenu = true)
    public Boolean getShowLegend() {
        return getJsonData("showLegend", Boolean.TRUE);
    }

    public T setShowLegend(Boolean value) {
        setJsonData("showLegend", value);
        return (T) this;
    }

    @UIField(order = 24)
    public Boolean getTooltipDisabled() {
        return getJsonData("tooltipDisabled", Boolean.FALSE);
    }

    public T setTooltipDisabled(Boolean value) {
        setJsonData("tooltipDisabled", value);
        return (T) this;
    }

    @UIField(order = 25)
    public Boolean getAnimations() {
        return getJsonData("animations", Boolean.FALSE);
    }

    public T setAnimations(Boolean value) {
        setJsonData("animations", value);
        return (T) this;
    }

    public enum LegendPosition {
        right,
        below
    }
}
