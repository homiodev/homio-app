package org.touchhome.app.repository.workspace.bool;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.entity.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WorkspaceBooleanRepository extends AbstractRepository<WorkspaceBooleanEntity> {

    public WorkspaceBooleanRepository() {
        super(WorkspaceBooleanEntity.class, WorkspaceBooleanEntity.PREFIX);
    }
}
