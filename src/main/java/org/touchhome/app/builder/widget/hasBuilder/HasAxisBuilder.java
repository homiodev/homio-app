package org.touchhome.app.builder.widget.hasBuilder;

import org.touchhome.bundle.api.EntityContextWidget.HasAxis;

public interface HasAxisBuilder<T extends org.touchhome.app.model.entity.widget.impl.chart.HasAxis, R>
    extends HasAxis<R> {

    T getWidget();

    @Override
    default R setShowAxisX(Boolean value) {
        getWidget().setShowAxisX(value);
        return (R) this;
    }

    @Override
    default R setShowAxisY(Boolean value) {
        getWidget().setShowAxisY(value);
        return (R) this;
    }

    @Override
    default R setAxisLabelX(String value) {
        getWidget().setAxisLabelX(value);
        return (R) this;
    }

    @Override
    default R setAxisLabelY(String value) {
        getWidget().setAxisLabelY(value);
        return (R) this;
    }

    @Override
    default R setAxisDateFormat(String value) {
        getWidget().setAxisDateFormat(value);
        return (R) this;
    }
}
