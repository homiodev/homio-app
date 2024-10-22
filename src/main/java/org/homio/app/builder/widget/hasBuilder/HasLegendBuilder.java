package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.HasLegend;
import org.homio.api.ContextWidget.LegendAlign;
import org.homio.api.ContextWidget.LegendPosition;
import org.jetbrains.annotations.NotNull;

public interface HasLegendBuilder<T extends org.homio.app.model.entity.widget.impl.chart.HasLegend, R>
        extends HasLegend<R> {

    T getWidget();

    @Override
    default @NotNull R setShowLegend(Boolean value) {
        getWidget().setShowLegend(value);
        return (R) this;
    }

    @Override
    default @NotNull R setLegendPosition(@NotNull LegendPosition value) {
        getWidget().setLegendPosition(value);
        return (R) this;
    }

    @Override
    default @NotNull R setLegendAlign(LegendAlign value) {
        getWidget().setLegendAlign(value);
        return (R) this;
    }
}
