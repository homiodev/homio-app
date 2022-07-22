package org.touchhome.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import net.rossillo.spring.web.mvc.CacheControl;
import net.rossillo.spring.web.mvc.CachePolicy;
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
import org.touchhome.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.touchhome.app.model.entity.widget.impl.display.WidgetDisplaySeriesEntity;
import org.touchhome.app.model.entity.widget.impl.fm.WidgetFMEntity;
import org.touchhome.app.model.entity.widget.impl.fm.WidgetFMNodeValue;
import org.touchhome.app.model.entity.widget.impl.fm.WidgetFMSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.js.WidgetJsEntity;
import org.touchhome.app.model.entity.widget.impl.slider.WidgetSliderEntity;
import org.touchhome.app.model.entity.widget.impl.slider.WidgetSliderSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.toggle.WidgetToggleEntity;
import org.touchhome.app.model.entity.widget.impl.toggle.WidgetToggleSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.video.WidgetVideoEntity;
import org.touchhome.app.model.entity.widget.impl.video.WidgetVideoSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.video.sourceResolver.WidgetVideoSourceResolver;
import org.touchhome.app.utils.JavaScriptBuilderImpl;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.storage.BaseFileSystemEntity;
import org.touchhome.bundle.api.entity.widget.*;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.field.action.HasDynamicContextMenuActions;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.video.BaseFFMPEGVideoStreamEntity;
import org.touchhome.bundle.api.widget.WidgetBaseTemplate;
import org.touchhome.bundle.api.widget.WidgetJSBaseTemplate;
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.exception.ServerException;
import org.touchhome.common.fs.FileSystemProvider;
import org.touchhome.common.fs.TreeNode;
import org.touchhome.common.util.CommonUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.util.Constants.ADMIN_ROLE;
import static org.touchhome.bundle.api.util.Constants.PRIVILEGED_USER_ROLE;

@Log4j2
@RestController
@RequestMapping("/rest/widget")
@RequiredArgsConstructor
public class WidgetController {

    private final EntityContextImpl entityContext;
    private final ScriptService scriptService;
    private final ObjectMapper objectMapper;
    private final WidgetService widgetService;
    private final List<WidgetVideoSourceResolver> videoSourceResolvers;

    @SneakyThrows
    @GetMapping("/plugins")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public List<WidgetService.AvailableWidget> getAvailableWidgets() {
        return widgetService.getAvailableWidgets();
    }

    @RequestMapping(value = "/fm/{entityID}", method = RequestMethod.HEAD)
    public boolean requireUpdateFiles(@PathVariable("entityID") String entityID) {
        return false;
    }

    @PostMapping("/fm/{entityID}")
    public List<WidgetFMNodes> getWidgetFileManagerData(@PathVariable("entityID") String entityID, @RequestParam("w") int width,
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
            String[] path = seriesEntity.getDataSource().split("###");
            String parentId = path[0];
            String fs = path[1];
            BaseFileSystemEntity fileSystemEntity =
                    fileSystems.stream().filter(fileSystem -> fileSystem.getEntityID().equals(fs)).findAny().orElse(null);
            if (fileSystemEntity != null) {
                Long snapshot = seriesToSnapValue.get(seriesEntity.getEntityID());
                FileSystemProvider fileSystem = fileSystemEntity.getFileSystem(entityContext);
                Long lastUpdated = fileSystem.toTreeNode(parentId).getAttributes().getLastUpdated();
                Set<WidgetFMNodeValue> items = null;
                if (!Objects.equals(snapshot, lastUpdated)) {
                    Set<TreeNode> children = fileSystem.getChildren(parentId).stream().filter(n -> !n.getAttributes().isDir())
                            .collect(Collectors.toSet());
                    items = children.stream().map(treeNode -> new WidgetFMNodeValue(treeNode, width, height))
                            .collect(Collectors.toSet());

                }
                nodes.add(new WidgetFMNodes(fileSystemEntity.getTitle(), seriesEntity.getEntityID(), lastUpdated, items));
            } else {
                log.info("Unable to find fileSystem");
            }
        }
        return nodes;
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

