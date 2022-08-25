package org.touchhome.app.rest.widget;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.bundle.api.entity.widget.ability.HasTimeValueSeries;

import java.util.List;

@Getter
@RequiredArgsConstructor
@Accessors(chain = true)
public class TimeSeriesContext<T extends WidgetSeriesEntity<?> & HasChartDataSource<?>> {
    private final String id;
    // private final String name;
    // private final String color;
    private final T seriesEntity;
    private final HasTimeValueSeries series;

    @Setter
    private TimeSeriesValues<T> owner;

    @Setter
    private List<Object[]> value;

    @Setter
    private List<List<Float>> values;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TimeSeriesContext<T> that = (TimeSeriesContext<T>) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
