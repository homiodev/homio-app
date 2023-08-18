package org.homio.app.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static java.lang.String.format;
import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.app.rest.widget.EvaluateDatesAndValues.convertValuesToFloat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.validation.Valid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.homio.addon.z2m.model.Z2MLocalCoordinatorEntity;
import org.homio.addon.z2m.service.Z2MDeviceService;
import org.homio.api.EntityContextUI;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.widget.AggregationType;
import org.homio.api.entity.widget.PeriodRequest;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasGetStatusValue.GetStatusValueRequest;
import org.homio.api.entity.widget.ability.HasTimeValueSeries;
import org.homio.api.entity.zigbee.ZigBeeDeviceBaseEntity;
import org.homio.api.exception.NotFoundException;
import org.homio.api.exception.ServerException;
import org.homio.api.model.OptionModel;
import org.homio.api.state.State;
import org.homio.api.storage.SourceHistory;
import org.homio.api.storage.SourceHistoryItem;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.util.DataSourceUtil.DataSourceContext;
import org.homio.api.util.Lang;
import org.homio.app.config.cacheControl.CacheControl;
import org.homio.app.config.cacheControl.CachePolicy;
import org.homio.app.js.assistant.impl.CodeParser;
import org.homio.app.js.assistant.impl.ParserContext;
import org.homio.app.js.assistant.model.Completion;
import org.homio.app.js.assistant.model.CompletionRequest;
import org.homio.app.manager.ScriptService;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.ScriptEntity;
import org.homio.app.model.entity.widget.impl.js.WidgetFrameEntity;
import org.homio.app.model.rest.DynamicUpdateRequest;
import org.homio.app.rest.widget.ChartDataset;
import org.homio.app.rest.widget.EvaluateDatesAndValues;
import org.homio.app.rest.widget.WidgetChartsController;
import org.homio.app.rest.widget.WidgetChartsController.TimeSeriesChartData;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Log4j2
@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
@Validated
public class UtilsController {

    private final EntityContextImpl entityContext;
    private final ScriptService scriptService;
    private final CodeParser codeParser;

    private static final LoadingCache<String, GitHubReadme> readmeCache =
            CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build(new CacheLoader<>() {
                public @NotNull GitHubReadme load(@NotNull String url) {
                    return new GitHubReadme(url, Curl.get(url + "/raw/master/README.md", String.class));
                }
            });

    // get all device that able to get status
    @GetMapping("/deviceWithStatus")
    public List<OptionModel> getItemOptionsByType() {
        List<BaseEntity> entities = new ArrayList<>(entityContext.findAll(DeviceBaseEntity.class));
        for (Z2MLocalCoordinatorEntity coordinator : entityContext.findAll(Z2MLocalCoordinatorEntity.class)) {
            entities.addAll(coordinator.getService().getDeviceHandlers().values().stream()
                    .map(Z2MDeviceService::getDeviceEntity).toList());
        }
        entities.removeIf(e -> !(e instanceof HasStatusAndMsg) || ((HasStatusAndMsg) e).getStatus() == null);
        Map<String, List<BaseEntity>> groups =
                entities.stream().collect(Collectors.groupingBy(obj -> {

                    Class<?> superClass = (Class<?>) MergedAnnotations
                            .from(obj.getClass(), SearchStrategy.SUPERCLASS)
                            .get(UISidebarMenu.class, MergedAnnotation::isDirectlyPresent)
                            .getSource();
                    if (superClass != null && !DeviceBaseEntity.class.getSimpleName().equals(superClass.getSimpleName())) {
                        return superClass.getSimpleName();
                    }
                    return obj.getClass().getSimpleName();
                }));

        List<OptionModel> models = new ArrayList<>();
        for (Entry<String, List<BaseEntity>> entry : groups.entrySet()) {
            OptionModel parent = OptionModel.of(entry.getKey(), "DEVICE_TYPE." + entry.getKey());
            models.add(parent);
            BiConsumer<BaseEntity, OptionModel> configurator = null;
            if (!entry.getKey().equals(ZigBeeDeviceBaseEntity.class.getSimpleName())) {
                configurator = (entity, optionModel) -> optionModel
                        .setTitle(format("${SELECTION.%s}: %s", entity.getClass().getSimpleName(), entity.getTitle()));
            }
            parent.setChildren(OptionModel.entityList(entry.getValue(), configurator));
        }

        Collections.sort(models);
        return models;
    }

