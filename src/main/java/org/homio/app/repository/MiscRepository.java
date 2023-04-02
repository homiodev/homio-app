package org.homio.app.repository;

import org.homio.bundle.api.entity.types.MiscEntity;
import org.homio.bundle.api.repository.AbstractRepository;
import org.springframework.stereotype.Repository;

@Repository("miscRepository")
public class MiscRepository extends AbstractRepository<MiscEntity> {

    public MiscRepository() {
        super(MiscEntity.class);
    }
}
