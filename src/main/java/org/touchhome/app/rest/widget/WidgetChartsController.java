package org.touchhome.app.rest.widget;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.Valid;
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
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.DataSourceUtil;
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
        WidgetDisplayEntity entity = request.getEntity(entityContext, objectMapper, WidgetDisplayEntity.class);
        List<Object> values = new ArrayList<>(entity.getSeries().size());
        for (WidgetDisplaySeriesEntity item : entity.getSeries()) {
            values.add(timeSeriesUtil.getSingleValue(entity, item, o -> o));
        }

        TimeSeriesChartData<ChartDataset> chartData = null;
        if (StringUtils.isNotEmpty(entity.getChartDataSource())) {
            chartData = timeSeriesUtil.buildTimeSeriesFullData(
                entity.getEntityID(),
                entity.buildTimePeriod(),
                entity.getListenSourceUpdates(),
                Collections.singleton(entity));
        }

        return new DisplayDataResponse(values, chartData);
    }

    @PostMapping("/line/series")
    public TimeSeriesChartData<ChartDataset> getChartSeries(@Valid @RequestBody WidgetDataRequest request) {
        WidgetLineChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetLineChartEntity.class);

        return timeSeriesUtil.buildTimeSeriesFullData(
            entity.getEntityID(),
            entity.buildTimePeriod(),
            entity.getListenSourceUpdates(),
            entity.getSeries());
    }

    @PostMapping("/bartime/series")
    public TimeSeriesChartData<ChartDataset> getBartimeSeries(
        @Valid @RequestBody WidgetDataRequest request) {
        WidgetBarTimeChartEntity entity = request.getEntity(entityContext, objectMapper, WidgetBarTimeChartEntity.class);

        return timeSeriesUtil.buildTimeSeriesFullData(
            entity.getEntityID(),
            entity.buildTimePeriod(),
            entity.getListenSourceUpdates(),
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
            request.getEntity(entityContext, objectMapper, WidgetDoughnutChartEntity.class);
        TimeSeriesChartData<ChartDataset> result =
            getValueDataset(request, WidgetDoughnutChartEntity.class);

        DataSourceUtil.DataSourceContext source =
            DataSourceUtil.getSource(entityContext, entity.getValueDataSource());
        if (source.getSource() != null) {
            result.value = timeSeriesUtil.getSingleValue(entity, entity, o -> o);
        }

        return result;
    }

    @PostMapping("/gauge/value")
    public Object getGaugeValue(@Valid @RequestBody WidgetDataRequest request) {
        WidgetGaugeEntity entity = request.getEntity(entityContext, objectMapper, WidgetGaugeEntity.class);
        return timeSeriesUtil.getSingleValue(entity, entity, o -> o);
    }

    private <
        S extends WidgetSeriesEntity<T> & HasSingleValueDataSource,
        T extends ChartBaseEntity<T, S>>
    TimeSeriesChartData<ChartDataset> getValueDataset(
        WidgetDataRequest request, Class<T> chartClass) {
        T entity = request.getEntity(entityContext, objectMapper, chartClass);
        TimeSeriesChartData<ChartDataset> timeSeriesChartData = new TimeSeriesChartData<>();

        for (S item : entity.getSeries()) {
            Object value = timeSeriesUtil.getSingleValue(entity, item, o -> o);

            ChartDataset dataset = new ChartDataset(item.getEntityID());
            timeSeriesChartData.datasets.add(dataset);
            dataset.setData(
                Collections.singletonList(value == null ? 0F : ((Number) value).floatValue()));
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
