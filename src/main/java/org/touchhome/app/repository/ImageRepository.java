package org.touchhome.app.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.bundle.api.entity.ImageEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class ImageRepository extends AbstractRepository<ImageEntity> {

    public static final String PREFIX = "i_";

    public ImageRepository() {
        super(ImageEntity.class, PREFIX);
    }

    @Transactional(readOnly = true)
    public ImageEntity getByPath(String path) {
        return findSingleByField("path", path);
    }
}

















