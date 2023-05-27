package org.homio.app.repository;

import org.homio.api.repository.AbstractRepository;
import org.homio.app.model.var.WorkspaceGroup;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceGroupRepository extends AbstractRepository<WorkspaceGroup> {

    public WorkspaceGroupRepository() {
        super(WorkspaceGroup.class);
    }
}
