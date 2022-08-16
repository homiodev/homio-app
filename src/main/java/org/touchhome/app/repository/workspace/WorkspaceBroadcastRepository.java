package org.touchhome.app.repository.workspace;

import org.springframework.stereotype.Repository;
import org.touchhome.app.model.workspace.WorkspaceBroadcastEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository("broadcastRepository")
public class WorkspaceBroadcastRepository extends AbstractRepository<WorkspaceBroadcastEntity> {

    public WorkspaceBroadcastRepository() {
        super(WorkspaceBroadcastEntity.class);
    }
}
