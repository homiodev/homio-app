package org.touchhome.app.repository.device;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.entity.workspace.WorkspaceShareVariableEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WorkspaceVariableEntityRepository extends AbstractRepository<WorkspaceShareVariableEntity> {

    public WorkspaceVariableEntityRepository() {
        super(WorkspaceShareVariableEntity.class, WorkspaceShareVariableEntity.PREFIX);
    }
}
