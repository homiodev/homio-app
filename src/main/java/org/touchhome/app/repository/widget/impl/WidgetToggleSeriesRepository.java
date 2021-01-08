package org.touchhome.app.repository.widget.impl;

import org.springframework.stereotype.Repository;
import org.touchhome.app.model.entity.widget.impl.toggle.WidgetToggleSeriesEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetToggleSeriesRepository extends AbstractRepository<WidgetToggleSeriesEntity> {

    public WidgetToggleSeriesRepository() {
        super(WidgetToggleSeriesEntity.class, "wttgs_");
    }
}
