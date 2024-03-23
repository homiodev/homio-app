package org.homio.app.rest.widget;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
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
import org.homio.app.manager.WidgetService;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetEntityAndSeries;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.WidgetTabEntity;
import org.homio.app.model.entity.widget.WidgetTabEntity.ScreenLayout;
import org.homio.app.model.entity.widget.attributes.HasSetSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.impl.color.WidgetColorEntity;
import org.homio.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.homio.app.model.entity.widget.impl.fm.WidgetFMEntity;
import org.homio.app.model.entity.widget.impl.fm.WidgetFMNodeValue;
import org.homio.app.model.entity.widget.impl.slider.WidgetSliderEntity;
import org.homio.app.model.entity.widget.impl.slider.WidgetSliderSeriesEntity;
import org.homio.app.model.entity.widget.impl.toggle.WidgetToggleEntity;
import org.homio.app.model.entity.widget.impl.toggle.WidgetToggleSeriesEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.homio.app.model.entity.widget.impl.video.sourceResolver.WidgetVideoSourceResolver;
import org.homio.app.model.entity.widget.impl.video.sourceResolver.WidgetVideoSourceResolver.VideoEntityResponse;
import org.homio.app.model.rest.WidgetDataRequest;
import org.homio.app.rest.widget.WidgetChartsController.SingleValueData;
import org.jetbrains.annotations.NotNull;
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
    private final WidgetService widgetService;
    private final List<WidgetVideoSourceResolver> videoSourceResolvers;
    private final TimeSeriesUtil timeSeriesUtil;

    public WidgetController(
        ObjectMapper objectMapper,
        ContextImpl context,
        WidgetService widgetService,
        List<WidgetVideoSourceResolver> videoSourceResolvers) {
        this.objectMapper = objectMapper;
        this.context = context;
        this.widgetService = widgetService;
        this.videoSourceResolvers = videoSourceResolvers;
        this.timeSeriesUtil = new TimeSeriesUtil(context);
    }

    @PostMapping("/sources")
    public Map<String, Object> getSources(@Valid @RequestBody WidgetController.SourcesRequest request) {
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
    public @NotNull Set<WidgetFMNodeValue> getWidgetFileManagerData(
            @PathVariable("entityID") String entityID,
            @RequestParam("w") int width,
            @RequestParam("h") int height,
            @RequestBody List<WidgetFMPrevSnapshot> prevValues) {
        Map<String, Long> seriesToSnapValue = prevValues.stream().collect(Collectors.toMap(n -> n.sid, n -> n.sn));
        WidgetFMEntity entity = context.db().getEntity(entityID);
        if (entity == null) { // in case if deleted but still requested
            return Set.of();
        }
        List<BaseFileSystemEntity> fileSystems = context.getEntityServices(BaseFileSystemEntity.class);
        SelectionSource source = DataSourceUtil.getSelection(entity.getValueDataSource());
        String parentId = source.getValue();
        String fs = DataSourceUtil.getSelection(entity.getValueDataSource()).getMetadata().get("fs").asText();
        BaseFileSystemEntity fileSystemEntity = fileSystems.stream().filter(fileSystem -> fileSystem.getEntityID().equals(fs)).findAny().orElse(null);
        if (fileSystemEntity != null) {
            return getWidgetFMNodes(width, height, seriesToSnapValue, entity, fileSystemEntity, parentId);
        } else {
            log.info("Unable to find fileSystem");
        }
        return Set.of();
    }

    private Set<WidgetFMNodeValue> getWidgetFMNodes(int width, int height, Map<String, Long> seriesToSnapValue, WidgetFMEntity entity,
        BaseFileSystemEntity fileSystemEntity, String parentId) {
        Long snapshot = seriesToSnapValue.get(entity.getEntityID());
        FileSystemProvider fileSystem = fileSystemEntity.getFileSystem(context, 0);
        Long lastUpdated = fileSystem.toTreeNode(parentId).getAttributes().getLastUpdated();
        Set<WidgetFMNodeValue> items = null;
        if (!Objects.equals(snapshot, lastUpdated)) {
            Set<TreeNode> children = findVisibleNodes(entity, parentId, fileSystem);
            items = children
                .stream()
                .map(WidgetFMNodeValue::new)
                .collect(Collectors.toSet());
        }
        return items;
    }

    @NotNull
    private static Set<TreeNode> findVisibleNodes(WidgetFMEntity entity, String parentId, FileSystemProvider fileSystem) {
        List<Pattern> filters = entity
            .getFileFilters()
            .stream()
            .map(regex -> {
                if (regex.startsWith("*")) {
                    regex = "." + regex;
                } else if (!regex.startsWith(".*")) {
                    regex = ".*" + regex;
                }
                try {
                    return Pattern.compile(regex);
                } catch (Exception ignore) {}
                return null;
            }).filter(Objects::nonNull)
            .toList();
        Set<TreeNode> allNodes = fileSystem.getChildren(parentId);
        return allNodes
            .stream()
            .filter(n -> filterFile(n, entity, filters))
            .collect(Collectors.toSet());
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
        WidgetEntity entity = request.getEntity(context, objectMapper);
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
    public WidgetColorValueResponse getColorValues(@RequestBody WidgetDataRequest request) {
        WidgetColorEntity entity = request.getEntity(context, objectMapper, WidgetColorEntity.class);
        return new WidgetColorValueResponse(
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
    public WidgetEntity getWidget(@PathVariable("entityID") String entityID) {
        WidgetEntity widget = context.db().getEntity(entityID);
        updateWidgetBeforeReturnToUI(widget);
        return widget;
    }

    @GetMapping("/tab/{tabId}")
    public List<WidgetEntityResponse> getWidgetsInTab(
        @PathVariable("tabId") String tabId,
        @RequestParam("w") int width,
        @RequestParam("h") int height,
        @RequestParam(value = "widget", required = false) String widgetEntityID) {
        List<WidgetEntity> widgets = context
            .db()
            .findAll(WidgetEntity.class)
            .stream()
            .filter(w -> (widgetEntityID == null || w.getEntityID().equals(widgetEntityID)) && w.getWidgetTabEntity().getEntityID().equals(tabId))
            .toList();
        if (!widgets.isEmpty()) {
            ScreenLayout layout = widgets.get(0).getWidgetTabEntity().getLayoutOrDefault(width, height);

            for (WidgetEntity widget : widgets) {
                if (StringUtils.isEmpty(widget.getParent())) {
                    widget.setXb(widget.getXb(layout.getKey()));
                    widget.setYb(widget.getYb(layout.getKey()));
                    widget.setBw(widget.getBh(layout.getKey()));
                    widget.setBh(widget.getBw(layout.getKey()));
                }
            }
        }

        List<WidgetEntityResponse> result = new ArrayList<>();
        for (WidgetEntity<?> widget : widgets) {
            updateWidgetBeforeReturnToUI(widget);

            UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
            if (widget instanceof HasDynamicContextMenuActions da) {
                da.assembleActions(uiInputBuilder);
            }
            result.add(new WidgetEntityResponse(widget, uiInputBuilder.buildAll()));
        }

        return result;
    }

    @PostMapping("/create/{tabId}/{type}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public BaseEntity createWidget(
        @PathVariable("tabId") String tabId,
        @PathVariable("type") String type,
        @RequestParam("w") int width,
        @RequestParam("h") int height) throws Exception {
        log.debug("Request creating widget entity by type: <{}> in tabId <{}>", type, tabId);
        WidgetTabEntity widgetTabEntity = context.db().getEntity(tabId);
        if (widgetTabEntity == null) {
            throw new NotFoundException("ERROR.TAB_NOT_FOUND", tabId);
        }

        Class<? extends EntityFieldMetadata> typeClass = ContextImpl.uiFieldClasses.get(type);
        WidgetEntity<?> baseEntity = (WidgetEntity<?>) typeClass.getConstructor()
                                                                .newInstance();

        baseEntity.setWidgetTabEntity(widgetTabEntity);
        baseEntity.getWidgetTabEntity().addLayoutOptional(width, height);
        findSuitablePosition(baseEntity, new AtomicBoolean());
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
    public WidgetTabEntity createWidgetTab(@PathVariable("name") String name, @RequestBody CreateWidgetRequest request) {
        BaseEntity widgetTab = context.db().getEntity(WidgetTabEntity.PREFIX + name);
        if (widgetTab == null) {
            WidgetTabEntity widgetTabEntity = new WidgetTabEntity();
            widgetTabEntity.setEntityID(name);
            widgetTabEntity.setName(name);
            widgetTabEntity.setIcon(request.icon);
            widgetTabEntity.setIconColor(request.color);
            widgetTabEntity.setOrder(this.findHighestOrder() + 1);
            return context.db().save(widgetTabEntity);
        }
        throw new ServerException("Widget tab with same name already exists");
    }

    @SneakyThrows
    @PostMapping("/tab/{tabId}/layout")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void updateTabLayout(@PathVariable("tabId") String tabId, @RequestBody LayoutTabRequest request) {
        WidgetTabEntity tab = context.db().getEntity(tabId);
        if (tab == null) {
            throw new IllegalArgumentException("No tab: " + tabId + " found");
        }
        tab.addLayout(request.hb, request.vb, request.sw, request.sh);
        context.db().save(tab);
    }

    @SneakyThrows
    @PostMapping("/insert/{widgetEntityId}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void updateTabLayout(@PathVariable("widgetEntityId") String widgetEntityID) {
        WidgetEntity widgetEntity = context.db().getEntityRequire(widgetEntityID);
        AtomicBoolean processed = new AtomicBoolean(true);
        findSuitablePosition(widgetEntity, processed);
        if(!processed.get()) {
            throw new IllegalStateException("Unable to find free position for widget");
        }
        context.db().save(widgetEntity);
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

    private void updateWidgetBeforeReturnToUI(WidgetEntity<?> widget) {
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
        WidgetEntityAndSeries entity = context.db().getEntityRequire(request.entityID);
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

    private static boolean filterFile(TreeNode n, WidgetFMEntity entity, List<Pattern> filters) {
        if (!entity.getShowDirectories() && n.getAttributes().isDir()) {
            return false;
        }
        if (!filters.isEmpty()) {
            for (Pattern filter : filters) {
                if (filter.matcher(StringUtils.defaultIfEmpty(n.getId(), n.getId())).matches()) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * Find free space in matrix for new item
     */
    private void findSuitablePosition(WidgetEntity<?> widget, AtomicBoolean processed) {
        List<WidgetEntity> widgets = context
            .db()
            .findAll(WidgetEntity.class)
            .stream().filter(w -> w.getWidgetTabEntity().getEntityID().equals(widget.getWidgetTabEntity().getEntityID()))
            .toList();
        if (isNotEmpty(widget.getParent())) {
            WidgetEntity layout = widgets.stream().filter(w -> w.getEntityID().equals(widget.getParent())).findAny().orElse(null);
            if (layout == null) {
                throw new IllegalArgumentException("Widget: " + widget.getTitle() + " has xbl/tbl and have to be belong to layout widget but it's not found");
            }
            // do not change position for widget which belong to layout
            return;
        }

        for (ScreenLayout sl : widget.getWidgetTabEntity().getLayout()) {
            boolean[][] matrix = initMatrix(widgets, sl);
            AtomicBoolean slProcessed = new AtomicBoolean(false);
            findMatrixFreePosition(widget, matrix, sl, widget.getBw(sl.getKey()), widget.getBh(sl.getKey()), slProcessed);
            if(!slProcessed.get()) {
                processed.set(false);
            }

        }
    }

    private static void findMatrixFreePosition(WidgetEntity<?> widget, boolean[][] matrix, ScreenLayout sl,
        int bw, int bh, AtomicBoolean processed) {
        boolean satisfyPosition = isSatisfyPosition(matrix, widget.getXb(), widget.getYb(), bw, bh, sl.getHb(), sl.getVb());
        if (!satisfyPosition) {
            Pair<Integer, Integer> freePosition = findMatrixFreePosition(matrix, bw, bh, sl.getHb(), sl.getVb());
            if (freePosition == null) {
                // try decrease widget bw/bh
                if (bw >= bh && bw > 1) {
                    findMatrixFreePosition(widget, matrix, sl, bw - 1, bh, processed);
                    if (processed.get()) {
                        return;
                    }
                }
                if (bh >= bw && bh > 1) {
                    findMatrixFreePosition(widget, matrix, sl, bw, bh - 1, processed);
                    if (processed.get()) {
                        return;
                    }
                }

                if (!processed.get()) {
                    widget.setXb(-1, sl.getKey());
                    widget.setYb(-1, sl.getKey());
                }
            } else {
                widget.setBw(bw, sl.getKey());
                widget.setBh(bh, sl.getKey());
                widget.setXb(freePosition.getKey(), sl.getKey());
                widget.setYb(freePosition.getValue(), sl.getKey());
                processed.set(true);
            }
        } else {
            widget.setBw(bw, sl.getKey());
            widget.setBh(bh, sl.getKey());
            processed.set(true);
        }
    }

    /**
     * Check if matrix has free slot for specific width/height and return first available position
     */
    private static Pair<Integer, Integer> findMatrixFreePosition(boolean[][] matrix, int bw, int bh, int hBlockCount, int vBlockCount) {
        for (int j = 0; j < hBlockCount; j++) {
            for (int i = 0; i < vBlockCount; i++) {
                if (isSatisfyPosition(matrix, i, j, bw, bh, hBlockCount, vBlockCount)) {
                    return Pair.of(i, j);
                }
            }
        }
        return null;
    }

    private static boolean isSatisfyPosition(boolean[][] matrix, int xPos, int yPos, int width, int height, int hBlockCount, int vBlockCount) {
        for (int j = xPos; j < xPos + width; j++) {
            for (int i = yPos; i < yPos + height; i++) {
                if (j >= vBlockCount || i >= hBlockCount || matrix[j][i]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean[][] initMatrix(List<WidgetEntity> widgets, ScreenLayout sl) {
        boolean[][] matrix = new boolean[sl.getVb()][sl.getHb()];
        for (int j = 0; j < sl.getVb(); j++) {
            matrix[j] = new boolean[sl.getHb()];
        }

        List<WidgetEntity> copyWidgets = new ArrayList<>(widgets);
        for (WidgetEntity model : copyWidgets) {
            try {
                fillMatrixWidget(matrix, sl, model);
            } catch (Exception ignore) {
                // for now we ignore widget if it's unable to insert into matrix
                copyWidgets.remove(model);
                return initMatrix(copyWidgets, sl);
            }
        }
        return matrix;
    }

    private static void fillMatrixWidget(boolean[][] matrix, ScreenLayout sl, WidgetEntity model) {
        if (isEmpty(model.getParent())) {
            int x = model.getXb(sl.getKey());
            int y = model.getYb(sl.getKey());
            if (x < 0 || y < 0) {
                return;
            }
            for (int j = x; j < x + model.getBw(sl.getKey()); j++) {
                for (int i = y; i < y + model.getBh(sl.getKey()); i++) {
                    matrix[i][j] = true;
                }
            }
        }
    }

    @Getter
    @Setter
    public static class WidgetFMPrevSnapshot {

        private long sn;
        private String sid;
    }

    @Setter
    public static class VideoActionRequest extends SingleValueRequest<Void> {

        private String name;
        private String value;
    }

    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SingleValueRequest<T> {

        private String entityID;
        private String seriesEntityID;
        private T value;
    }

    @Getter
    @AllArgsConstructor
    public static class WidgetEntityResponse {

        private WidgetEntity<?> widget;
        private Collection<UIInputEntity> actions;
    }

    @Getter
    @AllArgsConstructor
    public static class WidgetColorValueResponse {

        private Integer brightness;
        private String color;
        private Boolean onOffValue;
        private Integer colorTemp;
    }

    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColorValueRequest {

        private String entityID;
        private Object value;
        private Type type;

        private enum Type {
            color, colorTemp, onOff, brightness
        }
    }

    @Setter
    public static class SourcesRequest {
        private String[] sources;
    }

    @Getter
    @Setter
    public static class VideoSourceRequest {

        private String source;
    }

    @Getter
    @Setter
    public static class CreateWidgetRequest {

        private String icon;
        private String color;
    }

    @Getter
    @Setter
    private static class LayoutTabRequest {

        private int hb;
        private int vb;
        private int sw;
        private int sh;
    }
}
