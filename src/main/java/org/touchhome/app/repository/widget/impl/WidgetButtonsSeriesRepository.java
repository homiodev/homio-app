package org.touchhome.app.repository.widget.impl;

import org.springframework.stereotype.Repository;
import org.touchhome.app.model.entity.widget.impl.button.WidgetButtonSeriesEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetButtonsSeriesRepository extends AbstractRepository<WidgetButtonSeriesEntity> {

    public WidgetButtonsSeriesRepository() {
        super(WidgetButtonSeriesEntity.class, "wtbs_");
    }
}



