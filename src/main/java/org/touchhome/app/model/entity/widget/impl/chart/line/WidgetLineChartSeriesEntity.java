package org.touchhome.app.model.entity.widget.impl.chart.line;

import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasLineChartDataSource;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.Entity;

@Entity
public class WidgetLineChartSeriesEntity extends WidgetSeriesEntity<WidgetLineChartEntity>
        implements HasLineChartDataSource<WidgetLineChartEntity> {

    public static final String PREFIX = "wgslcs_";

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void beforePersist() {
        setInitChartColor(UI.Color.random());
    }

    @UIField(order = 0, visible = false)
    public ChartType getChartType() {
        return ChartType.line;
    }
}
