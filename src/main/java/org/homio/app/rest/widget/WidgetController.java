package org.homio.app.rest.widget;

import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Collection;
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
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.EntityFieldMetadata;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.fs.FileSystemProvider;
import org.homio.api.fs.TreeNode;
import org.homio.api.model.OptionModel;
import org.homio.api.ui.action.UIActionHandler;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.util.Lang;
import org.homio.api.video.BaseFFMPEGVideoStreamEntity;
import org.homio.api.widget.WidgetBaseTemplate;
import org.homio.api.widget.WidgetJSBaseTemplate;
import org.homio.app.config.cacheControl.CacheControl;
import org.homio.app.config.cacheControl.CachePolicy;
import org.homio.app.manager.ScriptService;
import org.homio.app.manager.WidgetService;
import org.homio.app.manager.common.EntityContextImpl;
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
import org.homio.app.model.entity.widget.impl.js.WidgetJavaJsEntity;
import org.homio.app.model.entity.widget.impl.slider.WidgetSliderEntity;
import org.homio.app.model.entity.widget.impl.slider.WidgetSliderSeriesEntity;
import org.homio.app.model.entity.widget.impl.toggle.WidgetToggleEntity;
import org.homio.app.model.entity.widget.impl.toggle.WidgetToggleSeriesEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoEntity;
import org.homio.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.homio.app.model.entity.widget.impl.video.sourceResolver.WidgetVideoSourceResolver;
import org.homio.app.model.rest.WidgetDataRequest;
import org.homio.app.rest.widget.WidgetChartsController.SingleValueData;
import org.homio.app.utils.JavaScriptBuilderImpl;
import org.json.JSONObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/rest/widget")
public class WidgetController {

    private final ObjectMapper objectMapper;
    private final EntityContextImpl entityContext;
    private final ScriptService scriptService;
    private final WidgetService widgetService;
    private final List<WidgetVideoSourceResolver> videoSourceResolvers;
    private final TimeSeriesUtil timeSeriesUtil;

