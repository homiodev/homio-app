package org.touchhome.app.manager.var;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelectionParent;
import org.touchhome.common.util.CommonUtils;
import org.touchhome.common.util.Lang;

import javax.persistence.*;
import java.util.Date;
import java.util.Set;
import java.util.function.Consumer;

@Entity
@Setter
@Getter
@Accessors(chain = true)
@UIFieldSelectionParent(value = "OVERRIDES_BY_INTERFACE", icon = "fas fa-layer-group", iconColor = "#28A60C",
        description = "Group variables")
public class WorkspaceVariable extends BaseEntity<WorkspaceVariable> implements HasJsonData, HasAggregateValueFromSeries,
        UIFieldSelectionParent.SelectionParent {
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

    @UIField(order = 12, label = "shortDescription")
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

    @Override
    public void afterDelete(EntityContext entityContext) {
        ((EntityContextVarImpl) entityContext.var()).afterVariableEntityDeleted(variableId);
    }

    @Override
    protected void afterPersist(EntityContext entityContext) {
        entityContext.var().set(variableId, restriction.getDefaultValue());
    }

    @Override
    public void afterUpdate(EntityContext entityContext) {
        ((EntityContextVarImpl) entityContext.var()).variableUpdated(this);
    }

    @Lob
    @Getter
    @Column(length = 10_000)
    @Convert(converter = JSONObjectConverter.class)
    private JSONObject jsonData = new JSONObject();

    @Column(unique = true, nullable = false)
    public String variableId;

    @Override
    protected void beforePersist() {
        if (StringUtils.isEmpty(variableId)) {
            variableId = CommonUtils.generateUUID();
        }
        setEntityID(PREFIX + variableId);
    }

    @Override
    public void getAllRelatedEntities(Set<BaseEntity> set) {
        set.add(workspaceGroup);
    }

    @Override
    public @Nullable Object getAggregateValueFromSeries(@NotNull ChartRequest request, @NotNull AggregationType aggregationType,
                                                        boolean exactNumber) {
        return ((EntityContextVarImpl) request.getEntityContext().var()).aggregate(getVariableId(), request.getFromTime(),
                request.getToTime(), aggregationType, exactNumber);
    }

    @Override
    public String getAggregateValueDescription() {
        return description;
    }

    @Override
    public void addUpdateValueListener(EntityContext entityContext, String key, JSONObject dynamicParameters,
                                       Consumer<Object> listener) {

    }

    @Override
    public String getParentName() {
        return Lang.getServerMessage("VARIABLE_GROUP", "name", getWorkspaceGroup().getName());
    }

    @Override
    public String getParentDescription() {
        return getWorkspaceGroup().getDescription();
    }
}
