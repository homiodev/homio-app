package org.touchhome.app.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.data.util.Pair;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.manager.ScriptService;
import org.touchhome.app.manager.WidgetService;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.CompileScriptContext;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.model.entity.widget.impl.button.WidgetButtonSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.chart.ChartPeriod;
import org.touchhome.app.model.entity.widget.impl.chart.bar.WidgetBarChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.chart.pie.WidgetPieChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.pie.WidgetPieChartSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.touchhome.app.model.entity.widget.impl.display.WidgetDisplaySeriesEntity;
import org.touchhome.app.model.entity.widget.impl.gauge.WidgetGaugeEntity;
import org.touchhome.app.model.entity.widget.impl.js.WidgetJsEntity;
import org.touchhome.app.model.entity.widget.impl.slider.WidgetSliderEntity;
import org.touchhome.app.model.entity.widget.impl.slider.WidgetSliderSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.toggle.WidgetToggleEntity;
import org.touchhome.app.model.entity.widget.impl.toggle.WidgetToggleSeriesEntity;
import org.touchhome.app.repository.widget.impl.chart.WidgetBarChartSeriesEntity;
import org.touchhome.app.utils.JavaScriptBuilderImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.*;
import org.touchhome.bundle.api.exception.NotFoundException;
import org.touchhome.bundle.api.exception.ServerException;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.api.widget.WidgetBaseTemplate;
import org.touchhome.bundle.api.widget.WidgetJSBaseTemplate;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.touchhome.bundle.api.util.Constants.ADMIN_ROLE;
import static org.touchhome.bundle.api.util.Constants.PRIVILEGED_USER_ROLE;

@Log4j2
@RestController
@RequestMapping("/rest/widget")
@RequiredArgsConstructor
public class WidgetController {

    private final EntityContext entityContext;
    private final ScriptService scriptService;
    private final ObjectMapper objectMapper;
    private final WidgetService widgetService;

    @SneakyThrows
    @GetMapping("/plugins")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public List<WidgetService.AvailableWidget> getAvailableWidgets() {
        return widgetService.getAvailableWidgets();
    }

    @GetMapping("/button/{entityID}/handle")
    public void handleButtonClick(@PathVariable("entityID") String entityID) {
        WidgetButtonSeriesEntity entity = entityContext.getEntity(entityID);
        BaseEntity<?> source = entityContext.getEntity(entity.getDataSource());
        if (source instanceof HasPushButtonSeries) {
            ((HasPushButtonSeries) source).pushButton(entityContext);
        } else {
            throw new ServerException("Unable to find handler for button");
        }
    }

    @GetMapping("/bar/{entityID}/series")
    public Set<BarSeries> getBarSeries(@PathVariable("entityID") String entityID,
                                       @RequestParam(value = "liveEntity", required = false) String liveEntity) throws JsonProcessingException {
        WidgetBarChartEntity entity = entityContext.getEntity(entityID);
        if (liveEntity != null) {
            entity = objectMapper.readValue(liveEntity, entity.getClass());
        }
        Set<BarSeries> series = new LinkedHashSet<>();
        for (WidgetBarChartSeriesEntity item : entity.getSeries()) {
            if (item.getDataSource() != null) {
                BaseEntity<?> source = entityContext.getEntity(item.getDataSource());
                if (source instanceof HasBarChartSeries) {
                    double value = ((HasBarChartSeries) source).getBarValue(entityContext);
                    BarSeries barSeries = new BarSeries(StringUtils.defaultString(item.getName(), source.getTitle()), value);
                    int i = 1;
                    while (!series.add(barSeries)) {
                        barSeries = new BarSeries(StringUtils.defaultString(item.getName(), source.getTitle()) + "(" + i++ + ")", value);
                    }
                }
            }
        }
        return series;
    }

