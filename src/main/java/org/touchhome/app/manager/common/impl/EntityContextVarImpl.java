package org.touchhome.app.manager.common.impl;

import static java.lang.String.format;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.var.WorkspaceGroup;
import org.touchhome.app.model.var.WorkspaceVariable;
import org.touchhome.app.model.var.WorkspaceVariableMessage;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextVar;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.inmemory.InMemoryDB;
import org.touchhome.bundle.api.inmemory.InMemoryDBService;
import org.touchhome.bundle.api.state.DecimalType;
import org.touchhome.bundle.api.state.QuantityType;
import org.touchhome.bundle.api.state.State;
import org.touchhome.common.exception.NotFoundException;
import tech.units.indriya.internal.function.Calculator;

@Log4j2
@RequiredArgsConstructor
public class EntityContextVarImpl implements EntityContextVar {

    public static final Map<String, VariableContext> globalVarStorageMap = new HashMap<>();
    @Getter private final EntityContextImpl entityContext;

    public static void createBroadcastGroup(EntityContext entityContext) {
        entityContext.var().createGroup("broadcasts", "Broadcasts", false, "fas fa-tower-broadcast", "#A32677");
    }

    public void onContextCreated() {
        createBroadcastGroup(entityContext);

        for (WorkspaceVariable workspaceVariable : entityContext.findAll(WorkspaceVariable.class)) {
            createContext(workspaceVariable);
        }

        entityContext.event().addEntityCreateListener(WorkspaceVariable.class, "var-create", this::createContext);
        entityContext.event().addEntityUpdateListener(WorkspaceVariable.class, "var-update", workspaceVariable -> {
            VariableContext context = getOrCreateContext(workspaceVariable.getVariableId());
            context.groupVariable = workspaceVariable;
            context.service.updateQuota((long) workspaceVariable.getQuota());
        });
        entityContext.event().addEntityRemovedListener(WorkspaceVariable.class, "var-delete",
            workspaceVariable -> globalVarStorageMap.remove(workspaceVariable.getVariableId()).service.deleteAll());
    }

    @Override
    public void setLinkListener(@NotNull String variableId, @NotNull Consumer<Object> listener) {
        getOrCreateContext(getVariableId(variableId)).linkListener = listener;
    }

    @Override
    public Object get(@NotNull String variableId) {
        WorkspaceVariableMessage latest = getOrCreateContext(variableId).service.getLatest();
        return latest == null ? null : latest.getValue();
    }

