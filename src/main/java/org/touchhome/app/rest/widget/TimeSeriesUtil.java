package org.touchhome.app.rest.widget;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.HasTimePeriod;
import org.touchhome.app.model.rest.DynamicUpdateRequest;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ChartRequest;
import org.touchhome.bundle.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasTimeValueSeries;
import org.touchhome.bundle.api.entity.widget.ability.HasUpdateValueListener;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.defaultString;

@RequiredArgsConstructor
public class TimeSeriesUtil {
    private final EntityContextImpl entityContext;

    public <T extends HasDynamicParameterFields & HasChartDataSource> WidgetChartsController.TimeSeriesChartData<ChartDataset>
    buildTimeSeriesFullData(String entityID, HasTimePeriod.TimePeriod timePeriod,
                            boolean addUpdateListener, Set<T> series) {

        List<TimeSeriesValues<T>> timeSeriesValuesList = new ArrayList<>(series.size());

        for (T item : series) {
            BaseEntity<?> source = entityContext.getEntity(item.getChartDataSource());
            if (source instanceof HasTimeValueSeries) {
                timeSeriesValuesList.add(new TimeSeriesValues<>(source,
                        buildTimeSeriesFromDataSource(timePeriod, item, (HasTimeValueSeries) source)));
            }
        }

        WidgetChartsController.TimeSeriesChartData<ChartDataset>
                timeSeriesChartData = new WidgetChartsController.TimeSeriesChartData<>();

        if (!timeSeriesValuesList.isEmpty()) {
            List<Date> dates = EvaluateDatesAndValues.calculateDates(timePeriod, timeSeriesValuesList);
            timeSeriesChartData.setTimestamp(dates.stream().map(Date::getTime).collect(Collectors.toList()));
        }

        for (TimeSeriesValues<T> timeSeriesValues : timeSeriesValuesList) {
            for (TimeSeriesContext<T> item : timeSeriesValues.getItemSeries()) {
                T seriesEntity = item.getSeriesEntity();
                timeSeriesChartData.getDatasets().add(seriesEntity.buildTargetDataset(item));

                // add update listeners
                if (addUpdateListener) {
                    addChangeListenerForTimeSeriesEntity(item, timePeriod, entityID, series);
                }
            }
        }

        return timeSeriesChartData;
    }

    public <T extends HasDynamicParameterFields & HasChartDataSource> void addChangeListenerForTimeSeriesEntity(
            TimeSeriesContext<T> timeSeriesContext,
            HasTimePeriod.TimePeriod timePeriod, String entityID, Set<T> series) {

        T item = timeSeriesContext.getSeriesEntity();
        BaseEntity<?> source = entityContext.getEntity(item.getChartDataSource());
        ((HasTimeValueSeries) source).addUpdateValueListener(entityContext, entityID + "_timeSeries",
                item.getChartDynamicParameterFields(),
                o -> {
                    Set<TimeSeriesContext<T>> cts =
                            buildTimeSeriesFromDataSource(timePeriod, item, timeSeriesContext.getSeries());
                    TimeSeriesValues<T> values = timeSeriesContext.getOwner();

                    // if context was updated - we need update rest of values also !!!
                    if (!values.isEqualSeries(cts)) {
                        WidgetChartsController.TimeSeriesChartData<ChartDataset> fullUpdatedData =
                                this.buildTimeSeriesFullData(entityID, timePeriod, false, series);

                        entityContext.ui().sendDynamicUpdate(
                                new DynamicUpdateRequest(item.getChartDataSource(),
                                        WidgetChartsController.TimeSeriesChartData.class.getSimpleName(), entityID),
                                fullUpdatedData);
                    }
                });
    }

