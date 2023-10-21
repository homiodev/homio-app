package org.homio.app.rest;

import static org.homio.app.rest.widget.EvaluateDatesAndValues.convertValuesToFloat;

import com.fathzer.soft.javaluator.Constant;
import com.fathzer.soft.javaluator.DoubleEvaluator;
import com.fathzer.soft.javaluator.Operator;
import com.fathzer.soft.javaluator.Parameters;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.homio.api.ContextVar.TransformVariableSource;
import org.homio.api.ContextVar.VariableType;
import org.homio.api.entity.widget.AggregationType;
import org.homio.api.entity.widget.PeriodRequest;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasGetStatusValue.GetStatusValueRequest;
import org.homio.api.entity.widget.ability.HasTimeValueSeries;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.storage.SourceHistory;
import org.homio.api.storage.SourceHistoryItem;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.util.DataSourceUtil.SelectionSource;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.model.var.WorkspaceVariable;
import org.homio.app.rest.widget.ChartDataset;
import org.homio.app.rest.widget.EvaluateDatesAndValues;
import org.homio.app.rest.widget.WidgetChartsController;
import org.homio.app.rest.widget.WidgetChartsController.TimeSeriesChartData;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/rest/var")
@RequiredArgsConstructor
public class VariableController {

    private final ContextImpl context;

    @GetMapping("/translateFunctions")
    public TranslateLegend getTranslateFunctions() {
        List<OptionModel> functions = new ArrayList<>();
        Parameters defaultParameters = DoubleEvaluator.getDefaultParameters();
        for (com.fathzer.soft.javaluator.Function function : defaultParameters.getFunctions()) {
            if (function.getMinimumArgumentCount() != function.getMaximumArgumentCount()) {
                functions.add(OptionModel.of(function.getName(),
                    function.getName() + "(%s..%s)".formatted(function.getMinimumArgumentCount(), function.getMaximumArgumentCount())));
            } else {
                functions.add(OptionModel.of(function.getName(), function.getName() + "(%s)".formatted(function.getMaximumArgumentCount())));
            }
        }
        List<OptionModel> constants = new ArrayList<>();
        for (Constant constant : defaultParameters.getConstants()) {
            constants.add(OptionModel.of(constant.getName()));
        }
        List<OptionModel> operators = new ArrayList<>();
        for (Operator operator : defaultParameters.getOperators()) {
            operators.add(OptionModel.of(operator.getSymbol()));
        }
        List<OptionModel> aggregations = new ArrayList<>();
        for (AggregationType aggregationType : AggregationType.values()) {
            if (aggregationType != AggregationType.None) {
                aggregations.add(OptionModel.of(aggregationType.name() + "('VAR0', PT24H)"));
            }
        }
        TranslateLegend legend = new TranslateLegend();
        legend.getItems().put("AGGREGATIONS", aggregations);
        legend.getItems().put("FUNCTIONS", functions);
        legend.getItems().put("CONSTANTS", constants);
        legend.getItems().put("OPERATORS", operators);
        return legend;
    }

    @PostMapping("/{group}/transform")
    public void createTransformVariable(@PathVariable("group") String group, @RequestBody TransformVarRequest request) {
        context.var().createTransformVariable(group, null, request.name, VariableType.Float,
            builder -> builder
                .setTransformCode(request.getCode())
                .setSourceVariables(request.getSources() == null ? List.of() : request.getSources())
                .setIcon(new Icon(request.getIcon(), request.getIconColor()))
                .setDescription(request.getDescription())
                .setPersistent(request.isBackup())
                .setQuota(request.getQuota())
        );
    }

    @PostMapping("/evaluate")
    public Object evaluate(@RequestBody EvaluateRequest request) {
        return context.var().evaluate(request.code, request.sources);
    }

    // show all read/write variables
    @GetMapping("/options")
    public List<OptionModel> getWorkspaceVariableValues() {
        return context.toOptionModels(getAllVariables());
    }

    @GetMapping("/{type}")
    public List<OptionModel> getWorkspaceVariables(@PathVariable("type") String type) {
        return OptionModel.entityList(context.db().findAllByPrefix(type));
    }

    @PostMapping("/source/history/info")
    public SourceHistory getSourceHistory(@RequestBody SourceHistoryRequest request) {
        SelectionSource selection = DataSourceUtil.getSelection(request.dataSource);
        HasGetStatusValue source = selection.getValue(context);
        val historyRequest = new GetStatusValueRequest(context, request.dynamicParameters);
        return source.getSourceHistory(historyRequest);
    }

