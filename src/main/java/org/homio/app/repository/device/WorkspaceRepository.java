package org.homio.app.repository.device;

import org.homio.app.model.entity.WorkspaceEntity;
import org.homio.app.repository.AbstractRepository;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceRepository extends AbstractRepository<WorkspaceEntity> {

    public static final String GENERAL_WORKSPACE_TAB_NAME = "main";

    public WorkspaceRepository() {
        super(WorkspaceEntity.class);
    }
}
