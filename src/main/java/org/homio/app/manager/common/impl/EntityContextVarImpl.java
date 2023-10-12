package org.homio.app.manager.common.impl;

import static java.lang.String.format;
import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;
import static org.homio.api.util.CommonUtils.getErrorMessage;

import com.fathzer.soft.javaluator.DoubleEvaluator;
import com.fathzer.soft.javaluator.Operator;
import com.fathzer.soft.javaluator.Parameters;
import com.pivovarit.function.ThrowingConsumer;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextService.MQTTEntityService;
import org.homio.api.EntityContextVar;
import org.homio.api.entity.widget.AggregationType;
import org.homio.api.entity.widget.PeriodRequest;
import org.homio.api.exception.NotFoundException;
import org.homio.api.model.Icon;
import org.homio.api.state.DecimalType;
import org.homio.api.state.State;
import org.homio.api.storage.DataStorageService;
import org.homio.api.storage.SourceHistory;
import org.homio.api.storage.SourceHistoryItem;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.DataSourceUtil;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.manager.common.impl.EntityContextVarImpl.TransformVariableContext.ExtendedDoubleEvaluator;
import org.homio.app.manager.common.impl.javaluator.DynamicVariableSet;
import org.homio.app.manager.common.impl.javaluator.ObjectEvaluator;
import org.homio.app.model.var.VariableBackup;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.model.var.WorkspaceGroup.WorkspaceVariableEntity;
import org.homio.app.model.var.WorkspaceVariable;
import org.homio.app.model.var.WorkspaceVariable.VarType;
import org.homio.app.model.var.WorkspaceVariableMessage;
import org.homio.app.repository.VariableBackupRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@RequiredArgsConstructor
public class EntityContextVarImpl implements EntityContextVar {

    public static final Map<String, VariableContext> globalVarStorageMap = new ConcurrentHashMap<>();
    @Getter
    private final EntityContextImpl entityContext;
    private final VariableBackupRepository variableBackupRepository;
    private final ReentrantLock createContextLock = new ReentrantLock();

    public static void createBroadcastGroup(EntityContext entityContext) {
        entityContext.var().createGroup("broadcasts", "Broadcasts", group ->
            group.setIcon(new Icon("fas fa-tower-broadcast", "#A32677")).setLocked(true));
    }

    public void onContextCreated() {
        createBroadcastGroup(entityContext);

        for (WorkspaceVariable workspaceVariable : entityContext.findAll(WorkspaceVariable.class)) {
            getOrCreateContext(workspaceVariable.getEntityID());
        }

        entityContext.event().addEntityCreateListener(WorkspaceVariable.class, "var-create", variable ->
            getOrCreateContext(variable.getEntityID()));
        entityContext.event().addEntityUpdateListener(WorkspaceVariable.class, "var-update", variable -> {
            VariableContext context = getOrCreateContext(variable.getEntityID());
            if (variable.isBackup() != context.hasBackup) {
                context.hasBackup = variable.isBackup();
                variableBackupRepository.delete(context.variable);
            }
            context.variable = variable;
            context.storageService.updateQuota((long) variable.getQuota());
            if (context.transformVariableContext != null) {
                context.transformVariableContext.dispose();
                context.transformVariableContext.registerSources();
            }
        });
        entityContext.event().addEntityRemovedListener(WorkspaceVariable.class, "var-delete",
                workspaceVariable -> {
                    VariableContext context = globalVarStorageMap.remove(workspaceVariable.getEntityID());
                    context.storageService.deleteAll();
                    if (context.variable.isBackup()) {
                        variableBackupRepository.delete(context.variable);
                    }
                    Optional.ofNullable(context.transformVariableContext).ifPresent(TransformVariableContext::dispose);
                });

        entityContext.bgp().builder("var-backup").intervalWithDelay(Duration.ofSeconds(60))
                .cancelOnError(false).execute(this::backupVariables);
    }

    public int backupCount(WorkspaceVariable variable) {
        return variableBackupRepository.count(variable);
    }