    @GetMapping("/line/{entityID}/series")
    public List<ChartSeries> getChartSeries(@PathVariable("entityID") String entityID,
                                            @RequestParam("period") String period) {
        WidgetLineChartEntity entity = entityContext.getEntity(entityID);
        List<ChartSeries> series = new ArrayList<>();
        for (WidgetLineChartSeriesEntity item : entity.getSeries()) {
            BaseEntity<?> source = entityContext.getEntity(item.getDataSource());
            if (source instanceof HasLineChartSeries) {
                ChartPeriod chartPeriod = ChartPeriod.fromValue(period);
                Pair<Date, Date> range = chartPeriod.getDateRange();
                JSONObject dynamicParameterFields = item.getDynamicParameterFieldsHolder();
                Map<HasLineChartSeries.LineChartDescription, List<Object[]>> result = ((HasLineChartSeries) source).getLineChartSeries(entityContext,
                        dynamicParameterFields, range.getFirst(), range.getSecond(), chartPeriod.getDateFromNow());

                for (Map.Entry<HasLineChartSeries.LineChartDescription, List<Object[]>> entry : result.entrySet()) {
                    List<Object[]> chartItems = entry.getValue();

                    // convert chartItem[0] to long if it's a Date type
                    if (!chartItems.isEmpty() && chartItems.get(0)[0] instanceof Date) {
                        for (Object[] chartItem : chartItems) {
                            chartItem[0] = ((Date) chartItem[0]).getTime();
                        }
                    }
                    EvaluateDatesAndValues evaluateDatesAndValues = new EvaluateDatesAndValues(chartPeriod, chartItems).invoke(item);

                    // aggregation
                    List<Float> finalValues = evaluateDatesAndValues.getValues().stream()
                            .map(items -> item.getAggregateFunction().getAggregateFn().apply(items))
                            .collect(Collectors.toList());

                    String title = StringUtils.defaultString(entry.getKey().getName(), source.getTitle());
                    series.add(new ChartSeries(title + "(" + chartItems.size() + ")", entry.getKey().getColor(), evaluateDatesAndValues.getDates(), finalValues));
                }
            }
        }
        return series;
    }

    private List<List<Float>> fulfillValues(List<Date> dates, List<Object[]> chartItems, Boolean fillMissingValues) {
        List<List<Float>> values = new ArrayList<>(dates.size());
        IntStream.range(0, dates.size()).forEach(value -> values.add(new ArrayList<>()));

        // push values to date between buckets
        for (Object[] chartItem : chartItems) {
            long time = chartItem[0] instanceof Date ? ((Date) chartItem[0]).getTime() : (Long) chartItem[0];
            int index = getDateIndex(dates, time);
            values.get(index).add((Float) chartItem[1]);
        }

        // remove empty values
        if (!fillMissingValues) {
            Iterator<Date> dateIterator = dates.iterator();
            for (Iterator<List<Float>> iterator = values.iterator(); iterator.hasNext(); ) {
                List<Float> filledValues = iterator.next();
                dateIterator.next();
                if (filledValues.isEmpty()) {
                    iterator.remove();
                    dateIterator.remove();
                }
            }
        }
        return values;
    }

    private int getDateIndex(List<Date> dateList, long time) {
        for (int i = 0; i < dateList.size(); i++) {
            if (time < dateList.get(i).getTime()) {
                return i - 1;
            }
        }
        return dateList.size() - 1;
    }

    @GetMapping("/pie/{entityID}/series")
    public List<PieSeries> getPieSeries(@PathVariable("entityID") String entityID, @RequestParam("period") String period) {
        WidgetPieChartEntity entity = entityContext.getEntity(entityID);
        List<PieSeries> series = new ArrayList<>();
        for (WidgetPieChartSeriesEntity item : entity.getSeries()) {
            BaseEntity<?> source = entityContext.getEntity(item.getDataSource());
            if (source instanceof HasPieChartSeries) {
                ChartPeriod chartPeriod = ChartPeriod.fromValue(period);
                Pair<Date, Date> range = chartPeriod.getDateRange();

                double value;
                if (entity.getPieChartValueType() == WidgetPieChartEntity.PieChartValueType.Sum) {
                    value = ((HasPieChartSeries) source).getPieSumChartSeries(entityContext, range.getFirst(), range.getSecond(), chartPeriod.getDateFromNow());
                } else {
                    value = ((HasPieChartSeries) source).getPieCountChartSeries(entityContext, range.getFirst(),
                            range.getSecond(), chartPeriod.getDateFromNow());
                }
                series.add(new PieSeries(source.getTitle(), value, new PieSeries.Extra(item.getTitle())));
            }
        }
        return series;
    }

