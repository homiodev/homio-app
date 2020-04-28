package org.touchhome.bundle.api.model.workspace.var;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.model.BaseEntity;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import java.util.Set;

@Entity
@Getter
@Accessors(chain = true)
public class WorkspaceVariableGroupEntity extends BaseEntity<WorkspaceVariableGroupEntity> {

    public static final String PREFIX = "wvg_";

    @Setter
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "workspaceVariableGroupEntity", cascade = CascadeType.ALL)
    private Set<WorkspaceVariableEntity> workspaceGroupItemVariableEntities;
}
