package org.touchhome.app.rest.widget;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.app.model.entity.widget.impl.chart.bar.WidgetBarChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.bar.WidgetBarTimeChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.doughnut.WidgetDoughnutChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.pie.WidgetPieChartEntity;
import org.touchhome.app.model.entity.widget.impl.gauge.WidgetGaugeEntity;
import org.touchhome.app.model.entity.widget.impl.minicard.WidgetMiniCardChartEntity;
import org.touchhome.app.model.entity.widget.impl.minicard.WidgetMiniCardChartSeriesEntity;
import org.touchhome.app.model.rest.WidgetDataRequest;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.ui.TimePeriod;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Log4j2
@RestController
@RequestMapping("/rest/widget/chart")
public class WidgetChartsController {

    private final EntityContextImpl entityContext;
    private final ObjectMapper objectMapper;
    private final TimeSeriesUtil timeSeriesUtil;

    public WidgetChartsController(EntityContextImpl entityContext, ObjectMapper objectMapper) {
        this.entityContext = entityContext;
        this.objectMapper = objectMapper;
        this.timeSeriesUtil = new TimeSeriesUtil(entityContext);
    }

   /* @PostMapping("/button/series")
    public TimeSeriesChartData<ChartDataset> getButtonValues(@Valid @RequestBody WidgetDataRequest request) {
        return getTimeSeriesChartData(request, WidgetPushButtonEntity.class, entity -> {
            return entity.getSeries().isEmpty() ? null : entity.getSeries().iterator().next();
        });
    }*/

    @PostMapping("/minicard/series")
    public TimeSeriesChartData<ChartDataset> getMiniCardChartSeries(@Valid @RequestBody WidgetDataRequest request) {
        return getTimeSeriesChartData(request, WidgetMiniCardChartEntity.class, entity -> {
            return new WidgetMiniCardChartSeriesEntity() {
                @Override
                public JSONObject getJsonData() {
                    return entity.getJsonData();
                }
            };
        });
    }

    @PostMapping("/line/series")
    public TimeSeriesChartData<ChartDataset> getChartSeries(@Valid @RequestBody WidgetDataRequest request) {
        WidgetLineChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetLineChartEntity.class);
        TimePeriod.TimePeriodImpl timePeriod = TimePeriod.fromValue(request.getTimePeriod()).getTimePeriod();

