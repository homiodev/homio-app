package org.touchhome.app.rest.widget;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.app.model.entity.widget.impl.chart.bar.WidgetBarChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.bar.WidgetBarTimeChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.doughnut.WidgetDoughnutChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.pie.WidgetPieChartEntity;
import org.touchhome.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.touchhome.app.model.entity.widget.impl.display.WidgetDisplaySeriesEntity;
import org.touchhome.app.model.entity.widget.impl.gauge.WidgetGaugeEntity;
import org.touchhome.app.model.rest.WidgetDataRequest;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.ui.TimePeriod;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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

    @PostMapping("/display/series")
    public DisplayDataResponse getDisplayValues(@RequestBody WidgetDataRequest request) {
        WidgetDisplayEntity entity = entityContext.getEntity(request.getEntityID());
        List<Object> values = new ArrayList<>(entity.getSeries().size());
        for (WidgetDisplaySeriesEntity item : entity.getSeries()) {
            BaseEntity<?> valueSource = entityContext.getEntity(item.getValueDataSource());

            Object value = timeSeriesUtil.getSingleValue(entity, valueSource,
                    item.getValueDynamicParameterFields(), request, item.getAggregationType(), item.getEntityID(),
                    o -> o);
            values.add(value);
        }

        TimeSeriesChartData<ChartDataset> chartData = null;
        if (StringUtils.isNotEmpty(entity.getChartDataSource())) {
            TimePeriod.TimePeriodImpl timePeriod =
                    timeSeriesUtil.buildTimePeriodFromHoursToShow(entity.getHoursToShow(), entity.getPointsPerHour());
            chartData = timeSeriesUtil.buildTimeSeriesFullData(entity.getEntityID(), timePeriod, entity.getListenSourceUpdates(),
                    Collections.singleton(entity));

            fillSingleValue(entity, chartData, request);
        }

        return new DisplayDataResponse(values, chartData);
    }

    @PostMapping("/line/series")
    public TimeSeriesChartData<ChartDataset> getChartSeries(@Valid @RequestBody WidgetDataRequest request) {
        WidgetLineChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetLineChartEntity.class);
        TimePeriod.TimePeriodImpl timePeriod = TimePeriod.fromValue(request.getTimePeriod()).getTimePeriod();

        return timeSeriesUtil.buildTimeSeriesFullData(entity.getEntityID(), timePeriod, entity.getListenSourceUpdates(),
                entity.getSeries());
    }

    @PostMapping("/bartime/series")
    public TimeSeriesChartData<ChartDataset> getBartimeSeries(@Valid @RequestBody WidgetDataRequest request) {
        WidgetBarTimeChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetBarTimeChartEntity.class);
        TimePeriod.TimePeriodImpl timePeriod = TimePeriod.fromValue(request.getTimePeriod()).getTimePeriod();

        return timeSeriesUtil.buildTimeSeriesFullData(entity.getEntityID(), timePeriod, entity.getListenSourceUpdates(),
                entity.getSeries());
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
                entity.getValueDynamicParameterFields(), request, entity.getAggregationType(), null,
                o -> o);
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
                            valueEntity.getAggregationType(), entity.getEntityID(), o -> o);
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

    @Getter
    @AllArgsConstructor
    public static class DisplayDataResponse {
        private List<Object> values;
        private TimeSeriesChartData<ChartDataset> chart;
    }
}
