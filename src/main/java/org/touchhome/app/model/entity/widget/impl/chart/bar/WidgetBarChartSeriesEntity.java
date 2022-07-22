package org.touchhome.app.model.entity.widget.impl.chart.bar;

import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

import javax.persistence.Entity;

@Entity
public class WidgetBarChartSeriesEntity extends WidgetSeriesEntity<WidgetBarChartEntity> {

    public static final String PREFIX = "wgsbcs_";

    @UIField(order = 14, required = true)
    @UIFieldEntityByClassSelection(HasAggregateValueFromSeries.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    @UIField(order = 15)
    public AggregationType getAggregationType() {
        return getJsonDataEnum("aggr", AggregationType.Last);
    }

    public WidgetBarChartSeriesEntity setAggregationType(AggregationType value) {
        setJsonData("aggr", value);
        return this;
    }

    @UIField(order = 50, type = UIFieldType.ColorPicker)
    @UIFieldGroup("Bar customization")
    public String getColor() {
        return getJsonData("bc", "#FFFFFF");
    }

    public WidgetBarChartSeriesEntity setColor(String value) {
        setJsonData("bc", value);
        return this;
    }

    @UIField(order = 51)
    @UIFieldSlider(min = 1, max = 254, step = 5)
    @UIFieldGroup("Bar customization")
    public int getColorOpacity() {
        return getJsonData("bco", 120);
    }

    public void setColorOpacity(int value) {
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
