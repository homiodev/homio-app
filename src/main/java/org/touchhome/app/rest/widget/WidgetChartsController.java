package org.touchhome.app.rest.widget;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.data.util.Pair;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.app.model.entity.widget.impl.chart.TimeSeriesChartBaseEntity;
import org.touchhome.app.model.entity.widget.impl.chart.bar.WidgetBarChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.bar.WidgetBarChartSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.chart.bar.WidgetBarTimeChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetMiniCardChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetMiniCardChartSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.chart.pie.WidgetPieChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.pie.WidgetPieChartSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.gauge.WidgetGaugeEntity;
import org.touchhome.app.model.rest.DynamicUpdateRequest;
import org.touchhome.app.model.rest.WidgetDataRequest;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.*;
import org.touchhome.bundle.api.ui.TimePeriod;

import javax.validation.Valid;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Log4j2
@RestController
@RequestMapping("/rest/widget/chart")
@RequiredArgsConstructor
public class WidgetChartsController {

    private final EntityContextImpl entityContext;
    private final ObjectMapper objectMapper;

    @PostMapping("/minicard/series")
    public TimeSeriesChartData<ChartDataset> getMiniCardChartSeries(@Valid @RequestBody WidgetDataRequest request) {
        WidgetMiniCardChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetMiniCardChartEntity.class);
        double pointsPerHour = entity.getPointsPerHour();

        TimePeriod.TimePeriodImpl timePeriod = buildTimePeriodFromHoursToShow(entity.getHoursToShow(), pointsPerHour);

        // build fake series
        WidgetMiniCardChartSeriesEntity series = new WidgetMiniCardChartSeriesEntity();
        series.setDataSource(entity.getDataSource());
        series.setDynamicParameterFieldsHolder(entity.getDynamicParameterFieldsHolder());

        entity.setSeries(Collections.singleton(series));
        TimeSeriesChartData<ChartDataset> data =
                buildTimeSeriesFullData(entity, timePeriod, entity.getListenSourceUpdates());

        BaseEntity<?> source = entityContext.getEntity(entity.getDataSource());

