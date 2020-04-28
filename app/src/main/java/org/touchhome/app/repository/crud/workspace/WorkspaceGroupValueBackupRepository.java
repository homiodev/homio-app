package org.touchhome.app.repository.crud.workspace;

import org.springframework.stereotype.Repository;
import org.touchhome.app.repository.crud.base.BaseCrudRepository;
import org.touchhome.bundle.api.model.workspace.var.WorkspaceVariableBackupValueEntity;

@Repository
public interface WorkspaceGroupValueBackupRepository extends BaseCrudRepository<WorkspaceVariableBackupValueEntity> {

}