    @PutMapping("/multiDynamicUpdates")
    public void multiDynamicUpdates(@Valid @RequestBody List<DynamicRequestItem> request) {
        for (DynamicRequestItem requestItem : request) {
            entityContext.ui().registerForUpdates(new DynamicUpdateRequest(requestItem.did, requestItem.eid));
        }
    }

    @DeleteMapping("/dynamicUpdates")
    public void unregisterForUpdates(@Valid @RequestBody DynamicUpdateRequest request) {
        entityContext.ui().unRegisterForUpdates(request);
    }

    @PostMapping("/source/history/info")
    public SourceHistory getSourceHistory(@RequestBody SourceHistoryRequest request) {
        DataSourceContext context = DataSourceUtil.getSource(entityContext, request.dataSource);
        val historyRequest = new GetStatusValueRequest(entityContext, request.dynamicParameters);
        return ((HasGetStatusValue) context.getSource()).getSourceHistory(historyRequest);
    }

    @PostMapping("/source/chart")
    public WidgetChartsController.TimeSeriesChartData<ChartDataset> getSourceChart(@RequestBody SourceHistoryChartRequest request) {
        DataSourceContext context = DataSourceUtil.getSource(entityContext, request.dataSource);
        WidgetChartsController.TimeSeriesChartData<ChartDataset> chartData = new TimeSeriesChartData<>();
        if (context.getSource() instanceof HasTimeValueSeries) {
            PeriodRequest periodRequest = new PeriodRequest(entityContext, null, null).setParameters(request.getDynamicParameters());
            val timeSeries = ((HasTimeValueSeries) context.getSource()).getMultipleTimeValueSeries(periodRequest);
            List<Object[]> rawValues = timeSeries.values().iterator().next();

            if (!timeSeries.isEmpty()) {
                Pair<Long, Long> minMax = this.findMinAndMax(rawValues);
                long min = minMax.getLeft(), max = minMax.getRight();
                long delta = (max - min) / request.splitCount;
                List<Date> dates = IntStream.range(0, request.splitCount)
                        .mapToObj(value -> new Date(min + delta * value))
                        .collect(Collectors.toList());
                List<List<Float>> values = convertValuesToFloat(dates, rawValues);

                chartData.setTimestamp(dates.stream().map(Date::getTime).collect(Collectors.toList()));

                ChartDataset dataset = new ChartDataset(null, null);
                dataset.setData(EvaluateDatesAndValues.aggregate(values, AggregationType.AverageNoZero));
                chartData.getDatasets().add(dataset);
            }
        }
        return chartData;
    }

    @PostMapping("/source/history/items")
    public List<SourceHistoryItem> getSourceHistoryItems(@RequestBody SourceHistoryRequest request) {
        DataSourceContext context = DataSourceUtil.getSource(entityContext, request.dataSource);
        val historyRequest = new GetStatusValueRequest(entityContext, request.dynamicParameters);
        return ((HasGetStatusValue) context.getSource()).getSourceHistoryItems(historyRequest,
                request.getFrom(), request.getCount());
    }

    @GetMapping("/frame/{entityID}")
    public String getFrame(@PathVariable("entityID") String entityID) {
        WidgetFrameEntity widgetFrameEntity = entityContext.getEntityRequire(entityID);
        return widgetFrameEntity.getFrame();
    }

    @PostMapping("/github/readme")
    public GitHubReadme getUrlContent(@RequestBody String url) {
        try {
            return readmeCache.get(url.endsWith("/wiki") ? url.substring(0, url.length() - "/wiki".length()) : url);
        } catch (Exception ex) {
            throw new ServerException("No readme found");
        }
    }

    @PostMapping("/getCompletions")
    public Set<Completion> getCompletions(@RequestBody CompletionRequest completionRequest)
            throws NoSuchMethodException {
        ParserContext context = ParserContext.noneContext();
        return codeParser.addCompetitionFromManagerOrClass(
                CodeParser.removeAllComments(completionRequest.getLine()),
                new Stack<>(),
                context,
                completionRequest.getAllScript());
    }

