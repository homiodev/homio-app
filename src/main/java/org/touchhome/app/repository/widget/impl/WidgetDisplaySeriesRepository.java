package org.touchhome.app.repository.widget.impl;

import org.springframework.stereotype.Repository;
import org.touchhome.app.model.entity.widget.impl.display.WidgetDisplaySeriesEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetDisplaySeriesRepository extends AbstractRepository<WidgetDisplaySeriesEntity> {

    public WidgetDisplaySeriesRepository() {
        super(WidgetDisplaySeriesEntity.class, "wtdps_");
    }
}



