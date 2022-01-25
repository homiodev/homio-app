package org.touchhome.app.model.entity.widget.impl.chart.pie;

import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.Entity;

@Entity
public class WidgetPieChartEntity extends ChartBaseEntity<WidgetPieChartEntity, WidgetPieChartSeriesEntity> {

    public static final String PREFIX = "piew_";

    @UIField(order = 12)
    public TimePeriod getTimePeriod() {
        return getJsonDataEnum("timePeriod", TimePeriod.All);
    }

    public WidgetPieChartEntity setTimePeriod(TimePeriod value) {
        setJsonData("timePeriod", value);
        return this;
    }

    @UIField(order = 33, showInContextMenu = true)
    public Boolean getShowButtons() {
        return getJsonData("showButtons", Boolean.FALSE);
    }

    public WidgetPieChartEntity setShowButtons(Boolean value) {
        setJsonData("showButtons", value);
        return this;
    }

    @UIField(order = 34)
    public String getLabelFormatting() {
        return getJsonData("labelFormatting", "#LABEL#");
    }

    public WidgetPieChartEntity setLabelFormatting(String value) {
        setJsonData("labelFormatting", value);
        return this;
    }

    @UIField(order = 35)
    public String getValueFormatting() {
        return getJsonData("valueFormatting", "#VALUE#");
    }

    public WidgetPieChartEntity setValueFormatting(String value) {
        setJsonData("valueFormatting", value);
        return this;
    }

    @UIField(order = 36)
    public Boolean getDoughnut() {
        return getJsonData("doughnut", Boolean.FALSE);
    }

    public WidgetPieChartEntity setDoughnut(Boolean value) {
        setJsonData("doughnut", value);
        return this;
    }

    @UIField(order = 37, showInContextMenu = true)
    public Boolean getShowLabels() {
        return getJsonData("showLabels", Boolean.TRUE);
    }

    public WidgetPieChartEntity setShowLabels(Boolean value) {
        setJsonData("showLabels", value);
        return this;
    }

    @UIField(order = 38)
    public PieChartValueType getPieChartValueType() {
        return getJsonDataEnum("pieChartValueType", PieChartValueType.Count);
    }

    public WidgetPieChartEntity setPieChartValueType(PieChartValueType value) {
        setJsonData("pieChartValueType", value);
        return this;
    }

    @UIField(order = 39)
    public PieChartType getDisplayType() {
        return getJsonDataEnum("displayType", PieChartType.Regular);
    }

    public WidgetPieChartEntity setDisplayType(PieChartType value) {
        setJsonData("displayType", value);
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

    public enum PieChartValueType {
        Sum, Count
    }

    public enum PieChartType {
        Regular, Grid, Advanced, Tree
    }
}
