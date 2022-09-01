package org.touchhome.app.model.entity.widget.impl.chart.doughnut;

import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

import javax.persistence.Entity;

@Entity
public class WidgetDoughnutChartSeriesEntity extends WidgetSeriesEntity<WidgetDoughnutChartEntity>
        implements HasSingleValueDataSource {

    public static final String PREFIX = "wgspcs_";

    @UIField(order = 20)
    @UIFieldGroup("Chart ui")
    @UIFieldColorPicker
    public String getChartColor() {
        return getJsonData("chartC", UI.Color.WHITE);
    }

    @UIField(order = 21)
    @UIFieldSlider(min = 0, max = 100, step = 5)
    @UIFieldGroup("Chart ui")
    public int getChartColorOpacity() {
        return getJsonData("chartCO", 50);
    }

    public WidgetDoughnutChartSeriesEntity setChartColor(String value) {
        setJsonData("chartC", value);
        return this;
    }

    public void setChartColorOpacity(int value) {
        setJsonData("chartCO", value);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("chartC")) {
            setChartColor(UI.Color.random());
        }
    }
}
