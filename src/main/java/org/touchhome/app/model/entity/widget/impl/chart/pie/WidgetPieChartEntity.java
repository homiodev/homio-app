package org.touchhome.app.model.entity.widget.impl.chart.pie;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.app.model.entity.widget.impl.chart.ChartPeriod;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.*;
import java.util.Set;

@Getter
@Setter
@Entity
public class WidgetPieChartEntity extends ChartBaseEntity<WidgetPieChartEntity> {

    @UIField(order = 12)
    @Enumerated(EnumType.STRING)
    private ChartPeriod chartPeriod = ChartPeriod.All;

    @UIField(order = 33, showInContextMenu = true)
    private Boolean showButtons = Boolean.FALSE;

    @UIField(order = 35)
    private String labelFormatting = "#LABEL#";

    @UIField(order = 36)
    private String valueFormatting = "#VALUE#";

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "widgetPieChartEntity")
    @UIField(order = 30, onlyEdit = true)
    @OrderBy("priority asc")
    private Set<WidgetPieChartSeriesEntity> series;

    @UIField(order = 33)
    private Boolean doughnut = Boolean.FALSE;

    @UIField(order = 33, showInContextMenu = true)
    private Boolean showLabels = Boolean.TRUE;

    @UIField(order = 35)
    @Enumerated(EnumType.STRING)
    private PieChartValueType pieChartValueType = PieChartValueType.Count;

    @UIField(order = 35)
    @Enumerated(EnumType.STRING)
    private PieChartType displayType = PieChartType.Regular;

    @Override
    public String getImage() {
        return "fas fa-chart-pie";
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

    public enum PieChartValueType {
        Sum, Count
    }

    public enum PieChartType {
        Regular, Grid, Advanced, Tree
    }
}
