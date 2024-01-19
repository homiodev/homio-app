package org.homio.app.rest.widget;

import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.EntityFieldMetadata;
import org.homio.api.entity.HasOrder;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.fs.FileSystemProvider;
import org.homio.api.fs.TreeNode;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.util.DataSourceUtil.SelectionSource;
import org.homio.api.util.Lang;
import org.homio.app.config.cacheControl.CacheControl;
import org.homio.app.config.cacheControl.CachePolicy;
import org.homio.app.manager.ScriptService;
import org.homio.app.manager.WidgetService;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.WidgetBaseEntity;
import org.homio.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.WidgetTabEntity;
import org.homio.app.model.entity.widget.attributes.HasSetSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.impl.color.WidgetColorEntity;
import org.homio.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.homio.app.model.entity.widget.impl.fm.WidgetFMEntity;
import org.homio.app.model.entity.widget.impl.fm.WidgetFMNodeValue;
import org.homio.app.model.entity.widget.impl.fm.WidgetFMSeriesEntity;
import org.homio.app.model.entity.widget.impl.slider.WidgetSliderEntity;
import org.homio.app.model.entity.widget.impl.slider.WidgetSliderSeriesEntity;
import org.homio.app.model.entity.widget.impl.toggle.WidgetToggleEntity;
import org.homio.app.model.entity.widget.impl.toggle.WidgetToggleSeriesEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.homio.app.model.entity.widget.impl.video.sourceResolver.WidgetVideoSourceResolver;
import org.homio.app.model.entity.widget.impl.video.sourceResolver.WidgetVideoSourceResolver.VideoEntityResponse;
import org.homio.app.model.rest.WidgetDataRequest;
import org.homio.app.repository.widget.WidgetTabRepository;
import org.homio.app.rest.widget.WidgetChartsController.SingleValueData;
import org.json.JSONObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/rest/widget")
public class WidgetController {

    private final ObjectMapper objectMapper;
    private final ContextImpl context;
    private final ScriptService scriptService;
    private final WidgetService widgetService;
    private final List<WidgetVideoSourceResolver> videoSourceResolvers;
    private final TimeSeriesUtil timeSeriesUtil;
    private final WidgetTabRepository widgetTabRepository;

    public WidgetController(
            ObjectMapper objectMapper,
        ContextImpl context,
            ScriptService scriptService,
            WidgetService widgetService,
            List<WidgetVideoSourceResolver> videoSourceResolvers,
            WidgetTabRepository widgetTabRepository) {
        this.objectMapper = objectMapper;
        this.context = context;
        this.scriptService = scriptService;
        this.widgetService = widgetService;
        this.widgetTabRepository = widgetTabRepository;
        this.videoSourceResolvers = videoSourceResolvers;
        this.timeSeriesUtil = new TimeSeriesUtil(context);
    }

    @PostMapping("/sources")
    public Map<String, Object> getSources(@Valid @RequestBody Sources request) {
        Map<String, Object> result = new HashMap<>();
        for (String source : request.sources) {
            SelectionSource selection = DataSourceUtil.getSelection(source);
            BaseEntity entity = selection.getValue(context);
            if (!(entity instanceof HasGetStatusValue)) {
                throw new IllegalArgumentException("Unable to get value from non source entity");
            }
            val valueRequest = new HasGetStatusValue.GetStatusValueRequest(context, null);
            result.put(source, ((HasGetStatusValue) entity).getStatusValue(valueRequest));
            timeSeriesUtil.addListenValueIfRequire(true, "dashboard",
                entity, null, null, source,
                object -> ((HasGetStatusValue) entity).getStatusValue(valueRequest));
        }
        return result;
    }

    @SneakyThrows
    @GetMapping("/plugins")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public List<WidgetService.AvailableWidget> getAvailableWidgets() {
        return widgetService.getAvailableWidgets();
    }

