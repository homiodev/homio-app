package org.touchhome.app.repository;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.entity.StorageEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository("storageRepository")
public class StorageRepository extends AbstractRepository<StorageEntity> {

    public StorageRepository() {
        super(StorageEntity.class);
    }
}
