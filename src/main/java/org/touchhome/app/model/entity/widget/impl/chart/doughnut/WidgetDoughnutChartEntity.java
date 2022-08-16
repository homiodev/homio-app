package org.touchhome.app.model.entity.widget.impl.chart.doughnut;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

import javax.persistence.Entity;

@Entity
public class WidgetDoughnutChartEntity extends ChartBaseEntity<WidgetDoughnutChartEntity, WidgetDoughnutChartSeriesEntity>
        implements HasSingleValueDataSource<WidgetDoughnutChartEntity> {

    public static final String PREFIX = "wgtpc_";

    @UIField(order = 2)
    @UIFieldGroup(value = "Value", order = 1)
    public String getUnit() {
        return getJsonData("unit", "°C");
    }

    @UIField(order = 3)
    @UIFieldGroup("Value")
    @UIFieldSlider(min = 14, max = 40)
    public int getValueFontSize() {
        return getJsonData("vfs", 28);
    }

    @UIField(order = 52)
    @UIFieldSlider(min = 1, max = 4)
    public int getBorderWidth() {
        return getJsonData("bw", 1);
    }

    public WidgetDoughnutChartEntity setBorderWidth(int value) {
        setJsonData("bw", value);
        return this;
    }

    @Override
    public String getImage() {
        return "fas fa-circle-dot";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public Integer getMin() {
        return 0;
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public Integer getMax() {
        return 0;
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getAxisLabelX() {
        return null;
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getAxisLabelY() {
        return null;
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public Boolean getShowAxisX() {
        return false;
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public Boolean getShowAxisY() {
        return false;
    }
}