    public WidgetController(ObjectMapper objectMapper, EntityContextImpl entityContext, ScriptService scriptService, WidgetService widgetService,
        List<WidgetVideoSourceResolver> videoSourceResolvers) {
        this.objectMapper = objectMapper;
        this.entityContext = entityContext;
        this.scriptService = scriptService;
        this.widgetService = widgetService;
        this.videoSourceResolvers = videoSourceResolvers;
        this.timeSeriesUtil = new TimeSeriesUtil(entityContext);
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
        WidgetFMEntity entity = entityContext.getEntity(entityID);
        if (entity == null) { // in case if deleted but still requested
            return null;
        }
        List<BaseFileSystemEntity> fileSystems = entityContext.getEntityServices(BaseFileSystemEntity.class);
        List<WidgetFMNodes> nodes = new ArrayList<>();
        for (WidgetFMSeriesEntity seriesEntity : entity.getSeries()) {
            String[] path = seriesEntity.getValueDataSource().split("###");
            String parentId = path[0];
            String fs = path[1];
            BaseFileSystemEntity fileSystemEntity = fileSystems.stream().filter(fileSystem -> fileSystem.getEntityID().equals(fs)).findAny().orElse(null);
            if (fileSystemEntity != null) {
                Long snapshot = seriesToSnapValue.get(seriesEntity.getEntityID());
                FileSystemProvider fileSystem = fileSystemEntity.getFileSystem(entityContext);
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

    @GetMapping("/video/{entityID}")
    public List<WidgetVideoSourceResolver.VideoEntityResponse> getCameraData(@PathVariable("entityID") String entityID) {
        WidgetVideoEntity entity = getEntity(entityID);
        List<WidgetVideoSourceResolver.VideoEntityResponse> result = new ArrayList<>();
        for (WidgetVideoSeriesEntity item : entity.getSeries()) {
            for (WidgetVideoSourceResolver videoSourceResolver : videoSourceResolvers) {
                WidgetVideoSourceResolver.VideoEntityResponse response = videoSourceResolver.resolveDataSource(item);
                if (response != null) {
                    result.add(response);
                    break;
                }
            }
        }
        return result;
    }

    @PostMapping("/video/action")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void fireVideoAction(@RequestBody VideoActionRequest request) {
        WidgetVideoSeriesEntity series = getSeriesEntity(request);
        try {
            BaseFFMPEGVideoStreamEntity streamEntity = entityContext.getEntity(series.getValueDataSource());
            if (streamEntity == null) {
                throw new NotFoundException("video.error.seriesNotFound");
            }

            UIInputBuilder uiInputBuilder = streamEntity.getService().assembleActions();
            UIActionHandler actionHandler = uiInputBuilder.findActionHandler(request.name);
            if (actionHandler == null) {
                throw new NotFoundException("video.error.actionNotFound");
            }
            actionHandler.handleAction(entityContext, new JSONObject().put("value", request.value));
        } catch (Exception ex) {
            throw new IllegalStateException(Lang.getServerMessage(ex.getMessage()));
        }
    }

    @PostMapping("/value")
    public SingleValueData getValue(@Valid @RequestBody WidgetDataRequest request) {
        WidgetBaseEntity entity = request.getEntity(entityContext, objectMapper);
        if (entity instanceof HasSingleValueDataSource) {
            return new SingleValueData(timeSeriesUtil.getSingleValue(entity, (HasSingleValueDataSource) entity, o -> o), null);
        }
        throw new IllegalStateException("Entity: " + request.getEntityID() + " not implement 'HasSingleValueDataSource'");
    }

    @PostMapping("/value/update")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void updateValue(@RequestBody SingleValueRequest<Object> request) {
        HasSetSingleValueDataSource source = getEntity(request.entityID);
        DataSourceUtil.setValue(entityContext, source.getSetValueDataSource(),
            source.getDynamicParameterFields("value"), request.value);
    }

    @PostMapping("/slider/values")
    public List<Integer> getSliderValues(@RequestBody WidgetDataRequest request) {
        WidgetSliderEntity entity = request.getEntity(entityContext, objectMapper, WidgetSliderEntity.class);
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
        DataSourceUtil.setValue(entityContext, series.getSetValueDataSource(), series.getSetValueDynamicParameterFields(),
            request.value);
    }

    @PostMapping("/toggle/values")
    public List<String> getToggleValues(@RequestBody WidgetDataRequest request) {
        WidgetToggleEntity entity = request.getEntity(entityContext, objectMapper, WidgetToggleEntity.class);
        List<String> values = new ArrayList<>(entity.getSeries().size());
        for (WidgetToggleSeriesEntity item : entity.getSeries()) {
            values.add(timeSeriesUtil.getSingleValue(entity, item, String::valueOf));
        }
        return values;
    }

    @PostMapping("/display/update")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void handleButtonClick(@RequestBody SingleValueRequest<String> request) {
        WidgetDisplayEntity entity = getEntity(request.entityID);
        DataSourceUtil.setValue(entityContext, entity.getSetValueDataSource(), entity.getSetValueDynamicParameterFields(),
            request.value);
    }

    @PostMapping("/colors/value")
    public WidgetColorValue getColorValues(@RequestBody WidgetDataRequest request) {
        WidgetColorEntity entity = request.getEntity(entityContext, objectMapper, WidgetColorEntity.class);
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
        WidgetColorEntity entity = entityContext.getEntityRequire(request.entityID);
        switch (request.type) {
            case colorTemp:
                DataSourceUtil.setValue(entityContext,
                    entity.getColorTemperatureSetValueDataSource(),
                    entity.getDynamicParameterFields("colorTemp"), request.value);
                break;
            case color:
                DataSourceUtil.setValue(entityContext,
                    entity.getColorSetValueDataSource(),
                    entity.getDynamicParameterFields("color"), request.value);
                break;
            case onOff:
                DataSourceUtil.setValue(entityContext,
                    entity.getOnOffSetValueDataSource(),
                    entity.getDynamicParameterFields("onOff"), request.value);
                break;
            case brightness:
                DataSourceUtil.setValue(entityContext,
                    entity.getBrightnessSetValueDataSource(),
                    entity.getDynamicParameterFields("brightness"), request.value);
                break;
        }
    }

    @PostMapping("/toggle/update")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void updateToggleValue(@RequestBody SingleValueRequest<Boolean> request) {
        WidgetToggleSeriesEntity series = getSeriesEntity(request);
        DataSourceUtil.setValue(entityContext, series.getSetValueDataSource(), series.getSetValueDynamicParameterFields(),
            request.value ? series.getPushToggleOnValue() : series.getPushToggleOffValue());
    }

    @GetMapping("/{entityID}")
    public WidgetBaseEntity getWidget(@PathVariable("entityID") String entityID) {
        WidgetBaseEntity widget = entityContext.getEntity(entityID);
        updateWidgetBeforeReturnToUI(widget);
        return widget;
    }

    @GetMapping("/tab/{tabId}")
    public List<WidgetEntity> getWidgetsInTab(@PathVariable("tabId") String tabId) {
        List<WidgetBaseEntity> widgets = entityContext.findAll(WidgetBaseEntity.class).stream()
                                                      .filter(w -> w.getWidgetTabEntity().getEntityID().equals(tabId))
                                                      .collect(Collectors.toList());

        List<WidgetEntity> result = new ArrayList<>();
        for (WidgetBaseEntity<?> widget : widgets) {
            updateWidgetBeforeReturnToUI(widget);

            UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
            if (widget instanceof HasDynamicContextMenuActions) {
                ((HasDynamicContextMenuActions) widget).assembleActions(uiInputBuilder);
            }
            result.add(new WidgetEntity(widget, uiInputBuilder.buildAll()));
        }

        return result;
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

    @PostMapping("/create/{tabId}/{type}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public BaseEntity<?> createWidget(@PathVariable("tabId") String tabId, @PathVariable("type") String type) throws Exception {
        log.debug("Request creating widget entity by type: <{}> in tabId <{}>", type, tabId);
        WidgetTabEntity widgetTabEntity = entityContext.getEntity(tabId);
        if (widgetTabEntity == null) {
            throw new NotFoundException("Unable to find tab with tabId: " + tabId);
        }

        Class<? extends EntityFieldMetadata> typeClass = EntityContextImpl.uiFieldClasses.get(type);
        WidgetBaseEntity<?> baseEntity = (WidgetBaseEntity<?>) typeClass.getConstructor().newInstance();

        baseEntity.setWidgetTabEntity(widgetTabEntity);
        return entityContext.save(baseEntity);
    }

    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    @PostMapping("/create/{tabId}/{type}/{addon}")
    public BaseEntity<?> createExtraWidget(
        @PathVariable("tabId") String tabId,
        @PathVariable("type") String type,
        @PathVariable("addon") String addon) {
        log.debug("Request creating extra widget entity by type: <{}> in tabId <{}>, addon: <{}>", type, tabId, addon);
        WidgetTabEntity widgetTabEntity = entityContext.getEntity(tabId);
        if (widgetTabEntity == null) {
            throw new NotFoundException("Unable to find tab with tabId: " + tabId);
        }

        Collection<WidgetBaseTemplate> widgets = entityContext.getBeansOfTypeByAddons(WidgetBaseTemplate.class).get(addon);
        if (widgets == null) {
            throw new NotFoundException("Unable to find addon: " + tabId + " or widgets in addon");
        }
        WidgetBaseTemplate template = widgets.stream()
                                             .filter(w -> w.getClass().getSimpleName().equals(type))
                                             .findAny()
                                             .orElseThrow(() -> new NotFoundException("Unable to find widget: " + type + " in addon: " + addon));

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

        WidgetJavaJsEntity widgetJavaJsEntity = new WidgetJavaJsEntity()
            .setJavaScriptParameters(params)
            .setJavaScriptParametersReadOnly(paramReadOnly)
            .setJavaScript(js);

        //TODO: fix:::  widgetJsEntity.setWidgetTabEntity(widgetTabEntity).setFieldFetchType(addon + ":" + template.getClass().getSimpleName());

        return null; // TODO: fix::::entityContext.save(widgetJsEntity);
    }

    @GetMapping("/tab")
    public List<OptionModel> getWidgetTabs() {
        return entityContext.findAll(WidgetTabEntity.class).stream().sorted()
                            .map(t -> OptionModel.of(t.getEntityID(), t.getName()))
                            .collect(Collectors.toList());
    }

    @SneakyThrows
    @PostMapping("/tab/{name}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public OptionModel createWidgetTab(@PathVariable("name") String name) {
        BaseEntity<?> widgetTab = entityContext.getEntity(WidgetTabEntity.PREFIX + name);
        if (widgetTab == null) {
            widgetTab = entityContext.save(new WidgetTabEntity().setEntityID(name));
            return OptionModel.of(widgetTab.getEntityID(), widgetTab.getName());
        }
        throw new ServerException("Widget tab with same name already exists");
    }

    @SneakyThrows
    @PutMapping("/tab/{tabId}/{name}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void renameWidgetTab(@PathVariable("tabId") String tabId, @PathVariable("name") String name) {
        WidgetTabEntity entity = getWidgetTabEntity(tabId);
        WidgetTabEntity newEntity = entityContext.getEntityByName(name, WidgetTabEntity.class);

        if (newEntity == null) {
            entityContext.save(entity.setName(name));
        }
    }

    @DeleteMapping("/tab/{tabId}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
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

    private <T extends WidgetSeriesEntity> T getSeriesEntity(SingleValueRequest<?> request) {
        WidgetBaseEntityAndSeries entity = entityContext.getEntity(request.entityID);
        T series = ((Set<T>) entity.getSeries()).stream().filter(s -> s.getEntityID().equals(request.seriesEntityID)).findAny().orElse(null);
        if (series == null) {
            throw new NotFoundException("Unable to find series: " + request.seriesEntityID + " for entity: " + entity.getTitle());
        }
        return series;
    }

    private <T> T getEntity(String entityID) {
        BaseEntity entity = entityContext.getEntity(entityID);
        if (entity == null) {
            throw new IllegalArgumentException("Unable to find widget with entityID: " + entityID);
        }
        return (T) entity;
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
}
