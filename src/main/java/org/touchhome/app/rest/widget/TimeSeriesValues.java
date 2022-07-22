package org.touchhome.app.rest.widget;

import lombok.Getter;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartSeriesEntity;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;

import java.util.Set;

@Getter
public class TimeSeriesValues {
    private final WidgetSeriesEntity item;
    private final BaseEntity<?> source;
    private final Set<TimeSeriesContext> itemSeries;

    public TimeSeriesValues(WidgetSeriesEntity item, BaseEntity<?> source, Set<TimeSeriesContext> itemSeries) {
        this.item = item;
        this.source = source;
        this.itemSeries = itemSeries;

        for (TimeSeriesContext timeSeriesContext : itemSeries) {
            timeSeriesContext.setOwner(this);
        }
    }

    public boolean isEqualSeries(Set<TimeSeriesContext> updatedContext) {
        if (this.itemSeries.equals(updatedContext)) {
            for (TimeSeriesContext item : this.itemSeries) {
                TimeSeriesContext otherItem = updatedContext.stream().filter(c -> c.getId().equals(item.getId())).findAny().get();

                if (!item.getValue().equals(otherItem.getValue())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
