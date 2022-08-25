package org.touchhome.app.model.entity.widget.impl.chart.line;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

import javax.persistence.Entity;

@Entity
public class WidgetLineChartSeriesEntity extends WidgetSeriesEntity<WidgetLineChartEntity>
        implements HasChartDataSource<WidgetLineChartEntity> {

    public static final String PREFIX = "wgslcs_";

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void beforePersist() {
        setInitChartColor(UI.Color.random());
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public int getHoursToShow() {
        throw new RuntimeException("MNC");
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public int getPointsPerHour() {
        throw new RuntimeException("MNC");
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public ChartType getChartType() {
        throw new RuntimeException("MNC");
    }
}
