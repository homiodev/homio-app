package org.homio.app.rest.widget;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.entity.widget.ability.HasTimeValueSeries;
import org.homio.app.model.entity.widget.impl.chart.HasChartDataSource;

@Getter
@RequiredArgsConstructor
@Accessors(chain = true)
public class TimeSeriesContext<T extends HasChartDataSource> {

    private final String id;
    private final T seriesEntity;
    private final HasTimeValueSeries series;

    @Setter private TimeSeriesValues<T> owner;

    @Setter private List<Object[]> value;

    @Setter private List<List<Float>> values;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TimeSeriesContext<T> that = (TimeSeriesContext<T>) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
