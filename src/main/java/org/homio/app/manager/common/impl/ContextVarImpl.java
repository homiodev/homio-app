package org.homio.app.manager.common.impl;

import com.fathzer.soft.javaluator.DoubleEvaluator;
import com.fathzer.soft.javaluator.Operator;
import com.fathzer.soft.javaluator.Parameters;
import com.pivovarit.function.ThrowingConsumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.ContextService.MQTTEntityService;
import org.homio.api.ContextVar;
import org.homio.api.entity.HasJsonData;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContract;
import org.homio.api.entity.widget.AggregationType;
import org.homio.api.entity.widget.PeriodRequest;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.exception.NotFoundException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.state.DecimalType;
import org.homio.api.state.State;
import org.homio.api.storage.DataStorageService;
import org.homio.api.storage.SourceHistory;
import org.homio.api.storage.SourceHistoryItem;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.DataSourceUtil;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextVarImpl.TransformVariableContext.ExtendedDoubleEvaluator;
import org.homio.app.manager.common.impl.javaluator.DynamicVariableSet;
import org.homio.app.manager.common.impl.javaluator.ObjectEvaluator;
import org.homio.app.model.var.VariableBackup;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.model.var.WorkspaceGroup.WorkspaceVariableEntity;
import org.homio.app.model.var.WorkspaceVariable;
import org.homio.app.model.var.WorkspaceVariable.VarType;
import org.homio.app.model.var.WorkspaceVariableMessage;
import org.homio.app.repository.VariableBackupRepository;
import org.homio.app.repository.WorkspaceVariableRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;
import static org.homio.api.util.CommonUtils.getErrorMessage;
import static org.homio.api.util.Constants.ADMIN_ROLE;
import static org.homio.app.manager.common.impl.ContextUserImpl.GENERATED_SUPER_ADMIN;

@Log4j2
@RequiredArgsConstructor
public class ContextVarImpl implements ContextVar {

    public static final Map<String, VariableContext> globalVarStorageMap = new ConcurrentHashMap<>();
    private final @Getter
    @Accessors(fluent = true) ContextImpl context;
    private final VariableBackupRepository variableBackupRepository;
    private final ReentrantLock createContextLock = new ReentrantLock();

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