    @PostMapping("/source/chart")
    public WidgetChartsController.TimeSeriesChartData<ChartDataset> getSourceChart(@RequestBody SourceHistoryChartRequest request) {
        SelectionSource selection = DataSourceUtil.getSelection(request.dataSource);
        HasTimeValueSeries source = selection.getValue(context);
        if(request.minutes == -1) {
            return getSourceChartSnapshot(source, request);
        }

        WidgetChartsController.TimeSeriesChartData<ChartDataset> chartData = new TimeSeriesChartData<>();
        Date from = null, to = null;
        boolean sortAsc = true;
        if (request.minutes > 0) {
            if (request.timestamp == 0) {
                request.timestamp = System.currentTimeMillis();
            }
            if (request.forward) {
                from = new Date(request.timestamp);
                to = new Date(request.timestamp + TimeUnit.MINUTES.toMillis(request.minutes));
            } else {
                to = new Date(request.timestamp);
                from = new Date(request.timestamp - TimeUnit.MINUTES.toMillis(request.minutes));
                sortAsc = false;
            }
        }
        PeriodRequest periodRequest = new PeriodRequest(context, from, to).setParameters(request.getDynamicParameters());
        periodRequest.setMinItemsCount(request.minItems);
        periodRequest.setForward(request.forward);
        periodRequest.setSortAsc(sortAsc);

            val timeSeries = source.getMultipleTimeValueSeries(periodRequest);
            List<Object[]> rawValues = timeSeries.values().iterator().next();

            if (!timeSeries.isEmpty()) {
                ChartDataset dataset = new ChartDataset(null, null);
                chartData.setTimestamp(rawValues.stream().map(objects -> (long) objects[0]).collect(Collectors.toList()));
                dataset.setData(rawValues.stream().map(objects -> (float) objects[1]).toList());
                chartData.getDatasets().add(dataset);
            }
        return chartData;
    }

    @PostMapping("/source/history/items")
    public List<SourceHistoryItem> getSourceHistoryItems(@RequestBody SourceHistoryRequest request) {
        SelectionSource selection = DataSourceUtil.getSelection(request.dataSource);
        HasGetStatusValue source = selection.getValue(context);

        val historyRequest = new GetStatusValueRequest(context, request.dynamicParameters);
        return source.getSourceHistoryItems(historyRequest, request.getFrom(), request.getCount());
    }

    private TimeSeriesChartData<ChartDataset> getSourceChartSnapshot(HasTimeValueSeries source, SourceHistoryChartRequest request) {
        WidgetChartsController.TimeSeriesChartData<ChartDataset> chartData = new TimeSeriesChartData<>();
        PeriodRequest periodRequest = new PeriodRequest(context, null, null).setParameters(request.getDynamicParameters());

        val timeSeries = source.getMultipleTimeValueSeries(periodRequest);
        List<Object[]> rawValues = timeSeries.values().iterator().next();

        if (!timeSeries.isEmpty()) {
            ChartDataset dataset = new ChartDataset(null, null);
            chartData.setTimestamp(rawValues.stream().map(objects -> (long) objects[0]).collect(Collectors.toList()));
            dataset.setData(rawValues.stream().map(objects -> (float) objects[1]).toList());

            Pair<Long, Long> minMax = this.findMinAndMax(rawValues);
            long min = minMax.getLeft(), max = minMax.getRight();
            long delta = (max - min) / request.splitCount;
            List<Date> dates = IntStream.range(0, request.splitCount)
                                        .mapToObj(value -> new Date(min + delta * value))
                                        .collect(Collectors.toList());
            List<List<Float>> values = convertValuesToFloat(dates, rawValues);
            chartData.setTimestamp(dates.stream().map(Date::getTime).collect(Collectors.toList()));
            dataset.setData(EvaluateDatesAndValues.aggregate(values, AggregationType.Average));
            chartData.getDatasets().add(dataset);
        }
        return chartData;
    }

    private List<WorkspaceVariable> getAllVariables() {
        return context.db().findAll(WorkspaceVariable.class)
                            .stream()
                            .filter(s -> !s.getWorkspaceGroup().getEntityID().equals(WorkspaceGroup.PREFIX + "broadcasts"))
                            .collect(Collectors.toList());
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
    public static class SourceHistoryRequest {

        private String dataSource;
        private JSONObject dynamicParameters;
        private int from;
        private int count;
    }

    @Getter
    @Setter
    public static class SourceHistoryChartRequest {

        private String dataSource;
        private JSONObject dynamicParameters;
        private int minutes; // show all data if -1
        private long timestamp; // may be 0 to search from last available date
        private boolean forward; // source from or to
        private int minItems = 100; // minimum items to load if too few items in from..to range
        private int splitCount = 100; // uses for full chart snapshot loading
    }

    @Getter
    @Setter
    public static class SourceHistorySnapshotRequest {

        private int splitCount;
        private String dataSource;
        private JSONObject dynamicParameters;
    }

    @Getter
    @Setter
    public static class EvaluateRequest {

        private String code;
        private List<TransformVariableSource> sources;
    }

    @Getter
    @Setter
    public static class TransformVarRequest extends EvaluateRequest {

        private String name;
        private String description;
        private int quota;
        private boolean backup;
        private String icon;
        private String iconColor;
    }

    @Getter
    public static class TranslateLegend {

        private final Map<String, List<OptionModel>> items = new HashMap<>();
    }
}
