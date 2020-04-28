package org.touchhome.app.repository.workspace;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.model.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WorkspaceStandaloneVariableRepository extends AbstractRepository<WorkspaceStandaloneVariableEntity> {

    public WorkspaceStandaloneVariableRepository() {
        super(WorkspaceStandaloneVariableEntity.class, WorkspaceStandaloneVariableEntity.PREFIX);
    }
}
