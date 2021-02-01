package org.touchhome.app.camera.repository;

import org.springframework.stereotype.Repository;
import org.touchhome.app.camera.entity.IpCameraEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class CameraRepository extends AbstractRepository<IpCameraEntity> {

    public CameraRepository() {
        super(IpCameraEntity.class, IpCameraEntity.PREFIX);
    }
}



