package org.touchhome.app.model.entity.widget.impl.chart.bar;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

@Entity
public class WidgetBarChartSeriesEntity extends WidgetSeriesEntity<WidgetBarChartEntity>
    implements HasSingleValueDataSource {

    public static final String PREFIX = "wgsbcs_";

    @UIField(order = 50, isRevert = true)
    @UIFieldGroup(value = "Chart ui", order = 2, borderColor = "#673AB7")
    @UIFieldColorPicker
    public String getChartColor() {
        return getJsonData("chartC", UI.Color.WHITE);
    }

    public void setChartColor(String value) {
        setJsonData("chartC", value);
    }

    @UIField(order = 51)
    @UIFieldSlider(min = 0, max = 100, step = 5)
    @UIFieldGroup("Chart ui")
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
