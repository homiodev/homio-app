package org.touchhome.app.camera.repository;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.app.camera.entity.WidgetCameraEntity;

@Repository
public class WidgetWebcamRepository extends AbstractRepository<WidgetCameraEntity> {

    public WidgetWebcamRepository() {
        super(WidgetCameraEntity.class, "wtvd_");
    }
}