    @Override
    public void setLinkListener(@NotNull String variableId, @NotNull ThrowingConsumer<Object, Exception> listener) {
        getOrCreateContext(variableId).linkListener = listener;
    }

    public SourceHistory getSourceHistory(String variableId) {
        VariableContext context = getOrCreateContext(variableId);
        if (context.variable.getRestriction() == VariableType.Float) {
            return context.storageService.getSourceHistory(null, null);
        } else {
            Number count = (Number) context.storageService.aggregate(null, null, null, null, AggregationType.Count, false);
            return new SourceHistory(count.intValue(), null, null, null);
        }
    }

    public List<SourceHistoryItem> getSourceHistoryItems(String variableId, int from, int count) {
        return getOrCreateContext(variableId).storageService.getSourceHistoryItems(null, null, from, count);
    }

    @Override
    public Object get(@NotNull String variableId) {
        VariableContext context = getOrCreateContext(variableId);
        WorkspaceVariableMessage latest = context.storageService.getLatest();
        if (latest == null) {
            return null;
        }
        Object value = latest.getValue();
        // force convert from double to float
        if (value instanceof Double) {
            return ((Double) value).floatValue();
        }
        return value;
    }

    @Override
    public Object set(@NotNull String variableId, @Nullable Object value) throws IllegalArgumentException {
        if (value == null) {
            return null;
        }
        return set(getOrCreateContext(variableId), value, false);
    }

    public Object set(String variableId, Object value, boolean logIfNoLinked) {
        return set(getOrCreateContext(variableId), value, logIfNoLinked);
    }

    @Override
    public boolean createGroup(@NotNull String groupId, @NotNull String groupName, @NotNull Consumer<GroupMetaBuilder> groupBuilder) {
        return saveOrUpdateGroup(groupId, groupName, groupBuilder, wg -> {
        });
    }

    @Override
    public String getTitle(@NotNull String variableId, String defaultTitle) {
        WorkspaceVariable variable = entityContext.getEntity(WorkspaceVariable.class, variableId);
        return variable == null ? defaultTitle : variable.getTitle();
    }

    @Override
    public long count(@NotNull String variableId) {
        return getOrCreateContext(variableId).storageService.count();
    }

    @Override
    public boolean exists(@NotNull String variableId) {
        return globalVarStorageMap.containsKey(variableId);
    }

    @Override
    public boolean existsGroup(@NotNull String groupId) {
        return entityContext.getEntity(WorkspaceGroup.class, groupId) != null;
    }

    @Override
    public boolean renameGroup(@NotNull String groupId, @NotNull String name, @Nullable String description) {
        WorkspaceGroup workspaceGroup = entityContext.getEntity(WorkspaceGroup.class, groupId);
        if (workspaceGroup != null
                && (!Objects.equals(workspaceGroup.getName(), name)
                || !Objects.equals(workspaceGroup.getDescription(), description))) {
            workspaceGroup.setName(name);
            workspaceGroup.setDescription(description);
            entityContext.save(workspaceGroup);
            return true;
        }
        return false;
    }

    @Override
    public boolean renameVariable(@NotNull String variableId, @NotNull String name, @Nullable String description) {
        WorkspaceVariable workspaceVariable = entityContext.getEntity(WorkspaceVariable.class, variableId);
        if (workspaceVariable != null
                && (!Objects.equals(workspaceVariable.getName(), name)
                || !Objects.equals(workspaceVariable.getDescription(), description))) {
            workspaceVariable.setName(name);
            workspaceVariable.setDescription(description);
            entityContext.save(workspaceVariable);
            return true;
        }
        return false;
    }

    @Override
    public boolean updateVariableIcon(@NotNull String variableId, @Nullable Icon icon) {
        if (icon == null) {
            return false;
        }
        WorkspaceVariable workspaceVariable = entityContext.getEntity(WorkspaceVariable.class, variableId);
        if (workspaceVariable != null
                && (!Objects.equals(workspaceVariable.getIcon(), icon.getIcon())
                || !Objects.equals(workspaceVariable.getIconColor(), icon.getColor()))) {
            entityContext.save(workspaceVariable.setIcon(icon.getIcon()).setIconColor(icon.getColor()));
            return true;
        }
        return false;
    }

