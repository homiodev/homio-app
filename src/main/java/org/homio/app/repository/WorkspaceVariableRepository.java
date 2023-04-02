package org.homio.app.repository;

import javax.transaction.Transactional;
import org.homio.app.model.var.WorkspaceVariable;
import org.homio.bundle.api.repository.AbstractRepository;
import org.springframework.stereotype.Repository;

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