    @PostMapping("/fm/{entityID}")
    public List<WidgetFMNodes> getWidgetFileManagerData(
            @PathVariable("entityID") String entityID,
            @RequestParam("w") int width,
            @RequestParam("h") int height,
            @RequestBody List<WidgetFMPrevSnapshot> prevValues) {
        Map<String, Long> seriesToSnapValue = prevValues.stream().collect(Collectors.toMap(n -> n.sid, n -> n.sn));
        WidgetFMEntity entity = context.db().getEntity(entityID);
        if (entity == null) { // in case if deleted but still requested
            return null;
        }
        List<BaseFileSystemEntity> fileSystems = context.getEntityServices(BaseFileSystemEntity.class);
        List<WidgetFMNodes> nodes = new ArrayList<>();
        for (WidgetFMSeriesEntity seriesEntity : entity.getSeries()) {
            String[] path = seriesEntity.getValueDataSource().split("###");
            String parentId = path[0];
            String fs = path[1];
            BaseFileSystemEntity fileSystemEntity = fileSystems.stream().filter(fileSystem -> fileSystem.getEntityID().equals(fs)).findAny().orElse(null);
            if (fileSystemEntity != null) {
                Long snapshot = seriesToSnapValue.get(seriesEntity.getEntityID());
                FileSystemProvider fileSystem = fileSystemEntity.getFileSystem(context, 0);
                Long lastUpdated = fileSystem.toTreeNode(parentId).getAttributes().getLastUpdated();
                Set<WidgetFMNodeValue> items = null;
                if (!Objects.equals(snapshot, lastUpdated)) {
                    Set<TreeNode> children = fileSystem.getChildren(parentId).stream().filter(n -> !n.getAttributes().isDir()).collect(Collectors.toSet());
                    items = children.stream().map(treeNode -> new WidgetFMNodeValue(treeNode, width, height)).collect(Collectors.toSet());
                }
                nodes.add(new WidgetFMNodes(fileSystemEntity.getTitle(), seriesEntity.getEntityID(), lastUpdated, items));
            } else {
                log.info("Unable to find fileSystem");
            }
        }
        return nodes;
    }

    @PostMapping("/videoSource")
    public WidgetVideoSourceResolver.VideoEntityResponse getVideoSource(@RequestBody VideoSourceRequest request) {
        for (WidgetVideoSourceResolver videoSourceResolver : videoSourceResolvers) {
            WidgetVideoSourceResolver.VideoEntityResponse response = videoSourceResolver.resolveDataSource(request.source);
            if (response != null) {
                return response;
            }
        }
        throw new ServerException("W.ERROR.NO_VIDEO_FOUND");
    }

    @GetMapping("/video/{entityID}")
    public List<WidgetVideoSourceResolver.VideoEntityResponse> getCameraData(@PathVariable("entityID") String entityID) {
        WidgetVideoEntity entity = getEntity(entityID);
        List<WidgetVideoSourceResolver.VideoEntityResponse> result = new ArrayList<>();
        for (WidgetVideoSeriesEntity item : entity.getSeries()) {
            for (WidgetVideoSourceResolver videoSourceResolver : videoSourceResolvers) {
                WidgetVideoSourceResolver.VideoEntityResponse response = videoSourceResolver.resolveDataSource(item);
                if (response != null) {
                    result.add(response);
                    handlePoster(item, response);
                    break;
                }
            }
        }
        return result;
    }

    private static void handlePoster(WidgetVideoSeriesEntity item, VideoEntityResponse response) {
        if (StringUtils.isNotEmpty(item.getPosterDataSource())) {
            SelectionSource poster = DataSourceUtil.getSelection(item.getPosterDataSource());
            String posterUrl = poster.getValue();
            if (StringUtils.isNotEmpty(posterUrl)) {
                String type = poster.getMetadata().path("type").asText();
                if ("file".equals(type)) {
                    posterUrl = "$DEVICE_URL/rest/media/image/%s".formatted(posterUrl);
                }
            }
            response.setPoster(posterUrl);
        }
    }

    @PostMapping("/video/action")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void fireVideoAction(@RequestBody VideoActionRequest request) {
        WidgetVideoSeriesEntity series = getSeriesEntity(request);
        try {
            BaseCameraEntity entity = context.db().getEntity(series.getValueDataSource());
            if (entity == null) {
                throw new NotFoundException("ERROR.VIDEO_SERIES_NOT_FOUND");
            }

            UIInputBuilder uiInputBuilder = entity.getService().assembleActions();
            UIActionHandler actionHandler = uiInputBuilder.findActionHandler(request.name);
            if (actionHandler == null) {
                throw new NotFoundException("ERROR.VIDEO_ACTION_NOT_FOUND");
            }
            actionHandler.handleAction(context, new JSONObject().put("value", request.value));
        } catch (Exception ex) {
            throw new IllegalStateException(Lang.getServerMessage(ex.getMessage()));
        }
    }

    @PostMapping("/value")
    public SingleValueData getValue(@Valid @RequestBody WidgetDataRequest request) {
        WidgetBaseEntity entity = request.getEntity(context, objectMapper);
        if (entity instanceof HasSingleValueDataSource) {
            return new SingleValueData(timeSeriesUtil.getSingleValue(entity, (HasSingleValueDataSource) entity, o -> o), null);
        }
        throw new IllegalStateException("Entity: " + request.getEntityID() + " not implement 'HasSingleValueDataSource'");
    }

