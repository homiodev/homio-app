package org.touchhome.app.model.entity.widget.impl.display;

import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.repository.AbstractRepository;

@Repository
public class WidgetDisplaySeriesRepository extends AbstractRepository<WidgetDisplaySeriesEntity> {

    public WidgetDisplaySeriesRepository() {
        super(WidgetDisplaySeriesEntity.class, "dsw_");
    }
}