    @GetMapping("/gauge/{entityID}/value")
    public Float getGaugeValue(@PathVariable("entityID") String entityID) {
        WidgetGaugeEntity entity = entityContext.getEntity(entityID);
        BaseEntity baseEntity = entityContext.getEntity(entity.getDataSource());
        return baseEntity instanceof HasGaugeSeries ? ((HasGaugeSeries) baseEntity).getGaugeValue() : null;
    }

    @GetMapping("/slider/{entityID}/values")
    public List<Float> getSliderValues(@PathVariable("entityID") String entityID) {
        WidgetSliderEntity entity = entityContext.getEntity(entityID);
        List<Float> values = new ArrayList<>(entity.getSeries().size());
        for (WidgetSliderSeriesEntity item : entity.getSeries()) {
            BaseEntity<?> dataSource = entityContext.getEntity(item.getDataSource());
            if (dataSource instanceof HasSliderSeries) {
                values.add(((HasSliderSeries) dataSource).getSliderValue());
            }
        }
        return values;
    }

    @GetMapping("/toggle/{entityID}/values")
    public List<Boolean> getToggleValues(@PathVariable("entityID") String entityID) {
        WidgetToggleEntity entity = entityContext.getEntity(entityID);
        List<Boolean> values = new ArrayList<>(entity.getSeries().size());
        for (WidgetToggleSeriesEntity item : entity.getSeries()) {
            BaseEntity<?> dataSource = entityContext.getEntity(item.getDataSource());
            if (dataSource instanceof HasToggleSeries) {
                values.add(((HasToggleSeries) dataSource).getToggleValue());
            } else {
                throw new ServerException("Unable to find handler for fetch value from <Data Source>: " + dataSource.getTitle());
            }
        }
        return values;
    }

    @GetMapping("/display/{entityID}/values")
    public List<Pair<Object, Date>> getDisplayValues(@PathVariable("entityID") String entityID) {
        WidgetDisplayEntity entity = entityContext.getEntity(entityID);
        List<Pair<Object, Date>> values = new ArrayList<>(entity.getSeries().size());
        for (WidgetDisplaySeriesEntity item : entity.getSeries()) {
            BaseEntity<?> source = entityContext.getEntity(item.getDataSource());
            if (source instanceof HasDisplaySeries) {
                Object val = ((HasDisplaySeries) source).getDisplayValue();
                if (val != null) {
                    values.add(Pair.of(val, source.getUpdateTime()));
                }
            }
        }
        return values;
    }

    @PostMapping("/slider/{entityID}/series/{seriesEntityID}")
    public void updateSliderValue(@PathVariable("entityID") String entityID, @PathVariable("seriesEntityID") String seriesEntityID, @RequestBody IntegerValue integerValue) {
        WidgetSliderEntity entity = entityContext.getEntity(entityID);
        WidgetSliderSeriesEntity series = entity.getSeries().stream().filter(s -> s.getEntityID().equals(seriesEntityID)).findAny().orElse(null);
        if (series == null) {
            throw new NotFoundException("Unable to find series: " + seriesEntityID + " for entity: " + entity.getTitle());
        }
        BaseEntity<?> source = entityContext.getEntity(series.getDataSource());
        if (source instanceof HasSliderSeries) {
            ((HasSliderSeries) source).setSliderValue(integerValue.value);
            entityContext.save(source);
        }
    }

    @PostMapping("/toggle/{entityID}/series/{seriesEntityID}")
    public void updateToggleValue(@PathVariable("entityID") String entityID, @PathVariable("seriesEntityID") String seriesEntityID, @RequestBody BooleanValue booleanValue) {
        WidgetToggleEntity entity = entityContext.getEntity(entityID);
        WidgetToggleSeriesEntity series = entity.getSeries().stream().filter(s -> s.getEntityID().equals(seriesEntityID)).findAny().orElse(null);
        if (series == null) {
            throw new NotFoundException("Unable to find series: " + seriesEntityID + " for entity: " + entity.getTitle());
        }
        BaseEntity<?> source = entityContext.getEntity(series.getDataSource());
        if (source instanceof HasToggleSeries) {
            ((HasToggleSeries) source).setToggleValue(booleanValue.value);
            entityContext.save(source);
        } else {
            throw new ServerException("Unable to find handler for set value for slider");
        }
    }

