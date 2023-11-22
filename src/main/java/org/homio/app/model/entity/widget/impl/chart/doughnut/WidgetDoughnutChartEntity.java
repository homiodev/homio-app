package org.homio.app.model.entity.widget.impl.chart.doughnut;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.apache.commons.lang3.NotImplementedException;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.app.model.entity.widget.attributes.HasChartTimePeriod;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasValueConverter;
import org.homio.app.model.entity.widget.attributes.HasValueTemplate;
import org.homio.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetDoughnutChartEntity
        extends ChartBaseEntity<WidgetDoughnutChartEntity, WidgetDoughnutChartSeriesEntity>
        implements HasSingleValueDataSource, HasChartTimePeriod, HasValueConverter, HasValueTemplate {

    //   @UIField(order = 3)

    // TODO:??????????/
    /*public double getValueFontSize() {
        return getJsonData("vfs", 18);
    }*/

    /*public void setValueFontSize(double value) {
        setJsonData("vfs", value);
    }*/

    @UIField(order = 4)
    @UIFieldGroup("VALUE")
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
    public @NotNull String getImage() {
        return "fas fa-circle-dot";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "doughnut";
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public boolean getShowChartFullScreenButton() {
        throw new NotImplementedException();
    }
}
