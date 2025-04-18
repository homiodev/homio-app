package org.homio.app.rest.widget;

import lombok.RequiredArgsConstructor;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.widget.PeriodRequest;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasGetStatusValue.GetStatusValueRequest;
import org.homio.api.entity.widget.ability.HasTimeValueSeries;
import org.homio.api.entity.widget.ability.HasUpdateValueListener;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.ui.field.selection.dynamic.HasDynamicParameterFields;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.util.DataSourceUtil.SelectionSource;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.attributes.HasChartTimePeriod;
import org.homio.app.model.entity.widget.attributes.HasChartTimePeriod.TimeRange;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.impl.chart.HasChartDataSource;
import org.homio.app.service.mem.InMemoryDB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isEmpty;

// Ugly
@RequiredArgsConstructor
public class TimeSeriesUtil {

  private final ContextImpl context;

  public <T extends HasDynamicParameterFields & HasChartDataSource & HasEntityIdentifier> WidgetChartsController.TimeSeriesChartData<ChartDataset>
  buildTimeSeriesFullData(String entityID, HasChartTimePeriod.TimePeriod timePeriod,
                          boolean addUpdateListener, Set<T> series) {

    List<TimeSeriesValues<T>> timeSeriesValuesList = new ArrayList<>(series.size());

    // create timestamp snapshot
    TimeRange timeRange = timePeriod.snapshot();

    for (T item : series) {
      SelectionSource selection = DataSourceUtil.getSelection(item.getChartDataSource());
      BaseEntity source = selection.getValue(context);
      if (source instanceof HasTimeValueSeries) {
        Set<TimeSeriesContext<T>> timeSeries = buildTimeSeriesFromDataSource(timeRange, item, (HasTimeValueSeries) source);
        timeSeriesValuesList.add(new TimeSeriesValues<>(timeSeries, source));
      }
    }

    WidgetChartsController.TimeSeriesChartData<ChartDataset>
      timeSeriesChartData = new WidgetChartsController.TimeSeriesChartData<>();

    if (!timeSeriesValuesList.isEmpty()) {
      List<Date> dates = EvaluateDatesAndValues.calculateDates(timeRange, timeSeriesValuesList);
      timeSeriesChartData.setTimestamp(dates.stream().map(Date::getTime).collect(Collectors.toList()));
    }

    for (TimeSeriesValues<T> timeSeriesValues : timeSeriesValuesList) {
      for (TimeSeriesContext<T> item : timeSeriesValues.getItemSeries()) {
        T seriesEntity = item.getSeriesEntity();
        timeSeriesChartData.getDatasets().add(seriesEntity.buildTargetDataset(item));
        String seriesEntityID = seriesEntity.getEntityID();

        // add update listeners
        if (addUpdateListener) {
          addChangeListenerForTimeSeriesEntity(item, timePeriod, seriesEntityID, series, timeSeriesValues.getSource());
        }
      }
    }

    return timeSeriesChartData;
  }

  public <T extends HasDynamicParameterFields & HasChartDataSource & HasEntityIdentifier> void addChangeListenerForTimeSeriesEntity(
    @NotNull TimeSeriesContext<T> timeSeriesContext,
    @NotNull HasChartTimePeriod.TimePeriod timePeriod,
    @NotNull String entityID,
    @NotNull Set<T> series,
    @NotNull Object source) {

    HasTimeValueSeries sourceValue = (HasTimeValueSeries) source;
    T item = timeSeriesContext.getSeriesEntity();
    String dataSourceEntityID = item.getChartDataSource();
    var listener = (sourceValue).addUpdateValueListener(context, entityID,
      Duration.ofSeconds(60), item.getChartDynamicParameterFields(),
      o -> {
        var cts = buildTimeSeriesFromDataSource(timePeriod.snapshot(), item, timeSeriesContext.getSeries());
        boolean isEqualContext = timeSeriesContext.getOwner().isEqualSeries(cts);

        // if context was updated - we need update rest of values also !!!
        if (!isEqualContext) {
          WidgetChartsController.TimeSeriesChartData<ChartDataset> fullUpdatedData =
            this.buildTimeSeriesFullData(entityID, timePeriod, false, series);

          context.ui().sendDynamicUpdateImpl(dataSourceEntityID, entityID, fullUpdatedData);
        }
      });
    context.ui().setUpdateListenerRefreshHandler(dataSourceEntityID, entityID, listener);
  }

  public <T extends WidgetEntity<T>, DS extends HasSingleValueDataSource, R> R getSingleValue(
    @NotNull T entity,
    @NotNull DS dataSource) {
    return (R) getSingleValue(entity, dataSource, o -> o);
  }

  public <T extends WidgetEntity<T>, DS extends HasSingleValueDataSource, R> R getSingleValue(
    @NotNull T entity,
    @NotNull DS dataSource,
    @NotNull Function<Object, R> resultConverter) {
    return getSingleValue(entity, dataSource.getEntityID(), dataSource.getValueDataSource(),
      dataSource.getValueDynamicParameterFields(), resultConverter);
  }

