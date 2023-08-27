package org.homio.app.model.entity.widget.impl.chart.doughnut;

import jakarta.persistence.Entity;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasValueConverter;

@Entity
public class WidgetDoughnutChartSeriesEntity extends WidgetSeriesEntity<WidgetDoughnutChartEntity>
        implements HasSingleValueDataSource, HasValueConverter {

    @UIField(order = 20, isRevert = true)
    @UIFieldGroup("CHART_UI")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    public String getChartColor() {
        return getJsonData("chartC", UI.Color.WHITE);
    }

    public WidgetDoughnutChartSeriesEntity setChartColor(String value) {
        setJsonData("chartC", value);
        return this;
    }

    @UIField(order = 21)
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
        return "doughnut";
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
