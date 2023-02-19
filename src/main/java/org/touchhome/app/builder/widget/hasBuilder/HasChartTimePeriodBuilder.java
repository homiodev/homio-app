package org.touchhome.app.builder.widget.hasBuilder;

import org.touchhome.bundle.api.EntityContextWidget.HasChartTimePeriod;

public interface HasChartTimePeriodBuilder<T extends org.touchhome.app.model.entity.widget.attributes.HasChartTimePeriod, R>
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