    @PostMapping("/value/update")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void updateValue(@RequestBody SingleValueRequest<Object> request) {
        HasSetSingleValueDataSource source = getEntity(request.entityID);
        setValue(request.value, source.getSetValueDataSource(), source.getDynamicParameterFields("value"));
    }

    @PostMapping("/slider/values")
    public List<Integer> getSliderValues(@RequestBody WidgetDataRequest request) {
        WidgetSliderEntity entity = request.getEntity(context, objectMapper, WidgetSliderEntity.class);
        List<Integer> values = new ArrayList<>(entity.getSeries().size());
        for (WidgetSliderSeriesEntity item : entity.getSeries()) {
            values.add(timeSeriesUtil.getSingleValue(entity, item, o -> HasSetStatusValue.SetStatusValueRequest.rawValueToNumber(o, 0).intValue()));
        }
        return values;
    }

    @PostMapping("/slider/update")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void handleSlider(@RequestBody SingleValueRequest<Number> request) {
        WidgetSliderSeriesEntity series = getSeriesEntity(request);
        setValue(request.value, series.getSetValueDataSource(), series.getSetValueDynamicParameterFields());
    }

    @PostMapping("/toggle/values")
    public List<String> getToggleValues(@RequestBody WidgetDataRequest request) {
        WidgetToggleEntity entity = request.getEntity(context, objectMapper, WidgetToggleEntity.class);
        List<String> values = new ArrayList<>(entity.getSeries().size());
        for (WidgetToggleSeriesEntity item : entity.getSeries()) {
            values.add(timeSeriesUtil.getSingleValue(entity, item, String::valueOf));
        }
        return values;
    }

    @PostMapping("/display/update")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void handleButtonClick(@RequestBody SingleValueRequest<String> request) {
        WidgetDisplayEntity source = getEntity(request.entityID);
        setValue(request.value, source.getSetValueDataSource(), source.getSetValueDynamicParameterFields());
    }

    @PostMapping("/colors/value")
    public WidgetColorValue getColorValues(@RequestBody WidgetDataRequest request) {
        WidgetColorEntity entity = request.getEntity(context, objectMapper, WidgetColorEntity.class);
        return new WidgetColorValue(
                timeSeriesUtil.getSingleValue(entity,
                        entity.getBrightnessValueDataSource(),
                        entity.getDynamicParameterFields("brightness"),
                        o -> (Integer) o),
                timeSeriesUtil.getSingleValue(entity,
                        entity.getColorValueDataSource(),
                        entity.getDynamicParameterFields("color"),
                        o -> (String) o),
                timeSeriesUtil.getSingleValue(entity,
                        entity.getOnOffValueDataSource(),
                        entity.getDynamicParameterFields("onOff"),
                        o -> (Boolean) o),
                timeSeriesUtil.getSingleValue(entity,
                        entity.getColorTemperatureValueDataSource(),
                        entity.getDynamicParameterFields("colorTemp"),
                        o -> (Integer) o));
    }

    @PostMapping("/colors/update")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void updateColorsValue(@RequestBody ColorValueRequest request) {
        WidgetColorEntity entity = context.db().getEntityRequire(request.entityID);
        switch (request.type) {
            case colorTemp -> setValue(request.value,
                    entity.getColorTemperatureSetValueDataSource(),
                    entity.getDynamicParameterFields("colorTemp"));
            case color -> setValue(request.value,
                    entity.getColorSetValueDataSource(),
                    entity.getDynamicParameterFields("color"));
            case onOff -> setValue(request.value,
                    entity.getOnOffSetValueDataSource(),
                    entity.getDynamicParameterFields("onOff"));
            case brightness -> setValue(request.value,
                    entity.getBrightnessSetValueDataSource(),
                    entity.getDynamicParameterFields("brightness"));
        }
    }

    @PostMapping("/toggle/update")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void updateToggleValue(@RequestBody SingleValueRequest<Boolean> request) {
        WidgetToggleSeriesEntity series = getSeriesEntity(request);
        setValue(
            request.value ? series.getPushToggleOnValue() : series.getPushToggleOffValue(),
            series.getSetValueDataSource(),
            series.getSetValueDynamicParameterFields());
    }

    @GetMapping("/{entityID}")
    public WidgetBaseEntity getWidget(@PathVariable("entityID") String entityID) {
        WidgetBaseEntity widget = context.db().getEntity(entityID);
        updateWidgetBeforeReturnToUI(widget);
        return widget;
    }

