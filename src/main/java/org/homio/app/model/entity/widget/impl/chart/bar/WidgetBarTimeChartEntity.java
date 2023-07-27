package org.homio.app.model.entity.widget.impl.chart.bar;

import jakarta.persistence.Entity;
import org.homio.api.EntityContextWidget.BarChartType;
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
public class WidgetBarTimeChartEntity
    extends ChartBaseEntity<WidgetBarTimeChartEntity, WidgetBarTimeChartSeriesEntity>
    implements HasChartTimePeriod, HasMinMaxChartValue, HasHorizontalLine, HasAxis {

    @UIField(order = 40)
    @UIFieldGroup("CHART_AXIS")
    public String getAxisLabel() {
        return getJsonData("al");
    }

    public WidgetBarTimeChartEntity setAxisLabel(String value) {
        setJsonData("al", value);
        return this;
    }

    @UIField(order = 10)
    @UIFieldGroup(value = "CHART_UI", order = 54, borderColor = "#673AB7")
    public BarChartType getDisplayType() {
        return getJsonDataEnum("displayType", BarChartType.Vertical);
    }

    public WidgetBarTimeChartEntity setDisplayType(BarChartType value) {
        setJsonData("displayType", value);
        return this;
    }

    @UIField(order = 12)
    @UIFieldGroup("CHART_UI")
    @UIFieldJSONLine(
        template = "{\"top\": number}, \"left\": number, \"bottom\": number, \"right\": number")
    public String getBarBorderWidth() {
        return getJsonData("bbw", "{\"top\": 1, \"left\": 1, \"bottom\": 1, \"right\": 1}");
    }

    public void setBarBorderWidth(String value) {
        setJsonData("bbw", value);
    }

    @Override
    public @NotNull String getImage() {
        return "fas fa-chart-bar";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "bar-time";
    }

    @Override
    public String getDefaultName() {
        return null;
    }
}