    @Override
    public boolean createSubGroup(
            @NotNull String parentGroupId,
            @NotNull String groupId,
            @NotNull String groupName,
        @NotNull Consumer<GroupMetaBuilder> groupBuilder) {
        WorkspaceGroup parentGroup = entityContext.getEntity(WorkspaceGroup.class, parentGroupId);
        if (parentGroup == null) {
            throw new IllegalArgumentException("Parent group '" + parentGroupId + "' not exists");
        }
        return saveOrUpdateGroup(groupId, groupName, groupBuilder, wg -> wg.setHidden(true).setParent(parentGroup));
    }

    @Override
    public boolean removeGroup(@NotNull String groupId) {
        return entityContext.delete(groupId) != null;
    }

    @Override
    public @NotNull String buildDataSource(@NotNull String variableId) {
        WorkspaceVariable variable = entityContext.getEntityRequire(variableId);
        return buildDataSource(variable);
    }

    public static @NotNull String buildDataSource(WorkspaceVariable variable) {
        List<String> items = new ArrayList<>();
        WorkspaceGroup group = variable.getWorkspaceGroup();
        if (group.getParent() != null) {
            items.add(group.getParent().getEntityID());
        }
        items.add(group.getEntityID());
        items.add(variable.getEntityID());

        return String.join("-->", items);
    }

    public Object aggregate(@NotNull String variableId, @Nullable Long from, @Nullable Long to, @NotNull AggregationType aggregationType, boolean exactNumber) {
        return getOrCreateContext(variableId)
                .storageService.aggregate(from, to, null, null, aggregationType, exactNumber);
    }

    public Object evaluate(@Nullable String code, @Nullable List<TransformVariableSource> sources) {
        List<TransformVariableSourceImpl> sourceIds = sources == null ? List.of() :
            sources.stream()
                   .filter(s -> StringUtils.isNotBlank(s.getType()) && StringUtils.isNotBlank(s.getValue()))
                   .map((TransformVariableSource source) -> new TransformVariableSourceImpl(source, this))
                   .toList();
        if (StringUtils.isNotEmpty(code)) {
            DynamicVariableSet variables = new DynamicVariableSet(sourceIds);
            return new ExtendedDoubleEvaluator(EntityContextVarImpl.this).evaluate(code, variables);
        }
        return "";
    }

    public List<Object[]> getTimeSeries(String variableId, PeriodRequest request) {
        DataStorageService<WorkspaceVariableMessage> service = getOrCreateContext(variableId).storageService;
        List<Object[]> series = service.getTimeSeries(request.getFromTime(), request.getToTime(), null, null, "value", null, request.isSortAsc());
        if (request.getFrom() != null || request.getTo() != null) {
            if (request.getMinItemsCount() > series.size()) {
                if (request.isForward()) {
                    series = service.getTimeSeries(request.getFromTime(), null, null, null, "value", request.getMinItemsCount(), true);
                } else {
                    series = service.getTimeSeries(null, request.getToTime(), null, null, "value", request.getMinItemsCount(), false);
                    request.setSortAsc(false);
                }
            }
        }
        if (!request.isSortAsc()) {
            series.sort(Comparator.comparingLong(o -> (Long) o[0]));
        }
        return series;
    }

    public boolean isLinked(String variableEntityID) {
        return getOrCreateContext(variableEntityID).linkListener != null;
    }

    /**
     * Backup all variables in scheduler
     */
    private void backupVariables() {
        for (VariableContext context : globalVarStorageMap.values()) {
            if (context.variable.isBackup()) {
                long nextTime = System.currentTimeMillis();
                List<WorkspaceVariableMessage> values = context.storageService.findAllSince(context.lastBackupTimestamp);
                if (!values.isEmpty()) {
                    variableBackupRepository.save(context.variable, values);
                }
                context.lastBackupTimestamp = nextTime;
            }
        }
    }

