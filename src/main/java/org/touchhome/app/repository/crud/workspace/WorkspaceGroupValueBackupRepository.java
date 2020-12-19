package org.touchhome.app.repository.crud.workspace;

import org.springframework.stereotype.Repository;
import org.touchhome.app.repository.crud.base.BaseCrudRepository;
import org.touchhome.bundle.api.entity.workspace.var.WorkspaceVariableBackupValueCrudEntity;

@Repository
public interface WorkspaceGroupValueBackupRepository extends BaseCrudRepository<WorkspaceVariableBackupValueCrudEntity> {

}
