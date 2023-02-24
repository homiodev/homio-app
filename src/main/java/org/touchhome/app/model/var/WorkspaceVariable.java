package org.touchhome.app.model.var;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

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
import org.touchhome.app.repository.VariableDataRepository;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextVar;
import org.touchhome.bundle.api.EntityContextVar.VariableType;
import org.touchhome.bundle.api.converter.JSONConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.PeriodRequest;
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

    @UIField(order = 12, color = "#7A7A7A")
    @UIFieldDisableEditOnCondition("return context.getParent('locked')")
    private String description;

    @UIField(order = 20, label = "format")
    @Enumerated(EnumType.STRING)
    @UIFieldInlineEntityWidth(15)
    @UIFieldInlineEntityEditWidth(10)
    @UIFieldShowOnCondition("return context.getParent('groupId') !== 'broadcasts'")
    @UIFieldDisableEditOnCondition("return context.getParent('locked')")
    private EntityContextVar.VariableType restriction = EntityContextVar.VariableType.Any;

    @UIField(order = 25)
    @UIFieldSlider(min = 500, max = 100000, step = 500)
    @UIFieldGroup(order = 10, value = "Quota")
    @UIFieldInlineEntityWidth(15)
    private int quota = 1000;
    /**
     * Is it possible to write to variable from UI
     */
    @UIField(order = 25, hideInEdit = true)
    @UIFieldGroup(order = 10, value = "Quota")
    @UIFieldInlineEntityWidth(15)
    private boolean readOnly = false;

    @UIField(order = 30)
    @UIFieldInlineEntityWidth(15)
    private boolean backup = false;

    @Column(unique = true, nullable = false)
    private String variableId;

    @UIFieldInlineEntityWidth(15)
    private String color;

    private String icon;
    private String iconColor;
    private String unit;

    @ManyToOne(fetch = FetchType.LAZY)
    private WorkspaceGroup workspaceGroup;

    @Getter
    @Column(length = 1000)
    @Convert(converter = JSONConverter.class)
    private JSON jsonData = new JSON();

    public int getBackupStorageCount() {
        return backup ? getEntityContext().getBean(VariableDataRepository.class).count(variableId) : 0;
    }

    public WorkspaceVariable(@NotNull String variableId, @NotNull String variableName, @NotNull WorkspaceGroup workspaceGroup,
        @NotNull VariableType variableType, @Nullable String description, @Nullable String color, boolean readOnly, @Nullable String unit) {
        this.variableId = variableId;
        this.readOnly = readOnly;
        this.workspaceGroup = workspaceGroup;
        this.restriction = variableType;
        this.color = color;
        this.description = description;
        this.unit = unit;
        this.setName(variableName);
        this.setEntityID(variableId);
    }

    @Override
    @UIField(order = 10, required = true)
    @UIFieldColorRef("color")
    @UIFieldInlineEntityEditWidth(35)
    @UIFieldDisableEditOnCondition("return context.getParent('locked')")
    public String getName() {
        return super.getName();
    }

    @UIField(order = 30, hideInEdit = true, disableEdit = true)
    @UIFieldProgress
    @UIFieldGroup("Quota")
    @UIFieldInlineEntityWidth(15)
    public UIFieldProgress.Progress getUsedQuota() {
        int count = 0;
        if (getEntityID() != null && getEntityContext().var().exists(getEntityID())) {
            count = (int) getEntityContext().var().count(getEntityID());
        }
        return UIFieldProgress.Progress.of(count, this.quota);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getTitle() {
        String title = super.getTitle();
        if (isNotEmpty(unit)) {
            return format("%s %s", title, unit);
        }
        return title;
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
    public void getAllRelatedEntities(Set<BaseEntity> set) {
        set.add(workspaceGroup);
        workspaceGroup.getAllRelatedEntities(set);
    }

    @Override
    public @Nullable Object getAggregateValueFromSeries(@NotNull PeriodRequest request, @NotNull AggregationType aggregationType, boolean exactNumber) {
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
    public List<Object[]> getTimeValueSeries(PeriodRequest request) {
        return ((EntityContextVarImpl) request.getEntityContext().var()).getTimeSeries(variableId, request.getFromTime(), request.getToTime());
    }

    @Override
    public Object getStatusValue(GetStatusValueRequest request) {
        return request.getEntityContext().var().get(variableId);
    }

    @Override
    public void setStatusValue(SetStatusValueRequest request) {
        ((EntityContextVarImpl) request.getEntityContext().var())
            .set(variableId, request.getValue(), ignore -> {}, true);
    }

    @Override
    public String getStatusValueRepresentation(EntityContext entityContext) {
        Object value = entityContext.var().get(variableId);
        return isEmpty(unit) ? value == null ? null : value.toString() : format("%s <small>%s</small>", value == null ? "-" : value, unit);
    }

    @Override
    public boolean isAbleToSetValue() {
        return !readOnly;
    }

    @Override
    public String toString() {
        return "Variable: " + getTitle();
    }

    @Override
    protected void beforePersist() {
        setVariableId(defaultIfEmpty(variableId, "" + System.currentTimeMillis()));
        setEntityID(this.getVariableId());
    }
}
