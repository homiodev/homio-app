package org.touchhome.app.model.entity.widget.impl.chart.line;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.app.model.entity.widget.impl.chart.LineInterpolation;
import org.touchhome.bundle.api.EntityContextWidget;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.Entity;

@Getter
@Setter
@Entity
public class WidgetLineChartEntity extends ChartBaseEntity<WidgetLineChartEntity, WidgetLineChartSeriesEntity> {

    @UIField(order = 12)
    public TimePeriod getTimePeriod() {
        return getJsonDataEnum("timePeriod", TimePeriod.All);
    }

    public WidgetLineChartEntity setTimePeriod(TimePeriod value) {
        setJsonData("timePeriod", value);
        return this;
    }

    @UIField(order = 33, showInContextMenu = true)
    public Boolean getShowButtons() {
        return getJsonData("showButtons", Boolean.FALSE);
    }

    public WidgetLineChartEntity setShowButtons(Boolean value) {
        setJsonData("showButtons", value);
        return this;
    }

    @UIField(order = 31)
    public Boolean getShowAxisX() {
        return getJsonData("showAxisX", Boolean.TRUE);
    }

    public WidgetLineChartEntity setShowAxisX(Boolean value) {
        setJsonData("showAxisX", value);
        return this;
    }

    @UIField(order = 32)
    public Boolean getShowAxisY() {
        return getJsonData("showAxisY", Boolean.TRUE);
    }

    public WidgetLineChartEntity setShowAxisY(Boolean value) {
        setJsonData("showAxisY", value);
        return this;
    }

    @UIField(order = 33)
    public String getAxisLabelX() {
        return getJsonData("axisLabelX", "Date");
    }

    public WidgetLineChartEntity setAxisLabelX(String value) {
        setJsonData("axisLabelX", value);
        return this;
    }

    @UIField(order = 34)
    public String getAxisLabelY() {
        return getJsonData("axisLabelY", "Value");
    }

    public WidgetLineChartEntity setAxisLabelY(String value) {
        setJsonData("axisLabelY", value);
        return this;
    }

    @UIField(order = 32)
    public Boolean getTimeline() {
        return getJsonData("timeline", Boolean.TRUE);
    }

    public WidgetLineChartEntity setTimeline(String value) {
        setJsonData("timeline", value);
        return this;
    }

    @UIField(order = 33)
    public LineInterpolation getLineInterpolation() {
        return getJsonDataEnum("lineInterpolation", LineInterpolation.curveLinear);
    }

    public WidgetLineChartEntity setLineInterpolation(LineInterpolation value) {
        setJsonData("lineInterpolation", value);
        return this;
    }

    @Override
    public String getImage() {
        return "fas fa-chart-line";
    }

    @Override
    public String getEntityPrefix() {
        return EntityContextWidget.LINE_CHART_WIDGET_PREFIX;
    }
}
