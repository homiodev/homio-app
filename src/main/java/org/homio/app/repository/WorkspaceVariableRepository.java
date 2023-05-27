package org.homio.app.repository;

import org.homio.api.repository.AbstractRepository;
import org.homio.app.model.var.WorkspaceVariable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
