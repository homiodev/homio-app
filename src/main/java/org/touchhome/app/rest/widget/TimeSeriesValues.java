package org.touchhome.app.rest.widget;

import lombok.Getter;
import org.touchhome.app.model.entity.widget.impl.chart.HasChartDataSource;

import java.util.Set;

@Getter
public class TimeSeriesValues<T extends HasChartDataSource> {
    private final Set<TimeSeriesContext<T>> itemSeries;
    private final Object source;

    public TimeSeriesValues(Set<TimeSeriesContext<T>> itemSeries, Object source) {
        this.itemSeries = itemSeries;
        this.source = source;

        for (TimeSeriesContext<T> timeSeriesContext : itemSeries) {
            timeSeriesContext.setOwner(this);
        }
    }

    public boolean isEqualSeries(Set<TimeSeriesContext<T>> updatedContext) {
        if (this.itemSeries.equals(updatedContext)) {
            for (TimeSeriesContext<T> item : this.itemSeries) {
                TimeSeriesContext<T> otherItem =
                        updatedContext.stream().filter(c -> c.getId().equals(item.getId())).findAny().get();

                if (!item.getValue().equals(otherItem.getValue())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
