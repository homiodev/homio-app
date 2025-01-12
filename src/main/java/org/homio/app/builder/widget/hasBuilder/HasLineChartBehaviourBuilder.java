package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.Fill;
import org.homio.api.ContextWidget.HasLineChartBehaviour;
import org.homio.api.ContextWidget.PointStyle;
import org.homio.api.ContextWidget.Stepped;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface HasLineChartBehaviourBuilder<T extends org.homio.app.model.entity.widget.impl.chart.HasLineChartBehaviour, R>
  extends HasLineChartBehaviour<R> {

  T getWidget();

  @Override
  default @NotNull R setLineBorderWidth(int value) {
    getWidget().setLineBorderWidth(value);
    return (R) this;
  }

  @Override
  default @NotNull R setLineFill(@NotNull Fill value) {
    getWidget().setLineFill(value);
    return (R) this;
  }

  @Override
  default @NotNull R setStepped(@NotNull Stepped value) {
    getWidget().setStepped(value);
    return (R) this;
  }

  @Override
  default @NotNull R setTension(int value) {
    getWidget().setTension(value);
    return (R) this;
  }

  @Override
  default @NotNull R setPointRadius(double value) {
    getWidget().setPointRadius(value);
    return (R) this;
  }

  @Override
  default @NotNull R setPointStyle(@NotNull PointStyle value) {
    getWidget().setPointStyle(value);
    return (R) this;
  }

  @Override
  default @NotNull R setPointBackgroundColor(@Nullable String value) {
    getWidget().setPointBackgroundColor(value);
    return (R) this;
  }

  @Override
  default @NotNull R setPointBorderColor(@Nullable String value) {
    getWidget().setPointBorderColor(value);
    return (R) this;
  }
}
