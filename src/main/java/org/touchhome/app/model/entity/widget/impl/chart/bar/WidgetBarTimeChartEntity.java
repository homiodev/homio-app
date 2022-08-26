package org.touchhome.app.model.entity.widget.impl.chart.bar;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

import javax.persistence.Entity;

@Entity
public class WidgetBarTimeChartEntity
        extends ChartBaseEntity<WidgetBarTimeChartEntity, WidgetBarTimeChartSeriesEntity> {

    public static final String PREFIX = "wgtbtc_";

    @UIField(order = 38)
    public BarChartType getDisplayType() {
        return getJsonDataEnum("displayType", BarChartType.Horizontal);
    }

    public WidgetBarTimeChartEntity setDisplayType(BarChartType value) {
        setJsonData("displayType", value);
        return this;
    }

    @UIField(order = 40)
    @UIFieldGroup("Chart axis")
    public String getAxisLabel() {
        return getJsonData("al", "example");
    }

    public WidgetBarTimeChartEntity setAxisLabel(String value) {
        setJsonData("al", value);
        return this;
    }

    @UIField(order = 52)
    @UIFieldSlider(min = 0, max = 4)
    public int getBorderWidth() {
        return getJsonData("bw", 1);
    }

    public WidgetBarTimeChartEntity setBorderWidth(int value) {
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

    @Override
    @JsonIgnore
    public String getLayout() {
        throw new IllegalStateException("MNC");
    }
}
