package org.touchhome.app.model.entity.widget.impl.chart.line;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.chart.*;
import org.touchhome.bundle.api.EntityContextWidget;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.Entity;

@Getter
@Setter
@Entity
public class WidgetLineChartEntity extends ChartBaseEntity<WidgetLineChartEntity, WidgetLineChartSeriesEntity>
        implements HasLineChartBehaviour, HasHorizontalLine, HasAxis {

    @Override
    public String getImage() {
        return "fas fa-chart-line";
    }

    @Override
    public String getEntityPrefix() {
        return EntityContextWidget.LINE_CHART_WIDGET_PREFIX;
    }

    @UIField(order = 0, visible = false)
    public HasChartDataSource.ChartType getChartType() {
        return HasChartDataSource.ChartType.line;
    }

}
