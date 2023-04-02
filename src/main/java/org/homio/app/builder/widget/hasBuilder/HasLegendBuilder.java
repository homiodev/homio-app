package org.homio.app.builder.widget.hasBuilder;

import org.homio.bundle.api.EntityContextWidget.HasLegend;
import org.homio.bundle.api.EntityContextWidget.LegendAlign;
import org.homio.bundle.api.EntityContextWidget.LegendPosition;

public interface HasLegendBuilder<T extends org.homio.app.model.entity.widget.impl.chart.HasLegend, R>
    extends HasLegend<R> {

    T getWidget();

    @Override
    default R setShowLegend(Boolean value) {
        getWidget().setShowLegend(value);
        return (R) this;
    }

    @Override
    default R setLegendPosition(LegendPosition value) {
        getWidget().setLegendPosition(value);
        return (R) this;
    }

    @Override
    default R setLegendAlign(LegendAlign value) {
        getWidget().setLegendAlign(value);
        return (R) this;
    }
}