        // actually it must be type HasTimeValueAndLastValueSeries but we checking against null
        if (source instanceof HasTimeValueAndLastValueSeries) {
            data.value =
                    ((HasTimeValueAndLastValueSeries) source).getLastAvailableValue(entity.getDynamicParameterFields());
        }
        return data;
    }

    @PostMapping("/line/series")
    public TimeSeriesChartData<ChartDataset> getChartSeries(@Valid @RequestBody WidgetDataRequest request) {
        WidgetLineChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetLineChartEntity.class);
        TimePeriod.TimePeriodImpl timePeriod = TimePeriod.fromValue(request.getTimePeriod()).getTimePeriod();

        return buildTimeSeriesFullData(entity, timePeriod, entity.getListenSourceUpdates());
    }

    @PostMapping("/bartime/series")
    public TimeSeriesChartData<ChartDataset> getBartimeSeries(@Valid @RequestBody WidgetDataRequest request) {
        WidgetBarTimeChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetBarTimeChartEntity.class);
        TimePeriod.TimePeriodImpl timePeriod = TimePeriod.fromValue(request.getTimePeriod()).getTimePeriod();

        return buildTimeSeriesFullData(entity, timePeriod, entity.getListenSourceUpdates());
    }

    @PostMapping("/bar/series")
    public TimeSeriesChartData<BarChartDataset> getBarSeries(@Valid @RequestBody WidgetDataRequest request) {
        WidgetBarChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetBarChartEntity.class);
        TimePeriod.TimePeriodImpl timePeriod = TimePeriod.fromValue(request.getTimePeriod()).getTimePeriod();
        TimeSeriesChartData<BarChartDataset> timeSeriesChartData = new TimeSeriesChartData<>();
        timeSeriesChartData.labels.add(entity.getAxisLabel());

        for (ChartContext<WidgetBarChartSeriesEntity> context : filterSeries(entity, WidgetBarChartSeriesEntity.class)) {
            BarChartDataset dataset = new BarChartDataset(context.source.getEntityID() + "___" + System.currentTimeMillis());
            WidgetBarChartSeriesEntity item = context.item;

            dataset.setLabel(defaultString(item.getName(), context.source.getTitle()));
            dataset.setBorderWidth(entity.getBorderWidth());

            String color = item.getChartColor();
            dataset.setBackgroundColor(Collections.singletonList(getColorWithOpacity(color, item.getChartColorOpacity())));
            dataset.setBorderColor(Collections.singletonList(color));
            timeSeriesChartData.datasets.add(dataset);

            AggregationType aggregationType = item.getAggregationType();
            ChartRequest chartRequest = buildChartRequest(timePeriod, item.getDynamicParameterFields());
            dataset.setData(Collections.singletonList(calcFloatValue(chartRequest, aggregationType,
                    (HasAggregateValueFromSeries) context.source)));

            if (entity.getListenSourceUpdates()) {
                ((HasAggregateValueFromSeries) context.source).addUpdateValueListener(entityContext, entity.getEntityID(),
                        item.getDynamicParameterFields(), o -> {
                            ChartRequest updChartRequest = buildChartRequest(timePeriod, item.getDynamicParameterFields());
                            dataset.setData(Collections.singletonList(calcFloatValue(updChartRequest, aggregationType,
                                    (HasAggregateValueFromSeries) context.source)));
                            entityContext.ui().sendDynamicUpdate(
                                    new DynamicUpdateRequest(item.getDataSource(), BarChartDataset.class.getSimpleName(),
                                            entity.getEntityID()), dataset);
                        });
            }
        }
        return timeSeriesChartData;
    }

    @PostMapping("/pie/series")
    public TimeSeriesChartData<PieChartDataset> getPieSeries(@Valid @RequestBody WidgetDataRequest request) {
        WidgetPieChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetPieChartEntity.class);
        String widgetEntityID = entity.getEntityID();
        TimePeriod.TimePeriodImpl timePeriod = TimePeriod.fromValue(request.getTimePeriod()).getTimePeriod();
        TimeSeriesChartData<PieChartDataset> timeSeriesChartData = new TimeSeriesChartData<>();
        PieChartDataset dataset = new PieChartDataset(widgetEntityID + "___" + System.currentTimeMillis());
        timeSeriesChartData.datasets.add(dataset);
        dataset.setBackgroundColor(new ArrayList<>());
        dataset.setBorderColor(new ArrayList<>());
        dataset.setData(new ArrayList<>());
        dataset.setBorderWidth(entity.getBorderWidth());

        for (ChartContext<WidgetPieChartSeriesEntity> context : filterSeries(entity, WidgetPieChartSeriesEntity.class)) {
            WidgetPieChartSeriesEntity item = context.item;

            timeSeriesChartData.labels.add(defaultString(item.getName(), context.source.getTitle()));

            String color = item.getChartColor();
            dataset.getBackgroundColor().add(getColorWithOpacity(color, item.getChartColorOpacity()));
            dataset.getBorderColor().add(color);

            AggregationType aggregationType = item.getAggregationType();
            ChartRequest chartRequest = buildChartRequest(timePeriod, item.getDynamicParameterFields());
            dataset.getData().add(calcFloatValue(chartRequest, aggregationType, (HasAggregateValueFromSeries) context.source));

            // listen DataSource changed events, convert to BarSeries and put again in even bus
            if (entity.getListenSourceUpdates()) {
                ((HasAggregateValueFromSeries) context.source).addUpdateValueListener(entityContext, entity.getEntityID(),
                        item.getDynamicParameterFields(), o -> {
                            ChartRequest updChartRequest = buildChartRequest(timePeriod, item.getDynamicParameterFields());
                            dataset.getData().set(context.index, calcFloatValue(updChartRequest, aggregationType,
                                    (HasAggregateValueFromSeries) context.source));
                            entityContext.ui().sendDynamicUpdate(
                                    new DynamicUpdateRequest(item.getDataSource(), PieChartDataset.class.getSimpleName(),
                                            widgetEntityID), dataset);
                        });
            }
        }
        return timeSeriesChartData;
    }

    @PostMapping("/gauge/value")
    public Object getGaugeValue(@Valid @RequestBody WidgetDataRequest request) {
        WidgetGaugeEntity entity = request.getEntity(entityContext, objectMapper, WidgetGaugeEntity.class);

        BaseEntity baseEntity = entityContext.getEntity(entity.getDataSource());
        return getAggregateValueFromSeries(request, entityContext, baseEntity,
                entity.getDynamicParameterFields(), entity.getListenSourceUpdates(),
                entity.getAggregationType(), entity.getEntityID(), null, true);
    }

    public static Object getAggregateValueFromSeries(WidgetDataRequest request, EntityContextImpl entityContext,
                                                     BaseEntity<?> source,
                                                     JSONObject dynamicParameterFieldsHolder, Boolean listenSourceUpdates,
                                                     AggregationType aggregationType, String entityID,
                                                     String seriesEntityID,
                                                     boolean filterOnlyNumbers) {
        Object value = null;
        if (source instanceof HasAggregateValueFromSeries) {
            HasAggregateValueFromSeries dataSource = (HasAggregateValueFromSeries) source;
            TimePeriod.TimePeriodImpl timePeriod = TimePeriod.fromValue(request.getTimePeriod()).getTimePeriod();

            ChartRequest chartRequest = buildChartRequest(timePeriod, entityContext, dynamicParameterFieldsHolder);
            value = dataSource.getAggregateValueFromSeries(chartRequest, aggregationType, filterOnlyNumbers);

            if (listenSourceUpdates) {
                AtomicReference<Object> valueRef = new AtomicReference<>(0F);
                ((HasAggregateValueFromSeries) source).addUpdateValueListener(entityContext, entityID,
                        dynamicParameterFieldsHolder, o -> {
                            ChartRequest updChartRequest =
                                    buildChartRequest(timePeriod, entityContext, dynamicParameterFieldsHolder);
                            Object updatedValue =
                                    dataSource.getAggregateValueFromSeries(updChartRequest, aggregationType, filterOnlyNumbers);
                            if (valueRef.get() != updatedValue) {
                                valueRef.set(updatedValue);
                                entityContext.ui().sendDynamicUpdate(
                                        new DynamicUpdateRequest(source.getEntityID(), SingleValueData.class.getSimpleName(),
                                                entityID), new SingleValueData(updatedValue, seriesEntityID));
                            }
                        });
            }
        }
        return value;
    }

    private static ChartRequest buildChartRequest(TimePeriod.TimePeriodImpl timePeriod, EntityContextImpl entityContext,
                                                  JSONObject parameters) {
        Pair<Date, Date> range = timePeriod.getDateRange();
        return new ChartRequest(entityContext, range.getFirst(), range.getSecond(), timePeriod.getDateFromNow(),
                !timePeriod.getDateFromNow().equals("0")).setParameters(parameters);
    }

    private ChartRequest buildChartRequest(TimePeriod.TimePeriodImpl timePeriod, JSONObject parameters) {
        return buildChartRequest(timePeriod, entityContext, parameters);
    }

    public static float calcFloatValue(ChartRequest chartRequest, AggregationType aggregationType,
                                       HasAggregateValueFromSeries source) {
        Float value = (Float) source.getAggregateValueFromSeries(chartRequest, aggregationType, true);
        return value == null ? 0F : value;
    }

    public static String getColorWithOpacity(String color, int colorOpacity) {
        if (StringUtils.isNotEmpty(color) && color.startsWith("#") && color.length() == 7) {
            return color + String.format("%02X", colorOpacity);
        }
        return color;
    }

    private <T extends WidgetSeriesEntity> List<ChartContext<T>> filterSeries(ChartBaseEntity entity, Class<T> tClass) {
        if (entity.getSeries() == null) {
            return Collections.emptyList();
        }
        List<ChartContext<T>> result =
                ((Set<T>) entity.getSeries()).stream().filter(s -> isNotEmpty(s.getDataSource())).map(s -> {
                    BaseEntity source = entityContext.getEntity(s.getDataSource());
                    return new ChartContext<>(0, s, source instanceof HasAggregateValueFromSeries ? source : null);
                }).filter(ctx -> ctx.source != null).collect(Collectors.toList());
        for (int i = 0; i < result.size(); i++) {
            result.get(i).index = i;
        }
        return result;
    }

    private TimeSeriesChartData<ChartDataset> buildTimeSeriesFullData(TimeSeriesChartBaseEntity entity,
                                                                      TimePeriod.TimePeriodImpl timePeriod,
                                                                      boolean addUpdateListener) {
        Set<WidgetSeriesEntity> series = entity.getSeries();
        List<TimeSeriesValues> timeSeriesValuesList = new ArrayList<>(series.size());

        for (WidgetSeriesEntity item : series) {
            BaseEntity<?> source = entityContext.getEntity(item.getDataSource());
            if (source instanceof HasTimeValueSeries) {
                timeSeriesValuesList.add(new TimeSeriesValues(item, source,
                        buildTimeSeriesFromDataSource(timePeriod, item, (HasTimeValueSeries) source)));
            }
        }

        List<Date> dates = EvaluateDatesAndValues.calculateDates(timePeriod, timeSeriesValuesList, entity);

        TimeSeriesChartData<ChartDataset> timeSeriesChartData = new TimeSeriesChartData<>();
        timeSeriesChartData.getLabels().addAll(EvaluateDatesAndValues.buildLabels(timePeriod, dates));

        for (TimeSeriesValues timeSeriesValues : timeSeriesValuesList) {
            for (TimeSeriesContext item : timeSeriesValues.getItemSeries()) {
                timeSeriesChartData.getDatasets().add(entity.buildTargetDataset(item));

                // add update listeners
                if (addUpdateListener) {
                    addChangeListenerForTimeSeriesEntity(entity, item, timePeriod, entity.getEntityID());
                }
            }
        }
        return timeSeriesChartData;
    }

    private void addChangeListenerForTimeSeriesEntity(TimeSeriesChartBaseEntity entity,
                                                      TimeSeriesContext timeSeriesContext,
                                                      TimePeriod.TimePeriodImpl timePeriod,
                                                      String entityID) {
        WidgetSeriesEntity item = timeSeriesContext.getSeriesEntity();
        BaseEntity<?> source = entityContext.getEntity(item.getDataSource());
        ((HasTimeValueSeries) source).addUpdateValueListener(entityContext, entityID, item.getDynamicParameterFields(),
                o -> {
                    Set<TimeSeriesContext> cts =
                            buildTimeSeriesFromDataSource(timePeriod, item, timeSeriesContext.getSeries());
                    TimeSeriesValues values = timeSeriesContext.getOwner();

                    // if context was updated - we need update rest of values also !!!
                    if (!values.isEqualSeries(cts)) {
                        TimeSeriesChartData<ChartDataset> fullUpdatedData =
                                this.buildTimeSeriesFullData(entity, timePeriod, false);

                        if (source instanceof HasTimeValueAndLastValueSeries) {
                            fullUpdatedData.value =
                                    ((HasTimeValueAndLastValueSeries) source).getLastAvailableValue(
                                            item.getDynamicParameterFields());
                        }

                        entityContext.ui().sendDynamicUpdate(
                                new DynamicUpdateRequest(item.getDataSource(), TimeSeriesChartData.class.getSimpleName(),
                                        entityID), fullUpdatedData);
                    }
                });
    }

    private Set<TimeSeriesContext> buildTimeSeriesFromDataSource(TimePeriod.TimePeriodImpl timePeriod,
                                                                 WidgetSeriesEntity item,
                                                                 HasTimeValueSeries source) {
        Set<TimeSeriesContext> result = new HashSet<>();
        ChartRequest chartRequest = buildChartRequest(timePeriod, item.getDynamicParameterFields());

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
            result.add(new TimeSeriesContext(tvd.getId(), item, source).setValue(entry.getValue()));
        }
        return result;
    }

    private TimePeriod.TimePeriodImpl buildTimePeriodFromHoursToShow(int hoursToShow, double pointsPerHour) {
        TimePeriod.TimePeriodImpl timePeriod = new TimePeriod.TimePeriodImpl() {
            @Override
            public Pair<Date, Date> getDateRange() {
                return Pair.of(new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hoursToShow)),
                        new Date());
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
        return timePeriod;
    }

    @Getter
    @Setter
    public static class BarChartDataset extends ChartDataset {
        private List<String> backgroundColor;
        private List<String> borderColor;
        private int borderWidth;

        public BarChartDataset(String id) {
            super(id);
        }
    }

    @Getter
    @Setter
    public static class PieChartDataset extends ChartDataset {
        private List<String> backgroundColor;
        private List<String> borderColor;
        private int borderWidth;

        public PieChartDataset(String id) {
            super(id);
        }
    }

    @Getter
    @Setter
    public static class TimeSeriesDataset extends ChartDataset {
        private String fill = "origin";
        private final String borderColor;
        private final String backgroundColor;
        private final double tension;
        private final Object stepped;

        public TimeSeriesDataset(String id, String label, String color, int opacity, double tension, Object stepped) {
            super(id);
            this.setLabel(label);
            this.borderColor = color;
            this.backgroundColor = getColorWithOpacity(color, opacity);
            this.tension = tension;
            this.stepped = stepped;
        }
    }

    @Getter
    public static class TimeSeriesChartData<T extends ChartDataset> {
        private final List<String> labels = new ArrayList<>();
        private final List<T> datasets = new ArrayList<>();
        private Object value; // for mini-card
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class ChartDataset {
        private final String id;
        private String label;
        private List<Float> data;
    }

    @Getter
    @RequiredArgsConstructor
    public static class SingleValueData {
        private final Object value;
        private final String seriesEntityID;
    }

    @AllArgsConstructor
    private static class ChartContext<T extends WidgetSeriesEntity> {
        private int index;
        private T item;
        private BaseEntity source;
    }
}
