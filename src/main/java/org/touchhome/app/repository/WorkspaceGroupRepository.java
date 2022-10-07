package org.touchhome.app.repository;

import org.springframework.stereotype.Repository;
import org.touchhome.app.manager.var.WorkspaceGroup;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WorkspaceGroupRepository extends AbstractRepository<WorkspaceGroup> {

    public WorkspaceGroupRepository() {
        super(WorkspaceGroup.class);
    }
}
