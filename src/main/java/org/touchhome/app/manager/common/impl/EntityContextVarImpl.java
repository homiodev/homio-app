package org.touchhome.app.manager.common.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.codehaus.plexus.util.StringUtils;
import org.touchhome.app.manager.var.WorkspaceGroup;
import org.touchhome.app.manager.var.WorkspaceVariable;
import org.touchhome.app.manager.var.WorkspaceVariableMessage;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextVar;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.inmemory.InMemoryDB;
import org.touchhome.bundle.api.inmemory.InMemoryDBService;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.util.CommonUtils;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
public class EntityContextVarImpl implements EntityContextVar {
    public static final Map<String, VariableContext> globalVarStorageMap = new HashMap<>();
    @Getter
    private final EntityContext entityContext;
    private final BroadcastLockManager broadcastLockManager;

    public EntityContextVarImpl(EntityContext entityContext, BroadcastLockManager broadcastLockManager) {
        this.entityContext = entityContext;
        this.broadcastLockManager = broadcastLockManager;
    }

    public void init() {
        List<WorkspaceVariable> storedVariables = entityContext.findAll(WorkspaceVariable.class);
        for (WorkspaceVariable storedVariable : storedVariables) {
            createContext(storedVariable);
        }
    }

    @Override
    public Object get(String variableId) {
        WorkspaceVariableMessage latest = getOrCreateContext(variableId).service.getLatest();
        return latest == null ? null : latest.getValue();
    }

    @SneakyThrows
    @Override
    public void set(String variableId, Object value) {
        if (value == null) return;

        VariableContext context = getOrCreateContext(variableId);
        if (!(value instanceof Boolean) && !(value instanceof Number)) {
            String strValue = value.toString();
            if (StringUtils.isNumeric(strValue)) {
                value = NumberFormat.getInstance().parse(strValue).floatValue();
            } else if (strValue.equalsIgnoreCase("true") || strValue.equalsIgnoreCase("false")) {
                value = NumberFormat.getInstance().parse(value.toString()).floatValue();
            }
        }
        if (!context.groupVariable.getRestriction().getValidate().test(value)) {
            throw new RuntimeException("Validation type restriction: Unable to set value: '" + value + "' to variable: '" +
                    context.groupVariable.getName() + "/" + context.groupVariable.getWorkspaceGroup().getGroupId() +
                    "' of type: '" +
                    context.groupVariable.getRestriction().name() + "'");
        }
        context.service.save(new WorkspaceVariableMessage(value));
    }

    @Override
    public String getTitle(String variableId, String defaultTitle) {
        WorkspaceVariable variable = entityContext.getEntity(WorkspaceVariable.PREFIX + getVariableId(variableId));
        return variable == null ? defaultTitle : variable.getTitle();
    }

    @Override
    public long count(String variableId) {
        return getOrCreateContext(variableId).service.count();
    }

    @Override
    public boolean exists(String variableId) {
        return globalVarStorageMap.containsKey(getVariableId(variableId));
    }

    @Override
    public String createVariable(String groupId, String name, VariableType type) {
        return entityContext.save(new WorkspaceVariable().setName(name).setRestriction(type)
                .setVariableId(CommonUtils.generateUUID())
                .setWorkspaceGroup(entityContext.getEntity(WorkspaceGroup.PREFIX + groupId))).getVariableId();
    }

    @Override
    public boolean createGroup(String groupId, String groupName) {
        if (entityContext.getEntity(WorkspaceGroup.PREFIX + groupId) == null) {
            entityContext.save(new WorkspaceGroup().setGroupId(groupId).setName(groupName));
            return true;
        }
        return false;
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
        var service = InMemoryDB.getOrCreateService(WorkspaceVariableMessage.class,
                        variable.getEntityID(), (long) variable.getQuota())
                .addSaveListener("", broadcastMessage -> {
                    broadcastLockManager.signalAll(variableId, broadcastMessage.getValue());
                    entityContext.event().fireEvent(variableId, broadcastMessage.getValue(), false);
                });
        VariableContext context = new VariableContext(variable, service);
        globalVarStorageMap.put(variableId, context);

        // initialise variable to put first value. require to getLatest(), ...
        if (service.count() == 0) {
            service.save(new WorkspaceVariableMessage(0));
        }

        return context;
    }

    private String getVariableId(String variableId) {
        if (variableId.startsWith(WorkspaceVariable.PREFIX)) {
            return variableId.substring(WorkspaceVariable.PREFIX.length());
        }
        return variableId;
    }

    public void afterVariableEntityDeleted(String variableId) {
        // all in-memory services will be removed in BaseEntity.preRemove()
        globalVarStorageMap.remove(variableId);
    }

    public void variableUpdated(WorkspaceVariable variable) {
        VariableContext context = getOrCreateContext(variable.getVariableId());
        context.groupVariable = variable;
        context.service.updateQuota((long) variable.quota);
    }

    public Object aggregate(String variableId, Long from, Long to, AggregationType aggregationType, boolean exactNumber) {
        return getOrCreateContext(variableId).service.aggregate(from, to, null, null, aggregationType, exactNumber);
    }

    @AllArgsConstructor
    private static class VariableContext {
        private WorkspaceVariable groupVariable;
        private final InMemoryDBService<WorkspaceVariableMessage> service;
    }
}
