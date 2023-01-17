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
import javax.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.touchhome.app.manager.common.impl.EntityContextVarImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextVar;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.api.converter.JSONConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ChartRequest;
import org.touchhome.bundle.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasTimeValueSeries;
import org.touchhome.bundle.api.model.JSON;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldProgress;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.color.UIFieldColorRef;
import org.touchhome.bundle.api.ui.field.condition.UIFieldDisableEditOnCondition;
import org.touchhome.bundle.api.ui.field.condition.UIFieldShowOnCondition;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEntityEditWidth;
import org.touchhome.bundle.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectionParent;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectionParent.SelectionParent;

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
public class WorkspaceVariable extends BaseEntity<WorkspaceVariable>
    implements HasJsonData,
    HasAggregateValueFromSeries,
    UIFieldSelectionParent.SelectionParent,
    HasTimeValueSeries,
    HasGetStatusValue,
    HasSetStatusValue {

    public static final String PREFIX = "wgv_";

    @Override
    @UIField(order = 10, required = true)
    @UIFieldColorRef("color")
    @UIFieldInlineEntityEditWidth(20)
    @UIFieldDisableEditOnCondition("return context.getParent('locked')")
    public String getName() {
        return super.getName();
    }

    @UIField(order = 12, color = "#7A7A7A")
    @UIFieldDisableEditOnCondition("return context.getParent('locked')")
    @UIFieldInlineEntityEditWidth(25)
    private String description;

    @UIField(order = 20, label = "format")
    @Enumerated(EnumType.STRING)
    @UIFieldShowOnCondition("return context.getParent('groupId') !== 'broadcasts'")
    @UIFieldDisableEditOnCondition("return context.getParent('locked')")
    @UIFieldInlineEntityWidth(15)
    @UIFieldInlineEntityEditWidth(20)
    private EntityContextVar.VariableType restriction = EntityContextVar.VariableType.Any;

    @UIField(order = 25)
    @UIFieldSlider(min = 500, max = 100000, step = 500)
    @UIFieldGroup(order = 10, value = "Quota")
    @UIFieldInlineEntityWidth(14)
    private int quota = 1000;

    /**
     * Is it possible to write to variable from UI
     */
    @UIField(order = 25)
    @UIFieldSlider(min = 500, max = 10_000, step = 500)
    @UIFieldGroup(order = 10, value = "Quota")
    @UIFieldInlineEntityWidth(14)
    private boolean readOnly = false;

    @UIField(order = 30, hideInEdit = true)
    @UIFieldProgress
    @UIFieldGroup("Quota")
    @UIFieldInlineEntityWidth(20)
    public UIFieldProgress.Progress getUsedQuota() {
        int count = 0;
        if (getEntityID() != null && getEntityContext().var().exists(getEntityID())) {
            count = (int) getEntityContext().var().count(getEntityID());
        }
        return new UIFieldProgress.Progress(count, this.quota);
    }

    @Column(unique = true, nullable = false)
    private String variableId;

    private String color;

    @ManyToOne(fetch = FetchType.LAZY)
    private WorkspaceGroup workspaceGroup;

    @Getter
    @Column(length = 100)
    @Convert(converter = JSONConverter.class)
    private JSON jsonData = new JSON();

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

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

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void beforePersist() {
        setVariableId(defaultIfEmpty(variableId, "" + System.currentTimeMillis()));
        setEntityID(this.getVariableId());
    }

    @Override
    public void getAllRelatedEntities(Set<BaseEntity> set) {
        set.add(workspaceGroup);
        workspaceGroup.getAllRelatedEntities(set);
    }

    @Override
    public @Nullable Object getAggregateValueFromSeries(@NotNull ChartRequest request, @NotNull AggregationType aggregationType, boolean exactNumber) {
        return ((EntityContextVarImpl) request.getEntityContext().var())
            .aggregate(variableId, request.getFromTime(), request.getToTime(), aggregationType, exactNumber);
    }

    @Override
    public String getAggregateValueDescription() {
        return description;
    }

    @Override
    public void addUpdateValueListener(EntityContext entityContext, String key, JSONObject dynamicParameters, Consumer<Object> listener) {
        entityContext.event().addEventListener(variableId, key, listener);
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

    @Override
    public String getTimeValueSeriesDescription() {
        return getDescription();
    }

    @Override
    public String getGetStatusDescription() {
        return getDescription();
    }

    @Override
    public String getSetStatusDescription() {
        return getDescription();
    }

    @Override
    public List<Object[]> getTimeValueSeries(ChartRequest request) {
        return ((EntityContextVarImpl) request.getEntityContext().var()).getTimeSeries(variableId, request.getFromTime(), request.getToTime());
    }

    @Override
    public Object getStatusValue(GetStatusValueRequest request) {
        return request.getEntityContext().var().get(variableId);
    }

    @Override
    public void setStatusValue(SetStatusValueRequest request) {
        request.getEntityContext().var().set(variableId, request.getValue());
    }

    public WorkspaceVariable(@NotNull String variableId, @NotNull String variableName, @NotNull WorkspaceGroup workspaceGroup,
        @NotNull VariableType variableType, @Nullable String description, @Nullable String color, boolean readOnly) {
        this.variableId = variableId;
        this.readOnly = readOnly;
        this.workspaceGroup = workspaceGroup;
        this.restriction = variableType;
        this.color = color;
        this.description = description;
        this.setName(variableName);
        this.setEntityID(variableId);
    }

    @Override
    public boolean isAbleToSetValue() {
        return !readOnly;
    }
}
