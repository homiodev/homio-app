package org.homio.app.model.entity.widget.impl.chart.bar;

import jakarta.persistence.Entity;
import org.homio.api.ContextWidget.BarChartType;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.app.model.entity.widget.UIFieldJSONLine;
import org.homio.app.model.entity.widget.attributes.HasChartTimePeriod;
import org.homio.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.homio.app.model.entity.widget.impl.chart.HasAxis;
import org.homio.app.model.entity.widget.impl.chart.HasHorizontalLine;
import org.homio.app.model.entity.widget.impl.chart.HasMinMaxChartValue;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetBarChartEntity
        extends ChartBaseEntity<WidgetBarChartEntity, WidgetBarChartSeriesEntity>
        implements HasChartTimePeriod, HasHorizontalLine, HasMinMaxChartValue, HasAxis {

    @UIField(order = 10)
    @UIFieldGroup(order = 54, value = "CHART_UI", borderColor = "#673AB7")
    public BarChartType getDisplayType() {
        return getJsonDataEnum("displayType", BarChartType.Vertical);
    }

    public WidgetBarChartEntity setDisplayType(BarChartType value) {
        setJsonData("displayType", value);
        return this;
    }

    @UIField(order = 40)
    @UIFieldGroup("CHART_AXIS")
    public String getAxisLabel() {
        return getJsonData("al", "");
    }

    public WidgetBarChartEntity setAxisLabel(String value) {
        setJsonData("al", value);
        return this;
    }

    @UIField(order = 12)
    @UIFieldGroup("CHART_UI")
    @UIFieldJSONLine(
            template = "{\"top\": number}, \"left\": number, \"bottom\": number, \"right\": number")
    public String getBarBorderWidth() {
        return getJsonData("bbw", "{\"top\": 0, \"left\": 0, \"bottom\": 0, \"right\": 0}");
    }

    public WidgetBarChartEntity setBarBorderWidth(String value) {
        setJsonData("bbw", value);
        return this;
    }

    @Override
    public @NotNull String getImage() {
        return "fas fa-chart-bar";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "bar";
    }

    @Override
    public String getDefaultName() {
        return null;
    }
}
