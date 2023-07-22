package org.homio.app.model.entity.widget.impl.chart.bar;

import jakarta.persistence.Entity;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;

@Entity
public class WidgetBarChartSeriesEntity extends WidgetSeriesEntity<WidgetBarChartEntity>
    implements HasSingleValueDataSource {

    public static final String PREFIX = "wgsbcs_";

    @UIField(order = 50, isRevert = true)
    @UIFieldGroup(value = "CHART_UI", order = 54, borderColor = "#673AB7")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    public String getChartColor() {
        return getJsonData("chartC", UI.Color.WHITE);
    }

    public void setChartColor(String value) {
        setJsonData("chartC", value);
    }

    @UIField(order = 51)
    @UIFieldSlider(min = 0, max = 100, step = 5)
    @UIFieldGroup("CHART_UI")
    public int getChartColorOpacity() {
        return getJsonData("chartCO", 50);
    }

    public void setChartColorOpacity(int value) {
        setJsonData("chartCO", value);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("chartC")) {
            setChartColor(UI.Color.random());
        }
    }
}