    @Override
    public void set(@NotNull String variableId, @Nullable Object value, Consumer<Object> convertedValue) throws IllegalArgumentException {
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
        if (value instanceof State) {
            switch (context.groupVariable.getRestriction()) {
                case Bool:
                    value = ((State) value).boolValue();
                    break;
                case Float:
                    if (value instanceof DecimalType) {
                        value = convertBigDecimal(((DecimalType) value).getValue());
                    } else if (value instanceof QuantityType) {
                        value = convertBigDecimal(((QuantityType) value).getQuantity().getValue());
                    } else {
                        value = ((State) value).floatValue();
                    }
                    break;
            }
        }

        if (!(value instanceof Boolean) && !(value instanceof Number)) {
            String strValue = value instanceof State ? ((State) value).stringValue() : value.toString();
            if (StringUtils.isNumeric(strValue)) {
                value = convertBigDecimal(NumberFormat.getInstance().parse(strValue));
            } else if (strValue.equalsIgnoreCase("true") || strValue.equalsIgnoreCase("false")) {
                value = strValue.equalsIgnoreCase("true");
            } else {
                value = strValue;
            }
        }
        if (validateValueBeforeSave(value, context)) {
            String error = format("Validation type restriction: Unable to set value: '%s' to variable: '%s/%s' of type: '%s'", value,
                context.groupVariable.getName(), context.groupVariable.getWorkspaceGroup().getGroupId(), context.groupVariable.getRestriction().name());
            throw new IllegalArgumentException(error);
        }
        convertedValue.accept(value);
        context.service.save(new WorkspaceVariableMessage(value));
        entityContext.event().fireEvent(variableId, value);
        if (context.linkListener != null) {
            context.linkListener.accept(value);
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
        return getOrCreateContext(variableId).service.count();
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
    public boolean setVariableIcon(@NotNull String variableId, @NotNull String icon, @Nullable String iconColor) {
        WorkspaceVariable workspaceVariable = entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);
        if (workspaceVariable != null
            && (!Objects.equals(workspaceVariable.getIcon(), icon)
            || !Objects.equals(workspaceVariable.getIconColor(), iconColor))) {
            entityContext.save(workspaceVariable.setIcon(icon).setIconColor(iconColor));
            return true;
        }
        return false;
    }

    @Override
    public @NotNull String createVariable(@NotNull String groupId, @Nullable String variableId, @NotNull String variableName,
        @NotNull VariableType variableType, @Nullable String description, boolean readOnly, @Nullable String color, String unit) {
        return createVariableInternal(groupId, variableId, variableName, variableType, description, readOnly, color, unit, wv -> {});
    }

    @Override
    public @NotNull String createEnumVariable(@NotNull String groupId, @Nullable String variableId, @NotNull String variableName, @Nullable String description,
        boolean readOnly, @Nullable String color, @NotNull List<String> values) {
        return createVariableInternal(groupId, variableId, variableName, VariableType.Enum, description, readOnly, color, null, wv ->
            wv.setJsonData("options", String.join("~~~", values)));
    }

    @Override
    public boolean createGroup(@NotNull String groupId, @NotNull String groupName, boolean locked, @NotNull String icon, @NotNull String iconColor,
        String description) {
        return saveOrUpdateGroup(groupId, groupName, locked, icon, iconColor, description, wg -> {});
    }

    @Override
    public boolean createGroup(
        @NotNull String parentGroupId,
        @NotNull String groupId,
        @NotNull String groupName,
        boolean locked,
        @NotNull String icon,
        @NotNull String iconColor,
        @Nullable String description) {
        WorkspaceGroup parentGroup = entityContext.getEntity(WorkspaceGroup.PREFIX + parentGroupId);
        if (parentGroup == null) {
            throw new IllegalArgumentException("Parent group '" + parentGroupId + "' not exists");
        }
        return saveOrUpdateGroup(groupId, groupName, locked, icon, iconColor, description, wg -> wg.setHidden(true).setParent(parentGroup));
    }

    @Override
    public boolean removeGroup(@NotNull String groupId) {
        return entityContext.delete(WorkspaceGroup.PREFIX + groupId) != null;
    }

    @Override
    public String buildDataSource(@NotNull String variableId) {
        // example: wg_z2m~~~wg_z2m_0x00124b001d04e04b~~~wgv_0x00124b001d04e04b_state~~~HasGetStatusValue~~~entityByClass
        WorkspaceVariable variable = entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);
        List<String> items = new ArrayList<>();

        WorkspaceGroup group = variable.getWorkspaceGroup();
        if (group.getParent() != null) {
            items.add(group.getParent().getEntityID());
        }
        items.add(group.getEntityID());
        items.add(variable.getEntityID());
        items.add(HasGetStatusValue.class.getSimpleName());
        items.add("entityByClass");

        return String.join("~~~", items);
    }

    public Object aggregate(String variableId, Long from, Long to, AggregationType aggregationType, boolean exactNumber) {
        return getOrCreateContext(variableId)
            .service.aggregate(from, to, null, null, aggregationType, exactNumber);
    }

    public List<Object[]> getTimeSeries(String variableId, Long from, Long to) {
        return getOrCreateContext(variableId).service.getTimeSeries(from, to, null, null, "value");
    }

    public boolean isLinked(String variableId) {
        return getOrCreateContext(getVariableId(getVariableId(variableId))).linkListener != null;
    }

    private Object convertBigDecimal(Number value) {
        return Calculator.of(value).peek();
    }

