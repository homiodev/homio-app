package org.homio.app.repository.device;

import org.homio.bundle.api.entity.PinBaseEntity;
import org.homio.bundle.api.repository.AbstractRepository;
import org.springframework.stereotype.Repository;

@Repository
public class PinRepository extends AbstractRepository<PinBaseEntity> {

    public PinRepository() {
        super(PinBaseEntity.class);
    }
}
