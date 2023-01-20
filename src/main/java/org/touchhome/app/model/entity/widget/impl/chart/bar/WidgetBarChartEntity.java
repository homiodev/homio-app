package org.touchhome.app.model.entity.widget.impl.chart.bar;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.UIFieldJSONLine;
import org.touchhome.app.model.entity.widget.impl.HasChartTimePeriod;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.app.model.entity.widget.impl.chart.HasAxis;
import org.touchhome.app.model.entity.widget.impl.chart.HasHorizontalLine;
import org.touchhome.app.model.entity.widget.impl.chart.HasMinMaxChartValue;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

@Entity
public class WidgetBarChartEntity
        extends ChartBaseEntity<WidgetBarChartEntity, WidgetBarChartSeriesEntity>
        implements HasChartTimePeriod, HasHorizontalLine, HasMinMaxChartValue, HasAxis {

    public static final String PREFIX = "wgtbc_";

    @UIField(order = 10)
    @UIFieldGroup(value = "Chart ui", order = 2, borderColor = "#673AB7")
    public BarChartType getDisplayType() {
        return getJsonDataEnum("displayType", BarChartType.Vertical);
    }

    public WidgetBarChartEntity setDisplayType(BarChartType value) {
        setJsonData("displayType", value);
        return this;
    }

    @UIField(order = 40)
    @UIFieldGroup("Chart axis")
    public String getAxisLabel() {
        return getJsonData("al", "");
    }

    public WidgetBarChartEntity setAxisLabel(String value) {
        setJsonData("al", value);
        return this;
    }

    @UIField(order = 12)
    @UIFieldGroup("Chart ui")
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

    public enum BarChartType {
        Horizontal,
        Vertical
    }
}
