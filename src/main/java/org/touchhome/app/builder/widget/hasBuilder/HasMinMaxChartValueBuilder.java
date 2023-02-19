package org.touchhome.app.builder.widget.hasBuilder;

import org.touchhome.bundle.api.EntityContextWidget.HasMinMaxChartValue;

public interface HasMinMaxChartValueBuilder<T extends org.touchhome.app.model.entity.widget.impl.chart.HasMinMaxChartValue, R>
    extends HasMinMaxChartValue<R> {

    T getWidget();

    @Override
    default R setMin(Integer value) {
        getWidget().setMin(value);
        return (R) this;
    }

    @Override
    default R setMax(Integer value) {
        getWidget().setMax(value);
        return (R) this;
    }
}
