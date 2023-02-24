package org.touchhome.app.builder.widget.hasBuilder;

import org.touchhome.bundle.api.EntityContextWidget.HasLegend;
import org.touchhome.bundle.api.EntityContextWidget.LegendAlign;
import org.touchhome.bundle.api.EntityContextWidget.LegendPosition;

public interface HasLegendBuilder<T extends org.touchhome.app.model.entity.widget.impl.chart.HasLegend, R>
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
