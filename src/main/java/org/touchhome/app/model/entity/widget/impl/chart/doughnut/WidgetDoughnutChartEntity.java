package org.touchhome.app.model.entity.widget.impl.chart.doughnut;

import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.HasTimePeriod;
import org.touchhome.app.model.entity.widget.impl.HasValueConverter;
import org.touchhome.app.model.entity.widget.impl.HasValueTemplate;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

import javax.persistence.Entity;

@Entity
public class WidgetDoughnutChartEntity extends ChartBaseEntity<WidgetDoughnutChartEntity, WidgetDoughnutChartSeriesEntity>
        implements HasSingleValueDataSource, HasTimePeriod, HasValueConverter, HasValueTemplate {

    public static final String PREFIX = "wgtpc_";

    @UIField(order = 3)
    @UIFieldGroup("Value")
    @UIFieldSlider(min = 8, max = 40)
    public double getValueFontSize() {
        return getJsonData("vfs", 18);
    }

    public double setValueFontSize(int value) {
        setJsonData("vfs", value);
    }

    @UIField(order = 4)
    @UIFieldGroup("Value")
    @UIFieldColorPicker(allowThreshold = true)
    public String getValueColor() {
        return getJsonData("vc", UI.Color.WHITE);
    }

    public void setValueColor(String value) {
        setJsonData("vc", value);
    }

    @UIField(order = 52)
    @UIFieldSlider(min = 1, max = 4)
    public int getBorderWidth() {
        return getJsonData("bw", 1);
    }

    public WidgetDoughnutChartEntity setBorderWidth(int value) {
        setJsonData("bw", value);
        return this;
    }

    @Override
    public String getImage() {
        return "fas fa-circle-dot";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
