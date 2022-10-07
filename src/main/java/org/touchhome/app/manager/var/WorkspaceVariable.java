package org.touchhome.app.manager.var;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.json.JSONObject;
import org.touchhome.app.manager.common.impl.EntityContextVarImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextVar;
import org.touchhome.bundle.api.converter.JSONObjectConverter;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.field.*;

import javax.persistence.*;
import java.util.Date;
import java.util.Set;

@Entity
@Setter
@Getter
@Accessors(chain = true)
public class WorkspaceVariable extends BaseEntity<WorkspaceVariable> implements HasJsonData {
    public static final String PREFIX = "wgv_";

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

    @UIField(order = 20)
    @Enumerated(EnumType.STRING)
    public EntityContextVar.VariableType restriction = EntityContextVar.VariableType.Any;

    @UIField(order = 25)
    @UIFieldSlider(min = 100, max = 100000, step = 100)
    @UIFieldGroup(order = 10, value = "Quota")
    public int quota = 1000;

    @UIField(order = 30, readOnly = true)
    @UIFieldProgress
    @UIFieldGroup("Quota")
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
        ((EntityContextVarImpl) entityContext.var()).delete(this);
    }

    @Override
    protected void afterPersist(EntityContext entityContext) {
        ((EntityContextVarImpl) entityContext.var()).createContext(this);
    }

    @Override
    public void afterUpdate(EntityContext entityContext) {
        ((EntityContextVarImpl) entityContext.var()).entityUpdated(this);
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
        setEntityID(PREFIX + variableId);
    }

    @Override
    public void getAllRelatedEntities(Set<BaseEntity> set) {
        super.getAllRelatedEntities(set);
    }
}