    @GetMapping("/tab/{tabId}")
    public List<WidgetEntity> getWidgetsInTab(@PathVariable("tabId") String tabId) {
        List<WidgetBaseEntity> widgets = context.db().findAll(WidgetBaseEntity.class).stream()
                .filter(w -> w.getWidgetTabEntity().getEntityID().equals(tabId)).toList();

        List<WidgetEntity> result = new ArrayList<>();
        for (WidgetBaseEntity<?> widget : widgets) {
            updateWidgetBeforeReturnToUI(widget);

            UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
            if (widget instanceof HasDynamicContextMenuActions) {
                ((HasDynamicContextMenuActions) widget).assembleActions(uiInputBuilder);
            }
            result.add(new WidgetEntity(widget, uiInputBuilder.buildAll()));
        }

        return result;
    }

    @PostMapping("/create/{tabId}/{type}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public BaseEntity createWidget(@PathVariable("tabId") String tabId, @PathVariable("type") String type) throws Exception {
        log.debug("Request creating widget entity by type: <{}> in tabId <{}>", type, tabId);
        WidgetTabEntity widgetTabEntity = context.db().getEntity(tabId);
        if (widgetTabEntity == null) {
            throw new NotFoundException("ERROR.TAB_NOT_FOUND", tabId);
        }

        Class<? extends EntityFieldMetadata> typeClass = ContextImpl.uiFieldClasses.get(type);
        WidgetBaseEntity<?> baseEntity = (WidgetBaseEntity<?>) typeClass.getConstructor().newInstance();

        baseEntity.setWidgetTabEntity(widgetTabEntity);
        return context.db().save(baseEntity);
    }

    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    @PostMapping("/create/{tabId}/{type}/{addon}")
    public BaseEntity createExtraWidget(
            @PathVariable("tabId") String tabId,
            @PathVariable("type") String type) {
        throw new ServerException("Not implemented yet");
        /*log.debug("Request creating extra widget entity by type: <{}> in tabId <{}>", type, tabId);
        WidgetTabEntity widgetTabEntity = context.db().getEntity(tabId);
        if (widgetTabEntity == null) {
            throw new NotFoundException("ERROR.TAB_NOT_FOUND", tabId);
        }

        Collection<WidgetBaseTemplate> widgets = context.getBeansOfType(WidgetBaseTemplate.class);

        List<WidgetBaseTemplate> templates = widgets.stream().filter(w -> w.getName().equals(type)).toList();
        if (templates.isEmpty()) {
            throw new ServerException("Unable to find widget template by name: " + type);
        }
        if (templates.size() > 1) {
            throw new ServerException("Found multiple widget templates by name: " + type);
        }

        WidgetBaseTemplate template = templates.get(0);
        String js = template.toJavaScript();
        String params = "";
        boolean paramReadOnly = false;
        if (js == null) {
            if (template instanceof WidgetJSBaseTemplate widgetJSBaseTemplate) {
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

        widgetJsEntity.setWidgetTabEntity(widgetTabEntity).setFieldFetchType(template.getName());
        return context.db().save(widgetJsEntity);*/
    }

    @GetMapping("/tab")
    public List<WidgetTabEntity> getWidgetTabs() {
        return context.db().findAll(WidgetTabEntity.class);
    }

    @SneakyThrows
    @PostMapping("/tab/{name}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public WidgetTabEntity createWidgetTab(@PathVariable("name") String name) {
        BaseEntity widgetTab = context.db().getEntity(WidgetTabEntity.PREFIX + name);
        if (widgetTab == null) {
            WidgetTabEntity widgetTabEntity = new WidgetTabEntity();
            widgetTabEntity.setEntityID(name);
            widgetTabEntity.setName(name);
            widgetTabEntity.setOrder(this.findHighestOrder() + 1);
            return context.db().save(widgetTabEntity);
        }
        throw new ServerException("Widget tab with same name already exists");
    }

    @PostMapping("/tab/{tabId}/move")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void moveWidgetTab(@PathVariable("tabId") String tabId, @RequestParam("left") boolean left) {
        List<WidgetTabEntity> tabs = context.db().findAll(WidgetTabEntity.class).stream().sorted().collect(Collectors.toList());
        WidgetTabEntity tabToMove = tabs.stream().filter(t -> t.getEntityID().equals(tabId)).findAny().orElseThrow(
                () -> new IllegalArgumentException("No tab: " + tabs + " found"));
        int order = tabToMove.getOrder();
        if (left && order > 1) {
            shiftTab(tabs, tabToMove, order, -1);
        } else if (!left) {
            shiftTab(tabs, tabToMove, order, 1);
        } else {
            throw new IllegalStateException("Unable to move tab");
        }
    }

