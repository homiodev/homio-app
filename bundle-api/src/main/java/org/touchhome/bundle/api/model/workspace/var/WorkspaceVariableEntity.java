package org.touchhome.bundle.api.model.workspace.var;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.model.BaseEntity;

import javax.persistence.*;
import java.util.Set;

@Entity
@Getter
@Accessors(chain = true)
public class WorkspaceVariableEntity extends BaseEntity<WorkspaceVariableEntity> {

    public static final String PREFIX = "wv_";

    @Setter
    @Column(nullable = false)
    private float value;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    private WorkspaceVariableGroupEntity workspaceVariableGroupEntity;

    @Setter
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "workspaceVariableEntity", cascade = CascadeType.REMOVE)
    private Set<WorkspaceVariableBackupValueCrudEntity> values;

    @Override
    public String getTitle() {
        return "Var: " + getName() + " / group [" + workspaceVariableGroupEntity.getName() + "]";
    }
}
