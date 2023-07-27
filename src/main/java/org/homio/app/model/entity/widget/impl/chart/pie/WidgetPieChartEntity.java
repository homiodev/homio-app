package org.homio.app.model.entity.widget.impl.chart.pie;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.homio.api.exception.ProhibitedExecution;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.app.model.entity.widget.attributes.HasChartTimePeriod;
import org.homio.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetPieChartEntity
    extends ChartBaseEntity<WidgetPieChartEntity, WidgetPieChartSeriesEntity>
    implements HasChartTimePeriod {

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
    public @NotNull String getImage() {
        return "fas fa-chart-pie";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "pie";
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
