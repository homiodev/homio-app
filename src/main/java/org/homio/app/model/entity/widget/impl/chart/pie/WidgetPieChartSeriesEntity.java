package org.homio.app.model.entity.widget.impl.chart.pie;

import javax.persistence.Entity;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasValueConverter;
import org.homio.bundle.api.ui.UI;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldColorPicker;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldReadDefaultValue;
import org.homio.bundle.api.ui.field.UIFieldSlider;

@Entity
public class WidgetPieChartSeriesEntity extends WidgetSeriesEntity<WidgetPieChartEntity>
    implements HasSingleValueDataSource, HasValueConverter {

    public static final String PREFIX = "wgspcs_";

    @UIField(order = 20, isRevert = true)
    @UIFieldGroup(value = "CHART_UI", order = 5, borderColor = "#673AB7")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    public String getChartColor() {
        return getJsonData("chartC", UI.Color.WHITE);
    }

    public WidgetPieChartSeriesEntity setChartColor(String value) {
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
