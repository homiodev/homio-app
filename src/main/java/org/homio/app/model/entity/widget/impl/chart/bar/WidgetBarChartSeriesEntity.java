package org.homio.app.model.entity.widget.impl.chart.bar;

import jakarta.persistence.Entity;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;

@Entity
public class WidgetBarChartSeriesEntity extends WidgetSeriesEntity<WidgetBarChartEntity>
        implements HasSingleValueDataSource {

    @UIField(order = 50)
    @UIFieldGroup(value = "CHART_UI", order = 54, borderColor = "#673AB7")
    @UIFieldColorPicker
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
    protected String getSeriesPrefix() {
        return "bar";
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    public void beforePersist() {
        if (!getJsonData().has("chartC")) {
            setChartColor(UI.Color.random());
        }
    }
}