        return timeSeriesUtil.buildTimeSeriesFullData(entity, timePeriod, entity.getListenSourceUpdates(), entity.getSeries());
    }

    @PostMapping("/bartime/series")
    public TimeSeriesChartData<ChartDataset> getBartimeSeries(@Valid @RequestBody WidgetDataRequest request) {
        WidgetBarTimeChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetBarTimeChartEntity.class);
        TimePeriod.TimePeriodImpl timePeriod = TimePeriod.fromValue(request.getTimePeriod()).getTimePeriod();

        return timeSeriesUtil.buildTimeSeriesFullData(entity, timePeriod, entity.getListenSourceUpdates(), entity.getSeries());
    }

    @PostMapping("/bar/series")
    public TimeSeriesChartData<ChartDataset> getBarSeries(@Valid @RequestBody WidgetDataRequest request) {
        return getValueDataset(request, WidgetBarChartEntity.class);
    }

    @PostMapping("/pie/series")
    public TimeSeriesChartData<ChartDataset> getPieSeries(@Valid @RequestBody WidgetDataRequest request) {
        return getValueDataset(request, WidgetPieChartEntity.class);
    }

    @PostMapping("/doughnut/series")
    public TimeSeriesChartData<ChartDataset> getDoughnutSeries(@Valid @RequestBody WidgetDataRequest request) {
        return getValueDataset(request, WidgetDoughnutChartEntity.class);
    }

    @PostMapping("/gauge/value")
    public Object getGaugeValue(@Valid @RequestBody WidgetDataRequest request) {
        WidgetGaugeEntity entity = request.getEntity(entityContext, objectMapper, WidgetGaugeEntity.class);

        BaseEntity<?> valueSource = entityContext.getEntity(entity.getValueDataSource());
        return timeSeriesUtil.getSingleValue(entity, valueSource,
                entity.getValueDynamicParameterFields(), request, entity.getAggregationType(), null);
    }

    private <T extends ChartBaseEntity> TimeSeriesChartData<ChartDataset> getValueDataset(WidgetDataRequest request,
                                                                                          Class<T> chartClass) {
        T entity = request.getEntity(entityContext, objectMapper, chartClass);
        TimeSeriesChartData<ChartDataset> timeSeriesChartData = new TimeSeriesChartData<>();

        Set<WidgetSeriesEntity> series = entity.getSeries();
        for (WidgetSeriesEntity item : series) {
            BaseEntity<?> source = entityContext.getEntity(((HasSingleValueDataSource) item).getValueDataSource());
            ChartDataset dataset = new ChartDataset(item.getEntityID());
            timeSeriesChartData.datasets.add(dataset);

            Object value =
                    timeSeriesUtil.getAggregateValueFromSeries(request, source, item.getValueDynamicParameterFields(),
                            entity.getListenSourceUpdates(), ((HasSingleValueDataSource) item).getAggregationType(),
                            entity.getEntityID(),
                            item.getEntityID(),
                            true);
            dataset.setData(Collections.singletonList(value == null ? 0F : ((Number) value).floatValue()));
        }
        // fill single value for doughnut
        fillSingleValue(entity, timeSeriesChartData, request);

        return timeSeriesChartData;
    }

    private <S extends WidgetSeriesEntity<T> & HasChartDataSource<T>, T extends WidgetBaseEntityAndSeries<T, S>>
    TimeSeriesChartData<ChartDataset> getTimeSeriesChartData(WidgetDataRequest request, Class<T> typeClass,
                                                             Function<T, S> itemSupplier) {
        T entity = request.getEntity(entityContext, objectMapper, typeClass);

        S item = itemSupplier.apply(entity);
        if (item != null) {
            TimePeriod.TimePeriodImpl timePeriod =
                    timeSeriesUtil.buildTimePeriodFromHoursToShow(item.getHoursToShow(), item.getPointsPerHour());
            TimeSeriesChartData<ChartDataset> data =
                    timeSeriesUtil.buildTimeSeriesFullData(entity, timePeriod, entity.getListenSourceUpdates(),
                            Collections.singleton(item));

            fillSingleValue(entity, data, request);
            return data;
        }

        return null;
    }

    private <S extends WidgetSeriesEntity<T>, T extends WidgetBaseEntityAndSeries<T, S>> void fillSingleValue(
            T entity, TimeSeriesChartData<ChartDataset> data, WidgetDataRequest request) {
        if (entity instanceof HasSingleValueDataSource) {
            HasSingleValueDataSource<?> valueEntity = (HasSingleValueDataSource<?>) entity;
            String valueDataSource = valueEntity.getValueDataSource();

            if (StringUtils.isNotEmpty(valueDataSource)) {
                BaseEntity<?> valueSource = entityContext.getEntity(valueDataSource);
                if (valueSource != null) {
                    data.value = timeSeriesUtil.getSingleValue(entity, valueSource,
                            valueEntity.getValueDynamicParameterFields(), request,
                            valueEntity.getAggregationType(), entity.getEntityID());
                }
            }
        }
    }

    public static String getColorWithOpacity(String color, int colorOpacity) {
        if (StringUtils.isNotEmpty(color) && color.startsWith("#") && color.length() == 7) {
            return color + String.format("%02X", colorOpacity);
        }
        return color;
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
    @Setter
    public static class TimeSeriesChartData<T extends ChartDataset> {
        private final List<String> labels = new ArrayList<>();
        private final List<T> datasets = new ArrayList<>();
        private Object value; // for mini-card
    }

    @Getter
    @RequiredArgsConstructor
    public static class SingleValueData {
        private final Object value;
        private final String seriesEntityID;
    }
}
