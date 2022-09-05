package org.touchhome.app.model.entity.widget.impl.chart.pie;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

import javax.persistence.Entity;

@Entity
public class WidgetPieChartEntity extends ChartBaseEntity<WidgetPieChartEntity, WidgetPieChartSeriesEntity> {

    public static final String PREFIX = "wgtpc_";

    @UIField(order = 52)
    @UIFieldSlider(min = 1, max = 4)
    public int getBorderWidth() {
        return getJsonData("bw", 1);
    }

    public WidgetPieChartEntity setBorderWidth(int value) {
        setJsonData("bw", value);
        return this;
    }

    @Override
    public String getImage() {
        return "fas fa-chart-pie";
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
