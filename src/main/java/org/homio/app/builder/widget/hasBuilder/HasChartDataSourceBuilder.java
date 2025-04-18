package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.HasChartDataSource;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.widget.AggregationType;
import org.jetbrains.annotations.NotNull;

public interface HasChartDataSourceBuilder<T extends BaseEntity & org.homio.app.model.entity.widget.impl.chart.HasChartDataSource, R>
  extends HasChartDataSource<R> {

  T getWidget();

  @Override
  default @NotNull R setChartColor(String value) {
    getWidget().setChartColor(value);
    return (R) this;
  }

  @Override
  default @NotNull R setChartColorOpacity(int value) {
    getWidget().setChartColorOpacity(value);
    return (R) this;
  }

  @Override
  default @NotNull R setChartLabel(String value) {
    getWidget().setChartLabel(value);
    return (R) this;
  }

  @Override
  default @NotNull R setChartDataSource(String value) {
    getWidget().setChartDataSource(value);
    return (R) this;
  }

  @Override
  default @NotNull R setChartAggregationType(@NotNull AggregationType value) {
    getWidget().setChartAggregationType(value);
    return (R) this;
  }

  @Override
  default @NotNull R setFinalChartValueConverter(String value) {
    getWidget().setFinalChartValueConverter(value);
    return (R) this;
  }

  @Override
  default @NotNull R setSmoothing(boolean value) {
    getWidget().setSmoothing(value);
    return (R) this;
  }

  @Override
  default @NotNull R setFillEmptyValues(boolean value) {
    getWidget().setFillEmptyValues(value);
    return (R) this;
  }
}
