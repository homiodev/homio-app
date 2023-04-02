package org.homio.app.repository;

import org.homio.bundle.api.entity.types.StorageEntity;
import org.homio.bundle.api.repository.AbstractRepository;
import org.springframework.stereotype.Repository;

@Repository("storageRepository")
public class StorageRepository extends AbstractRepository<StorageEntity> {

    public StorageRepository() {
        super(StorageEntity.class);
    }
}