    @GetMapping("/video/{entityID}")
    public List<WidgetVideoSourceResolver.VideoEntityResponse> getCameraData(@PathVariable("entityID") String entityID) {
        WidgetVideoEntity entity = entityContext.getEntity(entityID);
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

    @PostMapping("/video/{entityID}/series/{seriesEntityID}/action")
    public void fireVideoAction(@PathVariable("entityID") String entityID, @PathVariable("seriesEntityID") String seriesEntityID,
                                @RequestBody VideoActionRequest videoActionRequest) {
        WidgetVideoEntity entity = entityContext.getEntity(entityID);
        WidgetVideoSeriesEntity series =
                entity.getSeries().stream().filter(s -> s.getEntityID().equals(seriesEntityID)).findAny().orElse(null);
        if (series == null) {
            throw new NotFoundException("Unable to find series: " + seriesEntityID + " for entity: " + entity.getTitle());
        }
        BaseFFMPEGVideoStreamEntity streamEntity = entityContext.getEntity(series.getDataSource());
        if (streamEntity == null) {
            throw new NotFoundException("Unable to find base video for series: " + series.getTitle());
        }

        UIInputBuilder uiInputBuilder = streamEntity.getVideoHandler().assembleActions();
        UIActionHandler actionHandler = uiInputBuilder.findActionHandler(videoActionRequest.name);
        if (actionHandler == null) {
            throw new RuntimeException("No video action " + videoActionRequest.name + "found");
        }
        actionHandler.handleAction(entityContext, new JSONObject().put("value", videoActionRequest.value));

        /*TODO: Set<StatefulContextMenuAction> statefulContextMenuActions = streamEntity.getVideoHandler().getCameraActions
           (false);

        StatefulContextMenuAction statefulContextMenuAction = statefulContextMenuActions.stream()
                .filter(ca -> ca.getName().equals(cameraActionRequest.name)).findAny().orElseThrow(
                        () -> new RuntimeException("No camera action " + cameraActionRequest.name + "found"));
        statefulContextMenuAction.getAction().accept(new JSONObject().put("value", cameraActionRequest.value));*/
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
    public List<Pair<Object, Date>> getDisplayValues(@PathVariable("entityID") String entityID,
                                                     @RequestParam(value = "period", defaultValue = "All") String period) {
        WidgetDisplayEntity entity = entityContext.getEntity(entityID);
        List<Pair<Object, Date>> values = new ArrayList<>(entity.getSeries().size());
        ChartRequest chartRequest = buildChartRequest(period);
        for (WidgetDisplaySeriesEntity item : entity.getSeries()) {
            BaseEntity<?> source = entityContext.getEntity(item.getDataSource());
            if (source instanceof HasAggregateValueFromSeries) {
                chartRequest.setParameters(item.getDynamicParameterFieldsHolder());
                Float value = ((HasAggregateValueFromSeries) source).getAggregateValueFromSeries(chartRequest,
                        item.getAggregationType());
                if (value != null) {
                    values.add(Pair.of(value, source.getUpdateTime()));
                }
            }
        }
        return values;
    }

    @PostMapping("/slider/{entityID}/series/{seriesEntityID}")
    public void updateSliderValue(@PathVariable("entityID") String entityID,
                                  @PathVariable("seriesEntityID") String seriesEntityID, @RequestBody IntegerValue integerValue) {
        WidgetSliderEntity entity = entityContext.getEntity(entityID);
        WidgetSliderSeriesEntity series =
                entity.getSeries().stream().filter(s -> s.getEntityID().equals(seriesEntityID)).findAny().orElse(null);
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
    public void updateToggleValue(@PathVariable("entityID") String entityID,
                                  @PathVariable("seriesEntityID") String seriesEntityID, @RequestBody BooleanValue booleanValue) {
        WidgetToggleEntity entity = entityContext.getEntity(entityID);
        WidgetToggleSeriesEntity series =
                entity.getSeries().stream().filter(s -> s.getEntityID().equals(seriesEntityID)).findAny().orElse(null);
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

    @GetMapping("/tab/{tabId}")
    public List<WidgetEntity> getWidgetsInTab(@PathVariable("tabId") String tabId) {
        List<WidgetBaseEntity> widgets = entityContext.findAll(WidgetBaseEntity.class).stream()
                .filter(w -> w.getWidgetTabEntity().getEntityID().equals(tabId)).collect(Collectors.toList());

        boolean updated = false;
        for (WidgetBaseEntity<?> widget : widgets) {
            updated |= widget.updateRelations(entityContext);
        }
        if (updated) {
            widgets = entityContext.findAll(WidgetBaseEntity.class);
        }

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
        if (widget instanceof WidgetJsEntity) {
            WidgetJsEntity jsEntity = (WidgetJsEntity) widget;
            try {
                jsEntity.setJavaScriptErrorResponse(null);
                ScriptEntity scriptEntity = new ScriptEntity().setJavaScript(jsEntity.getJavaScript())
                        .setJavaScriptParameters(jsEntity.getJavaScriptParameters());

                CompileScriptContext compileScriptContext = scriptService.createCompiledScript(scriptEntity, null);
                jsEntity.setJavaScriptResponse(scriptService.runJavaScript(compileScriptContext));

            } catch (Exception ex) {
                jsEntity.setJavaScriptErrorResponse(CommonUtils.getErrorMessage(ex));
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
    public BaseEntity<?> createExtraWidget(@PathVariable("tabId") String tabId, @PathVariable("type") String type,
                                           @PathVariable("bundle") String bundle) {
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

        WidgetJsEntity widgetJsEntity =
                new WidgetJsEntity().setJavaScriptParameters(params).setJavaScriptParametersReadOnly(paramReadOnly)
                        .setJavaScript(js);

        widgetJsEntity.setWidgetTabEntity(widgetTabEntity).setFieldFetchType(bundle + ":" + template.getClass().getSimpleName());

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

    private ChartRequest buildChartRequest(String period) {
        TimePeriod timePeriod = TimePeriod.fromValue(period);
        Pair<Date, Date> range = timePeriod.getDateRange();
        return new ChartRequest(entityContext, range.getFirst(), range.getSecond(), timePeriod.getDateFromNow(),
                timePeriod != TimePeriod.All);
    }

    @Setter
    private static class IntegerValue {
        private Integer value;
    }

    @Setter
    private static class BooleanValue {
        private Boolean value;
    }

    @Setter
    private static class VideoActionRequest {
        private String name;
        private String value;
    }

    @Getter
    @AllArgsConstructor
    private class WidgetEntity {
        private WidgetBaseEntity widget;
        private Collection<UIInputEntity> actions;
    }
}
