package org.touchhome.app.manager.var;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.touchhome.app.manager.common.impl.EntityContextVarImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextVar;
import org.touchhome.bundle.api.converter.JSONObjectConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ChartRequest;
import org.touchhome.bundle.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasTimeValueSeries;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldInlineEntityWidth;
import org.touchhome.bundle.api.ui.field.UIFieldProgress;
import org.touchhome.bundle.api.ui.field.UIFieldShowOnCondition;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectionParent;
import org.touchhome.common.util.CommonUtils;
import org.touchhome.common.util.Lang;

@Entity
@Setter
@Getter
@Accessors(chain = true)
@UIFieldSelectionParent(value = "OVERRIDES_BY_INTERFACE", icon = "fas fa-layer-group", iconColor = "#28A60C",
    description = "Group variables")
public class WorkspaceVariable extends BaseEntity<WorkspaceVariable> implements HasJsonData, HasAggregateValueFromSeries,
    UIFieldSelectionParent.SelectionParent, HasTimeValueSeries, HasGetStatusValue, HasSetStatusValue {

  public static final String PREFIX = "wgv_";

  @Override
  public String getEntityPrefix() {
    return PREFIX;
  }

  @Override
  @UIField(order = 10, required = true)
  @UIFieldInlineEntityWidth(viewWidth = 20, editWidth = 20)
  public String getName() {
    return super.getName();
  }

  @UIField(order = 12)
  @UIFieldInlineEntityWidth(viewWidth = 20, editWidth = 20)
  public String description;

  @Override
  @UIFieldIgnore
  @JsonIgnore
  public Date getCreationTime() {
    return super.getCreationTime();
  }

  @Override
  @UIFieldIgnore
  @JsonIgnore
  public Date getUpdateTime() {
    return super.getUpdateTime();
  }

  @UIField(order = 20, label = "format")
  @Enumerated(EnumType.STRING)
  @UIFieldShowOnCondition("return context.item.groupId === 'broadcasts'")
  @UIFieldInlineEntityWidth(viewWidth = 20, editWidth = 20)
  public EntityContextVar.VariableType restriction = EntityContextVar.VariableType.Any;

  @UIField(order = 25)
  @UIFieldSlider(min = 100, max = 100000, step = 100)
  @UIFieldGroup(order = 10, value = "Quota")
  @UIFieldInlineEntityWidth(viewWidth = 15, editWidth = 40)
  public int quota = 1000;

  @UIField(order = 30, readOnly = true)
  @UIFieldProgress
  @UIFieldGroup("Quota")
  @SuppressWarnings("unused")
  @UIFieldInlineEntityWidth(viewWidth = 25, editWidth = 0)
  public UIFieldProgress.Progress getUsedQuota() {
    int count = 0;
    if (getEntityID() != null && getEntityContext().var().exists(getEntityID())) {
      count = (int) getEntityContext().var().count(getEntityID());
    }
    return new UIFieldProgress.Progress(count, this.quota);
  }

  @ManyToOne(fetch = FetchType.LAZY)
  private WorkspaceGroup workspaceGroup;

  @Lob
  @Getter
  @Column(length = 10_000)
  @Convert(converter = JSONObjectConverter.class)
  private JSONObject jsonData = new JSONObject();

  @Column(unique = true, nullable = false)
  public String variableId;

  @Override
  protected void beforePersist() {
    setVariableId(defaultIfEmpty(variableId, CommonUtils.generateUUID()));
    setEntityID(WorkspaceVariable.PREFIX + variableId);
  }

  @Override
  public void getAllRelatedEntities(Set<BaseEntity> set) {
    set.add(workspaceGroup);
  }

  @Override
  public @Nullable Object getAggregateValueFromSeries(@NotNull ChartRequest request, @NotNull AggregationType aggregationType,
      boolean exactNumber) {
    return ((EntityContextVarImpl) request.getEntityContext().var()).aggregate(variableId, request.getFromTime(),
        request.getToTime(), aggregationType, exactNumber);
  }

  @Override
  public String getAggregateValueDescription() {
    return description;
  }

  @Override
  public void addUpdateValueListener(EntityContext entityContext, String key, JSONObject dynamicParameters,
      Consumer<Object> listener) {
    entityContext.event().addEventListener(variableId, key, listener);
  }

  @Override
  public String getParentName() {
    return Lang.getServerMessage(getWorkspaceGroup().getName());
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
  public String getTimeValueSeriesDescription() {
    return "DESCRIPTION.VARIABLE_TIME_SERIES";
  }

  @Override
  public String getGetStatusDescription() {
    return "DESCRIPTION.VARIABLE_GET_STATUS";
  }

  @Override
  public String getSetStatusDescription() {
    return "DESCRIPTION.VARIABLE_SET_STATUS";
  }

  @Override
  public List<Object[]> getTimeValueSeries(ChartRequest request) {
    return ((EntityContextVarImpl) request.getEntityContext().var()).getTimeSeries(variableId, request.getFromTime(),
        request.getToTime());
  }

  @Override
  public Object getStatusValue(GetStatusValueRequest request) {
    return request.getEntityContext().var().get(variableId);
  }

  @Override
  public void setStatusValue(SetStatusValueRequest request) {
    request.getEntityContext().var().set(variableId, request.getValue());
  }
}
