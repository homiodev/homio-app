package org.touchhome.app.camera.repository;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.app.camera.entity.WidgetCameraSeriesEntity;

@Repository
public class WidgetWebcamSeriesRepository extends AbstractRepository<WidgetCameraSeriesEntity> {

    public WidgetWebcamSeriesRepository() {
        super(WidgetCameraSeriesEntity.class, "wtvds_");
    }
}



