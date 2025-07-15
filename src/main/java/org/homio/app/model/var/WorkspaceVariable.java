package org.homio.app.model.var;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.pivovarit.function.ThrowingConsumer;
import jakarta.persistence.*;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.ContextVar;
import org.homio.api.ContextVar.TransformVariableSource;
import org.homio.api.ContextVar.Variable;
import org.homio.api.ContextVar.VariableType;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.entity.widget.PeriodRequest;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.entity.widget.ability.HasTimeValueSeries;
import org.homio.api.model.Icon;
import org.homio.api.model.JSON;
import org.homio.api.model.OptionModel;
import org.homio.api.state.DecimalType;
import org.homio.api.state.State;
import org.homio.api.storage.SourceHistory;
import org.homio.api.storage.SourceHistoryItem;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.color.UIFieldColorRef;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.selection.SelectionConfiguration;
import org.homio.api.ui.field.selection.UIFieldSelectionParent;
import org.homio.api.ui.field.selection.UIFieldSelectionParent.SelectionParent;
import org.homio.app.manager.common.impl.ContextVarImpl;
import org.homio.app.repository.VariableBackupRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

@Entity
@Setter
@Getter
@Accessors(chain = true)
@UIFieldSelectionParent(
    value = "OVERRIDES_BY_INTERFACE",
    icon = "fas fa-layer-group",
    iconColor = "#28A60C",
    description = "Group variables")
