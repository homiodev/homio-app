package org.touchhome.app.model.entity.widget.impl.chart;

import org.touchhome.app.rest.widget.TimeSeriesContext;
import org.touchhome.app.rest.widget.WidgetChartsController;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;

import javax.persistence.Entity;

@Entity
public abstract class TimeSeriesChartBaseEntity<T extends WidgetBaseEntityAndSeries,
        S extends WidgetSeriesEntity<T>,
        DS extends WidgetChartsController.ChartDataset>
        extends ChartBaseEntity<T, S> {

    public abstract DS buildTargetDataset(TimeSeriesContext item);

    public boolean fillMissingValues(WidgetSeriesEntity seriesEntity) {
        return false;
    }
}