    private String createVariableInternal(@NotNull String groupId, @Nullable String variableId, @NotNull String variableName,
        @NotNull VariableType variableType, @Nullable String description, boolean readOnly, @Nullable String color, @Nullable String unit,
        Consumer<WorkspaceVariable> beforeSaveHandler) {
        WorkspaceVariable entity = variableId == null ? null : entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);

        if (entity == null) {
            WorkspaceGroup groupEntity = entityContext.getEntity(WorkspaceGroup.PREFIX + groupId);
            if (groupEntity == null) {
                throw new IllegalArgumentException("Variable group with id: " + groupId + " not exists");
            }
            String varId = StringUtils.defaultString(variableId, String.valueOf(System.currentTimeMillis()));
            entity = new WorkspaceVariable(varId, variableName, groupEntity, variableType, description, color, readOnly, unit);
            beforeSaveHandler.accept(entity);
            entity = entityContext.save(entity);
            beforeSaveHandler.accept(entity);
        } else if (!Objects.equals(entity.getName(), variableName)
            || !Objects.equals(entity.getDescription(), description)
            || !Objects.equals(entity.getColor(), color)) {
            entity = entityContext.save(entity.setName(variableName).setColor(color).setDescription(description));
        }
        return entity.getVariableId();
    }

    private VariableContext getOrCreateContext(String varId) {
        final String variableId = getVariableId(varId);
        VariableContext context = globalVarStorageMap.get(variableId);
        if (context == null) {
            WorkspaceVariable entity = entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);
            if (entity == null) {
                throw new NotFoundException("Unable to find variable: " + varId);
            }
            context = createContext(entity);
        }
        return context;
    }

    private VariableContext createContext(WorkspaceVariable variable) {
        String variableId = variable.getVariableId();
        var service = InMemoryDB.getOrCreateService(WorkspaceVariableMessage.class, variable.getEntityID(), (long) variable.getQuota());

        VariableContext context = new VariableContext(service);
        context.groupVariable = variable;
        globalVarStorageMap.put(variableId, context);

        // initialise variable to put first value. require to getLatest(), ...
        if (service.count() == 0) {
            switch (variable.getRestriction()) {
                case Json:
                    service.save(new WorkspaceVariableMessage("{}"));
                    break;
                case Color:
                    service.save(new WorkspaceVariableMessage("#FFFFFF"));
                    break;
                case Bool:
                    service.save(new WorkspaceVariableMessage(false));
                    break;
                case Float:
                    service.save(new WorkspaceVariableMessage(0));
                    break;
                default:
                    service.save(new WorkspaceVariableMessage(""));
                    break;
            }
        }

        return context;
    }

    private String getVariableId(String variableId) {
        if (variableId.startsWith(WorkspaceVariable.PREFIX)) {
            return variableId.substring(WorkspaceVariable.PREFIX.length());
        }
        return variableId;
    }

    private boolean saveOrUpdateGroup(@NotNull String groupId, @NotNull String groupName, boolean locked, @NotNull String icon, @NotNull String iconColor,
        String description,
        @NotNull Consumer<WorkspaceGroup> additionalHandler) {
        WorkspaceGroup entity = entityContext.getEntity(WorkspaceGroup.PREFIX + groupId);
        if (entity == null) {
            WorkspaceGroup workspaceGroup = new WorkspaceGroup().setGroupId(groupId).setLocked(locked).setName(groupName)
                                                                .setIcon(icon).setIconColor(iconColor).setDescription(description);
            additionalHandler.accept(workspaceGroup);
            entityContext.save(workspaceGroup);
            return true;
        } else if (!Objects.equals(entity.getName(), groupName)
            || !Objects.equals(entity.getDescription(), description)
            || !Objects.equals(entity.getIconColor(), iconColor)
            || !Objects.equals(entity.getIcon(), icon)
            || entity.isLocked() != locked) {
            entityContext.save(entity.setName(groupName).setDescription(description).setIconColor(iconColor).setIcon(icon).setLocked(locked));
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

        private final InMemoryDBService<WorkspaceVariableMessage> service;
        private WorkspaceVariable groupVariable;
        private Consumer<Object> linkListener;
    }
}