    private Object convertBigDecimal(BigDecimal value) {
        // using unary operation will lead that return value would be always as float for some reason
        if (value.scale() == 0) {
            return value.longValueExact();
        } else {
            return value.floatValue();
        }
    }

    @Override
    public @NotNull String createVariable(
            @NotNull String groupId,
            @Nullable String variableId,
            @NotNull String variableName,
            @NotNull VariableType variableType,
            @Nullable Consumer<VariableMetaBuilder> builder) {
        WorkspaceVariable entity = getOrCreateVariable(groupId, variableId);
        if (entity.tryUpdateVariable(variableId, variableName, (variable) -> {
            if (builder != null) {
                builder.accept(new VariableMetaBuilderImpl(variable));
            }
        }, variableType)) {
            entity = entityContext.save(entity);
        }
        return entity.getEntityID();
    }

    @Override
    public @NotNull String createTransformVariable(
        @NotNull String groupId,
        @Nullable String variableId,
        @NotNull String variableName,
        @NotNull VariableType variableType,
        @Nullable Consumer<TransformVariableMetaBuilder> builder) {
        WorkspaceVariable entity = getOrCreateVariable(groupId, variableId);
        entity.setReadOnly(true);
        entity.setVarType(VarType.transform);
        if (entity.tryUpdateVariable(variableId, variableName, (variable) -> {
            if (builder != null) {
                builder.accept(new VariableMetaBuilderImpl(variable));
            }
        }, variableType)) {
            entity = entityContext.save(entity);
        }
        return entity.getEntityID();
    }

    private WorkspaceVariable getOrCreateVariable(@NotNull String groupId, @Nullable String variableId) {
        WorkspaceVariable entity = variableId == null ? null : entityContext.getEntity(WorkspaceVariable.class,variableId);

        if (entity == null) {
            WorkspaceGroup groupEntity = entityContext.getEntity(WorkspaceGroup.class, groupId);
            if (groupEntity == null) {
                throw new IllegalArgumentException("Variable group with id: " + groupId + " not exists");
            }
            entity = new WorkspaceVariable(groupEntity);
        }
        return entity;
    }

    private VariableContext getOrCreateContext(String variableId) {
        VariableContext context = globalVarStorageMap.get(variableId);
        if (context == null) {
            try {
                createContextLock.lock();
                context = globalVarStorageMap.get(variableId);
                if (context == null) {
                    WorkspaceVariable entity = entityContext.getEntity(WorkspaceVariable.class, variableId);
                    if (entity == null) {
                        throw new NotFoundException("Unable to find variable: " + variableId);
                    }
                    context = createContext(entity);
                }
            } finally {
                createContextLock.unlock();
            }
        }
        return context;
    }

    private Object set(VariableContext context, Object value, boolean logIfNoLinked) {
        value = context.valueConverter.apply(value);
        WorkspaceGroup workspaceGroup = context.variable.getTopGroup();
        if (validateValueBeforeSave(value, context)) {
            String error = format("Validation type restriction: Unable to set value: '%s' to variable: '%s/%s' of type: '%s'", value,
                context.variable.getName(), workspaceGroup.getEntityID(), context.variable.getRestriction().name());
            throw new IllegalArgumentException(error);
        }
        context.storageService.save(new WorkspaceVariableMessage(value));
        // entityContext.event().fireEvent(context.groupVariable.getVariableId(), value);
        entityContext.event().fireEvent(context.variable.getEntityID(), State.of(value));

        // Fire update 'value' on UI
        WorkspaceVariableEntity updatedEntity = WorkspaceVariableEntity.updatableEntity(context.variable, entityContext);
        entityContext.ui().updateInnerSetItem(workspaceGroup, "workspaceVariableEntities",
            context.variable.getEntityID(), context.variable.getEntityID(), updatedEntity);

        if (context.linkListener != null) {
            try {
                context.linkListener.accept(value);
            } catch (Exception ex) {
                log.error("Unable to handle variable {} link handler. {}", context.variable.getTitle(), getErrorMessage(ex));
            }
        } else if (logIfNoLinked) {
            log.warn("Updated variable: {} has no linked handler", context.variable.getTitle());
        }
        return value;
    }

