package org.homio.app.manager.common.impl;

import static java.lang.String.format;
import static org.homio.api.util.CommonUtils.getErrorMessage;

import com.pivovarit.function.ThrowingConsumer;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.codehaus.plexus.util.StringUtils;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextVar;
import org.homio.api.entity.widget.AggregationType;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.exception.NotFoundException;
import org.homio.api.model.Icon;
import org.homio.api.state.DecimalType;
import org.homio.api.state.State;
import org.homio.api.storage.DataStorageService;
import org.homio.api.storage.SourceHistory;
import org.homio.api.storage.SourceHistoryItem;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.var.VariableBackup;
import org.homio.app.model.var.WorkspaceGroup;
import org.homio.app.model.var.WorkspaceVariable;
import org.homio.app.model.var.WorkspaceVariableMessage;
import org.homio.app.repository.VariableDataRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@RequiredArgsConstructor
public class EntityContextVarImpl implements EntityContextVar {

    public static final Map<String, VariableContext> globalVarStorageMap = new ConcurrentHashMap<>();
    @Getter private final EntityContextImpl entityContext;
    private final VariableDataRepository variableDataRepository;
    private final ReentrantLock createContextLock = new ReentrantLock();

    public static void createBroadcastGroup(EntityContext entityContext) {
        entityContext.var().createGroup("broadcasts", "Broadcasts", true, new Icon("fas fa-tower-broadcast", "#A32677"));
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
                    variableDataRepository.delete(context.groupVariable.getVariableId());
                }
            });

        entityContext.bgp().builder("var-backup").delay(Duration.ofSeconds(60)).interval(Duration.ofSeconds(60))
                     .cancelOnError(false).execute(this::backupVariables);
    }

    public int backupCount(String variableID) {
        return variableDataRepository.count(getVariableId(variableID));
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
    public void set(@NotNull String variableId, @Nullable Object value, @NotNull Consumer<Object> convertedValue) throws IllegalArgumentException {
        set(variableId, value, convertedValue, false);
    }

    @SneakyThrows
    public void set(@NotNull String variableId, @Nullable Object value, Consumer<Object> convertedValue, boolean logIfNoLinked)
        throws IllegalArgumentException {
        if (value == null) {
            convertedValue.accept(null);
            return;
        }

        VariableContext context = getOrCreateContext(variableId);
        value = tryConvertValueToRestrictionTypeFormat(value, context);
        WorkspaceGroup workspaceGroup = context.groupVariable.getWorkspaceGroup();
        if (validateValueBeforeSave(value, context)) {
            String error = format("Validation type restriction: Unable to set value: '%s' to variable: '%s/%s' of type: '%s'", value,
                context.groupVariable.getName(), workspaceGroup.getGroupId(), context.groupVariable.getRestriction().name());
            throw new IllegalArgumentException(error);
        }
        convertedValue.accept(value);
        context.storageService.save(new WorkspaceVariableMessage(value));
        entityContext.event().fireEvent(context.groupVariable.getVariableId(), value);
        entityContext.event().fireEvent(context.groupVariable.getEntityID(), value);

        // Fire update 'value' on UI
        // Update only value. Skip updating usedQuota field because not so important
        if (workspaceGroup.getParent() == null) {
            entityContext.ui().updateInnerSetItem(workspaceGroup, "workspaceVariableEntities",
                context.groupVariable.getEntityID(), "value", WorkspaceGroup.generateValue(value, context.groupVariable));
        } else {
            entityContext.ui().updateInnerSetItem(workspaceGroup.getParent(), "workspaceVariableEntities",
                context.groupVariable.getEntityID(), "value", WorkspaceGroup.generateValue(value, context.groupVariable));
        }

        if (context.linkListener != null) {
            try {
                context.linkListener.accept(value);
            } catch (Exception ex) {
                log.error("Unable to handle variable {} link handler. {}", variableId, getErrorMessage(ex));
            }
        } else if (logIfNoLinked) {
            log.warn("Updated variable: {} has no linked handler", context.groupVariable.getTitle());
        }
    }

    @Override
    public String getTitle(@NotNull String variableId, String defaultTitle) {
        WorkspaceVariable variable = entityContext.getEntity(WorkspaceVariable.PREFIX + getVariableId(variableId));
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
        return entityContext.getEntity(WorkspaceGroup.PREFIX + groupId) != null;
    }

    @Override
    public boolean renameGroup(@NotNull String groupId, @NotNull String name, @Nullable String description) {
        WorkspaceGroup workspaceGroup = entityContext.getEntity(WorkspaceGroup.PREFIX + groupId);
        if (workspaceGroup != null
            && (!Objects.equals(workspaceGroup.getName(), name)
            || !Objects.equals(workspaceGroup.getDescription(), description))) {
            entityContext.save(workspaceGroup.setName(name).setDescription(description));
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
            entityContext.save(workspaceVariable.setName(name).setDescription(description));
            return true;
        }
        return false;
    }

    @Override
    public boolean setVariableIcon(@NotNull String variableId, @NotNull Icon icon) {
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
    public @NotNull String createVariable(@NotNull String groupId, @Nullable String variableId, @NotNull String variableName,
        @NotNull VariableType variableType, @Nullable Consumer<VariableMetaBuilder> builder) {
        return createVariableInternal(groupId, variableId, variableName, variableType, builder, wv -> {});
    }

    @Override
    public @NotNull String createEnumVariable(@NotNull String groupId, @Nullable String variableId, @NotNull String variableName, @NotNull List<String> values,
        @Nullable Consumer<VariableMetaBuilder> builder) {
        return createVariableInternal(groupId, variableId, variableName, VariableType.Enum, builder, wv ->
            wv.setJsonData("options", String.join("~~~", values)));
    }

    @Override
    public boolean createGroup(@NotNull String groupId, @NotNull String groupName, boolean locked, @NotNull Icon icon,
        String description) {
        return saveOrUpdateGroup(groupId, groupName, locked, icon, description, wg -> {});
    }

    @Override
    public boolean createGroup(
        @NotNull String parentGroupId,
        @NotNull String groupId,
        @NotNull String groupName,
        boolean locked,
        @NotNull Icon icon,
        @Nullable String description) {
        WorkspaceGroup parentGroup = entityContext.getEntity(WorkspaceGroup.PREFIX + parentGroupId);
        if (parentGroup == null) {
            throw new IllegalArgumentException("Parent group '" + parentGroupId + "' not exists");
        }
        return saveOrUpdateGroup(groupId, groupName, locked, icon, description, wg -> wg.setHidden(true).setParent(parentGroup));
    }

    @Override
    public boolean removeGroup(@NotNull String groupId) {
        return entityContext.delete(WorkspaceGroup.PREFIX + groupId) != null;
    }

    @Override
    public @NotNull String buildDataSource(@NotNull String variableId, boolean forSet) {
        // example: wg_z2m~~~wg_z2m_0x00124b001d04e04b~~~wgv_0x00124b001d04e04b_state~~~HasGetStatusValue~~~entityByClass
        WorkspaceVariable variable = entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);
        if (variable == null) {
            throw new IllegalArgumentException("Unable to find variable: " + variableId);
        }
        return buildDataSource(variable, forSet);
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
        items.add((forSet ? HasSetStatusValue.class : HasGetStatusValue.class).getSimpleName());
        items.add("entityByClass");

        return String.join("~~~", items);
    }

    public Object aggregate(String variableId, Long from, Long to, AggregationType aggregationType, boolean exactNumber) {
        return getOrCreateContext(variableId)
            .storageService.aggregate(from, to, null, null, aggregationType, exactNumber);
    }

    public List<Object[]> getTimeSeries(String variableId, Long from, Long to) {
        return getOrCreateContext(variableId).storageService.getTimeSeries(from, to, null, null, "value");
    }

    public boolean isLinked(String variableId) {
        return getOrCreateContext(getVariableId(getVariableId(variableId))).linkListener != null;
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
                    variableDataRepository.save(context.groupVariable.getVariableId(), values);
                }
                context.lastBackupTimestamp = nextTime;
            }
        }
    }

    @SneakyThrows
    private Object tryConvertValueToRestrictionTypeFormat(Object value, VariableContext context) {
        if (value instanceof State) {
            switch (context.groupVariable.getRestriction()) {
                case Bool -> value = ((State) value).boolValue();
                case Float -> {
                    if (value instanceof DecimalType) {
                        value = convertBigDecimal(((DecimalType) value).getValue());
                    } else {
                        value = ((State) value).floatValue();
                    }
                }
            }
        }

        if (!(value instanceof Boolean) && !(value instanceof Number)) {
            String strValue = value instanceof State ? ((State) value).stringValue() : value.toString();
            if (strValue.isEmpty()) {
                return strValue;
            }
            if (StringUtils.isNumeric(strValue)) {
                value = convertBigDecimal(new BigDecimal(strValue));
            } else if (strValue.equalsIgnoreCase("true") || strValue.equalsIgnoreCase("false")) {
                value = strValue.equalsIgnoreCase("true");
            } else {
                value = strValue;
            }
        }
        return value;
    }

    private Object convertBigDecimal(BigDecimal value) {
        // using unary operation will lead that return value would be always as float for some reason
        if (value.scale() == 0) {
            return value.longValueExact();
        } else {
            return value.floatValue();
        }
    }

    private String createVariableInternal(
        @NotNull String groupId,
        @Nullable String variableId,
        @NotNull String variableName,
        @NotNull VariableType variableType,
        @Nullable Consumer<VariableMetaBuilder> builder,
        @NotNull Consumer<WorkspaceVariable> beforeSaveHandler) {
        WorkspaceVariable entity = variableId == null ? null : entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);

        VariableMetaBuilderImpl metaBuilder = new VariableMetaBuilderImpl();
        if (builder != null) {
            builder.accept(metaBuilder);
        }

        if (entity == null) {
            WorkspaceGroup groupEntity = entityContext.getEntity(WorkspaceGroup.PREFIX + groupId);
            if (groupEntity == null) {
                throw new IllegalArgumentException("Variable group with id: " + groupId + " not exists");
            }
            String varId = Objects.toString(variableId, String.valueOf(System.currentTimeMillis()));
            entity = new WorkspaceVariable(varId, variableName, groupEntity, variableType, metaBuilder.description, metaBuilder.color,
                metaBuilder.readOnly, metaBuilder.unit);
            entity.setAttributes(metaBuilder.attributes);
            beforeSaveHandler.accept(entity);
            entity = entityContext.save(entity);
            beforeSaveHandler.accept(entity);
        } else if (!Objects.equals(entity.getName(), variableName)
            || !Objects.equals(entity.getDescription(), metaBuilder.description)
            || !Objects.equals(entity.getColor(), metaBuilder.color)) {
            entity = entityContext.save(entity.setName(variableName)
                                              .setColor(metaBuilder.color)
                                              .setDescription(metaBuilder.description)
                                              .setAttributes(metaBuilder.getAttributes()));
        }
        return entity.getVariableId();
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

    private VariableContext createContext(WorkspaceVariable variable) {
        String variableId = variable.getVariableId();
        var service = entityContext.storage().getOrCreateInMemoryService(
            WorkspaceVariableMessage.class, variable.getEntityID(), (long) variable.getQuota());

        VariableContext context = new VariableContext(service);
        context.groupVariable = variable;
        globalVarStorageMap.put(variableId, context);

        if (context.groupVariable.isBackup()) {
            List<VariableBackup> backupData = variableDataRepository.findAll(
                context.groupVariable.getVariableId(),
                context.groupVariable.getQuota());
            var messages = backupData.stream()
                                     .map(bd -> WorkspaceVariableMessage.of(bd,
                                         tryConvertValueToRestrictionTypeFormat(bd.getValue(), context)))
                                     .sorted().collect(Collectors.toList());
            service.save(messages);
        }

        return context;
    }

    private String getVariableId(String variableId) {
        if (variableId.startsWith(WorkspaceVariable.PREFIX)) {
            return variableId.substring(WorkspaceVariable.PREFIX.length());
        }
        return variableId;
    }

    private boolean saveOrUpdateGroup(@NotNull String groupId, @NotNull String groupName, boolean locked, @NotNull Icon icon,
        String description,
        @NotNull Consumer<WorkspaceGroup> additionalHandler) {
        WorkspaceGroup entity = entityContext.getEntity(WorkspaceGroup.PREFIX + groupId);
        if (entity == null) {
            WorkspaceGroup workspaceGroup = new WorkspaceGroup().setGroupId(groupId).setLocked(locked).setName(groupName)
                                                                .setIcon(icon.getIcon()).setIconColor(icon.getColor()).setDescription(description);
            additionalHandler.accept(workspaceGroup);
            entityContext.save(workspaceGroup);
            return true;
        } else if (!Objects.equals(entity.getName(), groupName)
            || !Objects.equals(entity.getDescription(), description)
            || !Objects.equals(entity.getIconColor(), icon.getColor())
            || !Objects.equals(entity.getIcon(), icon.getIcon())
            || entity.isLocked() != locked) {
            entityContext.save(entity.setName(groupName).setDescription(description)
                                     .setIconColor(icon.getColor()).setIcon(icon.getIcon()).setLocked(locked));
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

    @RequiredArgsConstructor
    private static class VariableContext {

        private final DataStorageService<WorkspaceVariableMessage> storageService;
        public long lastBackupTimestamp = System.currentTimeMillis();
        private WorkspaceVariable groupVariable;
        // fire every link listener in separate thread
        private ThrowingConsumer<Object, Exception> linkListener;

        @Override
        public String toString() {
            return format("%s. RO:[%s]. BP:[%s]. LL: [%s]", groupVariable.getVariableId(), groupVariable.isReadOnly(),
                groupVariable.isBackup(), linkListener != null);
        }
    }

    @Getter
    private static class VariableMetaBuilderImpl implements VariableMetaBuilder {

        private boolean readOnly;
        private String description;
        private String color;
        private String unit;
        private List<String> attributes;

        @Override
        public @NotNull VariableMetaBuilder setReadOnly(boolean value) {
            this.readOnly = value;
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilder setColor(String value) {
            this.color = value;
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilder setDescription(String value) {
            this.description = value;
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilder setUnit(String value) {
            this.unit = value;
            return this;
        }

        @Override
        public @NotNull VariableMetaBuilder setAttributes(List<String> attributes) {
            this.attributes = attributes;
            return this;
        }
    }
}
