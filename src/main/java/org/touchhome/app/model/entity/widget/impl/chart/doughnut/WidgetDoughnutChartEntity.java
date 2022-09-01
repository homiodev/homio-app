package org.touchhome.app.model.entity.widget.impl.chart.doughnut;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.HasTimePeriod;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;

import javax.persistence.Entity;

@Entity
public class WidgetDoughnutChartEntity extends ChartBaseEntity<WidgetDoughnutChartEntity, WidgetDoughnutChartSeriesEntity>
        implements HasSingleValueDataSource, HasTimePeriod {

    public static final String PREFIX = "wgtpc_";

    @UIField(order = 2)
    @UIFieldGroup(value = "Value", order = 1)
    public String getUnit() {
        return getJsonData("unit", "°C");
    }

    public void setUnit(String value) {
        setJsonData("unit", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("Value")
    @UIFieldSlider(min = 8, max = 40)
    public int getValueFontSize() {
        return getJsonData("vfs", 18);
    }

    public void setValueFontSize(String value) {
        setJsonData("vfs", value);
    }

    @UIField(order = 4)
    @UIFieldGroup("Value")
    @UIFieldColorPicker(allowThreshold = true)
    public String getValueColor() {
        return getJsonData("vc", UI.Color.WHITE);
    }

    public void setValueColor(String value) {
        setJsonData("vc", value);
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
    public String getAxisLabelX() {
        throw new IllegalStateException("MNC");
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getAxisLabelY() {
        throw new IllegalStateException("MNC");
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public Boolean getShowAxisX() {
        throw new IllegalStateException("MNC");
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public Boolean getShowAxisY() {
        throw new IllegalStateException("MNC");
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public String getLayout() {
        throw new IllegalStateException("MNC");
    }
}
