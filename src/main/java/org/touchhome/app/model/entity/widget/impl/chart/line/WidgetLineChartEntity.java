package org.touchhome.app.model.entity.widget.impl.chart.line;

import javax.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.app.model.entity.widget.impl.chart.HasAxis;
import org.touchhome.app.model.entity.widget.impl.chart.HasChartDataSource;
import org.touchhome.app.model.entity.widget.impl.chart.HasHorizontalLine;
import org.touchhome.app.model.entity.widget.impl.chart.HasLineChartBehaviour;
import org.touchhome.bundle.api.EntityContextWidget;
import org.touchhome.bundle.api.ui.field.UIField;

@Getter
@Setter
@Entity
public class WidgetLineChartEntity
        extends ChartBaseEntity<WidgetLineChartEntity, WidgetLineChartSeriesEntity>
        implements HasLineChartBehaviour, HasHorizontalLine, HasAxis {

    @Override
    public String getImage() {
        return "fas fa-chart-line";
    }

    @Override
    public String getEntityPrefix() {
        return EntityContextWidget.LINE_CHART_WIDGET_PREFIX;
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    public HasChartDataSource.ChartType getChartType() {
        return HasChartDataSource.ChartType.line;
    }

    @Override
    public String getDefaultName() {
        return null;
    }
}
