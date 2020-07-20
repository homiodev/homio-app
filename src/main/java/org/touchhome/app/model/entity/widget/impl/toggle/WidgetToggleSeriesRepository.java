package org.touchhome.app.model.entity.widget.impl.toggle;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetToggleSeriesRepository extends AbstractRepository<WidgetToggleSeriesEntity> {

    public WidgetToggleSeriesRepository() {
        super(WidgetToggleSeriesEntity.class, "tws_");
    }
}



