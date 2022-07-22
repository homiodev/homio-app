package org.touchhome.app.model.entity.widget.impl.chart;

import org.touchhome.app.rest.widget.TimeSeriesContext;
import org.touchhome.app.rest.widget.WidgetChartsController;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.Entity;

@Entity
public abstract class TimeSeriesChartBaseEntity<T extends WidgetBaseEntityAndSeries,
        S extends WidgetSeriesEntity<T>,
        DS extends WidgetChartsController.ChartDataset>
        extends ChartBaseEntity<T, S> {

    @UIField(order = 12)
    public TimePeriod getTimePeriod() {
        return getJsonDataEnum("timePeriod", TimePeriod.All);
    }

    public T setTimePeriod(TimePeriod value) {
        setJsonData("timePeriod", value);
        return (T) this;
    }

    @UIField(order = 32)
    public Boolean getTimeline() {
        return getJsonData("timeline", Boolean.TRUE);
    }

    public T setTimeline(String value) {
        setJsonData("timeline", value);
        return (T) this;
    }

    public abstract DS buildTargetDataset(TimeSeriesContext item);

    public boolean fillMissingValues(WidgetSeriesEntity seriesEntity) {
        return false;
    }
}
