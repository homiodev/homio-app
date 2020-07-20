package org.touchhome.app.repository.workspace.bool;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.model.workspace.bool.WorkspaceBooleanGroupEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WorkspaceBooleanGroupRepository extends AbstractRepository<WorkspaceBooleanGroupEntity> {

    public WorkspaceBooleanGroupRepository() {
        super(WorkspaceBooleanGroupEntity.class, WorkspaceBooleanGroupEntity.PREFIX);
    }
}
