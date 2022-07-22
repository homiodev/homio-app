package org.touchhome.app.model.entity.widget.impl.chart.bar;

import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.bundle.api.entity.widget.WidgetGroup;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

import javax.persistence.Entity;

@Entity
public class WidgetBarChartEntity extends ChartBaseEntity<WidgetBarChartEntity, WidgetBarChartSeriesEntity> {

    public static final String PREFIX = "wgtbc_";

    @UIField(order = 38)
    public BarChartType getDisplayType() {
        return getJsonDataEnum("displayType", BarChartType.Horizontal);
    }

    public WidgetBarChartEntity setDisplayType(BarChartType value) {
        setJsonData("displayType", value);
        return this;
    }

    @UIField(order = 40)
    @UIFieldGroup("Axis")
    public String getAxisLabel() {
        return getJsonData("al", "example");
    }

    public WidgetBarChartEntity setAxisLabel(String value) {
        setJsonData("al", value);
        return this;
    }

    @UIField(order = 52)
    @UIFieldSlider(min = 1, max = 4)
    public int getBorderWidth() {
        return getJsonData("bw", 1);
    }

    public WidgetBarChartEntity setBorderWidth(int value) {
        setJsonData("bw", value);
        return this;
    }

    @Override
    public String getImage() {
        return "fas fa-chart-bar";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public enum BarChartType {
        Horizontal, Vertical
    }
}
