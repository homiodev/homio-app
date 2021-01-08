package org.touchhome.app.repository.workspace;

import org.springframework.stereotype.Repository;
import org.touchhome.app.repository.widget.HasLastNumberValueRepository;
import org.touchhome.bundle.api.entity.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WorkspaceStandaloneVariableRepository extends AbstractRepository<WorkspaceStandaloneVariableEntity>
        implements HasLastNumberValueRepository<WorkspaceStandaloneVariableEntity> {

    public WorkspaceStandaloneVariableRepository() {
        super(WorkspaceStandaloneVariableEntity.class, WorkspaceStandaloneVariableEntity.PREFIX);
    }

    @Override
    public double getLastNumberValue(WorkspaceStandaloneVariableEntity source) {
        return (double) source.getValue();
    }
}
