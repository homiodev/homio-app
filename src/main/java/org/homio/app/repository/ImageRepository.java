package org.homio.app.repository;

import org.homio.api.entity.ImageEntity;
import org.springframework.stereotype.Repository;

@Repository
public class ImageRepository extends AbstractRepository<ImageEntity> {

    public ImageRepository() {
        super(ImageEntity.class);
    }

    public ImageEntity getByPath(String path) {
        /*return tm.executeInTransaction(entityManager -> {
            return findSingle(entityManager, "path", path);
        });*/
        return null;
    }
}
