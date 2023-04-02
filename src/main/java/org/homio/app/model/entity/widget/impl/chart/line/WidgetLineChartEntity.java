package org.homio.app.model.entity.widget.impl.chart.line;

import javax.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.homio.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.homio.app.model.entity.widget.impl.chart.HasAxis;
import org.homio.app.model.entity.widget.impl.chart.HasHorizontalLine;
import org.homio.app.model.entity.widget.impl.chart.HasLineChartBehaviour;
import org.homio.bundle.api.EntityContextWidget.ChartType;
import org.homio.bundle.api.ui.field.UIField;

@Getter
@Setter
@Entity
public class WidgetLineChartEntity
    extends ChartBaseEntity<WidgetLineChartEntity, WidgetLineChartSeriesEntity>
    implements HasLineChartBehaviour, HasHorizontalLine, HasAxis {

    public static final String LINE_CHART_WIDGET_PREFIX = "wgtlc_";

    @Override
    public String getImage() {
        return "fas fa-chart-line";
    }

    @Override
    public String getEntityPrefix() {
        return WidgetLineChartEntity.LINE_CHART_WIDGET_PREFIX;
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    public ChartType getChartType() {
        return ChartType.line;
    }

    @Override
    public String getDefaultName() {
        return null;
    }
}
