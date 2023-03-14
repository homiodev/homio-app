package org.touchhome.app.model.entity.widget.impl.chart.pie;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.attributes.HasChartTimePeriod;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.bundle.api.exception.ProhibitedExecution;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

@Entity
public class WidgetPieChartEntity
    extends ChartBaseEntity<WidgetPieChartEntity, WidgetPieChartSeriesEntity>
    implements HasChartTimePeriod {

    public static final String PREFIX = "wgtpc_";

    @UIField(order = 52)
    @UIFieldSlider(min = 1, max = 4)
    public int getBorderWidth() {
        return getJsonData("bw", 1);
    }

    public WidgetPieChartEntity setBorderWidth(int value) {
        setJsonData("bw", value);
        return this;
    }

    @Override
    public String getImage() {
        return "fas fa-chart-pie";
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
