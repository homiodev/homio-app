package org.homio.app.repository;

import org.homio.bundle.api.entity.ImageEntity;
import org.homio.bundle.api.repository.AbstractRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ImageRepository extends AbstractRepository<ImageEntity> {

    public ImageRepository() {
        super(ImageEntity.class);
    }

    @Transactional(readOnly = true)
    public ImageEntity getByPath(String path) {
        return findSingleByField("path", path);
    }
}
