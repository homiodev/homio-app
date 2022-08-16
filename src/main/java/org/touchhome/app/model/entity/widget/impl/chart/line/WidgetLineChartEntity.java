package org.touchhome.app.model.entity.widget.impl.chart.line;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.bundle.api.EntityContextWidget;

import javax.persistence.Entity;

@Getter
@Setter
@Entity
public class WidgetLineChartEntity extends ChartBaseEntity<WidgetLineChartEntity, WidgetLineChartSeriesEntity> {

    @Override
    public String getImage() {
        return "fas fa-chart-line";
    }

    @Override
    public String getEntityPrefix() {
        return EntityContextWidget.LINE_CHART_WIDGET_PREFIX;
    }
}
