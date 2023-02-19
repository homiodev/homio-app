package org.touchhome.app.builder.widget.hasBuilder;

import org.jetbrains.annotations.Nullable;
import org.touchhome.bundle.api.EntityContextWidget.Fill;
import org.touchhome.bundle.api.EntityContextWidget.HasLineChartBehaviour;
import org.touchhome.bundle.api.EntityContextWidget.PointStyle;
import org.touchhome.bundle.api.EntityContextWidget.Stepped;

public interface HasLineChartBehaviourBuilder<T extends org.touchhome.app.model.entity.widget.impl.chart.HasLineChartBehaviour, R>
    extends HasLineChartBehaviour<R> {

    T getWidget();

    @Override
    default R setLineBorderWidth(int value) {
        getWidget().setLineBorderWidth(value);
        return (R) this;
    }

    @Override
    default R setLineFill(Fill value) {
        getWidget().setLineFill(value);
        return (R) this;
    }

    @Override
    default R setStepped(Stepped value) {
        getWidget().setStepped(value);
        return (R) this;
    }

    @Override
    default R setTension(int value) {
        getWidget().setTension(value);
        return (R) this;
    }

    @Override
    default R setPointRadius(double value) {
        getWidget().setPointRadius(value);
        return (R) this;
    }

    @Override
    default R setPointStyle(PointStyle value) {
        getWidget().setPointStyle(value);
        return (R) this;
    }

    @Override
    default R setPointBackgroundColor(@Nullable String value) {
        getWidget().setPointBackgroundColor(value);
        return (R) this;
    }

    @Override
    default R setPointBorderColor(@Nullable String value) {
        getWidget().setPointBorderColor(value);
        return (R) this;
    }
}