    private Function<Object, Object> createValueConverter(VariableType restriction) {
        switch (restriction) {
            case Bool -> {
                return value -> {
                    if (value instanceof Boolean) {
                        return value;
                    }
                    if (value instanceof State stateValue) {
                        return stateValue.boolValue();
                    }
                    if (value instanceof Number valNumber) {
                        return valNumber.floatValue();
                    }
                    String strValue = value.toString();
                    if (strValue.equalsIgnoreCase("true")
                            || strValue.equalsIgnoreCase("1")
                            || strValue.equalsIgnoreCase("ON")) {
                        return true;
                    }
                    return false;
                };
            }
            case Float -> {
                return value -> {
                    if (value instanceof Number) {
                        return value;
                    }
                    if (value instanceof State) {
                        if (value instanceof DecimalType) {
                            return convertBigDecimal(((DecimalType) value).getValue());
                        } else {
                            return ((State) value).floatValue();
                        }
                    }
                    return convertBigDecimal(new BigDecimal(value.toString()));
                };
            }
            default -> {
                return Object::toString;
            }
        }
    }

    private VariableContext createContext(WorkspaceVariable variable) {
        String variableId = variable.getEntityID();
        var service = entityContext.storage().getOrCreateInMemoryService(
                WorkspaceVariableMessage.class, variable.getEntityID(), (long) variable.getQuota());

        VariableContext context = new VariableContext(service, createValueConverter(variable.getRestriction()));
        context.variable = variable;
        context.hasBackup = variable.isBackup();
        globalVarStorageMap.put(variableId, context);

        if (context.variable.getVarType() == VarType.transform) {
            context.setTransformVariableContext(new TransformVariableContext(context));
        }

        if (context.variable.isBackup()) {
            List<VariableBackup> backupData = variableBackupRepository.findAll(
                context.variable,
                context.variable.getQuota());
            var messages = backupData.stream()
                    .map(bd -> WorkspaceVariableMessage.of(bd, context.valueConverter.apply(bd.getValue())))
                    .sorted().collect(Collectors.toList());
            if (!messages.isEmpty()) {
                service.save(messages);
            }
        }

        return context;
    }

    private boolean saveOrUpdateGroup(@NotNull String groupId, @NotNull String groupName,
        @NotNull Consumer<GroupMetaBuilder> groupBuilder,
                                      @NotNull Consumer<WorkspaceGroup> additionalHandler) {
        WorkspaceGroup entity = entityContext.getEntity(WorkspaceGroup.class, groupId);
        if (entity == null) {
            entity = new WorkspaceGroup();
            entity.setEntityID(groupId);
            entity.setName(groupName);
            configureGroup(groupBuilder, entity);
            additionalHandler.accept(entity);
            entityContext.save(entity);
            return true;
        } else {
            long entityHashCode = entity.getEntityHashCode();
            configureGroup(groupBuilder, entity);
            additionalHandler.accept(entity);
            if (entityHashCode != entity.getEntityHashCode()) {
                entityContext.save(entity);
            }
        }
        return false;
    }

    private boolean validateValueBeforeSave(@NotNull Object value, VariableContext context) {
        if (context.variable.getRestriction() == VariableType.Enum &&
            !context.variable.getJsonDataList("values").contains(value.toString())) {
            return false;
        }
        return !context.variable.getRestriction().getValidate().test(value);
    }

