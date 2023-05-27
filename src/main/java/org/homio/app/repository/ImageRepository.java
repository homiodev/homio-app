package org.homio.app.repository;

import org.homio.api.entity.ImageEntity;
import org.homio.api.repository.AbstractRepository;
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