    public <T extends WidgetBaseEntity<T>, DS extends HasSingleValueDataSource & HasEntityIdentifier, R> R
    getSingleValue(@NotNull T entity,
                   @NotNull DS dataSource,
                   @NotNull Function<Object, R> resultConverter) {
        Pair<String, String> pair = dataSource.getSingleValueDataSource();
        String inheritValue = pair.getValue();
        String seriesEntityId = dataSource.getEntityID();
        JSONObject dynamicParameters = dataSource.getValueDynamicParameterFields();
        BaseEntity<?> source = entityContext.getEntity(pair.getKey());
        AggregationType aggregationType = dataSource.getAggregationType();
        if (source == null) {
            return null;
        }
        String dataSourceEntityID = dataSource.getValueDataSource();

        Object value;
        boolean aggregateGetter;
        if (source instanceof HasGetStatusValue || source instanceof HasAggregateValueFromSeries) {
            if (StringUtils.isNotEmpty(inheritValue)) {
                aggregateGetter = "HasAggregateValueFromSeries".equals(inheritValue);
            } else {
                aggregateGetter = source instanceof HasAggregateValueFromSeries;
            }
        } else {
            throw new IllegalStateException("Unable to calculate value for: " + entity.getEntityID());
        }

        if (aggregateGetter) {
            if (!(entity instanceof HasTimePeriod)) {
                throw new IllegalStateException("Entity with type " + entity.getTitle() + " with aggregation data source " +
                        "has to implement HasTimePeriod");
            }
            ChartRequest chartRequest = buildChartRequest(((HasTimePeriod) entity).buildTimePeriod(), dynamicParameters);
            value = ((HasAggregateValueFromSeries) source).getAggregateValueFromSeries(chartRequest, aggregationType, false);

            addListenValueIfRequire(entity.getListenSourceUpdates(), entity.getEntityID(), source, dynamicParameters,
                    seriesEntityId,
                    dataSourceEntityID,
                    () -> resultConverter.apply(((HasAggregateValueFromSeries) source).getAggregateValueFromSeries(
                            buildChartRequest(((HasTimePeriod) entity).buildTimePeriod(), dynamicParameters),
                            aggregationType, false)));
        } else {
            HasGetStatusValue.GetStatusValueRequest valueRequest =
                    new HasGetStatusValue.GetStatusValueRequest(entityContext, dynamicParameters);
            value = ((HasGetStatusValue) source).getStatusValue(valueRequest);

            addListenValueIfRequire(entity.getListenSourceUpdates(), entity.getEntityID(), source, dynamicParameters,
                    seriesEntityId, dataSourceEntityID,
                    () -> resultConverter.apply(((HasGetStatusValue) source).getStatusValue(valueRequest)));
        }

        return resultConverter.apply(value);
    }

    public  <R> void addListenValueIfRequire(boolean listenSourceUpdates,
                                             @NotNull String entityID,
                                             @NotNull BaseEntity<?> source,
                                             @Nullable JSONObject dynamicParameters,
                                             @Nullable String seriesEntityId,
                                             @Nullable String dataSourceEntityID,
                                             @NotNull Supplier<R> valueSupplier) {
        if (listenSourceUpdates) {
            AtomicReference<R> valueRef = new AtomicReference<>(null);
            String key = entityID + defaultString(seriesEntityId, "");
            ((HasUpdateValueListener) source).addUpdateValueListener(entityContext, key, dynamicParameters,
                    o -> {
                        R updatedValue = valueSupplier.get();
                        if (valueRef.get() != updatedValue) {
                            valueRef.set(updatedValue);
                            entityContext.ui().sendDynamicUpdate(
                                    new DynamicUpdateRequest(dataSourceEntityID,
                                            WidgetChartsController.SingleValueData.class.getSimpleName(), entityID),
                                    new WidgetChartsController.SingleValueData(updatedValue,
                                            seriesEntityId));
                        }
                    });
        }
    }

    public <T extends HasDynamicParameterFields & HasChartDataSource> Set<TimeSeriesContext<T>>
    buildTimeSeriesFromDataSource(HasTimePeriod.TimePeriod timePeriod, T item, HasTimeValueSeries source) {
        Set<TimeSeriesContext<T>> result = new HashSet<>();
        ChartRequest chartRequest = buildChartRequest(timePeriod, item.getChartDynamicParameterFields());

        Map<HasTimeValueSeries.TimeValueDatasetDescription, List<Object[]>> timeSeries =
                source.getMultipleTimeValueSeries(chartRequest);

        for (Map.Entry<HasTimeValueSeries.TimeValueDatasetDescription, List<Object[]>> entry : timeSeries.entrySet()) {
            // convert chartItem[0] to long if it's a Date type
            if (!entry.getValue().isEmpty() && entry.getValue().get(0)[0] instanceof Date) {
                for (Object[] chartItem : entry.getValue()) {
                    chartItem[0] = ((Date) chartItem[0]).getTime();
                }
            }
            HasTimeValueSeries.TimeValueDatasetDescription tvd = entry.getKey();
            result.add(new TimeSeriesContext<>(tvd.getId(), item, source).setValue(entry.getValue()));
        }
        return result;
    }

    public ChartRequest buildChartRequest(HasTimePeriod.TimePeriod timePeriod, JSONObject parameters) {
        return new ChartRequest(entityContext, timePeriod.getFrom(), timePeriod.getTo()).setParameters(parameters);
    }
}
