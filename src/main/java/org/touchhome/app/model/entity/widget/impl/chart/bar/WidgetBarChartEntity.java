package org.touchhome.app.model.entity.widget.impl.chart.bar;

import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.app.repository.widget.impl.chart.WidgetBarChartSeriesEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

import javax.persistence.*;
import java.util.Set;

@Getter
@Setter
@Entity
public class WidgetBarChartEntity extends ChartBaseEntity<WidgetBarChartEntity> {

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "widgetBarChartEntity")
    @UIField(order = 30, onlyEdit = true)
    @OrderBy("priority asc")
    private Set<WidgetBarChartSeriesEntity> series;

    @UIField(order = 31)
    private Boolean showAxisX = Boolean.TRUE;

    @UIField(order = 32)
    private Boolean showAxisY = Boolean.TRUE;

    @UIField(order = 33)
    private String axisLabelX = "Date";

    @UIField(order = 34)
    private String axisLabelY = "Value";

    @UIField(order = 35)
    private Integer min = 0;

    @UIField(order = 36)
    private Integer max = 100;

    @UIField(order = 37)
    private Boolean noBarWhenZero = Boolean.TRUE;

    @UIField(order = 38)
    @Enumerated(EnumType.STRING)
    private BarChartType displayType = BarChartType.Horizontal;

    @UIField(order = 39)
    private Boolean showDataLabel = Boolean.FALSE;

    @UIField(order = 40)
    private Boolean roundEdges = Boolean.TRUE;

    @UIField(order = 41)
    @UIFieldSlider(min = 0, max = 20)
    private Integer barPadding = 0;

    @Override
    public String getImage() {
        return "fas fa-chart-bar";
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

    public enum BarChartType {
        Horizontal, Vertical
    }
}
