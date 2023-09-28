package org.homio.app.repository.device;

import org.homio.app.model.entity.WorkspaceEntity;
import org.homio.app.repository.AbstractRepository;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceRepository extends AbstractRepository<WorkspaceEntity> {

    public WorkspaceRepository() {
        super(WorkspaceEntity.class);
    }
}
