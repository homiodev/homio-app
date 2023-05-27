package org.homio.app.repository.device;

import org.homio.api.entity.PinBaseEntity;
import org.homio.api.repository.AbstractRepository;
import org.springframework.stereotype.Repository;

@Repository
public class PinRepository extends AbstractRepository<PinBaseEntity> {

    public PinRepository() {
        super(PinBaseEntity.class);
    }
}