    private Integer findHighestOrder() {
        return context.db().findAll(WidgetTabEntity.class)
                .stream().map(HasOrder::getOrder)
                .max(Integer::compare).orElse(0);
    }

    private void shiftTab(List<WidgetTabEntity> tabs, WidgetTabEntity tabToMove, int order, int shift) {
        WidgetTabEntity replaceTab = tabs.stream().filter(t -> t.getOrder() == order + shift).findAny().orElseThrow(
                () -> new IllegalStateException("Unable to find tab with order: " + (order + shift)));
        tabToMove.setOrder(order + shift);
        replaceTab.setOrder(order);
        context.db().save(tabToMove, false);
        context.db().save(replaceTab, false);
    }

    private void updateWidgetBeforeReturnToUI(WidgetBaseEntity<?> widget) {
        /*TODO: fix:::if (widget instanceof WidgetJsEntity) {
            WidgetJsEntity jsEntity = (WidgetJsEntity) widget;
            try {
                jsEntity.setJavaScriptErrorResponse(null);
                ScriptEntity scriptEntity = new ScriptEntity().setJavaScript(jsEntity.getJavaScript()).setJavaScriptParameters(jsEntity
                .getJavaScriptParameters());

                CompileScriptContext compileScriptContext =
                    scriptService.createCompiledScript(scriptEntity, null);
                jsEntity.setJavaScriptResponse(scriptService.runJavaScript(compileScriptContext).stringValue());

            } catch (Exception ex) {
                jsEntity.setJavaScriptErrorResponse(CommonUtils.getErrorMessage(ex));
            }
        }*/
    }

    private WidgetTabEntity getWidgetTabEntity(String tabId) {
        BaseEntity baseEntity = context.db().getEntity(tabId);
        if (baseEntity instanceof WidgetTabEntity) {
            return (WidgetTabEntity) baseEntity;
        }
        throw new ServerException("Unable to find widget tab with id: " + tabId);
    }

    private <T extends WidgetSeriesEntity> T getSeriesEntity(SingleValueRequest<?> request) {
        WidgetBaseEntityAndSeries entity = context.db().getEntityRequire(request.entityID);
        T series = ((Set<T>) entity.getSeries()).stream().filter(s -> s.getEntityID().equals(request.seriesEntityID)).findAny().orElse(null);
        if (series == null) {
            throw new NotFoundException("Unable to find series: " + request.seriesEntityID + " for entity: " + entity.getTitle());
        }
        return series;
    }

    private <T> T getEntity(String entityID) {
        BaseEntity entity = context.db().getEntity(entityID);
        if (entity == null) {
            throw new IllegalArgumentException("Unable to find widget with entityID: " + entityID);
        }
        return (T) entity;
    }

    private void setValue(Object value, String dataSource, JSONObject dynamicParameters) {
        SelectionSource selection = DataSourceUtil.getSelection(dataSource);
        BaseEntity entity = selection.getValue(context);
        ((HasSetStatusValue) entity).setStatusValue(new HasSetStatusValue.SetStatusValueRequest(
            context,
            dynamicParameters,
            value));
    }

    @Getter
    @Setter
    private static class WidgetFMPrevSnapshot {

        private long sn;
        private String sid;
    }

    @Getter
    @AllArgsConstructor
    private static class WidgetFMNodes {

        private String title;
        private String seriesEntityID;
        private long snapshotHashCode;
        private Set<WidgetFMNodeValue> nodes;
    }

    @Setter
    private static class VideoActionRequest extends SingleValueRequest<Void> {

        private String name;
        private String value;
    }

    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SingleValueRequest<T> {

        private String entityID;
        private String seriesEntityID;
        private T value;
    }

    @Getter
    @AllArgsConstructor
    private static class WidgetEntity {

        private WidgetBaseEntity widget;
        private Collection<UIInputEntity> actions;
    }

    @Getter
    @AllArgsConstructor
    private static class WidgetColorValue {

        private Integer brightness;
        private String color;
        private Boolean onOffValue;
        private Integer colorTemp;
    }

    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ColorValueRequest {

        private String entityID;
        private Object value;
        private Type type;

        private enum Type {
            color, colorTemp, onOff, brightness
        }
    }

    @Setter
    private static class Sources {
        private String[] sources;
    }

    @Getter
    @Setter
    public static class VideoSourceRequest {

        private String source;
    }
}
