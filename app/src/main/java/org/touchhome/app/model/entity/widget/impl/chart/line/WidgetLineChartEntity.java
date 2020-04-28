package org.touchhome.app.model.entity.widget.impl.chart.line;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.app.model.entity.widget.impl.chart.LineInterpolation;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.*;
import java.util.Set;

@Getter
@Setter
@Entity
public class WidgetLineChartEntity extends ChartBaseEntity<WidgetLineChartEntity> {

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "widgetLineChartEntity")
    @UIField(order = 30, onlyEdit = true)
    @OrderBy("priority asc")
    private Set<WidgetLineChartSeriesEntity> series;

    @UIField(order = 28)
    private Boolean showxAxis = Boolean.TRUE;

    @UIField(order = 29)
    private Boolean showyAxis = Boolean.TRUE;

    @UIField(order = 30)
    private String xaxisLabel = "Date";

    @UIField(order = 31)
    private String yaxisLabel = "Value";

    @UIField(order = 32)
    private Boolean timeline = Boolean.TRUE;

    @UIField(order = 33)
    @Enumerated(EnumType.STRING)
    private LineInterpolation lineInterpolation = LineInterpolation.curveLinear;

    @Override
    public String getImage() {
        return "fas fa-chart-line";
    }

    @Override
    public boolean updateRelations(EntityContext entityContext) {
        return validateSeries(series, entityContext);
    }

    @Override
    public void copy() {
        super.copy();
        series.forEach(BaseEntity::copy);
    }
}
