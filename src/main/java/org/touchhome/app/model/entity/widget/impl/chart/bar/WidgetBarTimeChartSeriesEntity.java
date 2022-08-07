package org.touchhome.app.model.entity.widget.impl.chart.bar;

import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.HasTimeValueSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;

@Entity
public class WidgetBarTimeChartSeriesEntity extends WidgetSeriesEntity<WidgetBarTimeChartEntity> {

    public static final String PREFIX = "wgsbtcs_";

    @UIField(order = 14, required = true)
    @UIFieldEntityByClassSelection(HasTimeValueSeries.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 15)
    public AggregationType getAggregationType() {
        return getJsonDataEnum("aggr", AggregationType.Last);
    }

    public WidgetBarTimeChartSeriesEntity setAggregationType(AggregationType value) {
        setJsonData("aggr", value);
        return this;
    }

    @UIField(order = 50)
    @UIFieldGroup("Bar")
    @UIFieldColorPicker
    public String getColor() {
        return getJsonData("bc", "#FFFFFF");
    }

    public WidgetBarTimeChartSeriesEntity setColor(String value) {
        setJsonData("bc", value);
        return this;
    }

    @UIField(order = 51)
    @UIFieldSlider(min = 1, max = 254, step = 5)
    @UIFieldGroup("Bar")
    public int getChartColorOpacity() {
        return getJsonData("bco", 120);
    }

    public void setChartColorOpacity(int value) {
        setJsonData("bco", value);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("bc")) {
            setColor(UI.Color.random());
        }
    }
}
