package org.homio.app.repository;
import org.homio.app.model.entity.ImageEntity;
import org.springframework.stereotype.Repository;

@Repository
public class ImageRepository extends AbstractRepository<ImageEntity> {

    public ImageRepository() {
        super(ImageEntity.class);
    }

    public ImageEntity getByPath(String path) {
        return findSingleByField("path", path);
    }
}