    @GetMapping("/{entityID}")
    public WidgetBaseEntity getWidget(@PathVariable("entityID") String entityID) {
        WidgetBaseEntity widget = entityContext.getEntity(entityID);
        updateWidgetBeforeReturnToUI(widget);
        return widget;
    }

    @GetMapping("/{tabId}/widget")
    public List<WidgetBaseEntity> getWidgets(@PathVariable("tabId") String tabId) {
        List<WidgetBaseEntity> widgets = entityContext.findAll(WidgetBaseEntity.class)
                .stream().filter(w -> w.getWidgetTabEntity().getEntityID().equals(tabId)).collect(Collectors.toList());

        boolean updated = false;
        for (WidgetBaseEntity<?> widget : widgets) {
            updated |= widget.updateRelations(entityContext);
        }
        if (updated) {
            widgets = entityContext.findAll(WidgetBaseEntity.class);
        }
        for (WidgetBaseEntity<?> widget : widgets) {
            updateWidgetBeforeReturnToUI(widget);
        }

        return widgets;
    }

    private void updateWidgetBeforeReturnToUI(WidgetBaseEntity<?> widget) {
        if (widget instanceof WidgetJsEntity) {
            WidgetJsEntity jsEntity = (WidgetJsEntity) widget;
            try {
                jsEntity.setJavaScriptErrorResponse(null);
                ScriptEntity scriptEntity = new ScriptEntity()
                        .setJavaScript(jsEntity.getJavaScript())
                        .setJavaScriptParameters(jsEntity.getJavaScriptParameters());

                CompileScriptContext compileScriptContext = scriptService.createCompiledScript(scriptEntity, null);
                jsEntity.setJavaScriptResponse(scriptService.runJavaScript(compileScriptContext));

            } catch (Exception ex) {
                jsEntity.setJavaScriptErrorResponse(TouchHomeUtils.getErrorMessage(ex));
            }
        }
    }

    @Secured(PRIVILEGED_USER_ROLE)
    @PostMapping("/create/{tabId}/{type}")
    public BaseEntity<?> createWidget(@PathVariable("tabId") String tabId, @PathVariable("type") String type) throws Exception {
        log.debug("Request creating widget entity by type: <{}> in tabId <{}>", type, tabId);
        WidgetTabEntity widgetTabEntity = entityContext.getEntity(tabId);
        if (widgetTabEntity == null) {
            throw new NotFoundException("Unable to find tab with tabId: " + tabId);
        }

        Class<? extends BaseEntity> typeClass = EntityContextImpl.baseEntityNameToClass.get(type);
        WidgetBaseEntity<?> baseEntity = (WidgetBaseEntity<?>) typeClass.getConstructor().newInstance();

        baseEntity.setWidgetTabEntity(widgetTabEntity);
        return entityContext.save(baseEntity);
    }

