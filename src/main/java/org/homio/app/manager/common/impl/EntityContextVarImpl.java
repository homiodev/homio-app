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
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextVar;
import org.homio.api.entity.widget.AggregationType;
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
            getOrCreateContext(workspaceVariable.getVariableId());
        }

        entityContext.event().addEntityCreateListener(WorkspaceVariable.class, "var-create", variable ->
                getOrCreateContext(variable.getVariableId()));
        entityContext.event().addEntityUpdateListener(WorkspaceVariable.class, "var-update", variable -> {
            VariableContext context = getOrCreateContext(variable.getVariableId());
            context.groupVariable = variable;
            context.storageService.updateQuota((long) variable.getQuota());
        });
        entityContext.event().addEntityRemovedListener(WorkspaceVariable.class, "var-delete",
                workspaceVariable -> {
                    VariableContext context = globalVarStorageMap.remove(workspaceVariable.getVariableId());
                    context.storageService.deleteAll();
                    if (context.groupVariable.isBackup()) {
                        variableBackupRepository.delete(context.groupVariable.getVariableId());
                    }
                    Optional.ofNullable(context.transformVariableContext).ifPresent(TransformVariableContext::dispose);
                });

        entityContext.bgp().builder("var-backup").intervalWithDelay(Duration.ofSeconds(60))
                .cancelOnError(false).execute(this::backupVariables);
    }

    public int backupCount(WorkspaceVariable variable) {
        return variableBackupRepository.count(variable.getVariableId());
    }

    @Override
    public void setLinkListener(@NotNull String variableId, @NotNull ThrowingConsumer<Object, Exception> listener) {
        getOrCreateContext(getVariableId(variableId)).linkListener = listener;
    }

    public SourceHistory getSourceHistory(String variableId) {
        VariableContext context = getOrCreateContext(variableId);
        if (context.groupVariable.getRestriction() == VariableType.Float) {
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
        WorkspaceVariable variable = entityContext.getEntity(getVariableEntityId(variableId));
        return variable == null ? defaultTitle : variable.getTitle();
    }

    @Override
    public long count(@NotNull String variableId) {
        return getOrCreateContext(variableId).storageService.count();
    }

    @Override
    public boolean exists(@NotNull String variableId) {
        return globalVarStorageMap.containsKey(getVariableId(variableId));
    }

    @Override
    public boolean existsGroup(@NotNull String groupId) {
        return entityContext.getEntity(getGroupEntityId(groupId)) != null;
    }

    @Override
    public boolean renameGroup(@NotNull String groupId, @NotNull String name, @Nullable String description) {
        WorkspaceGroup workspaceGroup = entityContext.getEntity(getGroupEntityId(groupId));
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
        WorkspaceVariable workspaceVariable = entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);
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
        WorkspaceVariable workspaceVariable = entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);
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
        WorkspaceGroup parentGroup = entityContext.getEntity(getGroupEntityId(parentGroupId));
        if (parentGroup == null) {
            throw new IllegalArgumentException("Parent group '" + parentGroupId + "' not exists");
        }
        return saveOrUpdateGroup(groupId, groupName, groupBuilder, wg -> wg.setHidden(true).setParent(parentGroup));
    }

    @NotNull
    public String buildDataSource(WorkspaceVariable variable, boolean forSet) {
        List<String> items = new ArrayList<>();
        WorkspaceGroup group = variable.getWorkspaceGroup();
        if (group.getParent() != null) {
            items.add(group.getParent().getEntityID());
        }
        items.add(group.getEntityID());
        items.add(variable.getEntityID());

        return String.join("-->", items);
    }

    @Override
    public boolean removeGroup(@NotNull String groupId) {
        return entityContext.delete(getGroupEntityId(groupId)) != null;
    }

    @Override
    public @NotNull String buildDataSource(@NotNull String variableId, boolean forSet) {
        WorkspaceVariable variable = entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);
        if (variable == null) {
            throw new IllegalArgumentException("Unable to find variable: " + variableId);
        }
        return buildDataSource(variable, forSet);
    }

    public Object aggregate(@NotNull String variableId, @Nullable Long from, @Nullable Long to, @NotNull AggregationType aggregationType, boolean exactNumber) {
        return getOrCreateContext(variableId)
                .storageService.aggregate(from, to, null, null, aggregationType, exactNumber);
    }

    public Object evaluate(@Nullable String code, @Nullable List<String> sources) {
        List<String> sourceIds = sources == null ? List.of() :
            sources.stream()
                   .filter(StringUtils::isNotBlank)
                   .map(s -> DataSourceUtil.getSelection(s).getValue(entityContext).getEntityID())
                   .toList();
        if (StringUtils.isNotEmpty(code)) {
            DynamicVariableSet variables = new DynamicVariableSet(sourceIds, EntityContextVarImpl.this);
            return new ExtendedDoubleEvaluator(EntityContextVarImpl.this).evaluate(code, variables);
        }
        return "";
    }

    public List<Object[]> getTimeSeries(String variableId, Long from, Long to) {
        return getOrCreateContext(variableId).storageService.getTimeSeries(from, to, null, null, "value");
    }

    public boolean isLinked(String variableId) {
        return getOrCreateContext(getVariableId(variableId)).linkListener != null;
    }

    /**
     * Backup all variables in scheduler
     */
    private void backupVariables() {
        for (VariableContext context : globalVarStorageMap.values()) {
            if (context.groupVariable.isBackup()) {
                long nextTime = System.currentTimeMillis();
                List<WorkspaceVariableMessage> values = context.storageService.findAllSince(context.lastBackupTimestamp);
                if (!values.isEmpty()) {
                    variableBackupRepository.save(context.groupVariable.getVariableId(), values);
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
            entityContext.save(entity);
        }
        return entity.getVariableId();
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
            entityContext.save(entity);
        }
        return entity.getVariableId();
    }

    private WorkspaceVariable getOrCreateVariable(@NotNull String groupId, @Nullable String variableId) {
        WorkspaceVariable entity = variableId == null ? null : entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);

        if (entity == null) {
            WorkspaceGroup groupEntity = entityContext.getEntity(getGroupEntityId(groupId));
            if (groupEntity == null) {
                throw new IllegalArgumentException("Variable group with id: " + groupId + " not exists");
            }
            entity = new WorkspaceVariable(groupEntity);
        }
        return entity;
    }

    private VariableContext getOrCreateContext(String varId) {
        final String variableId = getVariableId(varId);
        VariableContext context = globalVarStorageMap.get(variableId);
        if (context == null) {
            try {
                createContextLock.lock();
                context = globalVarStorageMap.get(variableId);
                if (context == null) {
                    WorkspaceVariable entity = entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);
                    if (entity == null) {
                        throw new NotFoundException("Unable to find variable: " + varId);
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
        WorkspaceGroup workspaceGroup = context.groupVariable.getTopGroup();
        if (validateValueBeforeSave(value, context)) {
            String error = format("Validation type restriction: Unable to set value: '%s' to variable: '%s/%s' of type: '%s'", value,
                    context.groupVariable.getName(), workspaceGroup.getGroupId(), context.groupVariable.getRestriction().name());
            throw new IllegalArgumentException(error);
        }
        context.storageService.save(new WorkspaceVariableMessage(value));
        // entityContext.event().fireEvent(context.groupVariable.getVariableId(), value);
        entityContext.event().fireEvent(context.groupVariable.getEntityID(), value);

        // Fire update 'value' on UI
        WorkspaceVariableEntity updatedEntity = WorkspaceVariableEntity.updatableEntity(context.groupVariable, entityContext);
        entityContext.ui().updateInnerSetItem(workspaceGroup, "workspaceVariableEntities",
            context.groupVariable.getEntityID(), context.groupVariable.getEntityID(), updatedEntity);

        if (context.linkListener != null) {
            try {
                context.linkListener.accept(value);
            } catch (Exception ex) {
                log.error("Unable to handle variable {} link handler. {}", context.groupVariable.getVariableId(), getErrorMessage(ex));
            }
        } else if (logIfNoLinked) {
            log.warn("Updated variable: {} has no linked handler", context.groupVariable.getTitle());
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
        String variableId = variable.getVariableId();
        var service = entityContext.storage().getOrCreateInMemoryService(
                WorkspaceVariableMessage.class, variable.getEntityID(), (long) variable.getQuota());

        VariableContext context = new VariableContext(service, createValueConverter(variable.getRestriction()));
        context.groupVariable = variable;
        globalVarStorageMap.put(variableId, context);

        if (context.groupVariable.getVarType() == VarType.transform) {
            context.setTransformVariableContext(new TransformVariableContext(context));
        }

        if (context.groupVariable.isBackup()) {
            List<VariableBackup> backupData = variableBackupRepository.findAll(
                    context.groupVariable.getVariableId(),
                    context.groupVariable.getQuota());
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
        WorkspaceGroup entity = entityContext.getEntity(getGroupEntityId(groupId));
        if (entity == null) {
            entity = new WorkspaceGroup(groupId, groupName);
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
        if (context.groupVariable.getRestriction() == VariableType.Enum &&
                !context.groupVariable.getJsonDataList("values").contains(value.toString())) {
            return false;
        }
        return !context.groupVariable.getRestriction().getValidate().test(value);
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
        private WorkspaceVariable groupVariable;
        // fire every link listener in separate thread
        private ThrowingConsumer<Object, Exception> linkListener;
        private @Nullable @Setter TransformVariableContext transformVariableContext;

        @Override
        public String toString() {
            return format("%s. RO:[%s]. BP:[%s]. LL: [%s]", groupVariable.getVariableId(), groupVariable.isReadOnly(),
                    groupVariable.isBackup(), linkListener != null);
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
        public @NotNull VariableMetaBuilderImpl setSourceVariables(@NotNull List<String> sources) {
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
        private final DynamicVariableSet variables;
        boolean error = false;

        public TransformVariableContext(VariableContext context) {
            this.context = context;
            List<String> sources = List.of();
            try {
                sources = context.groupVariable
                    .getSources().stream()
                    .map(s -> DataSourceUtil.getSelection(s).getValue(entityContext).getEntityID())
                    .toList();
                for (String source : sources) {
                    entityContext.event().addEventListener(source, context.groupVariable.getVariableId(), o ->
                        this.recalculate());
                }
            } catch (Exception ex) {
                this.error = true;
                log.warn("Unable to register variable sources: {}. Msg: {}",
                    context.groupVariable.getTitle(), CommonUtils.getErrorMessage(ex));
            }
            this.variables = new DynamicVariableSet(sources, EntityContextVarImpl.this);
        }

        public void dispose() {
            for (String source : variables.getSources()) {
                entityContext.event().removeEventListener(context.groupVariable.getVariableId(), source);
            }
        }

        private void recalculate() {
            if (error) {return;}
            try {
                Double result = (Double) new ExtendedDoubleEvaluator(EntityContextVarImpl.this)
                    .evaluate(context.groupVariable.getCode(), variables);
                set(context, result, false);
            } catch (Exception ex) {
                log.warn("Unable to evaluate variable expression: '{}'. Msg: {}", context.groupVariable.getCode(), CommonUtils.getErrorMessage(ex));
            }
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

    private String getVariableId(String variableId) {
        if (variableId.startsWith(WorkspaceVariable.PREFIX)) {
            return variableId.substring(WorkspaceVariable.PREFIX.length());
        }
        return variableId;
    }

    private String getGroupEntityId(@NotNull String groupId) {
        return groupId.startsWith(WorkspaceGroup.PREFIX) ? groupId : WorkspaceGroup.PREFIX + groupId;
    }

    private String getVariableEntityId(@NotNull String groupId) {
        return groupId.startsWith(WorkspaceVariable.PREFIX) ? groupId : WorkspaceVariable.PREFIX + groupId;
    }
}
