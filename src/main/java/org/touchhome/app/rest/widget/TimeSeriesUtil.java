package org.touchhome.app.rest.widget;

import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.data.util.Pair;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.app.model.rest.DynamicUpdateRequest;
import org.touchhome.app.model.rest.WidgetDataRequest;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ChartRequest;
import org.touchhome.bundle.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasTimeValueSeries;
import org.touchhome.bundle.api.ui.TimePeriod;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
public class TimeSeriesUtil {
    private final EntityContextImpl entityContext;

    public <E extends WidgetBaseEntityAndSeries<?, T>, T extends WidgetSeriesEntity<?> & HasChartDataSource<?>>
    WidgetChartsController.TimeSeriesChartData<ChartDataset>
    buildTimeSeriesFullData(E entity, TimePeriod.TimePeriodImpl timePeriod,
                            boolean addUpdateListener, Set<T> series) {

        List<TimeSeriesValues<T>> timeSeriesValuesList = new ArrayList<>(series.size());

        for (T item : series) {
            BaseEntity<?> source = entityContext.getEntity(item.getChartDataSource());
            if (source instanceof HasTimeValueSeries) {
                timeSeriesValuesList.add(new TimeSeriesValues<>(item, source,
                        buildTimeSeriesFromDataSource(timePeriod, item, (HasTimeValueSeries) source)));
            }
        }

        WidgetChartsController.TimeSeriesChartData<ChartDataset>
                timeSeriesChartData = new WidgetChartsController.TimeSeriesChartData<>();

        if (!timeSeriesValuesList.isEmpty()) {
            List<Date> dates = EvaluateDatesAndValues.calculateDates(timePeriod, timeSeriesValuesList, entity);
            timeSeriesChartData.getLabels().addAll(EvaluateDatesAndValues.buildLabels(timePeriod, dates));
        }

        for (TimeSeriesValues<T> timeSeriesValues : timeSeriesValuesList) {
            for (TimeSeriesContext<T> item : timeSeriesValues.getItemSeries()) {
                T seriesEntity = item.getSeriesEntity();
                timeSeriesChartData.getDatasets().add(seriesEntity.buildTargetDataset(item));

                // add update listeners
                if (addUpdateListener) {
                    addChangeListenerForTimeSeriesEntity(entity, item, timePeriod, entity.getEntityID(), series);
                }
            }
        }

        return timeSeriesChartData;
    }

