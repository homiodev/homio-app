package org.touchhome.app.repository.workspace.var;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.model.workspace.var.WorkspaceVariableGroupEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WorkspaceVariableGroupRepository extends AbstractRepository<WorkspaceVariableGroupEntity> {

    public WorkspaceVariableGroupRepository() {
        super(WorkspaceVariableGroupEntity.class, WorkspaceVariableGroupEntity.PREFIX);
    }
}
