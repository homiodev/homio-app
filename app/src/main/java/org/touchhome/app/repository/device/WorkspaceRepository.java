package org.touchhome.app.repository.device;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.workspace.WorkspaceEntity;

@Repository
public class WorkspaceRepository extends AbstractRepository<WorkspaceEntity> {

    public static final String GENERAL_WORKSPACE_TAB_NAME = "main";

    public WorkspaceRepository() {
        super(WorkspaceEntity.class, WorkspaceEntity.PREFIX);
    }
}
