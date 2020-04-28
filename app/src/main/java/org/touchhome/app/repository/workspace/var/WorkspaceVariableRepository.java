package org.touchhome.app.repository.workspace.var;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.model.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WorkspaceVariableRepository extends AbstractRepository<WorkspaceVariableEntity> {

    public WorkspaceVariableRepository() {
        super(WorkspaceVariableEntity.class, WorkspaceVariableEntity.PREFIX);
    }
}
