package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.EntityContextWidget.HasChartTimePeriod;

public interface HasChartTimePeriodBuilder<T extends org.homio.app.model.entity.widget.attributes.HasChartTimePeriod, R>
        extends HasChartTimePeriod<R> {

    T getWidget();

    @Override
    default R setChartMinutesToShow(int value) {
        getWidget().setChartMinutesToShow(value);
        return (R) this;
    }

    @Override
    default R setChartPointsPerHour(int value) {
        getWidget().setChartPointsPerHour(value);
        return (R) this;
    }
}
