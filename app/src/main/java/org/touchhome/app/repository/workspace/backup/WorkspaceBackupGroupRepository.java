package org.touchhome.app.repository.workspace.backup;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.model.workspace.backup.WorkspaceBackupGroupEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WorkspaceBackupGroupRepository extends AbstractRepository<WorkspaceBackupGroupEntity> {

    public WorkspaceBackupGroupRepository() {
        super(WorkspaceBackupGroupEntity.class, WorkspaceBackupGroupEntity.PREFIX);
    }
}