    @Secured(PRIVILEGED_USER_ROLE)
    @PostMapping("/create/{tabId}/{type}/{bundle}")
    public BaseEntity<?> createExtraWidget(@PathVariable("tabId") String tabId, @PathVariable("type") String type, @PathVariable("bundle") String bundle) {
        log.debug("Request creating extra widget entity by type: <{}> in tabId <{}>, bundle: <{}>", type, tabId, bundle);
        WidgetTabEntity widgetTabEntity = entityContext.getEntity(tabId);
        if (widgetTabEntity == null) {
            throw new NotFoundException("Unable to find tab with tabId: " + tabId);
        }

        Collection<WidgetBaseTemplate> widgets = entityContext.getBeansOfTypeByBundles(WidgetBaseTemplate.class).get(bundle);
        if (widgets == null) {
            throw new NotFoundException("Unable to find bundle: " + tabId + " or widgets in bundle");
        }
        WidgetBaseTemplate template = widgets.stream().filter(w -> w.getClass().getSimpleName().equals(type)).findAny()
                .orElseThrow(() -> new NotFoundException("Unable to find widget: " + type + " in bundle: " + bundle));

        String js = template.toJavaScript();
        String params = "";
        boolean paramReadOnly = false;
        if (js == null) {
            if (template instanceof WidgetJSBaseTemplate) {
                WidgetJSBaseTemplate widgetJSBaseTemplate = (WidgetJSBaseTemplate) template;
                JavaScriptBuilderImpl javaScriptBuilder = new JavaScriptBuilderImpl(template.getClass());
                widgetJSBaseTemplate.createWidget(javaScriptBuilder);
                js = javaScriptBuilder.build();
                paramReadOnly = javaScriptBuilder.isJsonReadOnly();
                params = javaScriptBuilder.getJsonParams().toString();
            }
        }

        WidgetJsEntity widgetJsEntity = new WidgetJsEntity()
                .setJavaScriptParameters(params)
                .setJavaScriptParametersReadOnly(paramReadOnly)
                .setJavaScript(js);

        widgetJsEntity.setWidgetTabEntity(widgetTabEntity)
                .setFieldFetchType(bundle + ":" + template.getClass().getSimpleName())
                .setAutoScale(template.isDefaultAutoScale());

        return entityContext.save(widgetJsEntity);
    }

    @GetMapping("/tab")
    public List<OptionModel> getWidgetTabs() {
        return entityContext.findAll(WidgetTabEntity.class).stream().sorted()
                .map(t -> OptionModel.of(t.getEntityID(), t.getName())).collect(Collectors.toList());
    }

    @SneakyThrows
    @PostMapping("/tab/{name}")
    public OptionModel createWidgetTab(@PathVariable("name") String name) {
        BaseEntity<?> widgetTab = entityContext.getEntity(WidgetTabEntity.PREFIX + name);
        if (widgetTab == null) {
            widgetTab = entityContext.save(new WidgetTabEntity().computeEntityID(() -> name));
            return OptionModel.of(widgetTab.getEntityID(), widgetTab.getName());
        }
        throw new ServerException("Widget tab with same name already exists");
    }

    @SneakyThrows
    @PutMapping("/tab/{tabId}/{name}")
    @Secured(ADMIN_ROLE)
    public void renameWidgetTab(@PathVariable("tabId") String tabId, @PathVariable("name") String name) {
        WidgetTabEntity entity = getWidgetTabEntity(tabId);
        WidgetTabEntity newEntity = entityContext.getEntityByName(name, WidgetTabEntity.class);

        if (newEntity == null) {
            entityContext.save(entity.setName(name));
        }
    }

    @DeleteMapping("/tab/{tabId}")
    @Secured(ADMIN_ROLE)
    public void deleteWidgetTab(@PathVariable("tabId") String tabId) {
        if (WidgetTabEntity.GENERAL_WIDGET_TAB_NAME.equals(tabId)) {
            throw new IllegalStateException("Unable to delete main tab");
        }
        WidgetTabEntity widgetTabEntity = getWidgetTabEntity(tabId);
        entityContext.delete(widgetTabEntity);
    }

    private WidgetTabEntity getWidgetTabEntity(String tabId) {
        BaseEntity<?> baseEntity = entityContext.getEntity(tabId);
        if (baseEntity instanceof WidgetTabEntity) {
            return (WidgetTabEntity) baseEntity;
        }
        throw new ServerException("Unable to find widget tab with id: " + tabId);
    }

   /* @RequestMapping(value = "/getWidget", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChartWidgetJSON getMiniBoxWidget(@RequestBody WidgetJsEntity miniBoxWidgetEntity) {
        ChartBuilder chartBuilder = ChartBuilder.create(miniBoxWidgetEntity);
        ScriptEntity scriptEntity = ScriptEntity.create(miniBoxWidgetEntity.getJavaScript(),
                miniBoxWidgetEntity.getEntityID(),
                miniBoxWidgetEntity.getJavaScriptParameters());
        if (StringUtils.isNotEmpty(scriptEntity.getJavaScript())) {
            Object retValue;
            try {
                retValue = scriptManager.executeJavaScriptOnce(scriptEntity, null, null, false);
            } catch (Exception ex) {
                retValue = "<pre style='max-width:600px;max-height:400px;'>" + ExceptionUtils.getStackTrace(ex) + "</pre>";
            }
            chartBuilder.withXAxis().data(Collections.singletonList(retValue == null ? "script returns no value" : retValue.toString()));
        }

        return chartBuilder.build();
    }*/

