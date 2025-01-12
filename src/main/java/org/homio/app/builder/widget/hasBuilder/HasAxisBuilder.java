package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget;
import org.homio.api.ContextWidget.HasAxis;
import org.jetbrains.annotations.NotNull;

public interface HasAxisBuilder<T extends org.homio.app.model.entity.widget.impl.chart.HasAxis, R>
  extends HasAxis<R> {

  T getWidget();

  @Override
  default @NotNull R setShowAxisX(Boolean value) {
    getWidget().setShowAxisX(value);
    return (R) this;
  }

  @Override
  default @NotNull R setShowAxisY(Boolean value) {
    getWidget().setShowAxisY(value);
    return (R) this;
  }

  @Override
  default @NotNull R setAxisLabelX(String value) {
    getWidget().setAxisLabelX(value);
    return (R) this;
  }

  @Override
  default @NotNull R setAxisLabelY(String value) {
    getWidget().setAxisLabelY(value);
    return (R) this;
  }

  @Override
  default @NotNull R setAxisDateFormat(ContextWidget.AxisDateFormat value) {
    getWidget().setAxisDateFormat(value);
    return (R) this;
  }
}