  public <T extends WidgetEntity<T>, R> R getSingleValue(
    @NotNull T entity,
    @NotNull String seriesEntityId,
    @Nullable String dataSourceEntityID,
    @Nullable JSONObject dynamicParameters) {
    return (R) getSingleValue(entity, seriesEntityId, dataSourceEntityID, dynamicParameters, o -> o);
  }

  /**
   * Evaluate single value from specific data source and attach listener on it for dynamic updates
   */
  public <T extends WidgetEntity<T>, R> R getSingleValue(
    @NotNull T entity,
    @NotNull String seriesEntityId,
    @Nullable String dataSourceEntityID,
    @Nullable JSONObject dynamicParameters,
    @NotNull Function<Object, R> resultConverter) {
    if (isEmpty(dataSourceEntityID)) {
      return null;
    }
    SelectionSource selection = DataSourceUtil.getSelection(dataSourceEntityID);
    Object source = selection.getValue(context);
    if (source == null) {
      return (R) "W.ERROR.BAD_SOURCE";
    }

    var param = new TimeSeriesUtil.RequestParameters(entity.getEntityID(), source, dynamicParameters, seriesEntityId, dataSourceEntityID);
    return getValueFromGetStatusValue(resultConverter, dynamicParameters, source, param);
  }

  public <R> void addListenValueIfRequire(@NotNull RequestParameters request,
                                          @NotNull Function<Object, R> valueSupplier) {
    AtomicReference<R> valueRef = new AtomicReference<>(null);
    String key = request.getKey();
    var refreshHandler = ((HasUpdateValueListener) request.source).addUpdateValueListener(context, key, Duration.ofSeconds(60), request.dynamicParameters,
      o -> {
        R updatedValue = valueSupplier.apply(o);
        if (valueRef.get() != updatedValue) {
          valueRef.set(updatedValue);
          context.ui().sendDynamicUpdateImpl(request.dataSourceEntityID, key,
            new WidgetChartsController.SingleValueData(updatedValue, request.seriesEntityId));
        }
      });
    context.ui().setUpdateListenerRefreshHandler(request.dataSourceEntityID, key, refreshHandler);
  }

  public <T extends HasDynamicParameterFields & HasChartDataSource> Set<TimeSeriesContext<T>>
  buildTimeSeriesFromDataSource(HasChartTimePeriod.TimeRange timeRange, T item, HasTimeValueSeries source) {
    Set<TimeSeriesContext<T>> result = new HashSet<>();
    PeriodRequest periodRequest = new PeriodRequest(context, timeRange.getFrom(), timeRange.getTo())
      .setParameters(item.getChartDynamicParameterFields());

    Map<HasTimeValueSeries.TimeValueDatasetDescription, List<Object[]>> timeSeries =
      source.getMultipleTimeValueSeries(periodRequest);

    for (Map.Entry<HasTimeValueSeries.TimeValueDatasetDescription, List<Object[]>> entry : timeSeries.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        // convert chartItem[0] to long if it's a Date type
        if (entry.getValue().get(0)[0] instanceof Date) {
          for (Object[] chartItem : entry.getValue()) {
            chartItem[0] = ((Date) chartItem[0]).getTime();
          }
        }
      } else if (item.getFillEmptyValues()) { // we need find at least one value to fill chart if no data at all
        Object value = source.getStatusValue(new GetStatusValueRequest(context, item.getChartDynamicParameterFields()));
        Long timestamp = periodRequest.getFromTime() == null ? periodRequest.getToTime() : periodRequest.getFromTime();
        entry.setValue(Collections.singletonList(new Object[]{timestamp, InMemoryDB.toNumber(value)}));
      }
      HasTimeValueSeries.TimeValueDatasetDescription tvd = entry.getKey();
      result.add(new TimeSeriesContext<>(tvd.getId(), item, source).setValue(entry.getValue()));
    }
    return result;
  }

  private <T extends WidgetEntity<T>, R> R getValueFromGetStatusValue(
    @NotNull Function<Object, R> resultConverter,
    JSONObject dynamicParameters,
    Object source,
    TimeSeriesUtil.RequestParameters param) {

    Object value;
    HasGetStatusValue.GetStatusValueRequest valueRequest = new HasGetStatusValue.GetStatusValueRequest(context, dynamicParameters);
    value = ((HasGetStatusValue) source).getStatusValue(valueRequest);

    addListenValueIfRequire(param, o -> resultConverter.apply(((HasGetStatusValue) source).getStatusValue(valueRequest)));
    return resultConverter.apply(value);
  }

  public record RequestParameters(String entityID,
                                  Object source,
                                  JSONObject dynamicParameters,
                                  String seriesEntityId,
                                  String dataSourceEntityID) {
    public String getKey() {
      return Objects.toString(seriesEntityId, entityID);
    }
  }
}