@NoArgsConstructor
@Table(name = "workspace_variable")
public final class WorkspaceVariable extends BaseEntity
    implements HasJsonData,
        UIFieldSelectionParent.SelectionParent,
        HasTimeValueSeries,
        HasGetStatusValue,
        HasSetStatusValue,
        SelectionConfiguration,
        Variable {

  public static final String PREFIX = "var_";

  @UIField(order = 12, color = "#7A7A7A")
  // @UIFieldDisableEditOnCondition("return context.getParent('locked')")
  private String description;

  @UIField(order = 20, label = "format", disableEdit = true)
  @Enumerated(EnumType.STRING)
  @UIFieldShowOnCondition("return context.getParent('groupId') !== 'broadcasts'")
  private ContextVar.VariableType restriction = ContextVar.VariableType.Any;

  @UIField(order = 25)
  @UIFieldSlider(min = 500, max = 100000, step = 500)
  private int quota = 1000;

  @UIField(order = 30)
  @UIFieldIconPicker(allowSize = false, allowSpin = false)
  private String icon;

  @UIField(order = 35)
  @UIFieldColorPicker
  private String iconColor;

  @UIField(order = 40)
  private String unit;

  /** Is it possible to write to variable from UI? */
  @UIField(order = 25, hideInEdit = true)
  private boolean readOnly = true;

  @UIField(order = 1)
  @UIFieldGroup(value = "BACKUP", order = 10, borderColor = "#832DA6")
  @UIFieldSlider(min = 0, max = 365)
  private int backupDays = 31;

  // Backup runs once per minute. Does aggregate values into single value for backup. Highly
  // recommend true
  @UIField(order = 2)
  @UIFieldGroup("BACKUP")
  private boolean backupAggregateValues = true;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY, targetEntity = WorkspaceGroup.class)
  private WorkspaceGroup workspaceGroup;

  @Getter
  @Column(length = 1000)
  @Convert(converter = JSONConverter.class)
  private JSON jsonData = new JSON();

  @JsonIgnore
  @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "workspaceVariable")
  private Set<VariableBackup> backups;

  private boolean locked;

  public WorkspaceVariable(@NotNull WorkspaceGroup workspaceGroup) {
    this.workspaceGroup = workspaceGroup;
  }

  private static String formatVariableValue(Object value) {
    return value instanceof Double
        ? format("%.2f", (Double) value)
        : value instanceof Float ? format("%.2f", (Float) value) : value.toString();
  }

  // NEED FOR UI
  @SuppressWarnings("unused")
  public int getBackupStorageCount() {
    return backupDays > 0 ? context().getBean(VariableBackupRepository.class).count(this) : 0;
  }

  @Override
  @UIField(order = 10, required = true)
  @UIFieldColorRef("iconColor")
  public String getName() {
    return super.getName();
  }

  @UIField(order = 30, hideInEdit = true, disableEdit = true)
  @UIFieldProgress
  public UIFieldProgress.Progress getUsedQuota() {
    int count = 0;
    if (getEntityID() != null) {
      if (context().var().exists(getEntityID())) {
        count = (int) context().var().count(getEntityID());
      }
    }
    return UIFieldProgress.Progress.of(count, this.quota);
  }

  @Override
  public @NotNull String getEntityPrefix() {
    return PREFIX;
  }

  @Override
  public boolean isDisableEdit() {
    return locked || getJsonData("dis_edit", false);
  }

  @Override
  public boolean isDisableDelete() {
    return locked
        || getJsonData("dis_del", false)
        || context().event().getEventCount(getEntityID()) > 0;
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  @Override
  public void getAllRelatedEntities(Set<BaseEntity> set) {
    set.add(workspaceGroup);
    workspaceGroup.getAllRelatedEntities(set);
  }

  @Override
  public @NotNull UpdateValueListener addUpdateValueListener(
      @NotNull Context context,
      @NotNull String discriminator,
      @NotNull Duration ttl,
      @NotNull JSONObject dynamicParameters,
      @NotNull Consumer<State> listener) {
    context.event().addEventListener(getFullEntityID(), discriminator, ttl, listener);
    return () -> context.event().addEventListener(getFullEntityID(), discriminator, ttl, listener);
  }

  public String getParentId() {
    return getWorkspaceGroup().getEntityID();
  }

  @Override
  public String getParentName() {
    return getWorkspaceGroup().getName();
  }

  @Override
  public String getParentIcon() {
    return getWorkspaceGroup().getIcon();
  }

  @Override
  public String getParentIconColor() {
    return getWorkspaceGroup().getIconColor();
  }

  @Override
  public String getParentDescription() {
    return getWorkspaceGroup().getDescription();
  }

  @Override
  public SelectionParent getSuperParent() {
    if (workspaceGroup.getParent() != null) {
      return workspaceGroup.getParent();
    }
    return null;
  }

  @UIField(order = 100)
  @UIFieldShowOnCondition("return context.get('restriction') == 'Float'")
  public @Nullable Float getMin() {
    return getJsonData().has("min") ? getJsonData().getFloat("min") : null;
  }

  public void setMin(@Nullable Float min) {
    setJsonData("min", min);
  }

  @UIField(order = 110)
  @UIFieldShowOnCondition("return context.get('restriction') == 'Float'")
  public @Nullable Float getMax() {
    return getJsonData().has("max") ? getJsonData().getFloat("max") : null;
  }

  public void setMax(@Nullable Float max) {
    setJsonData("max", max);
  }

  @UIField(order = 110)
  @UIFieldShowOnCondition("return context.get('restriction') == 'Float'")
  public @Nullable Float getStep() {
    return getJsonData().has("step") ? getJsonData().getFloat("step") : null;
  }

  public void setStep(@Nullable Float step) {
    setJsonData("step", step);
  }

  @Override
  public @NotNull List<Object[]> getTimeValueSeries(PeriodRequest request) {
    return ((ContextVarImpl) request.context().var()).getTimeSeries(getEntityID(), request);
  }

  @Override
  public Object getStatusValue(GetStatusValueRequest request) {
    return request.context().var().getRawValue(getEntityID());
  }

  @Override
  public ValueType getValueType() {
    return switch (restriction) {
      case Bool -> ValueType.Boolean;
      case Float -> ValueType.Float;
      case Json, Color, Enum -> ValueType.String;
      default -> ValueType.Unknown;
    };
  }

  @Override
  public SourceHistory getSourceHistory(GetStatusValueRequest request) {
    SourceHistory sourceHistory =
        ((ContextVarImpl) request.context().var())
            .getSourceHistory(getEntityID())
            .setIcon(new Icon(icon, iconColor))
            .setName(getName())
            .setDescription(getDescription());
    sourceHistory.setAttributes(
        new ArrayList<>(
            Arrays.asList(
                "Owner:" + workspaceGroup.getName(),
                "Backup:" + backupDays + " days",
                "Quota:" + quota,
                "Type:" + restriction.name().toLowerCase(),
                "Locked:" + locked,
                "Listeners:" + context().event().getEventCount(getEntityID()),
                "Writable:" + !readOnly)));
    if (unit != null) {
      sourceHistory.getAttributes().add("Unit: " + unit);
    }
    Float min = getMin();
    Float max = getMax();
    if (min != null) {
      sourceHistory.getAttributes().add("Min: " + min);
    }
    if (max != null) {
      sourceHistory.getAttributes().add("Max: " + max);
    }
    sourceHistory.getAttributes().addAll(getAttributes());

    return sourceHistory;
  }

  @Override
  public List<SourceHistoryItem> getSourceHistoryItems(
      GetStatusValueRequest request, int from, int count) {
    return ((ContextVarImpl) request.context().var())
        .getSourceHistoryItems(getEntityID(), from, count);
  }

  @Override
  public void setStatusValue(SetStatusValueRequest request) {
    request.context().var().set(getEntityID(), request.getValue(), true);
  }

  @Override
  public String getStatusValueRepresentation(Context context) {
    Object value = context.var().getRawValue(getEntityID());
    String str = value == null ? null : formatVariableValue(value);
    if (isEmpty(unit)) {
      return str;
    }
    return format("%s <small>%s</small>", Objects.toString(str, "-"), unit);
  }

  @Override
  public @NotNull Icon getSelectionIcon() {
    return new Icon(icon, iconColor);
  }

  @Override
  public @Nullable String getSelectionDescription() {
    return getDescription();
  }

  @Override
  public @NotNull String getId() {
    return getEntityID();
  }

  @Override
  public Object getRawValue() {
    return context().var().getRawValue(getEntityID());
  }

  @Override
  public @NotNull State getValue() {
    State state = Variable.super.getValue();
    if (state instanceof DecimalType dt) {
      return new DecimalType(dt.getValue().setScale(getScale(), RoundingMode.HALF_UP)).setUnit(getUnit());
    }
    return state;
  }

  @Override
  public void set(Object value) {
    context().var().set(getEntityID(), value);
  }

  @Override
  public double getMinValue(double defValue) {
    Float min = this.getMin();
    return min == null ? defValue : min;
  }

  @Override
  public double getMaxValue(double defValue) {
    Float max = this.getMax();
    return max == null ? defValue : max;
  }

  @Override
  public double getStep(double defValue) {
    Float step = this.getStep();
    return step == null ? defValue : step;
  }

  @Override
  public boolean isWritable() {
    return !readOnly;
  }

  @Override
  public void addListener(
      @NotNull String key, @NotNull ThrowingConsumer<State, Exception> callback) {
    context()
        .event()
        .addEventBehaviourListener(
            getFullEntityID(),
            key,
            state -> {
              try {
                callback.accept(state);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Override
  public void removeListener(@NotNull String key) {
    context().event().removeEventListener(key, getEntityID());
  }

  @Override
  public boolean isAbleToSetValue() {
    return !readOnly;
  }

  @Override
  public String toString() {
    return "Variable: " + getTitle();
  }

  public List<String> getAttributes() {
    return getJsonDataList("attr");
  }

  public WorkspaceVariable setAttributes(List<String> attributes) {
    if (attributes != null && !attributes.isEmpty()) {
      setJsonData("attr", String.join(LIST_DELIMITER, attributes));
    }
    return this;
  }

  public boolean tryUpdateVariable(
      @Nullable String variableId,
      @NotNull String variableName,
      @NotNull Consumer<WorkspaceVariable> builder,
      @NotNull VariableType variableType) {
    return tryUpdateEntity(
        () -> {
          this.setName(variableName);
          this.restriction = variableType;
          if (variableId != null) {
            this.setEntityID(variableId);
          }
          builder.accept(this);
        });
  }

  @Override
  public long getChildEntityHashCode() {
    int result = description != null ? description.hashCode() : 0;
    result = 31 * result + (restriction != null ? restriction.hashCode() : 0);
    result = 31 * result + quota;
    result = 31 * result + (readOnly ? 1 : 0);
    result = 31 * result + backupDays;
    result = 31 * result + (locked ? 1 : 0);
    result = 31 * result + (icon != null ? icon.hashCode() : 0);
    result = 31 * result + (iconColor != null ? iconColor.hashCode() : 0);
    result = 31 * result + (unit != null ? unit.hashCode() : 0);
    result = 31 * result + jsonData.toString().hashCode();
    return result;
  }

  @Override
  protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {
    if (StringUtils.isEmpty(getName())) {
      fields.add("name");
    }
  }

  @SneakyThrows
  public @NotNull List<TransformVariableSource> getSources() {
    if (getJsonData().has("sources")) {
      return OBJECT_MAPPER.readValue(getJsonData("sources"), new TypeReference<>() {});
    }
    return List.of();
  }

  @SneakyThrows
  public void setSources(List<TransformVariableSource> sources) {
    sources =
        sources.stream()
            .filter(
                s -> StringUtils.isNotBlank(s.getType()) && StringUtils.isNotBlank(s.getValue()))
            .toList();
    String value = OBJECT_MAPPER.writeValueAsString(sources);
    setJsonData("sources", value);
  }

  public String getCode() {
    return getJsonData("code");
  }

  public void setCode(String code) {
    setJsonData("code", code);
  }

  public @NotNull VarType getVarType() {
    return getJsonDataEnum("vt", VarType.standard);
  }

  public void setVarType(VarType type) {
    setJsonDataEnum("vt", type == VarType.standard ? null : type);
  }

  @JsonIgnore
  public WorkspaceGroup getTopGroup() {
    if (workspaceGroup.getParent() != null) {
      return workspaceGroup.getParent();
    }
    return workspaceGroup;
  }

  @JsonIgnore
  public String getFullEntityID() {
    String key = workspaceGroup.getEntityID() + "-->" + getEntityID();
    WorkspaceGroup parent = workspaceGroup.getParent();
    if (parent != null) {
      key = parent.getEntityID() + "-->" + key;
    }
    return key;
  }

  @JsonIgnore
  public boolean isBackup() {
    return backupDays > 0;
  }

  @Override
  public void configureOptionModel(@NotNull OptionModel optionModel, @NotNull Context context) {
    optionModel.json(
        node -> {
          node.put("type", getRestriction().name());
          if (getMin() != null) {
            node.put("min", getMin());
            node.put("max", getMax());
          }
        });
  }

  public void setScale(int scale) {
    setJsonData("scale", scale);
  }

  @JsonIgnore
  public int getScale() {
    return getJsonData("scale", 2);
  }

  public enum VarType {
    standard,
    transform
  }
}
