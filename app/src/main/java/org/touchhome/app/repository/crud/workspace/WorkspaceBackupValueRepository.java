package org.touchhome.app.repository.crud.workspace;

import org.springframework.stereotype.Repository;
import org.touchhome.app.repository.crud.base.BaseCrudRepository;
import org.touchhome.bundle.api.model.workspace.backup.WorkspaceBackupValueCrudEntity;

@Repository
public interface WorkspaceBackupValueRepository extends BaseCrudRepository<WorkspaceBackupValueCrudEntity> {

}