    public <T extends WidgetSeriesEntity<?> & HasChartDataSource<?>> void addChangeListenerForTimeSeriesEntity(
            WidgetBaseEntityAndSeries<?, T> entity, TimeSeriesContext<T> timeSeriesContext,
            TimePeriod.TimePeriodImpl timePeriod, String entityID, Set<T> series) {

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
                                this.buildTimeSeriesFullData(entity, timePeriod, false, series);

                        entityContext.ui().sendDynamicUpdate(
                                new DynamicUpdateRequest(item.getChartDataSource(),
                                        WidgetChartsController.TimeSeriesChartData.class.getSimpleName(), entityID),
                                fullUpdatedData);
                    }
                });
    }

    public <T extends WidgetBaseEntity<T>> Object getSingleValue(T entity, BaseEntity<?> source,
                                                                 JSONObject dynamicParameters,
                                                                 WidgetDataRequest request,
                                                                 AggregationType aggregationType,
                                                                 String seriesEntityId) {
        if (source == null) {
            return null;
        }
        Object value;
        if (source instanceof HasGetStatusValue) {
            HasGetStatusValue.GetStatusValueRequest valueRequest =
                    new HasGetStatusValue.GetStatusValueRequest(entityContext, dynamicParameters);

            value = ((HasGetStatusValue) source).getStatusValue(
                    valueRequest);

            if (entity.getListenSourceUpdates()) {
                String key = entity.getEntityID() + seriesEntityId;
                ((HasGetStatusValue) source).addUpdateValueListener(entityContext, key, dynamicParameters,
                        o -> {
                            Object updatedValue = ((HasGetStatusValue) source).getStatusValue(valueRequest);
                            entityContext.ui().sendDynamicUpdate(
                                    new DynamicUpdateRequest(source.getEntityID(),
                                            WidgetChartsController.SingleValueData.class.getSimpleName(), entity.getEntityID()),
                                    new WidgetChartsController.SingleValueData(updatedValue, seriesEntityId));
                        });
            }
        } else {
            value = getAggregateValueFromSeries(request, entity, dynamicParameters, entity.getListenSourceUpdates(),
                    aggregationType, entity.getEntityID(), source.getEntityID(), false);
        }
        return value;
    }

    public <T extends WidgetSeriesEntity<?> & HasChartDataSource<?>> Set<TimeSeriesContext<T>> buildTimeSeriesFromDataSource(
            TimePeriod.TimePeriodImpl timePeriod, T item,
            HasTimeValueSeries source) {
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

    public TimePeriod.TimePeriodImpl buildTimePeriodFromHoursToShow(int hoursToShow, double pointsPerHour) {
        return new TimePeriod.TimePeriodImpl() {
            @Override
            public Pair<Date, Date> getDateRange() {
                return Pair.of(new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hoursToShow)), new Date());
            }

            @Override
            public String getDateFromNow() {
                return "-" + hoursToShow + "h";
            }

            @Override
            public List<Date> evaluateDateRange() {
                long endTime = System.currentTimeMillis();
                long startTime = endTime - TimeUnit.HOURS.toMillis(hoursToShow);
                double requiredNumOfPoints = Math.ceil(hoursToShow * pointsPerHour);
                double diff = (endTime - startTime) / requiredNumOfPoints;
                List<Date> dates = new ArrayList<>();
                for (int i = 0; i < requiredNumOfPoints; i++) {
                    dates.add(new Date((long) (startTime + i * diff)));
                }
                return dates;
            }
        };
    }

    public ChartRequest buildChartRequest(TimePeriod.TimePeriodImpl timePeriod, JSONObject parameters) {
        Pair<Date, Date> range = timePeriod.getDateRange();
        return new ChartRequest(entityContext, range.getFirst(), range.getSecond(), timePeriod.getDateFromNow(),
                !timePeriod.getDateFromNow().equals("0")).setParameters(parameters);
    }

    public Object getAggregateValueFromSeries(WidgetDataRequest request,
                                              BaseEntity<?> source, JSONObject dynamicParameterFieldsHolder,
                                              Boolean listenSourceUpdates, AggregationType aggregationType,
                                              String entityID, String seriesEntityID, boolean filterOnlyNumbers) {
        Object value = null;
        if (source instanceof HasAggregateValueFromSeries) {
            HasAggregateValueFromSeries dataSource = (HasAggregateValueFromSeries) source;
            TimePeriod.TimePeriodImpl timePeriod = TimePeriod.fromValue(request.getTimePeriod()).getTimePeriod();

            ChartRequest chartRequest = buildChartRequest(timePeriod, dynamicParameterFieldsHolder);
            value = dataSource.getAggregateValueFromSeries(chartRequest, aggregationType, filterOnlyNumbers);

            if (listenSourceUpdates) {
                AtomicReference<Object> valueRef = new AtomicReference<>(0F);
                ((HasAggregateValueFromSeries) source).addUpdateValueListener(entityContext, entityID,
                        dynamicParameterFieldsHolder, o -> {
                            ChartRequest updChartRequest =
                                    buildChartRequest(timePeriod, dynamicParameterFieldsHolder);
                            Object updatedValue =
                                    dataSource.getAggregateValueFromSeries(updChartRequest, aggregationType, filterOnlyNumbers);
                            if (valueRef.get() != updatedValue) {
                                valueRef.set(updatedValue);
                                entityContext.ui().sendDynamicUpdate(
                                        new DynamicUpdateRequest(source.getEntityID(),
                                                WidgetChartsController.SingleValueData.class.getSimpleName(),
                                                entityID),
                                        new WidgetChartsController.SingleValueData(updatedValue, seriesEntityID));
                            }
                        });
            }
        }
        return value;
    }
}