    @GetMapping(value = "/download/tmp/{fileName:.+}", produces = APPLICATION_OCTET_STREAM)
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable("FILE_NAME") String fileName) {
        Path outputPath = CommonUtils.getTmpPath().resolve(fileName);
        if (!Files.exists(outputPath)) {
            throw new NotFoundException("Unable to find file: " + fileName);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add(
                HttpHeaders.CONTENT_DISPOSITION,
                format("attachment; filename=\"%s\"", outputPath.getFileName()));
        headers.add(HttpHeaders.CONTENT_TYPE, APPLICATION_OCTET_STREAM);

        return new ResponseEntity<>(
                outputStream -> {
                    try (FileChannel inChannel = FileChannel.open(outputPath, StandardOpenOption.READ)) {
                        long size = inChannel.size();
                        WritableByteChannel writableByteChannel = Channels.newChannel(outputStream);
                        inChannel.transferTo(0, size, writableByteChannel);
                    }
                },
                headers,
                HttpStatus.OK);
    }

    @PostMapping("/code/run")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public RunScriptResponse runScriptOnce(@RequestBody RunScriptRequest request)
            throws IOException {
        RunScriptResponse runScriptResponse = new RunScriptResponse();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream logOutputStream = new PrintStream(outputStream);
        ScriptEntity scriptEntity = new ScriptEntity();
        scriptEntity.setEntityID(request.entityID);
        scriptEntity.setJavaScript(request.javaScript);
        scriptEntity.setJavaScriptParameters(request.javaScriptParameters);

        try {
            runScriptResponse.result =
                    scriptService.executeJavaScriptOnce(
                            scriptEntity,
                            logOutputStream,
                            false,
                            State.of(request.contextParameters)).stringValue();
        } catch (Exception ex) {
            runScriptResponse.error = ExceptionUtils.getStackTrace(ex);
        }
        int size = outputStream.size();
        if (size > 50000) {
            String name = scriptEntity.getEntityID() + "_size_" + outputStream.size() + "___.log";
            Path tempFile = CommonUtils.getTmpPath().resolve(name);
            Files.copy(tempFile, outputStream);
            runScriptResponse.logUrl =
                    "rest/download/tmp/" + CommonUtils.getTmpPath().relativize(tempFile);
        } else {
            runScriptResponse.log = outputStream.toString(StandardCharsets.UTF_8);
        }

        return runScriptResponse;
    }

    @GetMapping("/i18n/{lang}.json")
    @CacheControl(maxAge = 3600, policy = CachePolicy.PUBLIC)
    public ObjectNode getI18NLangNodes(@PathVariable("lang") String lang) {
        return Lang.getLangJson(lang);
    }

    @SneakyThrows
    @PostMapping("/header/dialog/{entityID}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void acceptDialog(@PathVariable("entityID") String entityID, @RequestBody DialogRequest dialogRequest) {
        entityContext.ui().handleDialog(entityID, EntityContextUI.DialogResponseType.Accepted, dialogRequest.pressedButton,
                OBJECT_MAPPER.readValue(dialogRequest.params, ObjectNode.class));
    }

    @DeleteMapping("/header/dialog/{entityID}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void discardDialog(@PathVariable("entityID") String entityID) {
        entityContext.ui().handleDialog(entityID, EntityContextUI.DialogResponseType.Cancelled, null, null);
    }

    private Pair<Long, Long> findMinAndMax(List<Object[]> rawValues) {
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (Object[] chartItem : rawValues) {
            min = Math.min(min, (long) chartItem[0]);
            max = Math.max(max, (long) chartItem[0]);
        }
        return Pair.of(min, max);
    }

    @Getter
    @Setter
    private static class DynamicRequestItem {

        private String eid;
        private String did;
    }

    @Getter
    @Setter
    private static class DialogRequest {

        private String pressedButton;
        private String params;
    }

    @Getter
    @AllArgsConstructor
    private static class GitHubReadme {

        private String url;
        private String content;
    }

    @Setter
    private static class RunScriptRequest {

        private String javaScriptParameters;
        private String contextParameters;
        private String entityID;
        private String javaScript;
    }

    @Getter
    private static class RunScriptResponse {

        private Object result;
        private String log;
        private String error;
        private String logUrl;
    }

    @Getter
    @Setter
    private static class SourceHistoryRequest {

        private String dataSource;
        private JSONObject dynamicParameters;
        private int from;
        private int count;
    }

    @Getter
    @Setter
    private static class SourceHistoryChartRequest {

        private String dataSource;
        private JSONObject dynamicParameters;
        private int splitCount;
    }
}
