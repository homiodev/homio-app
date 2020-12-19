package org.touchhome.app.repository.workspace;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.entity.workspace.WorkspaceJsonVariableEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WorkspaceJsonVariableRepository extends AbstractRepository<WorkspaceJsonVariableEntity> {

    public WorkspaceJsonVariableRepository() {
        super(WorkspaceJsonVariableEntity.class, WorkspaceJsonVariableEntity.PREFIX);
    }
}
