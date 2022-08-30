package org.touchhome.app.rest.widget;

import lombok.Getter;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.bundle.api.entity.BaseEntity;

import java.util.Set;

@Getter
public class TimeSeriesValues<T extends HasChartDataSource<?>> {
    private final BaseEntity<?> source;
    private final Set<TimeSeriesContext<T>> itemSeries;

    public TimeSeriesValues(BaseEntity<?> source, Set<TimeSeriesContext<T>> itemSeries) {
        this.source = source;
        this.itemSeries = itemSeries;

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
