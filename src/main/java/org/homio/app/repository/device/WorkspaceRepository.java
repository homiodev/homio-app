package org.homio.app.repository.device;

import org.homio.bundle.api.repository.AbstractRepository;
import org.homio.bundle.api.workspace.WorkspaceEntity;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceRepository extends AbstractRepository<WorkspaceEntity> {

    public static final String GENERAL_WORKSPACE_TAB_NAME = "main";

    public WorkspaceRepository() {
        super(WorkspaceEntity.class);
    }
}
