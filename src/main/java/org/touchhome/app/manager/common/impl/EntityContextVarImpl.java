package org.touchhome.app.manager.common.impl;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.var.WorkspaceGroup;
import org.touchhome.app.manager.var.WorkspaceVariable;
import org.touchhome.app.manager.var.WorkspaceVariableMessage;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextVar;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.inmemory.InMemoryDB;
import org.touchhome.bundle.api.inmemory.InMemoryDBService;
import org.touchhome.bundle.api.state.State;
import org.touchhome.common.exception.NotFoundException;

@Log4j2
@RequiredArgsConstructor
public class EntityContextVarImpl implements EntityContextVar {

  public static final Map<String, VariableContext> globalVarStorageMap = new HashMap<>();
  @Getter
  private final EntityContextImpl entityContext;

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
      context.service.updateQuota((long) workspaceVariable.quota);
    });
    entityContext.event().addEntityRemovedListener(WorkspaceVariable.class, "var-delete", workspaceVariable -> {
      globalVarStorageMap.remove(workspaceVariable.getVariableId()).service.deleteAll();
    });
  }

  @Override
  public Object get(@NotNull String variableId) {
    WorkspaceVariableMessage latest = getOrCreateContext(variableId).service.getLatest();
    return latest == null ? null : latest.getValue();
  }

  @SneakyThrows
  @Override
  public void set(@NotNull String variableId, Object value) {
    if (value == null) {
      return;
    }

    VariableContext context = getOrCreateContext(variableId);
    if (value instanceof State) {
      switch (context.groupVariable.getRestriction()) {
        case Boolean:
          value = ((State) value).boolValue();
          break;
        case Float:
          value = ((State) value).floatValue();
          break;
      }
    }

    if (!(value instanceof Boolean) && !(value instanceof Number)) {
      String strValue = value instanceof State ? ((State) value).stringValue() : value.toString();
      if (StringUtils.isNumeric(strValue)) {
        value = NumberFormat.getInstance().parse(strValue).floatValue();
      } else if (strValue.equalsIgnoreCase("true") || strValue.equalsIgnoreCase("false")) {
        value = NumberFormat.getInstance().parse(value.toString()).floatValue();
      }
    }
    if (!context.groupVariable.getRestriction().getValidate().test(value)) {
      throw new RuntimeException("Validation type restriction: Unable to set value: '" + value + "' to variable: '" +
          context.groupVariable.getName() + "/" + context.groupVariable.getWorkspaceGroup().getGroupId() +
          "' of type: '" + context.groupVariable.getRestriction().name() + "'");
    }
    context.service.save(new WorkspaceVariableMessage(value));
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
    if (workspaceGroup != null && (!Objects.equals(workspaceGroup.getName(), name) ||
        !Objects.equals(workspaceGroup.getDescription(), description))) {
      entityContext.save(workspaceGroup.setName(name).setDescription(description));
      return true;
    }
    return false;
  }

  @Override
  public boolean renameVariable(@NotNull String variableId, @NotNull String name, @Nullable String description) {
    WorkspaceVariable workspaceVariable = entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);
    if (workspaceVariable != null && (!Objects.equals(workspaceVariable.getName(), name) ||
        !Objects.equals(workspaceVariable.getDescription(), description))) {
      entityContext.save(workspaceVariable.setName(name).setDescription(description));
      return true;
    }
    return false;
  }

  @Override
  public String createVariable(@NotNull String groupId, String variableId, @NotNull String variableName,
      @NotNull VariableType variableType, String description, String color) {
    WorkspaceVariable entity = variableId == null ? null : entityContext.getEntity(WorkspaceVariable.PREFIX + variableId);

    if (entity == null) {
      WorkspaceGroup groupEntity = entityContext.getEntity(WorkspaceGroup.PREFIX + groupId);
      if (groupEntity == null) {
        throw new IllegalArgumentException("Variable group with id: " + groupId + " not exists");
      }
      String varId = StringUtils.defaultString(variableId, String.valueOf(System.currentTimeMillis()));
      entity = entityContext.save(new WorkspaceVariable(varId, variableName, groupEntity)
          .setDescription(description)
          .setRestriction(variableType)
          .setColor(color));
    } else if (!Objects.equals(entity.getName(), variableName) ||
        !Objects.equals(entity.getDescription(), description) ||
        !Objects.equals(entity.getColor(), color)) {
      entity = entityContext.save(entity.setName(variableName).setColor(color).setDescription(description));
    }
    return entity.getVariableId();
  }

  @Override
  public boolean createGroup(@NotNull String groupId, @NotNull String groupName, boolean locked, @NotNull String icon,
      @NotNull String iconColor, String description) {
    return saveOrUpdateGroup(groupId, groupName, locked, icon, iconColor, description, wg -> {});
  }

  @Override
  public boolean createGroup(@NotNull String parentGroupId, @NotNull String groupId, @NotNull String groupName, boolean locked,
      @NotNull String icon, @NotNull String iconColor,
      @Nullable String description) {
    WorkspaceGroup parentGroup = entityContext.getEntity(WorkspaceGroup.PREFIX + parentGroupId);
    if (parentGroup == null) {
      throw new IllegalArgumentException("Parent group '" + parentGroupId + "' not exists");
    }
    return saveOrUpdateGroup(groupId, groupName, locked, icon, iconColor, description,
        wg -> wg.setHidden(true).setParent(parentGroup));
  }

  @Override
  public boolean removeGroup(@NotNull String groupId) {
    return entityContext.delete(WorkspaceGroup.PREFIX + groupId) != null;
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
                              entityContext.event().fireEvent(variableId, broadcastMessage.getValue());
                            });
    VariableContext context = new VariableContext(service, variable);
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

  public Object aggregate(String variableId, Long from, Long to, AggregationType aggregationType, boolean exactNumber) {
    return getOrCreateContext(variableId).service.aggregate(from, to, null, null, aggregationType, exactNumber);
  }

  public List<Object[]> getTimeSeries(String variableId, Long from, Long to) {
    return getOrCreateContext(variableId).service.getTimeSeries(from, to, null, null, "value");
  }

  @AllArgsConstructor
  private static class VariableContext {

    private final InMemoryDBService<WorkspaceVariableMessage> service;
    private WorkspaceVariable groupVariable;
  }

  private boolean saveOrUpdateGroup(@NotNull String groupId, @NotNull String groupName, boolean locked,
      @NotNull String icon, @NotNull String iconColor, String description, @NotNull Consumer<WorkspaceGroup> additionalHandler) {
    WorkspaceGroup entity = entityContext.getEntity(WorkspaceGroup.PREFIX + groupId);
    if (entity == null) {
      WorkspaceGroup workspaceGroup = new WorkspaceGroup()
          .setGroupId(groupId)
          .setLocked(locked)
          .setName(groupName)
          .setIcon(icon)
          .setIconColor(iconColor)
          .setDescription(description);
      additionalHandler.accept(workspaceGroup);
      entityContext.save(workspaceGroup);
      return true;
    } else if (!Objects.equals(entity.getName(), groupName) ||
        !Objects.equals(entity.getDescription(), description) ||
        !Objects.equals(entity.getIconColor(), iconColor) ||
        !Objects.equals(entity.getIcon(), icon) ||
        entity.isLocked() != locked) {
      entityContext.save(entity.setName(groupName).setDescription(description).setIconColor(iconColor).setIcon(icon).setLocked(locked));
    }
    return false;
  }
}
