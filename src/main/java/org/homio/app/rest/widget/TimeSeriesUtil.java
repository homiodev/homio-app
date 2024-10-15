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

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isEmpty;

@RequiredArgsConstructor
public class TimeSeriesUtil {

    private final ContextImpl context;

    public <T extends HasDynamicParameterFields & HasChartDataSource> WidgetChartsController.TimeSeriesChartData<ChartDataset>
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

                // add update listeners
                if (addUpdateListener) {
                    addChangeListenerForTimeSeriesEntity(item, timePeriod, entityID, series, timeSeriesValues.getSource());
                }
            }
        }

        return timeSeriesChartData;
    }

    public <T extends HasDynamicParameterFields & HasChartDataSource> void addChangeListenerForTimeSeriesEntity(
            TimeSeriesContext<T> timeSeriesContext,
            HasChartTimePeriod.TimePeriod timePeriod, String entityID, Set<T> series, Object source) {

        T item = timeSeriesContext.getSeriesEntity();
        ((HasTimeValueSeries) source).addUpdateValueListener(context, entityID + "_timeSeries",
                item.getChartDynamicParameterFields(),
                o -> {
                    Set<TimeSeriesContext<T>> cts = buildTimeSeriesFromDataSource(timePeriod.snapshot(), item, timeSeriesContext.getSeries());
                    TimeSeriesValues<T> values = timeSeriesContext.getOwner();

                    // if context was updated - we need update rest of values also !!!
                    if (!values.isEqualSeries(cts)) {
                        WidgetChartsController.TimeSeriesChartData<ChartDataset> fullUpdatedData =
                                this.buildTimeSeriesFullData(entityID, timePeriod, false, series);

                        context.ui().sendDynamicUpdateImpl(item.getChartDataSource(), entityID, fullUpdatedData);
                    }
                });
    }

    /**
     * Evaluate single value from specific data source and attach listener on it for dynamic updates
     */
    public <T extends WidgetEntity<T>, R> R getSingleValue(
            @NotNull T entity,
            @Nullable String valueDataSource,
            @Nullable JSONObject dynamicParameters,
            @NotNull Function<Object, R> resultConverter) {
        if (isEmpty(valueDataSource)) {
            return null;
        }
        String seriesEntityId = entity.getEntityID();
        SelectionSource selection = DataSourceUtil.getSelection(valueDataSource);
        Object source = selection.getValue(context);
        if (source == null) {
            return null;
        }

        Object value = getValueFromGetStatusValue(entity, resultConverter, seriesEntityId, dynamicParameters, source, valueDataSource);
        return resultConverter.apply(value);
    }

    public <T extends WidgetEntity<T>, DS extends HasSingleValueDataSource, R> R
    getSingleValue(@NotNull T entity, @NotNull DS dataSource, @NotNull Function<Object, R> resultConverter) {
        String seriesEntityId = ((HasEntityIdentifier) dataSource).getEntityID();
        JSONObject dynamicParameters = dataSource.getValueDynamicParameterFields();
        SelectionSource selection = DataSourceUtil.getSelection(dataSource.getValueDataSource());
        BaseEntity source = selection.getValue(context);
        if (source == null) {
            return (R) "W.ERROR.BAD_SOURCE";
        }
        String dataSourceEntityID = dataSource.getValueDataSource();

        Object value = getValueFromGetStatusValue(entity, resultConverter, seriesEntityId, dynamicParameters, source, dataSourceEntityID);

        return resultConverter.apply(value);
    }

    public <R> void addListenValueIfRequire(@NotNull String entityID, @NotNull Object source,
                                            @Nullable JSONObject dynamicParameters, @Nullable String seriesEntityId, @NotNull String dataSourceEntityID,
                                            @NotNull Function<Object, R> valueSupplier) {
        AtomicReference<R> valueRef = new AtomicReference<>(null);
        String key = Objects.toString(seriesEntityId, entityID);
        ((HasUpdateValueListener) source).addUpdateValueListener(context, key, dynamicParameters,
                o -> {
                    R updatedValue = valueSupplier.apply(o);
                    if (valueRef.get() != updatedValue) {
                        valueRef.set(updatedValue);
                        context.ui().sendDynamicUpdateImpl(dataSourceEntityID, key,
                                new WidgetChartsController.SingleValueData(updatedValue, seriesEntityId));
                    }
                });
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

    private <T extends WidgetEntity<T>, R> Object getValueFromGetStatusValue(
            @NotNull T entity,
            @NotNull Function<Object, R> resultConverter,
            String seriesEntityId,
            JSONObject dynamicParameters,
            Object source,
            String dataSourceEntityID) {

        Object value;
        HasGetStatusValue.GetStatusValueRequest valueRequest = new HasGetStatusValue.GetStatusValueRequest(context, dynamicParameters);
        value = ((HasGetStatusValue) source).getStatusValue(valueRequest);

        addListenValueIfRequire(entity.getEntityID(),
                source, dynamicParameters, seriesEntityId, dataSourceEntityID,
                object -> resultConverter.apply(((HasGetStatusValue) source).getStatusValue(valueRequest)));
        return value;
    }
}
