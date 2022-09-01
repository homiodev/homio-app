package org.touchhome.app.model.entity.widget.impl.chart.line;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.app.model.entity.widget.impl.HasLineChartBehaviour;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.bundle.api.EntityContextWidget;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

import javax.persistence.Entity;

@Getter
@Setter
@Entity
public class WidgetLineChartEntity extends ChartBaseEntity<WidgetLineChartEntity, WidgetLineChartSeriesEntity>
        implements HasLineChartBehaviour {

    @Override
    public String getImage() {
        return "fas fa-chart-line";
    }

    @Override
    public String getEntityPrefix() {
        return EntityContextWidget.LINE_CHART_WIDGET_PREFIX;
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public String getLayout() {
        throw new IllegalStateException("MNC");
    }

    @UIField(order = 0, visible = false)
    public HasChartDataSource.ChartType getChartType() {
        return HasChartDataSource.ChartType.line;
    }

}
