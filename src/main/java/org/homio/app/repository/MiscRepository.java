package org.homio.app.repository;

import org.homio.api.entity.types.MiscEntity;
import org.homio.api.repository.AbstractRepository;
import org.springframework.stereotype.Repository;

@Repository("miscRepository")
public class MiscRepository extends AbstractRepository<MiscEntity> {

    public MiscRepository() {
        super(MiscEntity.class);
    }
}
