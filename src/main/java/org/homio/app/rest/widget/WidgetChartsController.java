package org.homio.app.rest.widget;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.util.DataSourceUtil.SelectionSource;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.homio.app.model.entity.widget.impl.chart.bar.WidgetBarChartEntity;
import org.homio.app.model.entity.widget.impl.chart.bar.WidgetBarTimeChartEntity;
import org.homio.app.model.entity.widget.impl.chart.doughnut.WidgetDoughnutChartEntity;
import org.homio.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity;
import org.homio.app.model.entity.widget.impl.chart.pie.WidgetPieChartEntity;
import org.homio.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.homio.app.model.entity.widget.impl.display.WidgetDisplaySeriesEntity;
import org.homio.app.model.rest.WidgetDataRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Log4j2
@RestController
@RequestMapping(value = "/rest/widget/chart", produces = "application/json")
public class WidgetChartsController {

  private final ContextImpl context;
  private final ObjectMapper objectMapper;
  private final TimeSeriesUtil timeSeriesUtil;

  public WidgetChartsController(ContextImpl context, ObjectMapper objectMapper) {
    this.context = context;
    this.objectMapper = objectMapper;
    this.timeSeriesUtil = new TimeSeriesUtil(context);
  }

  @PostMapping("/display/series")
  public DisplayDataResponse getDisplayValues(@RequestBody WidgetDataRequest request) {
    WidgetDisplayEntity entity = request.getEntity(context, objectMapper, WidgetDisplayEntity.class);
    List<Object> values = new ArrayList<>(entity.getSeries().size());
    for (WidgetDisplaySeriesEntity item : entity.getSeries()) {
      values.add(timeSeriesUtil.getSingleValue(entity, item, o -> o));
    }

    TimeSeriesChartData<ChartDataset> chartData = null;
    if (StringUtils.isNotEmpty(entity.getChartDataSource())) {
      chartData = timeSeriesUtil.buildTimeSeriesFullData(
        entity.getEntityID(),
        entity.buildTimePeriod(),
        true,
        Collections.singleton(entity));
    }

    return new DisplayDataResponse(values, chartData);
  }

  @PostMapping("/line/series")
  public TimeSeriesChartData<ChartDataset> getChartSeries(@Valid @RequestBody WidgetDataRequest request) {
    WidgetLineChartEntity entity = request.getEntity(context, objectMapper, WidgetLineChartEntity.class);

    return timeSeriesUtil.buildTimeSeriesFullData(
      entity.getEntityID(),
      entity.buildTimePeriod(),
      true,
      entity.getSeries());
  }

  @PostMapping("/bartime/series")
  public TimeSeriesChartData<ChartDataset> getBartimeSeries(
    @Valid @RequestBody WidgetDataRequest request) {
    WidgetBarTimeChartEntity entity = request.getEntity(context, objectMapper, WidgetBarTimeChartEntity.class);

    return timeSeriesUtil.buildTimeSeriesFullData(
      entity.getEntityID(),
      entity.buildTimePeriod(),
      true,
      entity.getSeries());
  }

  @PostMapping("/bar/series")
  public TimeSeriesChartData<ChartDataset> getBarSeries(
    @Valid @RequestBody WidgetDataRequest request) {
    return getValueDataset(request, WidgetBarChartEntity.class);
  }

  @PostMapping("/pie/series")
  public TimeSeriesChartData<ChartDataset> getPieSeries(
    @Valid @RequestBody WidgetDataRequest request) {
    return getValueDataset(request, WidgetPieChartEntity.class);
  }

  @PostMapping("/doughnut/series")
  public TimeSeriesChartData<ChartDataset> getDoughnutSeries(
    @Valid @RequestBody WidgetDataRequest request) {
    WidgetDoughnutChartEntity entity =
      request.getEntity(context, objectMapper, WidgetDoughnutChartEntity.class);
    TimeSeriesChartData<ChartDataset> result =
      getValueDataset(request, WidgetDoughnutChartEntity.class);

    SelectionSource selection = DataSourceUtil.getSelection(entity.getValueDataSource());
    if (StringUtils.isNotEmpty(selection.getValue())) {
      result.value = timeSeriesUtil.getSingleValue(entity, entity);
    }

    return result;
  }

  private <S extends WidgetSeriesEntity<T> & HasSingleValueDataSource, T extends ChartBaseEntity<T, S>> TimeSeriesChartData<ChartDataset>
  getValueDataset(WidgetDataRequest request, Class<T> chartClass) {
    T entity = request.getEntity(context, objectMapper, chartClass);
    TimeSeriesChartData<ChartDataset> timeSeriesChartData = new TimeSeriesChartData<>();

    for (S item : entity.getSeries()) {
      Object value = timeSeriesUtil.getSingleValue(entity, item);

      ChartDataset dataset = new ChartDataset(item.getEntityID(), item.getEntityID());
      timeSeriesChartData.datasets.add(dataset);
      dataset.setData(Collections.singletonList(value == null ? 0F : ((Number) value).floatValue()));
    }
    return timeSeriesChartData;
  }

  @Getter
  @Setter
  public static class TimeSeriesChartData<T extends ChartDataset> {

    private final List<T> datasets = new ArrayList<>();
    private Object value; // for doughnut
    private List<Long> timestamp;
  }

  public record SingleValueData(Object value, String seriesEntityID) {
  }

  public record DisplayDataResponse(List<Object> values, TimeSeriesChartData<ChartDataset> chart) {

  }
}
