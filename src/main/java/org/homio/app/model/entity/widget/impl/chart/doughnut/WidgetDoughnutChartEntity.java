package org.homio.app.model.entity.widget.impl.chart.doughnut;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.Entity;
import org.homio.app.model.entity.widget.attributes.HasChartTimePeriod;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasValueConverter;
import org.homio.app.model.entity.widget.attributes.HasValueTemplate;
import org.homio.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.homio.bundle.api.exception.ProhibitedExecution;
import org.homio.bundle.api.ui.UI;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldColorPicker;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldIgnore;
import org.homio.bundle.api.ui.field.UIFieldReadDefaultValue;
import org.homio.bundle.api.ui.field.UIFieldSlider;

@Entity
public class WidgetDoughnutChartEntity
    extends ChartBaseEntity<WidgetDoughnutChartEntity, WidgetDoughnutChartSeriesEntity>
    implements HasSingleValueDataSource, HasChartTimePeriod, HasValueConverter, HasValueTemplate {

    public static final String PREFIX = "wgtpc_";

 //   @UIField(order = 3)

    // TODO:??????????/
    /*public double getValueFontSize() {
        return getJsonData("vfs", 18);
    }*/

    /*public void setValueFontSize(double value) {
        setJsonData("vfs", value);
    }*/

    @UIField(order = 4, isRevert = true)
    @UIFieldGroup("Value")
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldReadDefaultValue
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

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public boolean getShowChartFullScreenButton() {
        throw new ProhibitedExecution();
    }
}
