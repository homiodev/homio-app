package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.HasMinMaxChartValue;
import org.jetbrains.annotations.NotNull;

public interface HasMinMaxChartValueBuilder<T extends org.homio.app.model.entity.widget.impl.chart.HasMinMaxChartValue, R>
        extends HasMinMaxChartValue<R> {

    T getWidget();

    @Override
    default @NotNull R setMin(Integer value) {
        getWidget().setMin(value);
        return (R) this;
    }

    @Override
    default @NotNull R setMax(Integer value) {
        getWidget().setMax(value);
        return (R) this;
    }
}
