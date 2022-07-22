package org.touchhome.app.rest.widget;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
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
import org.touchhome.app.model.entity.widget.impl.chart.pie.WidgetPieChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.pie.WidgetPieChartSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.gauge.WidgetGaugeEntity;
import org.touchhome.app.model.rest.DynamicUpdateRequest;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.*;
import org.touchhome.bundle.api.ui.TimePeriod;

import java.util.*;
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

    @PostMapping("/line/series")
    public TimeSeriesChartData<ChartDataset> getChartSeries(@RequestBody WidgetSeriesRequest request) {
        WidgetLineChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetLineChartEntity.class);
        TimePeriod timePeriod = TimePeriod.fromValue(request.getTimePeriod());
        ChartRequest chartRequest = buildChartRequest(timePeriod);

        return buildTimeSeriesFullData(entity, timePeriod, chartRequest, entity.getListenSourceUpdates());
    }

    @PostMapping("/bartime/series")
    public TimeSeriesChartData<ChartDataset> getBartimeSeries(@RequestBody WidgetSeriesRequest request) {
        WidgetBarTimeChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetBarTimeChartEntity.class);
        TimePeriod timePeriod = TimePeriod.fromValue(request.getTimePeriod());
        ChartRequest chartRequest = buildChartRequest(timePeriod);

        return buildTimeSeriesFullData(entity, timePeriod, chartRequest, entity.getListenSourceUpdates());
    }

    @PostMapping("/bar/series")
    public TimeSeriesChartData<BarChartDataset> getBarSeries(@RequestBody WidgetSeriesRequest request) {
        WidgetBarChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetBarChartEntity.class);
        TimePeriod timePeriod = TimePeriod.fromValue(request.getTimePeriod());
        ChartRequest chartRequest = buildChartRequest(timePeriod);
        TimeSeriesChartData<BarChartDataset> timeSeriesChartData = new TimeSeriesChartData<>();
        timeSeriesChartData.labels.add(entity.getAxisLabel());

        for (ChartContext<WidgetBarChartSeriesEntity> context : filterSeries(entity, WidgetBarChartSeriesEntity.class)) {
            BarChartDataset dataset = new BarChartDataset(context.source.getEntityID() + "___" + System.currentTimeMillis());
            WidgetBarChartSeriesEntity item = context.item;

            dataset.setLabel(defaultString(item.getName(), context.source.getTitle()));
            dataset.setBorderWidth(entity.getBorderWidth());

            String color = item.getColor();
            dataset.setBackgroundColor(Collections.singletonList(getColorWithOpacity(color, item.getColorOpacity())));
            dataset.setBorderColor(Collections.singletonList(color));
            timeSeriesChartData.datasets.add(dataset);

            AggregationType aggregationType = item.getAggregationType();
            chartRequest.setParameters(item.getDynamicParameterFieldsHolder());
            dataset.setData(Collections.singletonList(calcFloatValue(chartRequest, aggregationType, context.source)));

            if (entity.getListenSourceUpdates()) {
                entityContext.event().addEventListener(item.getDataSource(), o -> {
                    ChartRequest updChartRequest = buildChartRequest(timePeriod);
                    updChartRequest.setParameters(item.getDynamicParameterFieldsHolder());
                    dataset.setData(Collections.singletonList(calcFloatValue(updChartRequest, aggregationType, context.source)));
                    entityContext.ui().sendDynamicUpdate(
                            new DynamicUpdateRequest(item.getDataSource(), BarChartDataset.class.getSimpleName(),
                                    entity.getEntityID()), dataset);
                });
            }
        }
        return timeSeriesChartData;
    }

    @PostMapping("/pie/series")
    public TimeSeriesChartData<PieChartDataset> getPieSeries(@RequestBody WidgetSeriesRequest request) {
        WidgetPieChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetPieChartEntity.class);
        String widgetEntityID = entity.getEntityID();
        TimePeriod timePeriod = TimePeriod.fromValue(request.getTimePeriod());
        ChartRequest chartRequest = buildChartRequest(timePeriod);
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

            String color = item.getColor();
            dataset.getBackgroundColor().add(getColorWithOpacity(color, item.getColorOpacity()));
            dataset.getBorderColor().add(color);

            AggregationType aggregationType = item.getAggregationType();
            chartRequest.setParameters(item.getDynamicParameterFieldsHolder());
            dataset.getData().add(calcFloatValue(chartRequest, aggregationType, context.source));

            // listen DataSource changed events, convert to BarSeries and put again in even bus
            if (entity.getListenSourceUpdates()) {
                entityContext.event().addEventListener(item.getDataSource(), o -> {
                    ChartRequest updChartRequest = buildChartRequest(timePeriod);
                    updChartRequest.setParameters(item.getDynamicParameterFieldsHolder());
                    dataset.getData().set(context.index, calcFloatValue(updChartRequest, aggregationType, context.source));
                    entityContext.ui().sendDynamicUpdate(
                            new DynamicUpdateRequest(item.getDataSource(), PieChartDataset.class.getSimpleName(),
                                    widgetEntityID), dataset);
                });
            }
        }
        return timeSeriesChartData;
    }

    @PostMapping("/gauge/{entityID}/value")
    public Float getGaugeValue(@RequestBody WidgetGaugeRequest request) {
        WidgetGaugeEntity entity = request.getEntity(entityContext, objectMapper, WidgetGaugeEntity.class);

        BaseEntity baseEntity = entityContext.getEntity(entity.getDataSource());
        if (baseEntity instanceof HasAggregateValueFromSeries) {
            TimePeriod timePeriod = TimePeriod.fromValue(request.getTimePeriod());
            return ((HasAggregateValueFromSeries) baseEntity).getAggregateValueFromSeries(buildChartRequest(timePeriod),
                    entity.getAggregationType());
        }
        return 0F;
    }

    private ChartRequest buildChartRequest(TimePeriod timePeriod) {
        Pair<Date, Date> range = timePeriod.getDateRange();
        return new ChartRequest(entityContext, range.getFirst(), range.getSecond(), timePeriod.getDateFromNow(),
                timePeriod != TimePeriod.All);
    }

    private float calcFloatValue(ChartRequest chartRequest, AggregationType aggregationType, BaseEntity source) {
        Float value = ((HasAggregateValueFromSeries) source).getAggregateValueFromSeries(chartRequest, aggregationType);
        return value == null ? 0F : value;
    }

    public static String getColorWithOpacity(String color, int colorOpacity) {
        if (StringUtils.isNotEmpty(color) && color.startsWith("#") && color.length() == 7) {
            return color + String.format("%02X", colorOpacity);
        }
        return color;
    }

    private <T extends WidgetSeriesEntity> List<ChartContext<T>> filterSeries(ChartBaseEntity entity, Class<T> tClass) {
        List<ChartContext<T>> result =
                ((Set<T>) entity.getSeries()).stream().filter(s -> isNotEmpty(s.getDataSource())).map(s -> {
                    BaseEntity source = entityContext.getEntity(s.getDataSource());
                    return new ChartContext<T>(0, s, source instanceof HasAggregateValueFromSeries ? source : null);
                }).filter(ctx -> ctx.item != null).collect(Collectors.toList());
        for (int i = 0; i < result.size(); i++) {
            result.get(i).index = i;
        }
        return result;
    }

    private TimeSeriesChartData<ChartDataset> buildTimeSeriesFullData(TimeSeriesChartBaseEntity entity,
                                                                      TimePeriod timePeriod,
                                                                      ChartRequest chartRequest,
                                                                      boolean addUpdateListener) {
        Set<WidgetSeriesEntity> series = entity.getSeries();
        List<TimeSeriesValues> timeSeriesValuesList = new ArrayList<>(series.size());

        for (WidgetSeriesEntity item : series) {
            BaseEntity<?> source = entityContext.getEntity(item.getDataSource());
            if (source instanceof HasTimeValueSeries) {
                timeSeriesValuesList.add(new TimeSeriesValues(item, source,
                        buildTimeSeriesFromDataSource(chartRequest, item, (HasTimeValueSeries) source)));
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
                                                      TimePeriod timePeriod,
                                                      String entityID) {
        WidgetSeriesEntity item = timeSeriesContext.getSeriesEntity();
        entityContext.event().addEventListener(item.getDataSource(), o -> {
            ChartRequest updatedChartRequest = buildChartRequest(timePeriod);
            Set<TimeSeriesContext> cts = buildTimeSeriesFromDataSource(updatedChartRequest, item, timeSeriesContext.getSeries());
            TimeSeriesValues values = timeSeriesContext.getOwner();

            // if context was updated - we need update rest of values also !!!
            if (!values.isEqualSeries(cts)) {
                TimeSeriesChartData<ChartDataset> updatedData =
                        this.buildTimeSeriesFullData(entity, timePeriod, updatedChartRequest, false);

                entityContext.ui().sendDynamicUpdate(
                        new DynamicUpdateRequest(item.getDataSource(), TimeSeriesChartData.class.getSimpleName(),
                                entityID), updatedData);
            }
        });
    }

    private Set<TimeSeriesContext> buildTimeSeriesFromDataSource(ChartRequest chartRequest,
                                                                 WidgetSeriesEntity item,
                                                                 HasTimeValueSeries source) {
        Set<TimeSeriesContext> result = new HashSet<>();
        chartRequest.setParameters(item.getDynamicParameterFieldsHolder());

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
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class ChartDataset {
        private final String id;
        private String label;
        private List<Float> data;
    }

    @AllArgsConstructor
    private static class ChartContext<T extends WidgetSeriesEntity> {
        private int index;
        private T item;
        private BaseEntity source;
    }

    @Getter
    @Setter
    private static class WidgetSeriesRequest {
        private String entityID;
        private String timePeriod;
        private String liveEntity;

        public String getTimePeriod() {
            return timePeriod == null ? TimePeriod.All.name() : timePeriod;
        }

        @SneakyThrows
        public <T extends BaseEntity> T getEntity(EntityContext entityContext, ObjectMapper objectMapper, Class<T> tClass) {
            if (liveEntity != null) {
                return objectMapper.readValue(liveEntity, tClass);
            }
            return entityContext.getEntity(entityID);
        }
    }

    @Getter
    @Setter
    private static class WidgetGaugeRequest {
        private String entityID;
        private String timePeriod;
        private String liveEntity;

        public String getTimePeriod() {
            return timePeriod == null ? TimePeriod.All.name() : timePeriod;
        }

        @SneakyThrows
        public <T extends BaseEntity> T getEntity(EntityContext entityContext, ObjectMapper objectMapper, Class<T> tClass) {
            if (liveEntity != null) {
                return objectMapper.readValue(liveEntity, tClass);
            }
            return entityContext.getEntity(entityID);
        }
    }
}
