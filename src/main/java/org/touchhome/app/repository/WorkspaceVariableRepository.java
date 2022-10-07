package org.touchhome.app.repository;

import org.springframework.stereotype.Repository;
import org.touchhome.app.manager.var.WorkspaceVariable;
import org.touchhome.bundle.api.repository.AbstractRepository;

import javax.transaction.Transactional;

@Repository
public class WorkspaceVariableRepository extends AbstractRepository<WorkspaceVariable> {

    public WorkspaceVariableRepository() {
        super(WorkspaceVariable.class);
    }

    @Transactional
    public void deleteAll() {
        for (WorkspaceVariable workspaceVariable : listAll()) {
            deleteByEntityID(workspaceVariable.getEntityID());
        }
    }
}

