    private void configureGroup(Consumer<GroupMetaBuilder> groupBuilder, WorkspaceGroup workspaceGroup) {
        groupBuilder.accept(new GroupMetaBuilder() {
            @Override
            public @NotNull GroupMetaBuilder setLocked(boolean locked) {
                workspaceGroup.setLocked(locked);
                return this;
            }

            @Override
            public @NotNull GroupMetaBuilder setDescription(@Nullable String value) {
                workspaceGroup.setDescription(value);
                return this;
            }

            @Override
            public @NotNull GroupMetaBuilder setIcon(@Nullable Icon icon) {
                workspaceGroup.setIcon(icon == null ? null : icon.getIcon());
                workspaceGroup.setIconColor(icon == null ? null : icon.getColor());
                return this;
            }
        });
    }

    @RequiredArgsConstructor
    public static class VariableContext {

        public long lastBackupTimestamp = System.currentTimeMillis();
        private final DataStorageService<WorkspaceVariableMessage> storageService;
        private final Function<Object, Object> valueConverter;
        private WorkspaceVariable variable;
        // fire every link listener in separate thread
        private ThrowingConsumer<Object, Exception> linkListener;
        private @Nullable @Setter TransformVariableContext transformVariableContext;
        private boolean hasBackup;

        @Override
        public String toString() {
            return "%s. RO:[%s]. BP:[%s]. LL: [%s]".formatted(variable.getEntityID(), variable.isReadOnly(), variable.isBackup(), linkListener != null);
        }
    }

    @RequiredArgsConstructor
    public static class VariableMetaBuilderImpl implements VariableMetaBuilder, TransformVariableMetaBuilder {

        private final WorkspaceVariable entity;