    private static void executeAsSuperAdmin(Runnable runnable) {
        Authentication backupAuth = SecurityContextHolder.getContext().getAuthentication();
        User user = new User(GENERATED_SUPER_ADMIN, "", List.of(new SimpleGrantedAuthority(ADMIN_ROLE)));
        var admin = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            new Thread(() -> executeAsSuperAdmin(runnable, admin, backupAuth)).start();
        }
        executeAsSuperAdmin(runnable, admin, backupAuth);
    }

    private static void executeAsSuperAdmin(Runnable runnable, UsernamePasswordAuthenticationToken admin, Authentication backupAuth) {
        try {
            SecurityContextHolder.getContext().setAuthentication(admin);
            runnable.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(backupAuth);
        }
    }

    private static @Nullable String getCallService() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 2; i < Math.min(stackTrace.length, 7); i++) { // Start from 2 to skip getStackTrace()
            String className = stackTrace[i].getClassName();
            if (className.startsWith("org.homio.addon.")) {
                className = className.substring(className.lastIndexOf('.') + 1);
                return className.split("\\$")[0]; // Remove anonymous class suffix
            }
        }
        return null;
    }

    private static @NotNull String fixVariableEntityID(@NotNull String variableId) {
        if (!variableId.startsWith(WorkspaceVariable.PREFIX)) {
            variableId = WorkspaceVariable.PREFIX + variableId;
        }
        return variableId;
    }

    public void onContextCreated() {
        context.var().createGroup(GROUP_HARDWARE, "Hardware", group ->
                group.setIcon(new Icon("fas fa-server", "#2662a3")).setLocked(true));

        context.var().createGroup(GROUP_BROADCAST, "Broadcasts", group ->
                group.setIcon(new Icon("fas fa-tower-broadcast", "#A32677")).setLocked(true));

        context.var().createGroup(GROUP_MISC, "Misc", group ->
                group.setIcon(new Icon("fas fa-star-half-stroke", "#A32677")).setLocked(true));

        for (WorkspaceVariable workspaceVariable : context.db().findAll(WorkspaceVariable.class)) {
            getOrCreateContext(workspaceVariable.getEntityID());
        }

        context.event().addEntityCreateListener(WorkspaceVariable.class, "var-create", variable ->
                getOrCreateContext(variable.getEntityID()));
        context.event().addEntityUpdateListener(WorkspaceVariable.class, "var-update", variable -> {
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
        context.event().addEntityRemovedListener(WorkspaceVariable.class, "var-delete",
                this::onVariableRemoved);

        context.event().addEntityRemovedListener(WorkspaceGroup.class, "group-delete",
                workspaceGroup -> workspaceGroup.getWorkspaceVariables().forEach(this::onVariableRemoved));

        context.bgp().builder("var-backup").intervalWithDelay(Duration.ofSeconds(60))
                .cancelOnError(false).execute(this::backupVariablesEveryMinute);

        context.bgp().builder("clear-backup").intervalWithDelay(Duration.ofMinutes(60))
                .cancelOnError(false).execute(this::clearBackupsEveryHour);
    }

    @Override
    public String createSubGroup(
            @NotNull String parentGroupId,
            @NotNull String groupId,
            @NotNull String groupName,
            @NotNull Consumer<GroupMetaBuilder> groupBuilder) {
        WorkspaceGroup parentGroup = assertGroupExists(parentGroupId);
        return saveOrUpdateGroup(parentGroupId + "-" + groupId, groupName, groupBuilder, wg -> wg.setHidden(true).setParent(parentGroup));
    }

    public @NotNull ActionResponseModel createSubGroup(JSONObject params, String parentGroupID) {
        String name = params.getString("name");
        context.var().createSubGroup(parentGroupID, String.valueOf(name.hashCode()), name, builder ->
                builder.setIcon(new Icon(params.getString("icon"),
                                StringUtils.defaultIfEmpty(params.optString("color"), "#999999")))
                        .setDescription(params.optString("description")));
        return ActionResponseModel.success();
    }

    private void onVariableRemoved(WorkspaceVariable workspaceVariable) {
        VariableContext context = globalVarStorageMap.remove(workspaceVariable.getEntityID());
        context.storageService.deleteAll();
        /* Should be removed by hibernating
        if (context.variable.isBackup()) {
            variableBackupRepository.delete(context.variable);
        }*/
        Optional.ofNullable(context.transformVariableContext).ifPresent(TransformVariableContext::dispose);
    }

    public int backupCount(WorkspaceVariable variable) {
        return variableBackupRepository.count(variable);
    }

    @Override
    public void onVariableCreated(@NotNull String discriminator, @Nullable Pattern variableIdPattern, Consumer<Variable> variableListener) {
        context.event().addEntityCreateListener(WorkspaceVariable.class, "global-var", workspaceVariable -> {
            if (variableIdPattern == null || variableIdPattern.matcher(workspaceVariable.getEntityID()).matches()) {
                variableListener.accept(workspaceVariable);
            }
        });
    }

    @Override
    public void onVariableRemoved(@NotNull String discriminator, @Nullable Pattern variableIdPattern, Consumer<Variable> variable) {

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
    public Object getRawValue(@NotNull String variableId) {
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

    public Object set(@NotNull String variableId, Object value, boolean logIfNoLinked) {
        return set(getOrCreateContext(variableId), value, logIfNoLinked);
    }

    @Override
    public String createGroup(@NotNull String groupId, @NotNull String groupName, @NotNull Consumer<GroupMetaBuilder> groupBuilder) {
        return saveOrUpdateGroup(groupId, groupName, groupBuilder, wg -> {
        });
    }

    @Override
    public String getTitle(@NotNull String variableId, String defaultTitle) {
        WorkspaceVariable variable = context.db().get(WorkspaceVariable.class, variableId);
        return variable == null ? defaultTitle : variable.getTitle();
    }

    @Override
    public long count(@NotNull String variableId) {
        return getOrCreateContext(variableId).storageService.count();
    }

    @Override
    public Set<Variable> getVariables() {
        return context.db().findAll(WorkspaceVariable.class).stream().map(w -> (Variable) w).collect(Collectors.toSet());
    }

    @Override
    public Variable getVariable(@NotNull String variableId) {
        return getOrCreateContext(variableId).variable;
    }

    @Override
    public boolean exists(@NotNull String variableId) {
        return globalVarStorageMap.containsKey(fixVariableEntityID(variableId));
    }

    @Override
    public boolean existsGroup(@NotNull String groupId) {
        return context.db().get(WorkspaceGroup.class, groupId) != null;
    }

    @Override
    public boolean renameGroup(@NotNull String groupId, @NotNull String name, @Nullable String description) {
        WorkspaceGroup workspaceGroup = assertGroupExists(groupId);
        if (!Objects.equals(workspaceGroup.getName(), name)
            || !Objects.equals(workspaceGroup.getDescription(), description)) {
            workspaceGroup.setName(name);
            workspaceGroup.setDescription(description);
            executeAsSuperAdmin(() -> context.db().save(workspaceGroup));
            return true;
        }
        return false;
    }

    @Override
    public boolean renameVariable(@NotNull String variableId, @NotNull String name, @Nullable String description) {
        WorkspaceVariable workspaceVariable = context.db().get(WorkspaceVariable.class, variableId);
        if (workspaceVariable != null
            && (!Objects.equals(workspaceVariable.getName(), name)
                || !Objects.equals(workspaceVariable.getDescription(), description))) {
            workspaceVariable.setName(name);
            workspaceVariable.setDescription(description);
            executeAsSuperAdmin(() -> context.db().save(workspaceVariable));
            return true;
        }
        return false;
    }

    @Override
    public boolean updateVariableIcon(@NotNull String variableId, @Nullable Icon icon) {
        if (icon == null) {
            return false;
        }
        WorkspaceVariable workspaceVariable = context.db().get(WorkspaceVariable.class, variableId);
        if (workspaceVariable != null
            && (!Objects.equals(workspaceVariable.getIcon(), icon.getIcon())
                || !Objects.equals(workspaceVariable.getIconColor(), icon.getColor()))) {
            executeAsSuperAdmin(() -> context.db().save(workspaceVariable.setIcon(icon.getIcon()).setIconColor(icon.getColor())));
            return true;
        }
        return false;
    }

    @Override
    public boolean removeGroup(@NotNull String groupId) {
        return context.db().delete(groupId) != null;
    }

    @Override
    public <T extends BaseDeviceEndpoint<?>> void removeVariable(@NotNull String vid, @Nullable T owner) {
        String variableId = fixVariableEntityID(vid);
        if (!exists(variableId)) {
            return;
        }
        String callService = owner == null ? getCallService() : owner.getClass().getSimpleName();
        WorkspaceVariable variable = context.db().getRequire(variableId);
        if (isLinked(variableId)) {
            throw new IllegalStateException("Unable to remove linked variable: " + vid);
        }
        if (context.user().isAdminLoggedUser() || isServiceOwnerOfVariable(variable, callService)) {
            // execute remove from an external thread if removeVariable calls from within another transaction
            new Thread(() -> {
                if (variable.isBackup()) {
                    variableBackupRepository.delete(variable);
                }
                executeAsSuperAdmin(() -> context.db().delete(variableId));
            }).start();
        } else {
            log.error("Unable to remove variable: {}. User: {} is not owner of variable: {}", variableId, owner, variable);
        }
    }

    private boolean isServiceOwnerOfVariable(@NotNull HasJsonData variable, @Nullable String callService) {
        if (callService == null) {
            return false;
        }
        return callService.equals(variable.getJsonData("owner"));
    }

    @Override
    public @NotNull String buildDataSource(@NotNull String variableId) {
        WorkspaceVariable variable = context.db().getRequire(variableId);
        return buildDataSource(variable);
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
            return new ExtendedDoubleEvaluator(ContextVarImpl.this).evaluate(code, variables);
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

    public boolean isLinked(@NotNull String variableEntityID) {
        return getOrCreateContext(variableEntityID).linkListener != null;
    }

    public void deleteGroup(@Nullable String entityID) {
        if (entityID != null) {
            WorkspaceGroup group = context.db().get(WorkspaceGroup.class, entityID);
            if (group != null) {
                if (group.isDisableDelete()) {
                    group.setLocked(false);
                    group.setJsonData("dis_del", false);
                    executeAsSuperAdmin(() -> context.db().save(group));
                    Integer unlockedVariables = context.getBean(WorkspaceVariableRepository.class).unlockVariablesByGroup(group);
                    if (unlockedVariables > 0) {
                        log.info("Unlocked {} variables of group {} for deletion", unlockedVariables, entityID);
                    }
                }
                executeAsSuperAdmin(() -> context.db().delete(group));
            }
        }
    }

    private void clearBackupsEveryHour() {
        for (VariableContext context : globalVarStorageMap.values()) {
            if (context.variable.isBackup()) {
                variableBackupRepository.deleteButKeepDays(context.variable, context.variable.getBackupDays());
            }
        }
    }

    private void backupVariablesEveryMinute() {
        for (VariableContext context : globalVarStorageMap.values()) {
            if (context.variable.isBackup()) {
                long nextTime = System.currentTimeMillis();
                List<WorkspaceVariableMessage> values = context.storageService.findAllSince(context.lastBackupTimestamp);
                if (!values.isEmpty()) {
                    backupVariables(context, values);
                }
                context.lastBackupTimestamp = nextTime;
            }
        }
    }

    private void backupVariables(VariableContext context, List<WorkspaceVariableMessage> values) {
        if (context.variable.isBackupAggregateValues()) {
            WorkspaceVariableMessage variableMessage;
            if (context.variable.getValueType() == HasGetStatusValue.ValueType.Float) {
                variableMessage = WorkspaceVariableMessage.average(values);
            } else {
                variableMessage = values.getLast();
            }
            variableBackupRepository.save(context.variable, variableMessage);
        } else {
            variableBackupRepository.save(context.variable, values);
        }
    }

    private Object convertBigDecimal(BigDecimal value) {
        // using unary operation will lead that the return value would be always as float for some reason
        if (value.scale() == 0) {
            return value.longValueExact();
        } else {
            return value.floatValue();
        }
    }

    @Override
    public @NotNull Variable createVariable(
            @NotNull String groupId,
            @Nullable String variableId,
            @NotNull String variableName,
            @NotNull VariableType variableType,
            @Nullable Consumer<VariableMetaBuilder> builder) {
        final var entity = getOrCreateVariable(groupId, variableId);
        AtomicReference<WorkspaceVariable> ref = new AtomicReference<>(entity);
        if (entity.tryUpdateVariable(variableId, variableName, (variable) -> {
            if (builder != null) {
                builder.accept(new VariableMetaBuilderImpl(variable));
            }
        }, variableType)) {
            String callService = getCallService();
            executeAsSuperAdmin(() -> {
                VariableType oldRestriction = entity.getRestriction();
                entity.setJsonData("owner", callService);
                entity.getWorkspaceGroup().getWorkspaceVariables().add(entity);
                ref.set(context.db().save(entity));
                if (oldRestriction != variableType) {
                    globalVarStorageMap.remove(entity.getEntityID());
                    variableBackupRepository.delete(entity);
                }
            });
        }
        return ref.get();
    }

    @Override
    public @NotNull Variable createTransformVariable(
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
            entity = context.db().save(entity);
        }
        return entity;
    }

    private WorkspaceVariable getOrCreateVariable(@NotNull String groupId, @Nullable String variableId) {
        WorkspaceVariable entity = variableId == null ? null : context.db().get(WorkspaceVariable.class, variableId);

        if (entity == null) {
            entity = new WorkspaceVariable(assertGroupExists(groupId));
        }
        return entity;
    }

    private VariableContext getOrCreateContext(@NotNull String variableId) {
        if (variableId.contains("-->")) {
            variableId = variableId.substring(variableId.lastIndexOf("-->") + 3);
        }
        VariableContext variableContext = globalVarStorageMap.get(variableId);
        if (variableContext == null) {
            try {
                createContextLock.lock();
                variableContext = globalVarStorageMap.get(variableId);
                if (variableContext == null) {
                    WorkspaceVariable entity = context.db().get(WorkspaceVariable.class, variableId);
                    if (entity == null) {
                        throw new NotFoundException("Unable to find variable: " + variableId);
                    }
                    variableContext = createContext(entity);
                }
            } finally {
                createContextLock.unlock();
            }
        }
        return variableContext;
    }

    private Object set(VariableContext varContext, Object value, boolean logIfNoLinked) {
        value = varContext.valueConverter.apply(value);
        WorkspaceGroup workspaceGroup = varContext.variable.getTopGroup();
        if (validateValueBeforeSave(value, varContext)) {
            String error = format("Validation type restriction: Unable to set value: '%s' to variable: '%s/%s' of type: '%s'", value,
                    varContext.variable.getName(), workspaceGroup.getEntityID(), varContext.variable.getRestriction().name());
            throw new IllegalArgumentException(error);
        }
        varContext.storageService.save(new WorkspaceVariableMessage(value));
        // context.event().fireEvent(context.groupVariable.getVariableId(), value);
        context.event().fireEvent(varContext.variable.getFullEntityID(), State.of(value));

        // Fire update 'value' on UI
        WorkspaceVariableEntity updatedEntity = WorkspaceVariableEntity.updatableEntity(varContext.variable, context);
        context.ui().updateInnerSetItem(workspaceGroup, "workspaceVariableEntities",
                varContext.variable.getEntityID(), varContext.variable.getEntityID(), updatedEntity);

        if (varContext.linkListener != null) {
            try {
                varContext.linkListener.accept(value);
            } catch (Exception ex) {
                log.error("Unable to handle variable {} link handler. {}", varContext.variable.getTitle(), getErrorMessage(ex));
            }
        } else if (logIfNoLinked) {
            log.warn("Variable: '{}' has no linked handler", varContext.variable.getTitle());
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
        var service = context.db().getOrCreateInMemoryService(
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
                    .sorted(Comparator.comparingInt(VariableBackup::getId))
                    .map(bd -> WorkspaceVariableMessage.of(bd, context.valueConverter.apply(bd.getValue())))
                    .sorted().collect(Collectors.toList());
            if (!messages.isEmpty()) {
                service.save(messages);
            }
        }

        return context;
    }

    private String saveOrUpdateGroup(@NotNull String groupId, @NotNull String groupName,
                                     @NotNull Consumer<GroupMetaBuilder> groupBuilder,
                                     @NotNull Consumer<WorkspaceGroup> additionalHandler) {
        WorkspaceGroup entity = context.db().get(WorkspaceGroup.class, groupId);
        String callService = getCallService();
        if (entity == null) {
            entity = new WorkspaceGroup();
            entity.setEntityID(groupId);
            entity.setName(groupName);
            entity.setJsonData("owner", callService);
            configureGroup(groupBuilder, entity);
            additionalHandler.accept(entity);
            entity = context.db().save(entity);
        } else {
            long entityHashCode = entity.getEntityHashCode();
            configureGroup(groupBuilder, entity);
            additionalHandler.accept(entity);
            if (entityHashCode != entity.getEntityHashCode()) {
                WorkspaceGroup finalEntity = entity;
                if (isServiceOwnerOfVariable(entity, callService)) {
                    executeAsSuperAdmin(() -> context.db().save(finalEntity));
                } else {
                    context.db().save(finalEntity);
                }
            }
        }
        return entity.getEntityID();
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

    private WorkspaceGroup assertGroupExists(@NotNull String groupId) {
        WorkspaceGroup entity = context.db().get(WorkspaceGroup.class, groupId);
        if (entity == null) {
            entity = DataSourceUtil.getSelection(groupId).getValue(context);
            if (entity == null) {
                throw new IllegalArgumentException("Variable group with id: " + groupId + " not exists");
            }
        }
        return entity;
    }

    @RequiredArgsConstructor
    public static class VariableContext {

        private final DataStorageService<WorkspaceVariableMessage> storageService;
        private final Function<Object, Object> valueConverter;
        public long lastBackupTimestamp = System.currentTimeMillis();
        private WorkspaceVariable variable;
        // fire every link listener in a separate thread
        private ThrowingConsumer<Object, Exception> linkListener;
        private @Nullable
        @Setter TransformVariableContext transformVariableContext;
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
        public @NotNull VariableMetaBuilder setOwner(@NotNull DeviceBaseEntity owner) {
            String ieeeAddress = owner.getIeeeAddress();
            if (ieeeAddress != null) {
                entity.setJsonData("owner", ieeeAddress);
            }
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilder setOwner(@NotNull DeviceEndpointsBehaviourContract owner) {
            String identifier = owner.getIdentifier();
            if (identifier != null) {
                entity.setJsonData("owner", identifier);
            }
            return this;
        }

        @Override
        public @NotNull GeneralVariableMetaBuilder setPersistent(int days) {
            entity.setBackupDays(days);
            return this;
        }

        @Override
        public @NotNull GeneralVariableMetaBuilder setPersistentAggregateValues(boolean value) {
            entity.setBackupAggregateValues(value);
            return this;
        }

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

        @NotNull
        @Override
        public GeneralVariableMetaBuilder setDecimalScale(int scale) {
            entity.setScale(scale);
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilderImpl setAttributes(List<String> attributes) {
            entity.setAttributes(attributes);
            return this;
        }

        @NotNull
        @Override
        public GeneralVariableMetaBuilder set(@NotNull String key, @NotNull String value) {
            entity.setJsonData(key, value);
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilderImpl setValues(@NotNull Set<String> values) {
            entity.setJsonData("options", String.join(LIST_DELIMITER, values));
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilder setRange(float min, float max) {
            entity.setMin(min);
            entity.setMax(max);
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilder setStep(float step) {
            entity.setStep(step);
            return this;
        }
    }

    public static class TransformVariableSourceImpl {

        private final ContextVarImpl contextVar;
        private final @Getter String listenSource;
        private final @Getter TransformVariableValueHandler handler;

        public TransformVariableSourceImpl(@NotNull TransformVariableSource source, @NotNull ContextVarImpl contextVar) {
            this.listenSource = getListenerSource(source);
            this.handler = createHandler(source);
            this.contextVar = contextVar;
        }

        private String getListenerSource(@NotNull TransformVariableSource source) {
            String value = DataSourceUtil.getSelection(Objects.requireNonNull(source.getValue())).getEntityValue();
            return switch (source.getType()) {
                case "mqtt" -> MQTTEntityService.buildMqttListenEvent(Objects.requireNonNull(source.getMeta()), value);
                case "var" -> value;
                default ->
                        throw new IllegalArgumentException("Unable to find source listened for type: " + source.getType());
            };
        }

        private TransformVariableValueHandler createHandler(@NotNull TransformVariableSource source) {
            return switch (source.getType()) {
                case "mqtt" -> () -> {
                    State state = contextVar.context.event().getLastValues().get(listenSource);
                    return state == null ? null : state.floatValue();
                };
                case "var" -> () -> (Number) contextVar.getRawValue(listenSource);
                default -> throw new IllegalArgumentException("Unable to handle source with type: " + source.getType());
            };
        }

        public interface TransformVariableValueHandler {

            @Nullable
            Number getValue();
        }
    }

    public class TransformVariableContext {

        private final VariableContext varContext;
        boolean error = false;
        private DynamicVariableSet variables;

        public TransformVariableContext(VariableContext varContext) {
            this.varContext = varContext;
            registerSources();
        }

        public void dispose() {
            for (TransformVariableSourceImpl source : variables.getSources()) {
                context.event().removeEventListener(varContext.variable.getEntityID(), source.getListenSource());
            }
        }

        private void recalculate() {
            if (error) {
                return;
            }
            try {
                Double result = (Double) new ExtendedDoubleEvaluator(ContextVarImpl.this)
                        .evaluate(varContext.variable.getCode(), variables);
                set(varContext, result, false);
            } catch (Exception ex) {
                log.warn("Unable to evaluate variable expression: '{}'. Msg: {}", varContext.variable.getCode(), CommonUtils.getErrorMessage(ex));
            }
        }

        private void registerSources() {
            List<TransformVariableSourceImpl> sources = List.of();
            try {
                sources = varContext.variable.getSources().stream().map(
                                (TransformVariableSource source) ->
                                        new TransformVariableSourceImpl(source, ContextVarImpl.this))
                        .toList();
                for (TransformVariableSourceImpl source : sources) {
                    context.event().addEventBehaviourListener(source.getListenSource(), varContext.variable.getEntityID(), o ->
                            this.recalculate());
                }
            } catch (Exception ex) {
                this.error = true;
                log.warn("Unable to register variable sources: {}. Msg: {}",
                        varContext.variable.getTitle(), CommonUtils.getErrorMessage(ex));
            }
            this.variables = new DynamicVariableSet(sources);
        }

        public static class ExtendedDoubleEvaluator extends ObjectEvaluator {

            private static final Map<String, BiFunction<Iterator<Object>, ContextVarImpl, Double>> functions = new HashMap<>();
            private static final Parameters params = DoubleEvaluator.getDefaultParameters();

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

            private final ContextVarImpl var;

            public ExtendedDoubleEvaluator(ContextVarImpl var) {
                super(params);
                this.var = var;
            }

            private static Long getSince(Double seconds) {
                return System.currentTimeMillis() - seconds.intValue() * 1000L;
            }

            @Override
            protected Object evaluate(com.fathzer.soft.javaluator.Function function, Iterator<Object> arguments, Object evaluationContext) {
                BiFunction<Iterator<Object>, ContextVarImpl, Double> funcHandler = functions.get(function.getName());
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
}
