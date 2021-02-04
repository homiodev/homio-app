package org.touchhome.app.model.entity.widget.impl.chart.bar;

import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.app.repository.widget.impl.chart.WidgetBarChartSeriesEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

import javax.persistence.Entity;

@Entity
public class WidgetBarChartEntity extends ChartBaseEntity<WidgetBarChartEntity, WidgetBarChartSeriesEntity> {

    public static final String PREFIX = "barw_";

    @UIField(order = 31)
    public Boolean getShowAxisX() {
        return getJsonData("showAxisX", Boolean.TRUE);
    }

    public WidgetBarChartEntity setShowAxisX(Boolean value) {
        setJsonData("showAxisX", value);
        return this;
    }

    @UIField(order = 32)
    public Boolean getShowAxisY() {
        return getJsonData("showAxisY", Boolean.TRUE);
    }

    public WidgetBarChartEntity setShowAxisY(Boolean value) {
        setJsonData("showAxisY", value);
        return this;
    }

    @UIField(order = 33)
    public String getAxisLabelX() {
        return getJsonData("axisLabelX", "Date");
    }

    public WidgetBarChartEntity setAxisLabelX(String value) {
        setJsonData("axisLabelX", value);
        return this;
    }

    @UIField(order = 34)
    public String getAxisLabelY() {
        return getJsonData("axisLabelY", "Value");
    }

    public WidgetBarChartEntity setAxisLabelY(String value) {
        setJsonData("axisLabelY", value);
        return this;
    }

    @UIField(order = 35)
    public Integer getMin() {
        return getJsonData("min", 0);
    }

    public WidgetBarChartEntity setMin(Integer value) {
        setJsonData("min", value);
        return this;
    }

    @UIField(order = 36)
    public Integer getMax() {
        return getJsonData("max", 100);
    }

    public WidgetBarChartEntity setMax(Integer value) {
        setJsonData("max", value);
        return this;
    }

    @UIField(order = 37)
    public Boolean getNoBarWhenZero() {
        return getJsonData("noBarWhenZero", Boolean.TRUE);
    }

    public WidgetBarChartEntity setNoBarWhenZero(Boolean value) {
        setJsonData("noBarWhenZero", value);
        return this;
    }

    @UIField(order = 38)
    public BarChartType getDisplayType() {
        return getJsonDataEnum("displayType", BarChartType.Horizontal);
    }

    public WidgetBarChartEntity setDisplayType(BarChartType value) {
        setJsonData("displayType", value);
        return this;
    }

    @UIField(order = 39)
    public Boolean getShowDataLabel() {
        return getJsonData("showDataLabel", Boolean.FALSE);
    }

    public WidgetBarChartEntity setShowDataLabel(Boolean value) {
        setJsonData("showDataLabel", value);
        return this;
    }

    @UIField(order = 40)
    public Boolean getRoundEdges() {
        return getJsonData("roundEdges", Boolean.TRUE);
    }

    public WidgetBarChartEntity setRoundEdges(Boolean value) {
        setJsonData("roundEdges", value);
        return this;
    }

    @UIField(order = 41)
    @UIFieldSlider(min = 0, max = 20)
    public Integer getBarPadding() {
        return getJsonData("barPadding", 0);
    }

    public WidgetBarChartEntity setBarPadding(Integer value) {
        setJsonData("barPadding", value);
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