        @Override
        public @NotNull VariableMetaBuilderImpl setQuota(int value) {
            entity.setQuota(value);
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilder setWritable(boolean value) {
            entity.setReadOnly(!value);
            return this;
        }

        @Override
        @SneakyThrows
        public @NotNull VariableMetaBuilderImpl setSourceVariables(@NotNull List<TransformVariableSource> sources) {
            entity.setSources(sources);
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilderImpl setTransformCode(@NotNull String code) {
            entity.setCode(code);
            return this;
        }

        @Override
        public @NotNull GeneralVariableMetaBuilder setLocked(boolean locked) {
            entity.setLocked(true);
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilderImpl setColor(String value) {
            entity.setIconColor(value);
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilderImpl setPersistent(boolean value) {
            entity.setBackup(value);
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilderImpl setDescription(String value) {
            entity.setDescription(value);
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilderImpl setUnit(String value) {
            entity.setUnit(value);
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilderImpl setNumberRange(float min, float max) {
            entity.setMin(min);
            entity.setMax(max);
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilderImpl setIcon(@Nullable Icon value) {
            if (value != null) {
                entity.setIcon(value.getIcon());
                entity.setIconColor(value.getColor());
            }
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilderImpl setAttributes(List<String> attributes) {
            entity.setAttributes(attributes);
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilderImpl setValues(Set<String> values) {
            entity.setJsonData("options", String.join(LIST_DELIMITER, values));
            return this;
        }
    }

    public class TransformVariableContext {

        private final VariableContext context;
        private DynamicVariableSet variables;
        boolean error = false;

        public TransformVariableContext(VariableContext context) {
            this.context = context;
            registerSources();
        }

        public void dispose() {
            for (TransformVariableSourceImpl source : variables.getSources()) {
                entityContext.event().removeEventListener(context.variable.getEntityID(), source.getListenSource());
            }
        }

        private void recalculate() {
            if (error) {return;}
            try {
                Double result = (Double) new ExtendedDoubleEvaluator(EntityContextVarImpl.this)
                    .evaluate(context.variable.getCode(), variables);
                set(context, result, false);
            } catch (Exception ex) {
                log.warn("Unable to evaluate variable expression: '{}'. Msg: {}", context.variable.getCode(), CommonUtils.getErrorMessage(ex));
            }
        }

        private void registerSources() {
            List<TransformVariableSourceImpl> sources = List.of();
            try {
                sources = context.variable.getSources().stream().map(
                                     (TransformVariableSource source) ->
                                         new TransformVariableSourceImpl(source, EntityContextVarImpl.this))
                                          .toList();
                for (TransformVariableSourceImpl source : sources) {
                    entityContext.event().addEventBehaviourListener(source.getListenSource(), context.variable.getEntityID(), o ->
                        this.recalculate());
                }
            } catch (Exception ex) {
                this.error = true;
                log.warn("Unable to register variable sources: {}. Msg: {}",
                    context.variable.getTitle(), CommonUtils.getErrorMessage(ex));
            }
            this.variables = new DynamicVariableSet(sources);
        }

        public static class ExtendedDoubleEvaluator extends ObjectEvaluator {

            private static final Map<String, BiFunction<Iterator<Object>, EntityContextVarImpl, Double>> functions = new HashMap<>();
            private static final Parameters params = DoubleEvaluator.getDefaultParameters();
            private final EntityContextVarImpl var;

            static {
                for (AggregationType aggregationType : AggregationType.values()) {
                    if (aggregationType != AggregationType.None) {
                        var func = new com.fathzer.soft.javaluator.Function(aggregationType.name(), 1, 3);
                        functions.put(aggregationType.name(), (arguments, varContext) -> {
                            String varId = arguments.next().toString();
                            if (varId.contains(LIST_DELIMITER)) {
                                throw new IllegalArgumentException("Unable to aggregate value with no variable type");
                            }
                            Long from = arguments.hasNext() ? getSince((Double) arguments.next()) : null;
                            Long to = arguments.hasNext() ? getSince((Double) arguments.next()) : null;
                            Object value = varContext.aggregate(varId, from, to, aggregationType,
                                true);
                            return value == null ? 0 : ((Number) value).doubleValue();
                        });
                        params.add(func);
                    }
                }
            }

            private static Long getSince(Double seconds) {
                return System.currentTimeMillis() - seconds.intValue() * 1000L;
            }

            public ExtendedDoubleEvaluator(EntityContextVarImpl var) {
                super(params);
                this.var = var;
            }

            @Override
            protected Object evaluate(com.fathzer.soft.javaluator.Function function, Iterator<Object> arguments, Object evaluationContext) {
                BiFunction<Iterator<Object>, EntityContextVarImpl, Double> funcHandler = functions.get(function.getName());
                if (funcHandler != null) {
                    return funcHandler.apply(arguments, var);
                }
                return super.evaluate(function, arguments, evaluationContext);
            }

            @Override
            protected Object evaluate(Operator operator, Iterator<Object> operands, Object evaluationContext) {
                return super.evaluate(operator, operands, evaluationContext);
            }
        }
    }

    public static class TransformVariableSourceImpl {

        private final EntityContextVarImpl contextVar;
        private final @Getter String listenSource;
        private final @Getter TransformVariableValueHandler handler;

        public TransformVariableSourceImpl(TransformVariableSource source, EntityContextVarImpl contextVar) {
            this.listenSource = getListenerSource(source);
            this.handler = createHandler(source);
            this.contextVar = contextVar;
        }

        private String getListenerSource(TransformVariableSource source) {
            String value = DataSourceUtil.getSelection(Objects.requireNonNull(source.getValue())).getEntityValue();
            return switch (source.getType()) {
                case "mqtt" -> MQTTEntityService.buildMqttListenEvent(source.getMeta(), value);
                case "var" -> value;
                default -> throw new IllegalArgumentException("Unable to find source listened for type: " + source.getType());
            };
        }

        private TransformVariableValueHandler createHandler(TransformVariableSource source) {
            return switch (source.getType()) {
                case "mqtt" -> () -> {
                    State state = contextVar.entityContext.event().getLastValues().get(listenSource);
                    return state == null ? null : state.floatValue();
                };
                case "var" -> () -> (Number) contextVar.get(listenSource);
                default -> throw new IllegalArgumentException("Unable to handle source with type: " + source.getType());
            };
        }

        public interface TransformVariableValueHandler {

            @Nullable Number getValue();
        }
    }
}
