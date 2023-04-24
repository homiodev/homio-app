package org.homio.app.repository;

import org.homio.bundle.api.entity.types.KeyEntity;
import org.homio.bundle.api.repository.AbstractRepository;
import org.springframework.stereotype.Repository;

@Repository("keyRepository")
public class KeyRepository extends AbstractRepository<KeyEntity> {

    public KeyRepository() {
        super(KeyEntity.class);
    }
}
