package org.homio.app.model.var;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextVar;
import org.homio.api.EntityContextVar.VariableType;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.entity.widget.AggregationType;
import org.homio.api.entity.widget.PeriodRequest;
import org.homio.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.entity.widget.ability.HasTimeValueSeries;
import org.homio.api.model.Icon;
import org.homio.api.model.JSON;
import org.homio.api.storage.SourceHistory;
import org.homio.api.storage.SourceHistoryItem;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldProgress;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.color.UIFieldColorRef;
import org.homio.api.ui.field.condition.UIFieldDisableEditOnCondition;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.inline.UIFieldInlineEntityEditWidth;
import org.homio.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.homio.api.ui.field.selection.UIFieldSelectionParent;
import org.homio.api.ui.field.selection.UIFieldSelectionParent.SelectionParent;
import org.homio.app.manager.common.impl.EntityContextVarImpl;
import org.homio.app.repository.VariableDataRepository;
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
    @UIFieldGroup(order = 10, value = "QUOTA")
    @UIFieldInlineEntityWidth(15)
    private int quota = 1000;
    /**
     * Is it possible to write to variable from UI
     */
    @UIField(order = 25, hideInEdit = true)
    @UIFieldGroup(order = 10, value = "QUOTA")
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

    public int getBackupStorageCount() {
        return backup ? getEntityContext().getBean(VariableDataRepository.class).count(variableId) : 0;
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
    @UIFieldGroup("QUOTA")
    @UIFieldInlineEntityWidth(15)
    public UIFieldProgress.Progress getUsedQuota() {
        int count = 0;
        if (getEntityID() != null && getEntityContext().var().exists(getEntityID())) {
            count = (int) getEntityContext().var().count(getEntityID());
        }
        return UIFieldProgress.Progress.of(count, this.quota);
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    /*@Override
    public @NotNull String getTitle() {
        String title = super.getTitle();
        if (isNotEmpty(unit)) {
            return format("%s %s", title, unit);
        }
        return title;
    }*/

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
    public SourceHistory getSourceHistory(GetStatusValueRequest request) {
        SourceHistory sourceHistory = ((EntityContextVarImpl) request.getEntityContext().var())
            .getSourceHistory(variableId)
            .setIcon(new Icon(icon, iconColor))
            .setName(getName())
            .setDescription(getDescription());
        sourceHistory.setAttributes(new ArrayList<>(Arrays.asList(
            "Owner:" + workspaceGroup.getName(),
            "Backup:" + backup,
            "Quota:" + quota,
            "Type:" + restriction.name().toLowerCase(),
            "Writable:" + !readOnly)));
        if (unit != null) {
            sourceHistory.getAttributes().add("Unit: " + unit);
        }
        sourceHistory.getAttributes().addAll(getAttributes());

        return sourceHistory;
    }

    @Override
    public List<SourceHistoryItem> getSourceHistoryItems(GetStatusValueRequest request, int from, int count) {
        return ((EntityContextVarImpl) request.getEntityContext().var()).getSourceHistoryItems(variableId, from, count);
    }

    @Override
    public void setStatusValue(SetStatusValueRequest request) {
        ((EntityContextVarImpl) request.getEntityContext().var())
            .set(variableId, request.getValue(), ignore -> {}, true);
    }

    @Override
    public String getStatusValueRepresentation(EntityContext entityContext) {
        Object value = entityContext.var().get(variableId);
        String str =
            value == null ? null :
                value instanceof Double ? format("%.2f", (Double) value) :
                    value instanceof Float ? format("%.2f", (Float) value) : value.toString();
        if (isEmpty(unit)) {
            return str;
        }
        return format("%s <small>%s</small>", defaultString(str, "-"), unit);
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
            setJsonData("attr", String.join("~~~", attributes));
        }
        return this;
    }

    @Override
    protected void beforePersist() {
        setVariableId(defaultIfEmpty(variableId, "" + System.currentTimeMillis()));
        setEntityID(this.getVariableId());
    }
}
