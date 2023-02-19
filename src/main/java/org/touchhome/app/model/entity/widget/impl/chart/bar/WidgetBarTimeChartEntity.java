package org.touchhome.app.model.entity.widget.impl.chart.bar;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.UIFieldJSONLine;
import org.touchhome.app.model.entity.widget.attributes.HasChartTimePeriod;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.app.model.entity.widget.impl.chart.HasAxis;
import org.touchhome.app.model.entity.widget.impl.chart.HasHorizontalLine;
import org.touchhome.app.model.entity.widget.impl.chart.HasMinMaxChartValue;
import org.touchhome.bundle.api.EntityContextWidget.BarChartType;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

@Entity
public class WidgetBarTimeChartEntity
    extends ChartBaseEntity<WidgetBarTimeChartEntity, WidgetBarTimeChartSeriesEntity>
    implements HasChartTimePeriod, HasMinMaxChartValue, HasHorizontalLine, HasAxis {

    public static final String PREFIX = "wgtbtc_";

    @UIField(order = 40)
    @UIFieldGroup("Chart axis")
    public String getAxisLabel() {
        return getJsonData("al", "example");
    }

    public WidgetBarTimeChartEntity setAxisLabel(String value) {
        setJsonData("al", value);
        return this;
    }

    @UIField(order = 10)
    @UIFieldGroup(value = "Chart ui", order = 5, borderColor = "#673AB7")
    public BarChartType getDisplayType() {
        return getJsonDataEnum("displayType", BarChartType.Vertical);
    }

    public WidgetBarTimeChartEntity setDisplayType(BarChartType value) {
        setJsonData("displayType", value);
        return this;
    }

    @UIField(order = 12)
    @UIFieldGroup("Chart ui")
    @UIFieldJSONLine(
        template = "{\"top\": number}, \"left\": number, \"bottom\": number, \"right\": number")
    public String getBarBorderWidth() {
        return getJsonData("bbw", "{\"top\": 1, \"left\": 1, \"bottom\": 1, \"right\": 1}");
    }

    public void setBarBorderWidth(String value) {
        setJsonData("bbw", value);
    }

    @Override
    public String getImage() {
        return "fas fa-chart-bar";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }
}
