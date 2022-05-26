package org.touchhome.app.repository;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.entity.types.MiscEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository("miscRepository")
public class MiscRepository extends AbstractRepository<MiscEntity> {

    public MiscRepository() {
        super(MiscEntity.class);
    }
}