    /*@RequestMapping(value = "/getChartPieWidget", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChartWidgetJSON getChartPieWidget(@RequestParam(value = "entityID") String entityID) {
        ChartPieWidgetEntity chartPieWidgetEntity = chartPieWidgetRepository.getByEntityID(entityID);
        ChartBuilder chartBuilder = ChartBuilder.create(chartPieWidgetEntity);

        final ChartBuilder.LegendBuilder legendBuilder = chartPieWidgetEntity.getShowLegend() ? chartBuilder.withLegend().withOrient(chartPieWidgetEntity.getLegendOrient()) : new ChartBuilder.LegendBuilder();
        ChartBuilder.SeriesBuilder seriesBuilder = chartBuilder.withSeries("pie", chartPieWidgetEntity.getTitle()).withRadius(50, 70);

        if (chartPieWidgetEntity.getBehaviourType() == ChartPieWidgetEntity.ChartPieBehaviourType.ItemsCount) {
            // ALL
            if (chartPieWidgetEntity.getTargetItems().contains(""))
                entityManager.getRepositories().stream()
                        .filter(abstractRepository -> abstractRepository.getEntityClass().getDeclaredAnnotation(UISidebarMenu.class) != null)
                        .forEach(abstractRepository -> {
                            Class<?> clazz = abstractRepository.getEntityClass();
                            String href = SidebarMenuItem.getHref(clazz);
                            legendBuilder.withData(href);
                            seriesBuilder.withData(abstractRepository.size().intValue(), href).withItemStyleColor(TouchHomeUtils.randomColor());
                        });
        }
        return chartBuilder.build();
    }*/

    @Getter
    @AllArgsConstructor
    private static class ChartSeries {
        private String name;
        private String color;
        private List<Date> dates;
        private List<Float> values;
    }

    @Getter
    @AllArgsConstructor
    private static class PieSeries {
        private String name;
        private Object value;
        private Extra extra;

        @Getter
        @AllArgsConstructor
        private static class Extra {
            private String code;
        }
    }

    @Getter
    @AllArgsConstructor
    private static class BarSeries {
        private String name;
        private Object value;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BarSeries barSeries = (BarSeries) o;
            return name.equals(barSeries.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

    @Setter
    private static class IntegerValue {
        private Integer value;
    }

    @Setter
    private static class BooleanValue {
        private Boolean value;
    }

    @RequiredArgsConstructor
    private class EvaluateDatesAndValues {
        private final ChartPeriod chartPeriod;
        private final List<Object[]> chartItems;
        @Getter
        private List<Date> dates;
        @Getter
        private List<List<Float>> values;

        public EvaluateDatesAndValues invoke(WidgetLineChartSeriesEntity item) {
            // get dates split by algorithm
            dates = chartPeriod.getDates(chartItems);
            List<Date> initialDates = new ArrayList<>(dates);
            // minimum number not 0 values to fit requirements
            int minDateSize = initialDates.size() / 2;
            // fill values with remove 0 points. Attention: dates are modified by iterator
            values = WidgetController.this.fulfillValues(dates, chartItems, item.getFillMissingValues());
            int index = 2;
            int prevDates = -1; // prevDates uses to avoid extra iterations if prevDates == datesWithMultiplier
            while (index != 5 && prevDates != dates.size()) {
                prevDates = dates.size();
                dates = new ArrayList<>(initialDates.size() * index);
                for (int i = 0; i < initialDates.size() - 1; i++) {
                    dates.add(initialDates.get(i));
                    dates.add(new Date(((initialDates.get(i + 1).getTime() + initialDates.get(i).getTime())) / 2));
                }
                dates.add(initialDates.get(initialDates.size() - 1));
                initialDates = new ArrayList<>(dates);
                values = WidgetController.this.fulfillValues(dates, chartItems, item.getFillMissingValues());
                index++;
            }
            return this;
        }
    }
}
